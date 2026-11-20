package com.example.expensestracker.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.data.model.SavingsEntry
import com.example.expensestracker.data.model.SavingsEntryKind
import com.example.expensestracker.data.model.SavingsGoal
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.repository.SavingsRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.domain.SavingsMath
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.YearMonth

/** How long to wait for Firestore's server ack before moving on - a timeout just means "still offline", the write is already in the local cache. */
private const val WRITE_TIMEOUT_MS = 8000L

/** A goal with its own movements, and whether it's personal or shared with the group. */
data class GoalItem(
    val goal: SavingsGoal,
    val entries: List<SavingsEntry>,
    val isGroup: Boolean
) {
    val balance: Double get() = SavingsMath.balance(entries)
    val complete: Boolean get() = SavingsMath.isComplete(goal, entries)
    val progress: Float
        get() = if (goal.targetAmount > 0) (balance / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
}

data class SavingsUiState(
    val loaded: Boolean = false,
    val goals: List<GoalItem> = emptyList(),
    val myUid: String = "",
    val partnerUid: String? = null,
    val partnerName: String = "Partner",
    val inGroup: Boolean = false,
    val currencyRates: List<CurrencyRate> = emptyList(),
    val defaultCurrency: String = "EUR",
    val categories: List<Category> = emptyList()
) {
    val totalSaved: Double get() = goals.sumOf { it.balance }
}

private data class ScopeData(val goals: List<SavingsGoal>, val entries: List<SavingsEntry>)

private fun scopeFlow(repository: SavingsRepository?): Flow<ScopeData> =
    if (repository == null) flowOf(ScopeData(emptyList(), emptyList()))
    else combine(repository.observeGoals(), repository.observeEntries()) { goals, entries -> ScopeData(goals, entries) }

/**
 * Personal and (when in a group) shared savings goals in one list, plus every write on them.
 * Writes never block the screen: Firestore applies them to its local cache at once, so a slow or
 * missing connection only delays the sync, and a genuine failure is surfaced through [writeFailed].
 */
class SavingsViewModel(
    private val personalSavingsRepository: SavingsRepository,
    private val personalExpenseRepository: ExpenseRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val groupContext: GroupContext?,
    settingsRepository: SettingsRepository,
    private val myUid: String
) : ViewModel() {
    val uiState: StateFlow<SavingsUiState> = combine(
        scopeFlow(personalSavingsRepository),
        scopeFlow(groupContext?.savingsRepository),
        groupContext?.let { it.groupRepository.observeGroup(it.groupId) } ?: flowOf(null),
        personalDataRepository.observeCurrencyRates(),
        combine(settingsRepository.defaultCurrency, personalDataRepository.observeCategories()) { currency, categories -> currency to categories }
    ) { personal, shared, group, rates, currencyAndCategories ->
        val partnerUid = group?.otherMemberUid(myUid)
        fun items(scope: ScopeData, isGroup: Boolean) = scope.goals
            .filter { !it.archived }
            .map { goal -> GoalItem(goal, scope.entries.filter { it.goalId == goal.id }, isGroup) }
        val goals = (items(personal, false) + items(shared, true))
            // Still-running goals first, finished ones after; oldest first within each.
            .sortedWith(compareBy<GoalItem> { it.complete }.thenBy { it.goal.createdAt?.seconds ?: Long.MAX_VALUE })
        SavingsUiState(
            loaded = true,
            goals = goals,
            myUid = myUid,
            partnerUid = partnerUid,
            partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner",
            inGroup = groupContext != null,
            currencyRates = rates,
            defaultCurrency = currencyAndCategories.first,
            categories = currencyAndCategories.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SavingsUiState(myUid = myUid, inGroup = groupContext != null))

    private val _writeFailed = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeFailed

    fun clearWriteFailed() {
        _writeFailed.value = false
    }

    private fun repositoryFor(isGroup: Boolean): SavingsRepository =
        if (isGroup && groupContext != null) groupContext.savingsRepository else personalSavingsRepository

    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                withTimeoutOrNull(WRITE_TIMEOUT_MS) { block() }
            } catch (e: Exception) {
                _writeFailed.value = true
            }
        }
    }

    private suspend fun toBase(amount: Double, currencyCode: String): Double =
        personalDataRepository.convertToBase(amount, currencyCode)

    fun addGoal(
        inGroup: Boolean,
        name: String,
        icon: String,
        targetAmount: Double,
        monthlyPlan: Double?,
        currencyCode: String,
        targetDate: LocalDate?
    ) {
        launchWrite {
            repositoryFor(inGroup).addGoal(
                name = name,
                icon = icon,
                targetAmount = toBase(targetAmount, currencyCode),
                targetDate = targetDate,
                ownerUid = myUid,
                ownerMonthlyPlan = monthlyPlan?.let { toBase(it, currencyCode) }
            )
        }
    }

    /** Updates the shared fields and *my* plan only - a group goal's other member keeps their own. */
    fun updateGoal(
        item: GoalItem,
        name: String,
        icon: String,
        targetAmount: Double,
        monthlyPlan: Double?,
        currencyCode: String,
        targetDate: LocalDate?
    ) {
        launchWrite {
            val repository = repositoryFor(item.isGroup)
            repository.updateGoalDetails(item.goal.id, name, icon, toBase(targetAmount, currencyCode), targetDate)
            repository.setMonthlyPlan(item.goal.id, myUid, monthlyPlan?.let { toBase(it, currencyCode) })
        }
    }

    fun deleteGoal(item: GoalItem) {
        launchWrite { repositoryFor(item.isGroup).deleteGoal(item.goal.id) }
    }

    fun addEntry(item: GoalItem, kind: SavingsEntryKind, amount: Double, currencyCode: String, date: LocalDate, note: String?, byUid: String) {
        launchWrite {
            repositoryFor(item.isGroup).addEntry(
                goalId = item.goal.id,
                kind = kind,
                amount = amount,
                currencyCode = currencyCode,
                amountInBaseCurrency = toBase(amount, currencyCode),
                date = date,
                note = note?.takeIf { it.isNotBlank() },
                byUid = byUid
            )
        }
    }

    fun deleteEntry(item: GoalItem, entry: SavingsEntry) {
        launchWrite { repositoryFor(item.isGroup).deleteEntry(entry.id) }
    }

    /** Answers this month's "did you put it in?" prompt, optionally recording what went in (in the base currency). */
    fun checkIn(item: GoalItem, depositBase: Double?) {
        launchWrite {
            val repository = repositoryFor(item.isGroup)
            if (depositBase != null && depositBase > 0) {
                repository.addEntry(
                    goalId = item.goal.id,
                    kind = SavingsEntryKind.DEPOSIT,
                    amount = depositBase,
                    currencyCode = DefaultUserData.BASE_CURRENCY,
                    amountInBaseCurrency = depositBase,
                    date = LocalDate.now(),
                    note = null,
                    byUid = myUid
                )
            }
            repository.markCheckIn(item.goal.id, myUid, YearMonth.now().toString())
        }
    }

    /** Marks the prompt answered without recording anything (e.g. after typing a custom amount through the entry dialog). */
    fun dismissCheckIn(item: GoalItem) {
        launchWrite { repositoryFor(item.isGroup).markCheckIn(item.goal.id, myUid, YearMonth.now().toString()) }
    }

    /**
     * Turns a finished personal pot into the real expense it was saved for: books the expense under
     * [categoryId], then takes the whole balance out. The expense goes first - if this is cut short,
     * the money still shows in the pot where it can be taken out by hand, rather than vanishing
     * from the pot with no expense to account for it. Personal pots only: a shared pot spent as an
     * ordinary shared expense would wrongly credit whoever typed it in on the group balance.
     */
    fun useGoal(item: GoalItem, categoryId: String) {
        if (item.isGroup) return
        val category = uiState.value.categories.firstOrNull { it.id == categoryId } ?: return
        val amount = item.balance
        if (amount <= 0) return
        launchWrite {
            val today = LocalDate.now()
            personalExpenseRepository.addExpense(
                categoryId = category.id,
                categoryName = category.name,
                categoryIcon = category.icon,
                categoryColorHex = category.colorHex,
                amount = amount,
                currencyCode = DefaultUserData.BASE_CURRENCY,
                amountInBaseCurrency = amount,
                date = today,
                note = item.goal.name,
                paidByUid = myUid,
                isShared = false,
                payerShare = 0.5
            )
            personalSavingsRepository.addEntry(
                goalId = item.goal.id,
                kind = SavingsEntryKind.WITHDRAWAL,
                amount = amount,
                currencyCode = DefaultUserData.BASE_CURRENCY,
                amountInBaseCurrency = amount,
                date = today,
                note = item.goal.name,
                byUid = myUid
            )
        }
    }
}
