package com.example.expensestracker.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
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
import com.example.expensestracker.domain.CategoryResolver
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

data class MonthUiState(
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
    /** This month's expenses keyed by the viewer's category id they count toward (see [CategoryResolver]). */
    val categoryExpenses: Map<String, List<Expense>> = emptyMap()
) {
    /** Sum of the personal per-category budgets that have been set. */
    val categoryBudgetTotal: Double
        get() = categorySpending.sumOf { it.monthlyBudget ?: 0.0 }

    /** Categories with something to show, biggest spender first - the order every breakdown uses. */
    val categorySpendingByAmount: List<CategorySpending>
        get() = categorySpending.filter { it.spent > 0 || it.monthlyBudget != null }.sortedByDescending { it.spent }
}

/** One point of the Stats trend chart. */
data class MonthTotal(val yearMonth: YearMonth, val totalSpent: Double)

/** [months] oldest first, ending at the month the chart is centered on. */
data class MonthlyTrend(val months: List<MonthTotal> = emptyList(), val monthlyBudget: Double? = null)

/** One category's spend this month against the same category last month. */
data class CategoryDelta(
    val categoryId: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val currentSpent: Double,
    val previousSpent: Double
) {
    val delta: Double get() = currentSpent - previousSpent
}

/** A month's total against the one before it, plus the categories that moved the most. */
data class MonthComparison(
    val currentMonth: YearMonth = YearMonth.now(),
    val previousMonth: YearMonth = YearMonth.now().minusMonths(1),
    val currentTotal: Double = 0.0,
    val previousTotal: Double = 0.0,
    val topMovers: List<CategoryDelta> = emptyList()
) {
    /** Null when there's nothing last month to compare against - a percentage would be meaningless (or infinite). */
    val percentChange: Double?
        get() = if (previousTotal > 0) (currentTotal - previousTotal) / previousTotal * 100 else null
}

/**
 * One month's worth of derived data, shared by Home, History and Stats - they are three views over
 * the same underlying expenses, so they share a single instance (created in ExpensesTrackerRoot)
 * rather than each registering its own Firestore listeners.
 */
class MonthViewModel(
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

    /** Reactive state scoped to [yearMonth], recomputed as its underlying data changes. */
    fun uiStateFor(yearMonth: YearMonth): Flow<MonthUiState> {
        val monthStart = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()
        return combine(allExpenses, categories, settlementsAndGroup, budgets, currencyRates) {
            expenses, cats, settlementsAndGroupPair, budgetsTriple, rates ->
            val (settlements, group) = settlementsAndGroupPair
            val (monthlyBudget, categoryBudgets, defaultCurrency) = budgetsTriple
            buildUiState(expenses, cats, settlements, group, monthlyBudget, categoryBudgets, rates, defaultCurrency, monthStart, monthEnd)
        }
    }

    /** The empty shell a page shows while its real state is still loading. */
    fun emptyStateFor(yearMonth: YearMonth): MonthUiState =
        MonthUiState(
            monthStart = yearMonth.atDay(1),
            monthEnd = yearMonth.atEndOfMonth(),
            inGroup = groupContext != null,
            myUid = myUid
        )

    /**
     * Spend totals for the [monthsBack] months ending at [yearMonth] (inclusive), oldest first -
     * for the Stats trend chart. Reads off the same [allExpenses] snapshot as everything else
     * (it already holds full history, unfiltered by date), so this needs no separate Firestore query.
     */
    fun monthlyTrendFor(yearMonth: YearMonth, monthsBack: Int = 6): Flow<MonthlyTrend> {
        val months = (monthsBack - 1 downTo 0).map { yearMonth.minusMonths(it.toLong()) }
        return combine(allExpenses, budgets) { expenses, budgetsTriple ->
            val (monthlyBudget, _, _) = budgetsTriple
            val totals = months.map { ym ->
                val start = ym.atDay(1)
                val end = ym.atEndOfMonth()
                val spent = expenses.filter { !it.localDate.isBefore(start) && !it.localDate.isAfter(end) }.sumOf { it.shareFor(myUid) }
                MonthTotal(ym, spent)
            }
            MonthlyTrend(totals, monthlyBudget)
        }
    }

    /** How [yearMonth] compares to the month right before it, in total and by category. */
    fun comparisonFor(yearMonth: YearMonth): Flow<MonthComparison> {
        val previousMonth = yearMonth.minusMonths(1)
        return combine(allExpenses, categories, budgets) { expenses, cats, budgetsTriple ->
            val (_, categoryBudgets, _) = budgetsTriple
            val current = categorySpendingFor(expenses, cats, categoryBudgets, yearMonth)
            val previous = categorySpendingFor(expenses, cats, categoryBudgets, previousMonth)
            val previousById = previous.associateBy { it.categoryId }
            // Sorted by absolute change, not signed: a category that dropped to zero is just as
            // notable a "mover" as one that suddenly appeared.
            val topMovers = current
                .map { cur -> CategoryDelta(cur.categoryId, cur.name, cur.icon, cur.colorHex, cur.spent, previousById[cur.categoryId]?.spent ?: 0.0) }
                .filter { it.currentSpent > 0 || it.previousSpent > 0 }
                .sortedByDescending { kotlin.math.abs(it.delta) }
                .take(3)
            MonthComparison(
                currentMonth = yearMonth,
                previousMonth = previousMonth,
                currentTotal = current.sumOf { it.spent },
                previousTotal = previous.sumOf { it.spent },
                topMovers = topMovers
            )
        }
    }

    /**
     * Per-category spend for one month, independent of [buildUiState] which computes the same
     * thing but bundled with the synthetic "Shared" fallback bucket and the raw expense grouping
     * that only the single-month view needs. Kept separate rather than refactored together so this
     * addition can't regress the already-shipped month view.
     */
    private fun categorySpendingFor(expenses: List<Expense>, categories: List<Category>, categoryBudgets: Map<String, Double>, yearMonth: YearMonth): List<CategorySpending> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        val monthExpenses = expenses.filter { !it.localDate.isBefore(start) && !it.localDate.isAfter(end) }
        val categoryExpenses = monthExpenses.groupBy { CategoryResolver.resolve(it, categories)?.id }
        return categories.map { category ->
            CategorySpending(
                categoryId = category.id,
                name = category.name,
                icon = category.icon,
                colorHex = category.colorHex,
                monthlyBudget = categoryBudgets[category.id],
                spent = categoryExpenses[category.id].orEmpty().sumOf { it.shareFor(myUid) }
            )
        }
    }

    private fun buildUiState(
        expenses: List<Expense>,
        categories: List<Category>,
        settlements: List<Settlement>,
        group: Group?,
        monthlyBudget: Double?,
        categoryBudgets: Map<String, Double>,
        currencyRates: List<CurrencyRate>,
        defaultCurrency: String,
        monthStart: LocalDate,
        monthEnd: LocalDate
    ): MonthUiState {
        val monthExpenses = expenses.filter { !it.localDate.isBefore(monthStart) && !it.localDate.isAfter(monthEnd) }
        val myMonthSpend = monthExpenses.sumOf { it.shareFor(myUid) }

        // Each expense counts toward one of the viewer's own categories, matched by id or - for
        // a shared expense the partner filed under one of *their* private categories - by name,
        // falling back to "Other". Only when the viewer has no "Other" category at all does
        // anything land in the synthetic bucket, so that spend never silently disappears.
        val categoryExpenses = monthExpenses.groupBy { CategoryResolver.resolve(it, categories)?.id ?: SHARED_BUCKET_ID }
        val categorySpending = categories.map { category ->
            CategorySpending(
                categoryId = category.id,
                name = category.name,
                icon = category.icon,
                colorHex = category.colorHex,
                monthlyBudget = categoryBudgets[category.id],
                spent = categoryExpenses[category.id].orEmpty().sumOf { it.shareFor(myUid) }
            )
        }
        val unresolvedSpend = categoryExpenses[SHARED_BUCKET_ID].orEmpty().sumOf { it.shareFor(myUid) }
        val categorySpendingWithFallback = if (unresolvedSpend > 0) {
            categorySpending + CategorySpending(
                categoryId = SHARED_BUCKET_ID, name = sharedCategoryLabel, icon = "🤝", colorHex = "#8D6E63",
                monthlyBudget = null, spent = unresolvedSpend
            )
        } else categorySpending

        val sortedMonthExpenses = monthExpenses
            .sortedWith(compareByDescending<Expense> { it.localDate }.thenByDescending { it.createdAt?.seconds ?: 0 })

        val sharedExpenses = expenses.filter { it.isShared }
        val balance = group?.let { BalanceCalculator.compute(sharedExpenses, settlements, it.memberUids) } ?: Balance(0.0, null, null)
        val partnerUid = group?.otherMemberUid(myUid)
        val partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner"

        return MonthUiState(
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
            categoryExpenses = categoryExpenses
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
