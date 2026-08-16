package com.example.expensestracker.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.local.entity.CategoryEntity
import com.example.expensestracker.data.local.entity.CurrencyRateEntity
import com.example.expensestracker.data.local.entity.RecurrenceFrequency
import com.example.expensestracker.data.local.entity.RecurringExpenseEntity
import com.example.expensestracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecurringUiState(
    val items: List<RecurringExpenseEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val currencyRates: List<CurrencyRateEntity> = emptyList()
)

class RecurringViewModel(private val repository: ExpenseRepository) : ViewModel() {
    val uiState: StateFlow<RecurringUiState> = combine(
        repository.observeRecurring(),
        repository.observeCategories(),
        repository.observeCurrencyRates()
    ) { items, categories, currencyRates ->
        RecurringUiState(items, categories, currencyRates)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringUiState())

    fun addRecurring(
        categoryId: Long,
        amount: Double,
        currencyCode: String,
        note: String?,
        frequency: RecurrenceFrequency,
        dayOfPeriod: Int,
        startDate: LocalDate
    ) {
        viewModelScope.launch {
            repository.addRecurring(
                RecurringExpenseEntity(
                    categoryId = categoryId,
                    amount = amount,
                    currencyCode = currencyCode,
                    note = note?.takeIf { it.isNotBlank() },
                    frequency = frequency,
                    dayOfPeriod = dayOfPeriod,
                    startDate = startDate
                )
            )
        }
    }

    fun toggleActive(item: RecurringExpenseEntity) {
        viewModelScope.launch { repository.updateRecurring(item.copy(active = !item.active)) }
    }

    fun deleteRecurring(item: RecurringExpenseEntity) {
        viewModelScope.launch { repository.deleteRecurring(item) }
    }
}
