package com.example.expensestracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.domain.Balance
import com.example.expensestracker.ui.AppViewModelFactory
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatShortDate
import com.example.expensestracker.util.toColor
import java.time.LocalDate

@Composable
fun DashboardScreen(factory: AppViewModelFactory) {
    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    var showSettlementDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = uiState.monthLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

        item { BudgetOverviewCard(uiState.totalSpent, uiState.monthlyBudget, uiState.categoryBudgetTotal) }

        if (uiState.categorySpending.any { it.spent > 0 || it.monthlyBudget != null }) {
            item {
                Text("By category", style = MaterialTheme.typography.titleMedium)
            }
            items(uiState.categorySpending.filter { it.spent > 0 || it.monthlyBudget != null }) { category ->
                CategorySpendingRow(category)
            }
        }

        item {
            Text("Recent expenses", style = MaterialTheme.typography.titleMedium)
        }

        if (uiState.recentExpenses.isEmpty()) {
            item {
                Text(
                    "No expenses yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(uiState.recentExpenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    myUid = uiState.myUid,
                    partnerName = uiState.partnerName,
                    onDelete = { viewModel.deleteExpense(expense.id, expense.isShared) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(64.dp)) }
    }

    if (showSettlementDialog) {
        val partnerUid = uiState.partnerUid
        if (partnerUid != null) {
            SettlementDialog(
                myUid = uiState.myUid,
                partnerUid = partnerUid,
                partnerName = uiState.partnerName,
                currencyRates = uiState.currencyRates.map { it.code },
                onDismiss = { showSettlementDialog = false },
                onSave = { fromUid, toUid, amount, currencyCode, note ->
                    viewModel.addSettlement(fromUid, toUid, amount, currencyCode, LocalDate.now(), note)
                    showSettlementDialog = false
                }
            )
        }
    }
}

@Composable
private fun BalanceCard(balance: Balance, myUid: String, partnerName: String, onRecordSettlement: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Balance", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            val (text, color) = when {
                balance.owedByUid == null -> "Settled up" to MaterialTheme.colorScheme.onSurface
                balance.owedByUid == myUid ->
                    "You owe $partnerName ${formatMoney(balance.netAmount)}" to MaterialTheme.colorScheme.error
                else ->
                    "$partnerName owes you ${formatMoney(balance.netAmount)}" to MaterialTheme.colorScheme.tertiary
            }
            Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRecordSettlement, modifier = Modifier.fillMaxWidth()) {
                Text("Record a settlement")
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
    onDismiss: () -> Unit,
    onSave: (fromUid: String, toUid: String, amount: Double, currencyCode: String, note: String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var iPaid by remember { mutableStateOf(true) }
    var currency by remember { mutableStateOf("EUR") }
    var note by remember { mutableStateOf("") }

    val amount = amountText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record a settlement") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = iPaid, onClick = { iPaid = true }, label = { Text("I paid $partnerName") })
                    FilterChip(selected = !iPaid, onClick = { iPaid = false }, label = { Text("$partnerName paid me") })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d{0,7}([.,]\\d{0,2})?$"))) amountText = input
                    },
                    label = { Text("Amount") },
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
                    label = { Text("Note (optional)") },
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
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BudgetOverviewCard(totalSpent: Double, monthlyBudget: Double?, categoryBudgetTotal: Double) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Spent this month", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                formatMoney(totalSpent),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (monthlyBudget != null && monthlyBudget > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                val progress = (totalSpent / monthlyBudget).toFloat().coerceIn(0f, 1f)
                val overBudget = totalSpent > monthlyBudget
                val progressColor = when {
                    overBudget -> MaterialTheme.colorScheme.error
                    progress > 0.8f -> Color(0xFFF9A825)
                    else -> MaterialTheme.colorScheme.primary
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = progressColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                val remaining = monthlyBudget - totalSpent
                Text(
                    text = if (remaining >= 0)
                        "Budget: ${formatMoney(monthlyBudget)} · ${formatMoney(remaining)} left"
                    else
                        "Budget: ${formatMoney(monthlyBudget)} · Over by ${formatMoney(-remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (categoryBudgetTotal > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Category budgets", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    val allocationProgress = (categoryBudgetTotal / monthlyBudget).toFloat().coerceIn(0f, 1f)
                    val overAllocated = categoryBudgetTotal > monthlyBudget
                    LinearProgressIndicator(
                        progress = { allocationProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (overAllocated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val unallocated = monthlyBudget - categoryBudgetTotal
                    Text(
                        text = if (unallocated >= 0)
                            "${formatMoney(categoryBudgetTotal)} allocated · ${formatMoney(unallocated)} unallocated"
                        else
                            "${formatMoney(categoryBudgetTotal)} allocated · over by ${formatMoney(-unallocated)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overAllocated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No personal budget set. Set one in Categories.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategorySpendingRow(category: CategorySpending) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(category.colorHex.toColor().copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(category.icon)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Text(
                    text = if (category.monthlyBudget != null)
                        "${formatMoney(category.spent)} / ${formatMoney(category.monthlyBudget)}"
                    else
                        formatMoney(category.spent),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (category.monthlyBudget != null && category.monthlyBudget > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = (category.spent / category.monthlyBudget).toFloat().coerceIn(0f, 1f)
                val overBudget = category.spent > category.monthlyBudget
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (overBudget) MaterialTheme.colorScheme.error else category.colorHex.toColor()
                )
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, myUid: String, partnerName: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(expense.categoryColorHex.toColor().copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(expense.categoryIcon)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(expense.categoryName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val payerLabel = if (expense.paidByUid == myUid) "You" else partnerName
            val sharedLabel = if (expense.isShared) "Paid by $payerLabel" else "Personal"
            val subtitle = listOfNotNull(formatShortDate(expense.localDate), sharedLabel, expense.note?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatMoney(expense.amount, expense.currencyCode), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}
