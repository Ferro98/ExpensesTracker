package com.example.expensestracker.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.data.model.RecurringExpense
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
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
    val inGroup: Boolean = false
)

class RecurringViewModel(
    private val personalExpenseRepository: ExpenseRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val groupContext: GroupContext?,
    private val myUid: String
) : ViewModel() {
    private val allItems = combine(
        personalExpenseRepository.observeRecurring(),
        groupContext?.expenseRepository?.observeRecurring() ?: flowOf(emptyList())
    ) { personal, group -> personal + group }

    val uiState: StateFlow<RecurringUiState> = combine(
        allItems,
        personalDataRepository.observeCategories(),
        personalDataRepository.observeCurrencyRates(),
        groupContext?.let { it.groupRepository.observeGroup(it.groupId) } ?: flowOf(null)
    ) { items, categories, currencyRates, group ->
        val partnerUid = group?.otherMemberUid(myUid)
        RecurringUiState(
            items = items.sortedWith(compareByDescending<RecurringExpense> { it.active }.thenBy { it.dayOfPeriod }),
            categories = categories,
            currencyRates = currencyRates,
            myUid = myUid,
            partnerUid = partnerUid,
            partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner",
            inGroup = groupContext != null
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
    }

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
        payerShare: Double
    ) {
        viewModelScope.launch {
            val category = uiState.value.categories.firstOrNull { it.id == categoryId } ?: return@launch
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
                payerShare = payerShare
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
