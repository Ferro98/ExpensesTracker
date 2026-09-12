package com.example.expensestracker.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.data.model.RecurringExpense
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.domain.nextOccurrence
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecurringUiState(
    val items: List<RecurringExpense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencyRates: List<CurrencyRate> = emptyList(),
    val myUid: String = "",
    val partnerUid: String? = null,
    val partnerName: String = "Partner",
    val inGroup: Boolean = false,
    /** Active items' cost normalized to a monthly figure (weekly ones scaled by ~4.33 weeks/month), in the base currency. */
    val monthlyTotal: Double = 0.0,
    val defaultShared: Boolean = false,
    val defaultCurrency: String = "EUR"
)

/** Kept UI-string-free (resolved to text in the Composable) so the ViewModel doesn't need a Context. */
enum class RecurringError {
    CATEGORY_NOT_FOUND,
    SAVE_FAILED
}

/** Average weeks per month (52 weeks / 12 months) - used to project a weekly recurring cost onto a monthly total. */
private const val WEEKS_PER_MONTH = 52.0 / 12.0

class RecurringViewModel(
    private val personalExpenseRepository: ExpenseRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val groupContext: GroupContext?,
    private val settingsRepository: SettingsRepository,
    private val myUid: String
) : ViewModel() {
    private val allItems = combine(
        personalExpenseRepository.observeRecurring(),
        groupContext?.expenseRepository?.observeRecurring() ?: flowOf(emptyList())
    ) { personal, group -> personal + group }

    // Combine already sits at the 5-flow overload limit, so the two independent settings flows
    // share one slot rather than pulling in a 6th.
    private val defaultPrefs = combine(
        settingsRepository.defaultSharedForRecurring,
        settingsRepository.defaultCurrency
    ) { defaultShared, defaultCurrency -> defaultShared to defaultCurrency }

    val uiState: StateFlow<RecurringUiState> = combine(
        allItems,
        personalDataRepository.observeCategories(),
        personalDataRepository.observeCurrencyRates(),
        groupContext?.let { it.groupRepository.observeGroup(it.groupId) } ?: flowOf(null),
        defaultPrefs
    ) { items, categories, currencyRates, group, (defaultShared, defaultCurrency) ->
        val partnerUid = group?.otherMemberUid(myUid)
        val today = LocalDate.now()
        val monthlyTotal = items.filter { it.active }.sumOf { item ->
            val rate = currencyRates.firstOrNull { it.code == item.currencyCode }?.rateToBase ?: 1.0
            val amountInBase = item.amount * rate
            when (item.frequency) {
                RecurrenceFrequency.MONTHLY -> amountInBase
                RecurrenceFrequency.WEEKLY -> amountInBase * WEEKS_PER_MONTH
            }
        }
        RecurringUiState(
            // Active items float to the top, then soonest-due first - dayOfPeriod alone mixed
            // day-of-month (1-31) and day-of-week (1-7) semantics and didn't reflect real due order.
            items = items.sortedWith(compareByDescending<RecurringExpense> { it.active }.thenBy { it.nextOccurrence(today) }),
            categories = categories,
            currencyRates = currencyRates,
            myUid = myUid,
            partnerUid = partnerUid,
            partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner",
            inGroup = groupContext != null,
            monthlyTotal = monthlyTotal,
            defaultShared = defaultShared,
            defaultCurrency = defaultCurrency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringUiState(myUid = myUid, inGroup = groupContext != null))

    // Set when the dialog is opened to edit an existing recurring template rather than add a new
    // one; the dialog reads this once to prefill its fields, and saveRecurring() branches on it
    // to update (or move between personal/group scope) instead of creating a fresh document.
    private val _editingRecurring = MutableStateFlow<RecurringExpense?>(null)
    val editingRecurring: StateFlow<RecurringExpense?> = _editingRecurring.asStateFlow()

    fun startEdit(item: RecurringExpense) {
        _editingRecurring.value = item
    }

    fun clearEdit() {
        _editingRecurring.value = null
        _errorMessage.value = null
    }

    private val _errorMessage = MutableStateFlow<RecurringError?>(null)
    val errorMessage: StateFlow<RecurringError?> = _errorMessage.asStateFlow()

    fun saveRecurring(
        categoryId: String,
        amount: Double,
        currencyCode: String,
        note: String?,
        frequency: RecurrenceFrequency,
        dayOfPeriod: Int,
        startDate: LocalDate,
        paidByUid: String,
        isShared: Boolean,
        payerShare: Double,
        reminderDaysBefore: Int?,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            _errorMessage.value = null
            // See AddExpenseViewModel.saveExpense - categoryId is self-corrected client-side the
            // moment it doesn't match this list, so a mismatch here should only ever be transient.
            val category = uiState.value.categories.firstOrNull { it.id == categoryId }
            if (category == null) {
                _errorMessage.value = RecurringError.CATEGORY_NOT_FOUND
                return@launch
            }
            try {
                val shared = isShared && groupContext != null
                val editing = _editingRecurring.value

                val updated = RecurringExpense(
                    id = editing?.id ?: "",
                    categoryId = categoryId,
                    categoryName = category.name,
                    categoryIcon = category.icon,
                    categoryColorHex = category.colorHex,
                    amount = amount,
                    currencyCode = currencyCode,
                    note = note?.takeIf { it.isNotBlank() },
                    frequency = frequency,
                    dayOfPeriod = dayOfPeriod,
                    startDate = startDate.toString(),
                    active = editing?.active ?: true,
                    lastGeneratedDate = editing?.lastGeneratedDate,
                    paidByUid = paidByUid,
                    isShared = shared,
                    payerShare = payerShare,
                    reminderDaysBefore = reminderDaysBefore
                )

                when {
                    editing == null -> repositoryFor(updated).addRecurring(updated)
                    editing.isShared == shared -> repositoryFor(updated).updateRecurring(updated)
                    // Shared flag flipped - personal and group recurring templates live in different
                    // Firestore collections, so "editing" here means deleting the old document and
                    // creating a fresh one in the new scope.
                    else -> {
                        repositoryFor(editing).deleteRecurring(editing.id)
                        repositoryFor(updated).addRecurring(updated)
                    }
                }
                _editingRecurring.value = null
                onSaved()
            } catch (e: Exception) {
                _errorMessage.value = RecurringError.SAVE_FAILED
            }
        }
    }

    fun toggleActive(item: RecurringExpense) {
        viewModelScope.launch { repositoryFor(item).updateRecurring(item.copy(active = !item.active)) }
    }

    fun deleteRecurring(item: RecurringExpense) {
        viewModelScope.launch { repositoryFor(item).deleteRecurring(item.id) }
    }

    private fun repositoryFor(item: RecurringExpense): ExpenseRepository =
        if (item.isShared && groupContext != null) groupContext.expenseRepository else personalExpenseRepository
}
