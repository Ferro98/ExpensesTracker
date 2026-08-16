package com.example.expensestracker.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.data.model.RecurringExpense
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.GroupRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecurringUiState(
    val items: List<RecurringExpense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencyRates: List<CurrencyRate> = emptyList(),
    val myUid: String = "",
    val partnerUid: String? = null,
    val partnerName: String = "Partner"
)

class RecurringViewModel(
    private val repository: ExpenseRepository,
    groupRepository: GroupRepository,
    groupId: String,
    private val myUid: String
) : ViewModel() {
    val uiState: StateFlow<RecurringUiState> = combine(
        repository.observeRecurring(),
        repository.observeCategories(),
        repository.observeCurrencyRates(),
        groupRepository.observeGroup(groupId)
    ) { items, categories, currencyRates, group ->
        val partnerUid = group?.otherMemberUid(myUid)
        RecurringUiState(
            items = items.sortedWith(compareByDescending<RecurringExpense> { it.active }.thenBy { it.dayOfPeriod }),
            categories = categories,
            currencyRates = currencyRates,
            myUid = myUid,
            partnerUid = partnerUid,
            partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringUiState(myUid = myUid))

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
            repository.addRecurring(
                RecurringExpense(
                    categoryId = categoryId,
                    amount = amount,
                    currencyCode = currencyCode,
                    note = note?.takeIf { it.isNotBlank() },
                    frequency = frequency,
                    dayOfPeriod = dayOfPeriod,
                    startDate = startDate.toString(),
                    paidByUid = paidByUid,
                    isShared = isShared,
                    payerShare = payerShare
                )
            )
        }
    }

    fun toggleActive(item: RecurringExpense) {
        viewModelScope.launch { repository.updateRecurring(item.copy(active = !item.active)) }
    }

    fun deleteRecurring(item: RecurringExpense) {
        viewModelScope.launch { repository.deleteRecurring(item.id) }
    }
}
