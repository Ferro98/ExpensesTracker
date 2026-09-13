package com.example.expensestracker.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.domain.Balance
import com.example.expensestracker.ui.components.CategorySpendingRow
import com.example.expensestracker.ui.components.EmptyState
import com.example.expensestracker.ui.components.ExpenseRow
import com.example.expensestracker.ui.components.MonthSummaryCard
import com.example.expensestracker.ui.components.SectionHeader
import com.example.expensestracker.ui.month.MonthDetailSheets
import com.example.expensestracker.ui.month.MonthViewModel
import com.example.expensestracker.ui.month.rememberMonthDetailState
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMonthName
import com.example.expensestracker.util.formatMoney
import java.time.LocalDate

/** How many rows each Home section shows before handing off to the full list. */
private const val HOME_CATEGORY_LIMIT = 5
private const val HOME_EXPENSE_LIMIT = 5

/**
 * The "at a glance" screen: always the month in progress (browsing other months is History's and
 * Stats' job), each section cut short with a "see all" link so the whole thing fits a phone
 * screen instead of the endless scroll it used to be.
 */
@Composable
fun HomeScreen(
    viewModel: MonthViewModel,
    onEditExpense: (Expense) -> Unit,
    onDuplicateExpense: (Expense) -> Unit,
    onAddExpense: () -> Unit,
    onSeeAllCategories: () -> Unit,
    onSeeAllExpenses: () -> Unit
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
                CategorySpendingRow(category, onClick = { detailState.openCategory(category, uiState) })
            }
        }

        if (uiState.inGroup) {
            item {
                BalanceCard(
                    balance = uiState.balance,
                    myUid = uiState.myUid,
                    partnerName = uiState.partnerName,
                    onRecordSettlement = { showSettlementDialog = true }
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
                    onClick = { detailState.openExpense(expense) }
                )
            }
        }

        // Clears the centred FAB floating above the navigation bar.
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    MonthDetailSheets(
        state = detailState,
        uiState = uiState,
        onEditExpense = onEditExpense,
        onDuplicateExpense = onDuplicateExpense,
        onDeleteExpense = viewModel::deleteExpense
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
private fun BalanceCard(balance: Balance, myUid: String, partnerName: String, onRecordSettlement: () -> Unit) {
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
            Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = onContainerColor)
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
            OutlinedButton(
                onClick = onRecordSettlement,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = onContainerColor),
                border = BorderStroke(1.dp, onContainerColor.copy(alpha = 0.4f))
            ) {
                Text(stringResource(R.string.record_settlement))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettlementDialog(
    myUid: String,
    partnerUid: String,
    partnerName: String,
    currencyRates: List<String>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (fromUid: String, toUid: String, amount: Double, currencyCode: String, note: String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var iPaid by remember { mutableStateOf(true) }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var note by remember { mutableStateOf("") }

    val amount = amountText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_settlement)) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = iPaid, onClick = { iPaid = true }, label = { Text(stringResource(R.string.settlement_i_paid, partnerName)) })
                    FilterChip(selected = !iPaid, onClick = { iPaid = false }, label = { Text(stringResource(R.string.settlement_partner_paid_me, partnerName)) })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d{0,7}([.,]\\d{0,2})?$"))) amountText = input
                    },
                    label = { Text(stringResource(R.string.label_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencyRates.forEach { code ->
                        FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.label_note_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amount != null && amount > 0) {
                        val fromUid = if (iPaid) myUid else partnerUid
                        val toUid = if (iPaid) partnerUid else myUid
                        onSave(fromUid, toUid, amount, currency, note)
                    }
                },
                enabled = amount != null && amount > 0
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
