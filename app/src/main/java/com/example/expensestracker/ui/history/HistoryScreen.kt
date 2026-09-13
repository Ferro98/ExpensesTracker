package com.example.expensestracker.ui.history

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
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.ui.components.DayHeader
import com.example.expensestracker.ui.components.EmptyState
import com.example.expensestracker.ui.components.ExpenseRow
import com.example.expensestracker.ui.month.MonthDetailSheets
import com.example.expensestracker.ui.month.MonthPager
import com.example.expensestracker.ui.month.MonthUiState
import com.example.expensestracker.ui.month.MonthViewModel
import com.example.expensestracker.ui.month.currentMonthState
import com.example.expensestracker.ui.month.rememberMonthDetailState
import com.example.expensestracker.ui.month.rememberMonthPagerState

/**
 * Every expense of a month, swipeable month by month and grouped by day with a daily subtotal.
 * Search and filters land in Phase 3 (see docs/UX_REDESIGN_PLAN.md 3.4).
 */
@Composable
fun HistoryScreen(
    viewModel: MonthViewModel,
    onEditExpense: (Expense) -> Unit,
    onDuplicateExpense: (Expense) -> Unit
) {
    val pagerState = rememberMonthPagerState()
    val detailState = rememberMonthDetailState()
    val currentState = currentMonthState(viewModel, pagerState)

    MonthPager(viewModel = viewModel, pagerState = pagerState) { uiState ->
        DayGroupedExpenses(uiState, onOpenExpense = detailState::openExpense)
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
private fun DayGroupedExpenses(uiState: MonthUiState, onOpenExpense: (Expense) -> Unit) {
    // monthExpenses already comes newest-first, so groupBy preserves that order for both the days
    // and the expenses inside each day.
    val byDay = uiState.monthExpenses.groupBy { it.localDate }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (byDay.isEmpty()) {
            item { EmptyState(icon = "🧾", title = stringResource(R.string.no_expenses_this_month)) }
        }

        byDay.forEach { (date, expenses) ->
            item(key = "day-$date") {
                DayHeader(date = date, total = expenses.sumOf { it.shareFor(uiState.myUid) })
            }
            items(expenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    myUid = uiState.myUid,
                    partnerName = uiState.partnerName,
                    onClick = { onOpenExpense(expense) }
                )
            }
        }

        // Clears the centred FAB floating above the navigation bar.
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}
