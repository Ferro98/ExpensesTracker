package com.example.expensestracker.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.util.formatMonthLabel
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ~50 years either side of today: enough that the edges are unreachable in practice, cheap
// because pages are composed lazily.
private const val MONTH_PAGE_COUNT = 1201
private const val MONTH_INITIAL_PAGE = MONTH_PAGE_COUNT / 2

@Composable
fun rememberMonthPagerState(): PagerState =
    rememberPagerState(initialPage = MONTH_INITIAL_PAGE) { MONTH_PAGE_COUNT }

/** The month a given pager page stands for, counted off [anchor] (the month the pager opened on). */
fun monthForPage(anchor: YearMonth, page: Int): YearMonth =
    anchor.plusMonths((page - MONTH_INITIAL_PAGE).toLong())

/** Inverse of [monthForPage]: which page shows [target], given the same [anchor] - for "jump to this month" taps (e.g. the Stats trend chart). */
fun pageForMonth(anchor: YearMonth, target: YearMonth): Int =
    MONTH_INITIAL_PAGE + ChronoUnit.MONTHS.between(anchor, target).toInt()

/**
 * State of the month the pager has settled on, for the things that live *outside* the pages and so
 * can't take a page's own state: detail sheets and dialogs.
 */
@Composable
fun currentMonthState(viewModel: MonthViewModel, pagerState: PagerState): MonthUiState {
    val yearMonth = remember(pagerState.currentPage) { monthForPage(viewModel.currentMonth, pagerState.currentPage) }
    val uiState by remember(yearMonth) { viewModel.uiStateFor(yearMonth) }
        .collectAsState(initial = viewModel.emptyStateFor(yearMonth))
    return uiState
}

/**
 * Header ("‹ Settembre 2026 ›") + swipeable month pages, shared by History and Stats: both are the
 * same month-by-month browse over the same [MonthViewModel], differing only in what a page renders.
 */
@Composable
fun MonthPager(
    viewModel: MonthViewModel,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    page: @Composable (MonthUiState) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentYearMonth = remember(pagerState.currentPage) { monthForPage(viewModel.currentMonth, pagerState.currentPage) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = {
                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.cd_previous_month))
            }
            Text(
                text = formatMonthLabel(currentYearMonth),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }) {
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.cd_next_month))
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
            val yearMonth = remember(pageIndex) { monthForPage(viewModel.currentMonth, pageIndex) }
            val uiState by remember(yearMonth) { viewModel.uiStateFor(yearMonth) }
                .collectAsState(initial = viewModel.emptyStateFor(yearMonth))
            page(uiState)
        }
    }
}
