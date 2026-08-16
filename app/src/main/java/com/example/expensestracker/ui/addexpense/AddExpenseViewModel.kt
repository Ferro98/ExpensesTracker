package com.example.expensestracker.ui.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.local.entity.CategoryEntity
import com.example.expensestracker.data.local.entity.CurrencyRateEntity
import com.example.expensestracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currencyRates: StateFlow<List<CurrencyRateEntity>> = repository.observeCurrencyRates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveExpense(
        categoryId: Long,
        amount: Double,
        currencyCode: String,
        date: LocalDate,
        note: String?,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            repository.addExpense(
                categoryId = categoryId,
                amount = amount,
                currencyCode = currencyCode,
                date = date,
                note = note?.takeIf { it.isNotBlank() }
            )
            onSaved()
        }
    }
}
