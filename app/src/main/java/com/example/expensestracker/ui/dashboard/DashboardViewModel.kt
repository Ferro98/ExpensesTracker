package com.example.expensestracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.ExpenseWithCategory
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.GroupRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.domain.Balance
import com.example.expensestracker.domain.BalanceCalculator
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
    val recentExpenses: List<ExpenseWithCategory> = emptyList(),
    val balance: Balance = Balance(0.0, null, null),
    val myUid: String = "",
    val partnerUid: String? = null,
    val partnerName: String = "Partner",
    val currencyRates: List<CurrencyRate> = emptyList()
) {
    /** Sum of the personal per-category budgets that have been set. */
    val categoryBudgetTotal: Double
        get() = categorySpending.sumOf { it.monthlyBudget ?: 0.0 }
}

class DashboardViewModel(
    private val repository: ExpenseRepository,
    groupRepository: GroupRepository,
    settingsRepository: SettingsRepository,
    groupId: String,
    private val myUid: String,
    generator: RecurringExpenseGenerator
) : ViewModel() {
    private val today = LocalDate.now()
    private val monthStart = today.withDayOfMonth(1)
    private val monthEnd = today.withDayOfMonth(today.lengthOfMonth())

    init {
        viewModelScope.launch { generator.generateDueExpenses(today) }
    }

    private val expensesAndCategories = combine(repository.observeAllExpenses(), repository.observeCategories()) { expenses, categories ->
        expenses to categories
    }
    private val settlementsAndGroup = combine(repository.observeSettlements(), groupRepository.observeGroup(groupId)) { settlements, group ->
        settlements to group
    }
    private val budgets = combine(settingsRepository.myMonthlyBudget, settingsRepository.myCategoryBudgets) { budget, categoryBudgets ->
        budget to categoryBudgets
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        expensesAndCategories, settlementsAndGroup, budgets, repository.observeCurrencyRates()
    ) { (expenses, categories), (settlements, group), (monthlyBudget, categoryBudgets), currencyRates ->
        val monthExpenses = expenses.filter { !it.localDate.isBefore(monthStart) && !it.localDate.isAfter(monthEnd) }
        val myMonthSpend = monthExpenses.sumOf { it.shareFor(myUid) }

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

        val categoryMap = categories.associateBy { it.id }
        val recentExpenses = expenses
            .sortedWith(compareByDescending<com.example.expensestracker.data.model.Expense> { it.localDate }
                .thenByDescending { it.createdAt?.seconds ?: 0 })
            .take(20)
            .map { expense ->
                val category = categoryMap[expense.categoryId]
                ExpenseWithCategory(
                    id = expense.id,
                    categoryId = expense.categoryId,
                    amount = expense.amount,
                    currencyCode = expense.currencyCode,
                    amountInBaseCurrency = expense.amountInBaseCurrency,
                    date = expense.localDate,
                    note = expense.note,
                    recurringExpenseId = expense.recurringExpenseId,
                    paidByUid = expense.paidByUid,
                    isShared = expense.isShared,
                    payerShare = expense.payerShare,
                    categoryName = category?.name ?: "—",
                    categoryIcon = category?.icon ?: "📦",
                    categoryColorHex = category?.colorHex ?: "#78909C"
                )
            }

        val balance = group?.let { BalanceCalculator.compute(expenses, settlements, it.memberUids) } ?: Balance(0.0, null, null)
        val partnerUid = group?.otherMemberUid(myUid)
        val partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner"

        DashboardUiState(
            monthLabel = monthStart.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                .replaceFirstChar { it.uppercase() } + " " + monthStart.year,
            monthStart = monthStart,
            monthEnd = monthEnd,
            totalSpent = myMonthSpend,
            monthlyBudget = monthlyBudget,
            categorySpending = categorySpending,
            recentExpenses = recentExpenses,
            balance = balance,
            myUid = myUid,
            partnerUid = partnerUid,
            partnerName = partnerName,
            currencyRates = currencyRates
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(monthStart = monthStart, monthEnd = monthEnd, myUid = myUid)
    )

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch { repository.deleteExpense(expenseId) }
    }

    fun addSettlement(fromUid: String, toUid: String, amount: Double, currencyCode: String, date: LocalDate, note: String?) {
        viewModelScope.launch { repository.addSettlement(fromUid, toUid, amount, currencyCode, date, note) }
    }
}
