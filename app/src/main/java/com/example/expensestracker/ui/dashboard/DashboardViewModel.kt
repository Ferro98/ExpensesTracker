package com.example.expensestracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.local.dao.CategorySpending
import com.example.expensestracker.data.local.dao.ExpenseWithCategory
import com.example.expensestracker.data.local.entity.ExpenseEntity
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.domain.RecurringExpenseGenerator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class DashboardUiState(
    val monthLabel: String = "",
    val monthStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    val monthEnd: LocalDate = LocalDate.now(),
    val totalSpent: Double = 0.0,
    val monthlyBudget: Double? = null,
    val categorySpending: List<CategorySpending> = emptyList(),
    val recentExpenses: List<ExpenseWithCategory> = emptyList()
)

class DashboardViewModel(
    private val repository: ExpenseRepository,
    settingsRepository: SettingsRepository,
    private val generator: RecurringExpenseGenerator
) : ViewModel() {
    private val today = LocalDate.now()
    private val monthStart = today.withDayOfMonth(1)
    private val monthEnd = today.withDayOfMonth(today.lengthOfMonth())

    init {
        viewModelScope.launch { generator.generateDueExpenses(today) }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeTotalBetween(monthStart, monthEnd),
        settingsRepository.monthlyBudget,
        repository.observeSpendingByCategory(monthStart, monthEnd),
        repository.observeRecent(20)
    ) { total, budget, categorySpending, recent ->
        DashboardUiState(
            monthLabel = monthStart.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                .replaceFirstChar { it.uppercase() } + " " + monthStart.year,
            monthStart = monthStart,
            monthEnd = monthEnd,
            totalSpent = total,
            monthlyBudget = budget,
            categorySpending = categorySpending,
            recentExpenses = recent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(monthStart = monthStart, monthEnd = monthEnd)
    )

    fun deleteExpense(expense: ExpenseWithCategory) {
        viewModelScope.launch {
            repository.deleteExpense(
                ExpenseEntity(
                    id = expense.id,
                    categoryId = expense.categoryId,
                    amount = expense.amount,
                    currencyCode = expense.currencyCode,
                    amountInBaseCurrency = expense.amountInBaseCurrency,
                    date = expense.date,
                    note = expense.note,
                    recurringExpenseId = expense.recurringExpenseId,
                    createdAt = expense.createdAt
                )
            )
        }
    }
}
