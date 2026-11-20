package com.example.expensestracker.ui.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.SavingsEntry
import com.example.expensestracker.data.model.SavingsEntryKind
import com.example.expensestracker.domain.SavingsMath
import com.example.expensestracker.ui.components.BudgetProgressBar
import com.example.expensestracker.ui.components.CategoryPicker
import com.example.expensestracker.ui.components.EmptyState
import com.example.expensestracker.ui.components.SectionHeader
import com.example.expensestracker.ui.components.SegmentedBudgetBar
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatMonthLabel
import com.example.expensestracker.util.formatShortDate
import java.time.LocalDate
import java.time.YearMonth

/** What the entry dialog was opened for: a goal, and whether it's adding to it or taking out. */
private data class EntryRequest(val goalId: String, val kind: SavingsEntryKind)

/**
 * Every savings pot, personal and shared, with what's in each and how close it is. Tapping one
 * opens its sheet (movements, plan, deposit/withdraw). Created here through the shared "+" slot
 * the other management screens use.
 */
@Composable
fun SavingsScreen(
    viewModel: SavingsViewModel,
    showAddDialog: Boolean,
    onDismissAddDialog: () -> Unit,
    onShowAddDialog: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val uiState by viewModel.uiState.collectAsState()
    val writeFailed by viewModel.writeFailed.collectAsState()
    val failedMessage = stringResource(R.string.error_save_failed)
    LaunchedEffect(writeFailed) {
        if (writeFailed) {
            snackbarHostState.showSnackbar(failedMessage)
            viewModel.clearWriteFailed()
        }
    }

    var selectedGoalId by remember { mutableStateOf<String?>(null) }
    var editingGoalId by remember { mutableStateOf<String?>(null) }
    var entryRequest by remember { mutableStateOf<EntryRequest?>(null) }
    var useGoalId by remember { mutableStateOf<String?>(null) }
    var deleteGoalId by remember { mutableStateOf<String?>(null) }
    // (goal id, amount to suggest) while the "other amount" dialog of a monthly check-in is open.
    var checkInOther by remember { mutableStateOf<Pair<String, Double>?>(null) }

    fun goalById(id: String?) = (uiState.goals + uiState.archivedGoals).firstOrNull { it.goal.id == id }

    // Pots where I planned a monthly amount and haven't yet said whether it went in. A plan never
    // records a deposit by itself - only these answers do, so a pot can't claim money that wasn't
    // actually put aside. The start-of-month notification links straight here.
    val today = LocalDate.now()
    val dueCheckIns = uiState.goals.mapNotNull { item ->
        SavingsMath.checkInDue(item.goal, item.entries, uiState.myUid, today)?.let { item to it }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(dueCheckIns, key = { "checkin-${it.first.goal.id}" }) { (item, remaining) ->
            CheckInCard(
                item = item,
                remaining = remaining,
                onYes = { viewModel.checkIn(item, remaining) },
                onOther = { checkInOther = item.goal.id to remaining },
                onSkip = { viewModel.checkIn(item, null) },
                modifier = Modifier.animateItem()
            )
        }

        if (uiState.goals.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.savings_total_saved),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatMoney(uiState.totalSaved),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.semanticColors.positive
                        )
                    }
                }
            }
            items(uiState.goals, key = { it.goal.id }) { item ->
                GoalCard(
                    item = item,
                    myUid = uiState.myUid,
                    partnerName = uiState.partnerName,
                    onClick = { selectedGoalId = item.goal.id },
                    modifier = Modifier.animateItem()
                )
            }
        } else if (uiState.loaded && uiState.archivedGoals.isEmpty()) {
            item {
                EmptyState(
                    icon = "🐷",
                    title = stringResource(R.string.savings_empty_title),
                    action = { Button(onClick = onShowAddDialog) { Text(stringResource(R.string.savings_empty_action)) } }
                )
            }
        }

        if (uiState.archivedGoals.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.savings_archived), topPadding = 8.dp) }
            items(uiState.archivedGoals, key = { "archived-${it.goal.id}" }) { item ->
                GoalCard(
                    item = item,
                    myUid = uiState.myUid,
                    partnerName = uiState.partnerName,
                    onClick = { selectedGoalId = item.goal.id },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Clears the corner button.
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showAddDialog) {
        GoalEditDialog(
            initial = null,
            inGroup = uiState.inGroup,
            partnerUid = uiState.partnerUid,
            partnerName = uiState.partnerName,
            myUid = uiState.myUid,
            currencyRates = uiState.currencyRates,
            defaultCurrency = uiState.defaultCurrency,
            onDismiss = onDismissAddDialog,
            onSave = { isGroup, name, icon, target, plan, currency, date ->
                viewModel.addGoal(isGroup, name, icon, target, plan, currency, date)
                onDismissAddDialog()
            }
        )
    }

    goalById(editingGoalId)?.let { item ->
        GoalEditDialog(
            initial = item,
            inGroup = uiState.inGroup,
            partnerUid = uiState.partnerUid,
            partnerName = uiState.partnerName,
            myUid = uiState.myUid,
            currencyRates = uiState.currencyRates,
            defaultCurrency = uiState.defaultCurrency,
            onDismiss = { editingGoalId = null },
            onSave = { _, name, icon, target, plan, currency, date ->
                viewModel.updateGoal(item, name, icon, target, plan, currency, date)
                editingGoalId = null
            }
        )
    }

    goalById(selectedGoalId)?.let { item ->
        GoalDetailSheet(
            item = item,
            myUid = uiState.myUid,
            partnerUid = uiState.partnerUid,
            partnerName = uiState.partnerName,
            onDismiss = { selectedGoalId = null },
            onDeposit = { entryRequest = EntryRequest(item.goal.id, SavingsEntryKind.DEPOSIT) },
            onWithdraw = { entryRequest = EntryRequest(item.goal.id, SavingsEntryKind.WITHDRAWAL) },
            onUse = { useGoalId = item.goal.id },
            onArchive = {
                viewModel.setArchived(item, !item.goal.archived)
                selectedGoalId = null
            },
            onEdit = { editingGoalId = item.goal.id },
            onDelete = { deleteGoalId = item.goal.id },
            onDeleteEntry = { viewModel.deleteEntry(item, it) }
        )
    }

    entryRequest?.let { request ->
        val item = goalById(request.goalId)
        if (item == null) {
            entryRequest = null
        } else {
            SavingsEntryDialog(
                kind = request.kind,
                goalName = item.goal.name,
                inGroup = item.isGroup,
                myUid = uiState.myUid,
                partnerUid = uiState.partnerUid,
                partnerName = uiState.partnerName,
                currencyRates = uiState.currencyRates,
                defaultCurrency = uiState.defaultCurrency,
                suggestedAmount = null,
                onDismiss = { entryRequest = null },
                onSave = { amount, currency, date, note, byUid ->
                    viewModel.addEntry(item, request.kind, amount, currency, date, note, byUid)
                    entryRequest = null
                }
            )
        }
    }

    checkInOther?.let { (goalId, remaining) ->
        val item = goalById(goalId)
        if (item == null) {
            checkInOther = null
        } else {
            SavingsEntryDialog(
                kind = SavingsEntryKind.DEPOSIT,
                goalName = item.goal.name,
                inGroup = item.isGroup,
                myUid = uiState.myUid,
                partnerUid = uiState.partnerUid,
                partnerName = uiState.partnerName,
                currencyRates = uiState.currencyRates,
                defaultCurrency = uiState.defaultCurrency,
                suggestedAmount = remaining,
                onDismiss = { checkInOther = null },
                onSave = { amount, currency, date, note, byUid ->
                    viewModel.addEntry(item, SavingsEntryKind.DEPOSIT, amount, currency, date, note, byUid)
                    viewModel.dismissCheckIn(item)
                    checkInOther = null
                }
            )
        }
    }

    goalById(useGoalId)?.let { item ->
        UseGoalDialog(
            item = item,
            categories = uiState.categories,
            onDismiss = { useGoalId = null },
            onConfirm = { categoryId ->
                viewModel.useGoal(item, categoryId)
                useGoalId = null
                selectedGoalId = null
            }
        )
    }

    goalById(deleteGoalId)?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteGoalId = null },
            title = { Text(stringResource(R.string.savings_delete_title)) },
            text = { Text(stringResource(R.string.savings_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGoal(item)
                    deleteGoalId = null
                    selectedGoalId = null
                }) { Text(stringResource(R.string.cd_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteGoalId = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckInCard(
    item: GoalItem,
    remaining: Double,
    onYes: () -> Unit,
    onOther: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalIcon(item.goal.icon)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.savings_checkin_text, item.goal.name, formatMoney(remaining)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onYes) { Text(stringResource(R.string.savings_checkin_yes, formatMoney(remaining))) }
                OutlinedButton(onClick = onOther) { Text(stringResource(R.string.savings_checkin_other)) }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.savings_checkin_skip)) }
            }
        }
    }
}

/** The round emoji badge every goal row/card/sheet starts with. */
@Composable
fun GoalIcon(icon: String, modifier: Modifier = Modifier, size: Int = 40) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(icon)
    }
}

@Composable
private fun GoalCard(
    item: GoalItem,
    myUid: String,
    partnerName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val goal = item.goal
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalIcon(goal.icon)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        goal.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (item.isGroup) stringResource(R.string.savings_scope_group, partnerName) else stringResource(R.string.savings_scope_personal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.complete) Text("🎉", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            BudgetProgressBar(
                progress = item.progress,
                color = if (item.complete) MaterialTheme.semanticColors.positive else MaterialTheme.colorScheme.primary,
                height = 8.dp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${stringResource(R.string.savings_progress, formatMoney(item.balance), formatMoney(goal.targetAmount))} · ${(item.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            goalHint(item)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One line on pace: when it would be reached at the current rate, or what's needed to hit the deadline. */
@Composable
fun goalHint(item: GoalItem): String? {
    if (item.complete) return stringResource(R.string.savings_reached)
    val goal = item.goal
    val today = LocalDate.now()
    val remaining = goal.targetAmount - item.balance
    val rate = SavingsMath.monthlyRate(goal, item.entries, today)
    val finish = rate?.let { SavingsMath.projectedFinish(remaining, it, YearMonth.from(today)) }
    if (finish != null) return stringResource(R.string.savings_projection, formatMonthLabel(finish))
    val deadline = goal.localTargetDate
    val required = deadline?.let { SavingsMath.requiredMonthly(remaining, it, today) }
    if (deadline != null && required != null) {
        return stringResource(R.string.savings_required, formatMonthLabel(YearMonth.from(deadline)), formatMoney(required))
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDetailSheet(
    item: GoalItem,
    myUid: String,
    partnerUid: String?,
    partnerName: String,
    onDismiss: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onUse: () -> Unit,
    onArchive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteEntry: (SavingsEntry) -> Unit
) {
    val goal = item.goal
    val today = LocalDate.now()
    val me = stringResource(R.string.you)
    fun nameOf(uid: String) = if (uid == myUid) me else partnerName

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalIcon(goal.icon, size = 48)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (item.isGroup) stringResource(R.string.savings_scope_group, partnerName) else stringResource(R.string.savings_scope_personal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                formatMoney(item.balance),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (item.complete) MaterialTheme.semanticColors.positive else MaterialTheme.colorScheme.primary
            )
            Text(
                "${stringResource(R.string.savings_progress, formatMoney(item.balance), formatMoney(goal.targetAmount))} · ${(item.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            val net = SavingsMath.netByUid(item.entries)
            if (item.isGroup && partnerUid != null && goal.targetAmount > 0) {
                // The same two colours the Group screen uses for "me" and "the partner".
                val mine = (net[myUid] ?: 0.0).coerceAtLeast(0.0)
                val theirs = (net[partnerUid] ?: 0.0).coerceAtLeast(0.0)
                SegmentedBudgetBar(
                    spentFraction = (mine / goal.targetAmount).toFloat().coerceIn(0f, 1f),
                    savedFraction = (theirs / goal.targetAmount).toFloat().coerceIn(0f, 1f),
                    spentColor = MaterialTheme.colorScheme.primary,
                    savedColor = MaterialTheme.colorScheme.secondary,
                    height = 10.dp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.savings_contributions, me, formatMoney(net[myUid] ?: 0.0), partnerName, formatMoney(net[partnerUid] ?: 0.0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                BudgetProgressBar(
                    progress = item.progress,
                    color = if (item.complete) MaterialTheme.semanticColors.positive else MaterialTheme.colorScheme.primary,
                    height = 10.dp
                )
            }

            Spacer(Modifier.height(12.dp))
            val myPlan = goal.monthlyPlans[myUid]
            if (myPlan != null && myPlan > 0) {
                val deposited = SavingsMath.depositedInMonth(item.entries, YearMonth.from(today), myUid)
                Text(
                    stringResource(R.string.savings_plan_line, formatMoney(myPlan)) + " · " +
                        stringResource(R.string.savings_this_month_line, formatMoney(deposited), formatMoney(myPlan)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            val partnerPlan = partnerUid?.let { goal.monthlyPlans[it] }
            if (item.isGroup && partnerPlan != null && partnerPlan > 0) {
                Text(
                    stringResource(R.string.savings_partner_plan, partnerName, formatMoney(partnerPlan)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            goalHint(item)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            goal.localTargetDate?.let {
                Text(
                    stringResource(R.string.savings_goal_deadline, formatShortDate(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item.linkedTrip?.let { trip ->
                Text(
                    stringResource(
                        R.string.savings_linked_trip,
                        "${trip.icon} ${trip.name}",
                        formatShortDate(trip.localStart),
                        formatShortDate(trip.localEnd)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDeposit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.savings_deposit)) }
                OutlinedButton(onClick = onWithdraw, modifier = Modifier.weight(1f), enabled = item.balance > 0) {
                    Text(stringResource(R.string.savings_withdraw))
                }
            }
            if (item.complete) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.savings_use)) }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onArchive) {
                    Text(stringResource(if (goal.archived) R.string.savings_unarchive else R.string.savings_archive))
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.cd_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.cd_delete)) }
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.savings_movements), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val movements = item.entries.sortedWith(compareByDescending<SavingsEntry> { it.localDate }.thenByDescending { it.createdAt?.seconds ?: 0 })
            if (movements.isEmpty()) {
                Text(
                    stringResource(R.string.savings_no_movements),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            movements.take(40).forEach { entry ->
                val deposit = entry.kind == SavingsEntryKind.DEPOSIT
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${if (deposit) "+" else "−"} ${formatMoney(entry.amountInBaseCurrency)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (deposit) MaterialTheme.semanticColors.positive else MaterialTheme.semanticColors.negative
                        )
                        val subtitle = listOfNotNull(
                            formatShortDate(entry.localDate),
                            nameOf(entry.byUid).takeIf { item.isGroup },
                            entry.note
                        ).joinToString(" · ")
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onDeleteEntry(entry) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Books a finished personal pot as the real expense it was for. */
@Composable
private fun UseGoalDialog(
    item: GoalItem,
    categories: List<com.example.expensestracker.data.model.Category>,
    onDismiss: () -> Unit,
    onConfirm: (categoryId: String) -> Unit
) {
    var selected by remember { mutableStateOf(categories.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.savings_use_title, item.goal.name)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.savings_use_hint, formatMoney(item.balance)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                CategoryPicker(categories = categories, selectedCategoryId = selected, onSelect = { selected = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onConfirm) }, enabled = selected != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
