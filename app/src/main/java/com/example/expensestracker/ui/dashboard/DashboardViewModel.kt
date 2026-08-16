package com.example.expensestracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.domain.Balance
import com.example.expensestracker.domain.BalanceCalculator
import com.example.expensestracker.domain.RecurringExpenseGenerator
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val SHARED_BUCKET_ID = "__shared__"

data class DashboardUiState(
    val monthLabel: String = "",
    val monthStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    val monthEnd: LocalDate = LocalDate.now(),
    val totalSpent: Double = 0.0,
    val monthlyBudget: Double? = null,
    val categorySpending: List<CategorySpending> = emptyList(),
    val recentExpenses: List<Expense> = emptyList(),
    val balance: Balance = Balance(0.0, null, null),
    val inGroup: Boolean = false,
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
    private val personalExpenseRepository: ExpenseRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val groupContext: GroupContext?,
    private val myUid: String,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val today = LocalDate.now()
    private val monthStart = today.withDayOfMonth(1)
    private val monthEnd = today.withDayOfMonth(today.lengthOfMonth())

    init {
        viewModelScope.launch {
            RecurringExpenseGenerator(personalExpenseRepository, personalDataRepository).generateDueExpenses(today)
            groupContext?.let {
                RecurringExpenseGenerator(it.expenseRepository, personalDataRepository).generateDueExpenses(today)
            }
        }
    }

    private val allExpenses = combine(
        personalExpenseRepository.observeAllExpenses(),
        groupContext?.expenseRepository?.observeAllExpenses() ?: flowOf(emptyList())
    ) { personal, group -> personal + group }

    private val expensesAndCategories = combine(allExpenses, personalDataRepository.observeCategories()) { expenses, categories ->
        expenses to categories
    }
    private val settlementsAndGroup = combine(
        groupContext?.settlementRepository?.observeSettlements() ?: flowOf(emptyList()),
        groupContext?.let { it.groupRepository.observeGroup(it.groupId) } ?: flowOf(null)
    ) { settlements, group -> settlements to group }
    private val budgets = combine(settingsRepository.myMonthlyBudget, settingsRepository.myCategoryBudgets) { budget, categoryBudgets ->
        budget to categoryBudgets
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        expensesAndCategories, settlementsAndGroup, budgets, personalDataRepository.observeCurrencyRates()
    ) { (expenses, categories), (settlements, group), (monthlyBudget, categoryBudgets), currencyRates ->
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
                categoryId = SHARED_BUCKET_ID, name = "Shared", icon = "🤝", colorHex = "#8D6E63",
                monthlyBudget = null, spent = unmatchedSharedSpend
            )
        } else categorySpending

        val recentExpenses = expenses
            .sortedWith(compareByDescending<Expense> { it.localDate }.thenByDescending { it.createdAt?.seconds ?: 0 })
            .take(20)

        val sharedExpenses = expenses.filter { it.isShared }
        val balance = group?.let { BalanceCalculator.compute(sharedExpenses, settlements, it.memberUids) } ?: Balance(0.0, null, null)
        val partnerUid = group?.otherMemberUid(myUid)
        val partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner"

        DashboardUiState(
            monthLabel = monthStart.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                .replaceFirstChar { it.uppercase() } + " " + monthStart.year,
            monthStart = monthStart,
            monthEnd = monthEnd,
            totalSpent = myMonthSpend,
            monthlyBudget = monthlyBudget,
            categorySpending = categorySpendingWithFallback,
            recentExpenses = recentExpenses,
            balance = balance,
            inGroup = groupContext != null,
            myUid = myUid,
            partnerUid = partnerUid,
            partnerName = partnerName,
            currencyRates = currencyRates
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(monthStart = monthStart, monthEnd = monthEnd, inGroup = groupContext != null, myUid = myUid)
    )

    fun deleteExpense(expenseId: String, isShared: Boolean) {
        viewModelScope.launch {
            if (isShared && groupContext != null) groupContext.expenseRepository.deleteExpense(expenseId)
            else personalExpenseRepository.deleteExpense(expenseId)
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
