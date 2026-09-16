package com.example.expensestracker.ui.group

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensestracker.R
import com.example.expensestracker.data.WriteOutcome
import com.example.expensestracker.domain.Balance
import com.example.expensestracker.ui.AppViewModelFactory
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.Settlement
import com.example.expensestracker.ui.components.EmptyState
import com.example.expensestracker.ui.components.ExpenseDetailSheet
import com.example.expensestracker.ui.components.ExpenseRow
import com.example.expensestracker.ui.components.SettlementDialog
import com.example.expensestracker.ui.onboarding.GroupSetupSection
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatShortDate
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Everything about the shared side of the app in one place: who owes who, how it got that way,
 * and the full history of shared expenses and settlements - see docs/UX_REDESIGN_PLAN.md 3.6.
 * Replaces both Home's balance card content and Settings' old Group section; neither loses
 * anything, it's consolidated here. Not in a group yet? Shows the same setup flow Settings used to.
 */
@Composable
fun GroupScreen(
    factory: AppViewModelFactory,
    onEditExpense: (Expense) -> Unit,
    onDuplicateExpense: (Expense) -> Unit,
    onAddExpense: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val viewModel: GroupViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)
    val deletedMessage = stringResource(R.string.expense_deleted)
    val deleteFailedMessage = stringResource(R.string.expense_delete_failed)

    if (!uiState.inGroup) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            GroupSetupSection(factory)
        }
        return
    }

    var showSettlementDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var expenseDetail by remember { mutableStateOf<Expense?>(null) }
    val isLeaving by viewModel.isLeaving.collectAsState()
    val leaveFailed by viewModel.leaveFailed.collectAsState()
    // primary (cool blue-teal) and secondary (warm coral) - not primary/tertiary as first shipped:
    // tertiary is SeaGreen, close enough in hue to primary's FjordBlue that the two read as
    // near-identical in a thin 4dp stripe (user-reported). secondary is the FAB's own accent
    // colour, about as far from primary on the wheel as this palette has, which is exactly what a
    // "which of two people" indicator needs.
    val payerColorMe = MaterialTheme.colorScheme.primary
    val payerColorPartner = MaterialTheme.colorScheme.secondary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GroupHeader(
                partnerName = uiState.partnerName,
                inviteCode = uiState.group?.id,
                onLeaveGroup = { showLeaveConfirm = true }
            )
        }

        item {
            BalanceHero(
                balance = uiState.balance,
                myUid = uiState.myUid,
                partnerName = uiState.partnerName,
                onRecordSettlement = { showSettlementDialog = true }
            )
        }

        if (uiState.paidByMe > 0 || uiState.paidByPartner > 0) {
            item {
                PaidBreakdownCard(
                    paidByMe = uiState.paidByMe,
                    paidByPartner = uiState.paidByPartner,
                    partnerName = uiState.partnerName
                )
            }
        }

        item {
            Text(
                stringResource(R.string.group_category_hint, uiState.partnerName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.group_activity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (uiState.activity.any { it is GroupActivityItem.ExpenseActivity }) {
                    PayerLegend(myColor = payerColorMe, partnerColor = payerColorPartner, partnerName = uiState.partnerName)
                }
            }
        }

        if (uiState.activity.isEmpty()) {
            item {
                EmptyState(
                    icon = "🤝",
                    title = stringResource(R.string.group_no_activity_yet),
                    action = { Button(onClick = onAddExpense) { Text(stringResource(R.string.cd_add_expense)) } }
                )
            }
        } else {
            items(
                uiState.activity,
                key = { item ->
                    when (item) {
                        is GroupActivityItem.ExpenseActivity -> "e-${item.expense.id}"
                        is GroupActivityItem.SettlementActivity -> "s-${item.settlement.id}"
                    }
                }
            ) { item ->
                when (item) {
                    is GroupActivityItem.ExpenseActivity -> ExpenseRow(
                        expense = item.expense,
                        myUid = uiState.myUid,
                        partnerName = uiState.partnerName,
                        onClick = { expenseDetail = item.expense },
                        modifier = Modifier.animateItem(),
                        accentColor = if (item.expense.paidByUid == uiState.myUid) payerColorMe else payerColorPartner
                    )
                    is GroupActivityItem.SettlementActivity -> SettlementRow(
                        settlement = item.settlement,
                        myUid = uiState.myUid,
                        partnerName = uiState.partnerName,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

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

    expenseDetail?.let { expense ->
        ExpenseDetailSheet(
            expense = expense,
            myUid = uiState.myUid,
            partnerName = uiState.partnerName,
            onDismiss = { expenseDetail = null },
            onEdit = { onEditExpense(expense); expenseDetail = null },
            onDelete = {
                expenseDetail = null
                scope.launch {
                    when (viewModel.deleteExpense(expense.id)) {
                        WriteOutcome.FAILED -> snackbarHostState.showSnackbar(deleteFailedMessage)
                        WriteOutcome.SUCCESS, WriteOutcome.TIMED_OUT -> {
                            val result = snackbarHostState.showSnackbar(
                                message = deletedMessage,
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.restoreExpense(expense)
                        }
                    }
                }
            },
            onDuplicate = { onDuplicateExpense(expense); expenseDetail = null }
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.leave_group_dialog_title)) },
            text = { Text(stringResource(R.string.leave_group_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = { showLeaveConfirm = false; viewModel.leaveGroup {} },
                    enabled = !isLeaving
                ) { Text(stringResource(R.string.action_leave)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (leaveFailed) {
        AlertDialog(
            onDismissRequest = viewModel::clearLeaveFailed,
            title = { Text(stringResource(R.string.leave_group_failed)) },
            confirmButton = {
                TextButton(onClick = viewModel::clearLeaveFailed) { Text(stringResource(R.string.action_ok)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupHeader(partnerName: String, inviteCode: String?, onLeaveGroup: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.group_members_label, stringResource(R.string.you), partnerName),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (inviteCode != null) {
                    Text(
                        stringResource(R.string.invite_code_prefix, inviteCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (inviteCode != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_invite_code)) },
                            onClick = { clipboard.setText(AnnotatedString(inviteCode)); showMenu = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.leave_group)) },
                        onClick = { showMenu = false; onLeaveGroup() }
                    )
                }
            }
        }
    }
}

/**
 * Green when the balance is in your favour, red when it isn't, grey when you're square - same
 * palette Home's own balance summary uses, so the colour means the same thing everywhere.
 */
@Composable
private fun BalanceHero(balance: Balance, myUid: String, partnerName: String, onRecordSettlement: () -> Unit) {
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

/** "How it got that way": each member's lifetime total paid toward shared expenses. */
@Composable
private fun PaidBreakdownCard(paidByMe: Double, paidByPartner: Double, partnerName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.group_paid_breakdown_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            BreakdownRow(stringResource(R.string.you), paidByMe)
            Spacer(Modifier.height(6.dp))
            BreakdownRow(partnerName, paidByPartner)
        }
    }
}

@Composable
private fun BreakdownRow(label: String, amount: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(formatMoney(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** A dot + name for each payer, so the accent stripe on the rows below isn't a mystery colour code. */
@Composable
private fun PayerLegend(myColor: Color, partnerColor: Color, partnerName: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LegendDot(myColor, stringResource(R.string.you))
        LegendDot(partnerColor, partnerName)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Marta → Tu 40 €" - a settlement's row in the shared activity feed, styled to sit next to [ExpenseRow] without competing with it. */
@Composable
private fun SettlementRow(settlement: Settlement, myUid: String, partnerName: String, modifier: Modifier = Modifier) {
    val fromLabel = if (settlement.fromUid == myUid) stringResource(R.string.you) else partnerName
    val toLabel = if (settlement.toUid == myUid) stringResource(R.string.you) else partnerName

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🤝")
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.group_settlement_row, fromLabel, toLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    formatShortDate(settlement.localDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                formatMoney(settlement.amountInBaseCurrency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
