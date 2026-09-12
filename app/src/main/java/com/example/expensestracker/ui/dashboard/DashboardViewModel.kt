package com.example.expensestracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.Group
import com.example.expensestracker.data.model.Settlement
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.domain.Balance
import com.example.expensestracker.domain.BalanceCalculator
import com.example.expensestracker.domain.RecurringExpenseGenerator
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class DashboardUiState(
    val monthLabel: String = "",
    val monthStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    val monthEnd: LocalDate = LocalDate.now(),
    val totalSpent: Double = 0.0,
    val monthlyBudget: Double? = null,
    val categorySpending: List<CategorySpending> = emptyList(),
    val monthExpenses: List<Expense> = emptyList(),
    val balance: Balance = Balance(0.0, null, null),
    val inGroup: Boolean = false,
    val myUid: String = "",
    val partnerUid: String? = null,
    val partnerName: String = "Partner",
    val currencyRates: List<CurrencyRate> = emptyList(),
    val defaultCurrency: String = "EUR",
    /** Ids of this device's own categories - lets the UI replicate the "unmatched shared spend
     *  falls into the Condivise bucket" rule below when a viewer taps that bucket for details. */
    val categoryIds: Set<String> = emptySet()
) {
    /** Sum of the personal per-category budgets that have been set. */
    val categoryBudgetTotal: Double
        get() = categorySpending.sumOf { it.monthlyBudget ?: 0.0 }
}

class DashboardViewModel(
    private val personalExpenseRepository: ExpenseRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val groupContext: GroupContext?,
    private val myUid: String,
    private val sharedCategoryLabel: String,
    settingsRepository: SettingsRepository
) : ViewModel() {
    companion object {
        const val SHARED_BUCKET_ID = "__shared__"
    }

    val currentMonth: YearMonth = YearMonth.now()

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            RecurringExpenseGenerator(personalExpenseRepository, personalDataRepository).generateDueExpenses(today)
            groupContext?.let {
                RecurringExpenseGenerator(it.expenseRepository, personalDataRepository).generateDueExpenses(today)
            }
        }
    }

    // Shared, single-subscription sources: kept hot so browsing between months (each of which
    // combines over these) doesn't register a new Firestore listener per visible month.
    private val allExpenses = combine(
        personalExpenseRepository.observeAllExpenses(),
        groupContext?.expenseRepository?.observeAllExpenses() ?: flowOf(emptyList())
    ) { personal, group -> personal + group }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val categories = personalDataRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val settlementsAndGroup = combine(
        groupContext?.settlementRepository?.observeSettlements() ?: flowOf(emptyList()),
        groupContext?.let { it.groupRepository.observeGroup(it.groupId) } ?: flowOf(null)
    ) { settlements, group -> settlements to group }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Settlement>() to null)

    // Combine already sits at the 5-flow overload limit below, so this bundles the two budget
    // prefs with the currency default rather than pulling in a 6th flow.
    private val budgets = combine(
        settingsRepository.myMonthlyBudget,
        settingsRepository.myCategoryBudgets,
        settingsRepository.defaultCurrency
    ) { budget, categoryBudgets, defaultCurrency ->
        Triple(budget, categoryBudgets, defaultCurrency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(null, emptyMap(), "EUR"))

    private val currencyRates = personalDataRepository.observeCurrencyRates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Reactive dashboard state scoped to [yearMonth], recomputed as its underlying data changes. */
    fun uiStateFor(yearMonth: YearMonth): Flow<DashboardUiState> {
        val monthStart = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()
        return combine(allExpenses, categories, settlementsAndGroup, budgets, currencyRates) {
            expenses, cats, settlementsAndGroupPair, budgetsTriple, rates ->
            val (settlements, group) = settlementsAndGroupPair
            val (monthlyBudget, categoryBudgets, defaultCurrency) = budgetsTriple
            buildUiState(expenses, cats, settlements, group, monthlyBudget, categoryBudgets, rates, defaultCurrency, monthStart, monthEnd)
        }
    }

    private fun buildUiState(
        expenses: List<Expense>,
        categories: List<com.example.expensestracker.data.model.Category>,
        settlements: List<Settlement>,
        group: Group?,
        monthlyBudget: Double?,
        categoryBudgets: Map<String, Double>,
        currencyRates: List<CurrencyRate>,
        defaultCurrency: String,
        monthStart: LocalDate,
        monthEnd: LocalDate
    ): DashboardUiState {
        val monthExpenses = expenses.filter { !it.localDate.isBefore(monthStart) && !it.localDate.isAfter(monthEnd) }
        val myMonthSpend = monthExpenses.sumOf { it.shareFor(myUid) }

        val categoryIds = categories.map { it.id }.toSet()
        val categorySpending = categories.map { category ->
            CategorySpending(
                categoryId = category.id,
                name = category.name,
                icon = category.icon,
                colorHex = category.colorHex,
                monthlyBudget = categoryBudgets[category.id],
                spent = monthExpenses.filter { it.categoryId == category.id }.sumOf { it.shareFor(myUid) }
            )
        }
        // Shared expenses categorized by the *other* member reference a category id from
        // their own private list, which we can never resolve here - fold those into one
        // visible "Shared" bucket instead of letting that spend silently disappear.
        val unmatchedSharedSpend = monthExpenses
            .filter { it.isShared && it.categoryId !in categoryIds }
            .sumOf { it.shareFor(myUid) }
        val categorySpendingWithFallback = if (unmatchedSharedSpend > 0) {
            categorySpending + CategorySpending(
                categoryId = SHARED_BUCKET_ID, name = sharedCategoryLabel, icon = "🤝", colorHex = "#8D6E63",
                monthlyBudget = null, spent = unmatchedSharedSpend
            )
        } else categorySpending

        val sortedMonthExpenses = monthExpenses
            .sortedWith(compareByDescending<Expense> { it.localDate }.thenByDescending { it.createdAt?.seconds ?: 0 })

        val sharedExpenses = expenses.filter { it.isShared }
        val balance = group?.let { BalanceCalculator.compute(sharedExpenses, settlements, it.memberUids) } ?: Balance(0.0, null, null)
        val partnerUid = group?.otherMemberUid(myUid)
        val partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner"

        return DashboardUiState(
            monthLabel = monthStart.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                .replaceFirstChar { it.uppercase() } + " " + monthStart.year,
            monthStart = monthStart,
            monthEnd = monthEnd,
            totalSpent = myMonthSpend,
            monthlyBudget = monthlyBudget,
            categorySpending = categorySpendingWithFallback,
            monthExpenses = sortedMonthExpenses,
            balance = balance,
            inGroup = groupContext != null,
            myUid = myUid,
            partnerUid = partnerUid,
            partnerName = partnerName,
            currencyRates = currencyRates,
            defaultCurrency = defaultCurrency,
            categoryIds = categoryIds
        )
    }

    /**
     * Deletes from both scopes rather than trusting [Expense.isShared] to route to the right one.
     * Deleting a nonexistent document is a silent no-op in Firestore, so this is safe either way -
     * and it's the only way to guarantee removal if an expense's stored flag ever disagreed with
     * which collection it actually lives in (e.g. from a stale-ViewModel write in the past).
     */
    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            personalExpenseRepository.deleteExpense(expenseId)
            groupContext?.expenseRepository?.deleteExpense(expenseId)
        }
    }

    fun addSettlement(fromUid: String, toUid: String, amount: Double, currencyCode: String, date: LocalDate, note: String?) {
        val context = groupContext ?: return
        viewModelScope.launch {
            val amountInBaseCurrency = personalDataRepository.convertToBase(amount, currencyCode)
            context.settlementRepository.addSettlement(fromUid, toUid, amount, currencyCode, amountInBaseCurrency, date, note)
        }
    }
}
