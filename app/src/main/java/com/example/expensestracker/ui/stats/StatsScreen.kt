package com.example.expensestracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.ui.components.CategoryDonutChart
import com.example.expensestracker.ui.components.CategorySpendingRow
import com.example.expensestracker.ui.components.EmptyState
import com.example.expensestracker.ui.components.MonthSummaryCard
import com.example.expensestracker.ui.components.SectionHeader
import com.example.expensestracker.ui.month.MonthComparison
import com.example.expensestracker.ui.month.MonthDetailSheets
import com.example.expensestracker.ui.month.MonthPager
import com.example.expensestracker.ui.month.MonthUiState
import com.example.expensestracker.ui.month.MonthViewModel
import com.example.expensestracker.ui.month.MonthlyTrend
import com.example.expensestracker.ui.month.currentMonthState
import com.example.expensestracker.ui.month.pageForMonth
import com.example.expensestracker.ui.month.rememberMonthDetailState
import com.example.expensestracker.ui.month.rememberMonthPagerState
import com.example.expensestracker.util.formatMonthName
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Where the month gets broken down: the summary, the 6-month trend, the category donut, and
 * "how does this compare" - see docs/UX_REDESIGN_PLAN.md 3.5. Trend and comparison are fetched
 * per swiped page (like the page's own uiState already is), so the trend window and "vs last
 * month" figures shift together with whichever month is on screen.
 */
@Composable
fun StatsScreen(
    viewModel: MonthViewModel,
    onEditExpense: (Expense) -> Unit,
    onDuplicateExpense: (Expense) -> Unit
) {
    val pagerState = rememberMonthPagerState()
    val detailState = rememberMonthDetailState()
    val currentState = currentMonthState(viewModel, pagerState)
    val coroutineScope = rememberCoroutineScope()

    MonthPager(viewModel = viewModel, pagerState = pagerState) { uiState ->
        val yearMonth = remember(uiState.monthStart) { YearMonth.from(uiState.monthStart) }
        val trend by remember(yearMonth) { viewModel.monthlyTrendFor(yearMonth) }
            .collectAsState(initial = MonthlyTrend())
        val comparison by remember(yearMonth) { viewModel.comparisonFor(yearMonth) }
            .collectAsState(initial = MonthComparison(currentMonth = yearMonth, previousMonth = yearMonth.minusMonths(1)))

        MonthBreakdown(
            uiState = uiState,
            yearMonth = yearMonth,
            trend = trend,
            comparison = comparison,
            onOpenCategory = { detailState.openCategory(it, uiState) },
            onMonthClick = { target ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pageForMonth(viewModel.currentMonth, target))
                }
            }
        )
    }

    MonthDetailSheets(
        state = detailState,
        uiState = currentState,
        onEditExpense = onEditExpense,
        onDuplicateExpense = onDuplicateExpense,
        onDeleteExpense = viewModel::deleteExpense
    )
}

@Composable
private fun MonthBreakdown(
    uiState: MonthUiState,
    yearMonth: YearMonth,
    trend: MonthlyTrend,
    comparison: MonthComparison,
    onOpenCategory: (CategorySpending) -> Unit,
    onMonthClick: (YearMonth) -> Unit
) {
    val categories = uiState.categorySpendingByAmount
    val today = LocalDate.now()
    val isCurrentMonth = yearMonth == YearMonth.now()
    val daysElapsed = if (isCurrentMonth) today.dayOfMonth else null
    val daysLeft = if (!today.isBefore(uiState.monthStart) && !today.isAfter(uiState.monthEnd))
        uiState.monthEnd.dayOfMonth - today.dayOfMonth + 1
    else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            MonthSummaryCard(
                title = stringResource(R.string.spent_this_month),
                totalSpent = uiState.totalSpent,
                monthlyBudget = uiState.monthlyBudget,
                categoryBudgetTotal = uiState.categoryBudgetTotal,
                daysLeft = daysLeft
            )
        }

        if (trend.months.any { it.totalSpent > 0 }) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                        Text(
                            stringResource(R.string.stats_trend_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        MonthlyTrendChart(
                            trend = trend,
                            onMonthClick = onMonthClick,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }

        if (categories.isEmpty()) {
            item { EmptyState(icon = "📊", title = stringResource(R.string.no_expenses_this_month)) }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.stats_donut_title, formatMonthName(yearMonth)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        CategoryDonutChart(
                            categories = categories,
                            onCategoryClick = onOpenCategory
                        )
                    }
                }
            }

            item { MonthComparisonCard(comparison) }

            if (isCurrentMonth && daysElapsed != null && daysElapsed > 0) {
                item {
                    val dailyAverage = uiState.totalSpent / daysElapsed
                    val projectedTotal = dailyAverage * uiState.monthEnd.dayOfMonth
                    PaceCard(
                        dailyAverage = dailyAverage,
                        projectedTotal = projectedTotal,
                        monthlyBudget = uiState.monthlyBudget
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.by_category), topPadding = 10.dp) }
            items(categories, key = { it.categoryId }) { category ->
                CategorySpendingRow(category, onClick = { onOpenCategory(category) })
            }
        }

        // Clears the centred FAB floating above the navigation bar.
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}
