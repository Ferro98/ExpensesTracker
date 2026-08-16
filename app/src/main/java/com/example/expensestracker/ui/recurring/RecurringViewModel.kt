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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun addRecurring(
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
            val repository = if (shared) groupContext.expenseRepository else personalExpenseRepository
            repository.addRecurring(
                RecurringExpense(
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
                    paidByUid = paidByUid,
                    isShared = shared,
                    payerShare = payerShare
                )
            )
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
