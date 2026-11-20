package com.example.expensestracker.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.domain.TripPhase
import com.example.expensestracker.ui.components.BudgetProgressBar
import com.example.expensestracker.ui.components.EmptyState
import com.example.expensestracker.ui.components.ExpenseRow
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatShortDate
import com.example.expensestracker.util.localizedCategoryName
import com.example.expensestracker.util.toColor

/** A trip's progress bar turns amber from here, red once it's passed the budget. */
private const val TRIP_BUDGET_WARNING = 0.9f

/**
 * Trips and projects, each with its own dates and budget. Created through the shared corner "+"
 * the other management screens use; tapping one opens its sheet with the breakdown and its expenses.
 * Expenses join a trip from the add-expense sheet (the trip that's on today is pre-selected there).
 */
@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
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

    var selectedId by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }

    fun tripById(id: String?) = uiState.trips.firstOrNull { it.trip.id == id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.trips.isEmpty() && uiState.loaded) {
            item {
                EmptyState(
                    icon = "✈️",
                    title = stringResource(R.string.trips_empty_title),
                    action = { Button(onClick = onShowAddDialog) { Text(stringResource(R.string.trips_empty_action)) } }
                )
            }
        }
        items(uiState.trips, key = { it.trip.id }) { item ->
            TripCard(item, uiState.partnerName, onClick = { selectedId = item.trip.id }, modifier = Modifier.animateItem())
        }
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showAddDialog) {
        TripEditDialog(
            initial = null,
            inGroup = uiState.inGroup,
            partnerName = uiState.partnerName,
            currencyRates = uiState.currencyRates,
            defaultCurrency = uiState.defaultCurrency,
            onDismiss = onDismissAddDialog,
            onSave = { isGroup, name, icon, start, end, budget, currency ->
                viewModel.addTrip(isGroup, name, icon, start, end, budget, currency)
                onDismissAddDialog()
            }
        )
    }

    tripById(editingId)?.let { item ->
        TripEditDialog(
            initial = item,
            inGroup = uiState.inGroup,
            partnerName = uiState.partnerName,
            currencyRates = uiState.currencyRates,
            defaultCurrency = uiState.defaultCurrency,
            onDismiss = { editingId = null },
            onSave = { _, name, icon, start, end, budget, currency ->
                viewModel.updateTrip(item, name, icon, start, end, budget, currency)
                editingId = null
            }
        )
    }

    tripById(selectedId)?.let { item ->
        TripDetailSheet(
            item = item,
            myUid = uiState.myUid,
            partnerName = uiState.partnerName,
            onDismiss = { selectedId = null },
            onEdit = { editingId = item.trip.id },
            onDelete = { deleteId = item.trip.id }
        )
    }

    tripById(deleteId)?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.trip_delete_title)) },
            text = { Text(stringResource(R.string.trip_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTrip(item)
                    deleteId = null
                    selectedId = null
                }) { Text(stringResource(R.string.cd_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun phaseLabel(item: TripItem): String {
    val stats = item.stats
    return when (stats.phase) {
        TripPhase.UPCOMING -> {
            val days = (item.trip.localStart.toEpochDay() - java.time.LocalDate.now().toEpochDay()).toInt()
            stringResource(R.string.trip_phase_upcoming, pluralStringResource(R.plurals.period_days, days, days))
        }
        TripPhase.ONGOING -> stringResource(R.string.trip_phase_ongoing, stats.daysElapsed, stats.totalDays)
        TripPhase.ENDED -> stringResource(R.string.trip_phase_ended)
    }
}

@Composable
private fun budgetColor(item: TripItem): androidx.compose.ui.graphics.Color {
    val stats = item.stats
    val ratio = if (stats.budget > 0) (stats.spent / stats.budget).toFloat() else 0f
    return when {
        stats.overBudget -> MaterialTheme.semanticColors.negative
        ratio >= TRIP_BUDGET_WARNING -> MaterialTheme.semanticColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable
private fun TripCard(item: TripItem, partnerName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val trip = item.trip
    val stats = item.stats
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TripIcon(trip.icon)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(trip.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatShortDate(trip.localStart)} – ${formatShortDate(trip.localEnd)} · ${phaseLabel(item)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (item.isGroup) stringResource(R.string.savings_scope_group, partnerName) else stringResource(R.string.savings_scope_personal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (stats.budget > 0) {
                BudgetProgressBar(
                    progress = (stats.spent / stats.budget).toFloat(),
                    color = budgetColor(item),
                    height = 8.dp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.savings_progress, formatMoney(stats.spent), formatMoney(stats.budget)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stats.overBudget) MaterialTheme.semanticColors.negative else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    stringResource(R.string.trip_spent_no_budget, formatMoney(stats.spent)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TripIcon(icon: String, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) { Text(icon) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailSheet(
    item: TripItem,
    myUid: String,
    partnerName: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val trip = item.trip
    val stats = item.stats
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TripIcon(trip.icon, size = 48)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(trip.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatShortDate(trip.localStart)} – ${formatShortDate(trip.localEnd)} · ${phaseLabel(item)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                formatMoney(stats.spent),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = budgetColor(item)
            )
            if (stats.budget > 0) {
                Text(
                    stringResource(R.string.savings_progress, formatMoney(stats.spent), formatMoney(stats.budget)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                BudgetProgressBar(progress = (stats.spent / stats.budget).toFloat(), color = budgetColor(item), height = 10.dp)
                Spacer(Modifier.height(8.dp))
                val remaining = stats.remaining ?: 0.0
                Text(
                    if (remaining >= 0) stringResource(R.string.trip_remaining, formatMoney(remaining))
                    else stringResource(R.string.trip_over_by, formatMoney(-remaining)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (remaining >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.semanticColors.negative
                )
            }

            Spacer(Modifier.height(6.dp))
            stats.averagePerDay?.let {
                Text(stringResource(R.string.trip_average_per_day, formatMoney(it)), style = MaterialTheme.typography.bodyMedium)
            }
            stats.remainingPerDay?.let {
                Text(stringResource(R.string.trip_can_spend_per_day, formatMoney(it)), style = MaterialTheme.typography.bodyMedium)
            }
            if (stats.phase == TripPhase.UPCOMING) {
                stats.dailyBudget?.let {
                    Text(stringResource(R.string.trip_budget_per_day, formatMoney(it)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (item.isGroup) {
                Text(
                    stringResource(R.string.trip_my_share, formatMoney(stats.myShare)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.cd_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.cd_delete)) }
            }

            if (stats.byCategory.isNotEmpty()) {
                Text(stringResource(R.string.trip_by_category), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                stats.byCategory.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(category.colorHex.toColor().copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) { Text(category.icon) }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            localizedCategoryName(category.name),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(formatMoney(category.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Text(stringResource(R.string.trip_expenses), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (item.expenses.isEmpty()) {
                Text(
                    stringResource(R.string.trip_no_expenses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.expenses.sortedByDescending { it.localDate }.forEach { expense ->
                    ExpenseRow(expense = expense, myUid = myUid, partnerName = partnerName, onClick = {})
                }
            }
        }
    }
}
