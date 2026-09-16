package com.example.expensestracker.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.domain.Balance
import com.example.expensestracker.ui.components.CategorySpendingRow
import com.example.expensestracker.ui.components.EmptyState
import com.example.expensestracker.ui.components.ExpenseRow
import com.example.expensestracker.ui.components.MonthSummaryCard
import com.example.expensestracker.ui.components.SectionHeader
import com.example.expensestracker.ui.components.SettlementDialog
import com.example.expensestracker.ui.month.MonthDetailSheets
import com.example.expensestracker.ui.month.MonthViewModel
import com.example.expensestracker.ui.month.rememberMonthDetailState
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMonthName
import com.example.expensestracker.util.formatMoney
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** How many rows each Home section shows before handing off to the full list. */
private const val HOME_CATEGORY_LIMIT = 5
private const val HOME_EXPENSE_LIMIT = 5

/** Floor on how long the pull-to-refresh spinner stays up, so a near-instant "nothing to sync" doesn't read as "didn't even try". */
private const val MIN_REFRESH_INDICATOR_MS = 600L

/**
 * The "at a glance" screen: always the month in progress (browsing other months is History's and
 * Stats' job), each section cut short with a "see all" link so the whole thing fits a phone
 * screen instead of the endless scroll it used to be.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MonthViewModel,
    onEditExpense: (Expense) -> Unit,
    onDuplicateExpense: (Expense) -> Unit,
    onAddExpense: () -> Unit,
    onSeeAllCategories: () -> Unit,
    onSeeAllExpenses: () -> Unit,
    onOpenGroup: () -> Unit,
    onDeleteExpense: (Expense) -> Unit
) {
    val month = viewModel.currentMonth
    val uiState by remember { viewModel.uiStateFor(month) }
        .collectAsState(initial = viewModel.emptyStateFor(month))
    val detailState = rememberMonthDetailState()
    var showSettlementDialog by remember { mutableStateOf(false) }

    val categories = uiState.categorySpendingByAmount
    val today = LocalDate.now()
    // Only meaningful while the month is actually running; today can sit outside it if the app
    // was left open across a month boundary.
    val daysLeft = if (!today.isBefore(uiState.monthStart) && !today.isAfter(uiState.monthEnd))
        uiState.monthEnd.dayOfMonth - today.dayOfMonth + 1
    else null

    // Everything here is already live via Firestore's own snapshot listeners - there's no new
    // data pull-to-refresh needs to trigger. It's still a gesture people reach for when something
    // feels stale, so it's wired to a real signal (MonthViewModel.refresh) instead of doing nothing.
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            refreshScope.launch {
                isRefreshing = true
                // When there's nothing queued (the common case), waitForPendingWrites() resolves
                // in a handful of milliseconds - the spinner would flash and vanish, reading as
                // "didn't even try" rather than "already up to date". Runs the minimum-visible
                // delay concurrently with the real check and waits for whichever finishes last,
                // so a slow/offline refresh isn't held back, only a suspiciously fast one is.
                val minVisible = launch { delay(MIN_REFRESH_INDICATOR_MS) }
                viewModel.refresh()
                minVisible.join()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            MonthSummaryCard(
                title = stringResource(R.string.spent_in_month, formatMonthName(month)),
                totalSpent = uiState.totalSpent,
                monthlyBudget = uiState.monthlyBudget,
                categoryBudgetTotal = uiState.categoryBudgetTotal,
                daysLeft = daysLeft
            )
        }

        if (categories.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.by_category),
                    topPadding = 10.dp,
                    actionLabel = if (categories.size > HOME_CATEGORY_LIMIT) stringResource(R.string.see_all) else null,
                    onActionClick = onSeeAllCategories
                )
            }
            items(categories.take(HOME_CATEGORY_LIMIT), key = { it.categoryId }) { category ->
                CategorySpendingRow(
                    category,
                    onClick = { detailState.openCategory(category, uiState) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        if (uiState.inGroup) {
            item {
                BalanceCard(
                    balance = uiState.balance,
                    myUid = uiState.myUid,
                    partnerName = uiState.partnerName,
                    onRecordSettlement = { showSettlementDialog = true },
                    onSeeActivity = onOpenGroup
                )
            }
        }

        item {
            SectionHeader(
                stringResource(R.string.recent_expenses),
                topPadding = 10.dp,
                actionLabel = if (uiState.monthExpenses.size > HOME_EXPENSE_LIMIT) stringResource(R.string.see_all) else null,
                onActionClick = onSeeAllExpenses
            )
        }

        if (uiState.monthExpenses.isEmpty()) {
            item {
                EmptyState(
                    icon = "🧾",
                    title = stringResource(R.string.no_expenses_yet),
                    action = {
                        Button(onClick = onAddExpense) { Text(stringResource(R.string.cd_add_expense)) }
                    }
                )
            }
        } else {
            items(uiState.monthExpenses.take(HOME_EXPENSE_LIMIT), key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    myUid = uiState.myUid,
                    partnerName = uiState.partnerName,
                    onClick = { detailState.openExpense(expense) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Clears the centred FAB floating above the navigation bar.
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
    }

    MonthDetailSheets(
        state = detailState,
        uiState = uiState,
        onEditExpense = onEditExpense,
        onDuplicateExpense = onDuplicateExpense,
        onDeleteExpense = onDeleteExpense
    )

    if (showSettlementDialog) {
        val partnerUid = uiState.partnerUid
        if (partnerUid != null) {
            SettlementDialog(
                myUid = uiState.myUid,
                partnerUid = partnerUid,
                partnerName = uiState.partnerName,
                currencyRates = uiState.currencyRates.map { it.code },
                defaultCurrency = uiState.defaultCurrency,
                initialAmount = uiState.balance.netAmount.takeIf { uiState.balance.owedByUid != null },
                initialIPaid = uiState.balance.owedByUid == uiState.myUid,
                onDismiss = { showSettlementDialog = false },
                onSave = { fromUid, toUid, amount, currencyCode, note ->
                    viewModel.addSettlement(fromUid, toUid, amount, currencyCode, LocalDate.now(), note)
                    showSettlementDialog = false
                }
            )
        }
    }
}

/**
 * Green when the balance is in your favour, red when it isn't, grey when you're square - the
 * semantic palette rather than M3's secondary/tertiary containers, which carried no such meaning.
 */
@Composable
private fun BalanceCard(balance: Balance, myUid: String, partnerName: String, onRecordSettlement: () -> Unit, onSeeActivity: () -> Unit) {
    val settled = balance.owedByUid == null
    val youOwe = balance.owedByUid == myUid
    val semantic = MaterialTheme.semanticColors
    val containerColor = when {
        settled -> MaterialTheme.colorScheme.surfaceVariant
        youOwe -> semantic.negativeContainer
        else -> semantic.positiveContainer
    }
    val onContainerColor = when {
        settled -> MaterialTheme.colorScheme.onSurfaceVariant
        youOwe -> semantic.onNegativeContainer
        else -> semantic.onPositiveContainer
    }
    val emoji = if (settled) "🤝" else if (youOwe) "💸" else "🎉"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = onContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.balance_label).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainerColor.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(6.dp))
            val text = when {
                settled -> stringResource(R.string.balance_settled_up)
                youOwe -> stringResource(R.string.balance_you_owe, partnerName, formatMoney(balance.netAmount))
                else -> stringResource(R.string.balance_owes_you, partnerName, formatMoney(balance.netAmount))
            }
            AnimatedContent(targetState = text, label = "balanceText") { animatedText ->
                Text(animatedText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = onContainerColor)
            }
            if (!settled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    // This is a running lifetime total across every shared expense and settlement,
                    // not scoped to the month being viewed - unlike the numbers above it.
                    stringResource(R.string.balance_lifetime_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainerColor.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onRecordSettlement,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = onContainerColor),
                    border = BorderStroke(1.dp, onContainerColor.copy(alpha = 0.4f))
                ) {
                    Text(stringResource(R.string.record_settlement))
                }
                TextButton(
                    onClick = onSeeActivity,
                    colors = ButtonDefaults.textButtonColors(contentColor = onContainerColor)
                ) {
                    Text(stringResource(R.string.see_group_activity))
                }
            }
        }
    }
}

