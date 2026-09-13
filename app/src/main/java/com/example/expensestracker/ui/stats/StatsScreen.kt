package com.example.expensestracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.ui.components.CategorySpendingRow
import com.example.expensestracker.ui.components.EmptyState
import com.example.expensestracker.ui.components.MonthSummaryCard
import com.example.expensestracker.ui.components.SectionHeader
import com.example.expensestracker.ui.month.MonthDetailSheets
import com.example.expensestracker.ui.month.MonthPager
import com.example.expensestracker.ui.month.MonthUiState
import com.example.expensestracker.ui.month.MonthViewModel
import com.example.expensestracker.ui.month.currentMonthState
import com.example.expensestracker.ui.month.rememberMonthDetailState
import com.example.expensestracker.ui.month.rememberMonthPagerState
import java.time.LocalDate

/**
 * Where the month gets broken down. For now that's the summary plus the full, tappable
 * per-category list the user asked to keep from the old Home; the charts (6-month bars, donut,
 * month-over-month comparison) arrive in Phase 4 - see docs/UX_REDESIGN_PLAN.md 3.5.
 */
@Composable
fun StatsScreen(viewModel: MonthViewModel, onEditExpense: (Expense) -> Unit) {
    val pagerState = rememberMonthPagerState()
    val detailState = rememberMonthDetailState()
    val currentState = currentMonthState(viewModel, pagerState)

    MonthPager(viewModel = viewModel, pagerState = pagerState) { uiState ->
        MonthBreakdown(uiState, onOpenCategory = { detailState.openCategory(it, uiState) })
    }

    MonthDetailSheets(
        state = detailState,
        uiState = currentState,
        onEditExpense = onEditExpense,
        onDeleteExpense = viewModel::deleteExpense
    )
}

@Composable
private fun MonthBreakdown(
    uiState: MonthUiState,
    onOpenCategory: (CategorySpending) -> Unit
) {
    val categories = uiState.categorySpendingByAmount
    val today = LocalDate.now()
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

        if (categories.isEmpty()) {
            item { EmptyState(icon = "📊", title = stringResource(R.string.no_expenses_this_month)) }
        } else {
            item { SectionHeader(stringResource(R.string.by_category), topPadding = 10.dp) }
            items(categories, key = { it.categoryId }) { category ->
                CategorySpendingRow(category, onClick = { onOpenCategory(category) })
            }
        }

        // Clears the centred FAB floating above the navigation bar.
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}
