package com.example.expensestracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.expensestracker.util.localizedCategoryName

/**
 * Every expense of a month, swipeable month by month and grouped by day with a daily subtotal.
 * The search box and filter chips sit above the pager (not inside a page) so they survive
 * swiping between months instead of resetting on every page. See docs/UX_REDESIGN_PLAN.md 3.4.
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

    // Plain remember, not rememberSaveable, matching the rest of the app's transient sheet/filter
    // toggles (e.g. AddExpenseSheet's showDetails): losing the filter on process death is a minor
    // inconvenience, not a functional bug, and a Set<String> is a fragile thing to hand a Bundle.
    var filters by remember { mutableStateOf(HistoryFilterState()) }
    // The categories to offer as filter chips come from the "settled" page rather than whichever
    // page is mid-swipe, so the chip row doesn't reshuffle while scrolling; it's the viewer's full
    // category list regardless of month (MonthViewModel maps every category, not just ones with
    // spend), minus the synthetic "Shared" fallback bucket, which isn't a real filterable category.
    val filterableCategories = currentState.categorySpending.filter { it.categoryId != MonthViewModel.SHARED_BUCKET_ID }

    Column(modifier = Modifier.fillMaxSize()) {
        HistoryFilterBar(
            state = filters,
            onStateChange = { filters = it },
            categories = filterableCategories,
            inGroup = currentState.inGroup,
            partnerName = currentState.partnerName,
            modifier = Modifier.fillMaxWidth()
        )

        MonthPager(viewModel = viewModel, pagerState = pagerState, modifier = Modifier.weight(1f)) { uiState ->
            DayGroupedExpenses(uiState, filters, onOpenExpense = detailState::openExpense)
        }
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
private fun DayGroupedExpenses(uiState: MonthUiState, filters: HistoryFilterState, onOpenExpense: (Expense) -> Unit) {
    // Reverses the already-resolved grouping MonthViewModel computed (via CategoryResolver) into an
    // expenseId -> categoryId lookup, so the category filter matches the same category an expense's
    // amount is actually counted under - never `expense.categoryId` directly, which for a shared
    // expense the partner created only exists in *their* private category list.
    val categoryIdForExpense = uiState.categoryExpenses.flatMap { (catId, exps) -> exps.map { it.id to catId } }.toMap()

    val filtered = uiState.monthExpenses.filter { expense ->
        matchesHistoryFilters(
            expense = expense,
            state = filters,
            categoryIdOf = { categoryIdForExpense[it] },
            myUid = uiState.myUid,
            partnerUid = uiState.partnerUid,
            localizedCategoryName = localizedCategoryName(expense.categoryName)
        )
    }
    // monthExpenses already comes newest-first, so groupBy preserves that order for both the days
    // and the expenses inside each day.
    val byDay = filtered.groupBy { it.localDate }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (byDay.isEmpty()) {
            item {
                if (filters.isActive) {
                    EmptyState(icon = "🔍", title = stringResource(R.string.no_search_results))
                } else {
                    EmptyState(icon = "🧾", title = stringResource(R.string.no_expenses_this_month))
                }
            }
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
