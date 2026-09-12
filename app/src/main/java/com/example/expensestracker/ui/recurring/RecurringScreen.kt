package com.example.expensestracker.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensestracker.R
import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.data.model.RecurringExpense
import com.example.expensestracker.domain.nextOccurrence
import com.example.expensestracker.ui.AppViewModelFactory
import com.example.expensestracker.ui.components.CategoryPicker
import com.example.expensestracker.ui.components.PaidByAndSplitFields
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatShortDate
import com.example.expensestracker.util.toColor
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

private val reminderDayOptions = listOf(1, 2, 3, 5, 7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(factory: AppViewModelFactory) {
    val viewModel: RecurringViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val dismissDialog = { viewModel.clearEdit(); showAddDialog = false }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_recurring_expense)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.fixed_recurring_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.fixed_recurring_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.monthlyTotal > 0) {
                item { FixedMonthlyTotalCard(uiState.monthlyTotal) }
            }

            if (uiState.items.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🔁", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.no_recurring_yet),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(uiState.items, key = { it.id }) { item ->
                RecurringRow(
                    item = item,
                    myUid = uiState.myUid,
                    partnerName = uiState.partnerName,
                    onToggle = { viewModel.toggleActive(item) },
                    onEdit = { viewModel.startEdit(item); showAddDialog = true },
                    onDelete = { viewModel.deleteRecurring(item) }
                )
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showAddDialog) {
        AddRecurringDialog(
            viewModel = viewModel,
            onDismiss = dismissDialog
        )
    }
}

@Composable
private fun FixedMonthlyTotalCard(monthlyTotal: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.fixed_monthly_total_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatMoney(monthlyTotal),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun RecurringRow(
    item: RecurringExpense,
    myUid: String,
    partnerName: String,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Dims the whole row when the template is paused, so a glance at the list shows
                // which ones are actually still generating expenses without reading every switch.
                .alpha(if (item.active) 1f else 0.5f)
                .padding(start = 14.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(item.categoryColorHex.toColor().copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(item.categoryIcon)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val payerLabel = if (item.paidByUid == myUid) stringResource(R.string.you) else partnerName
                val sharedLabel = if (item.isShared) stringResource(R.string.paid_by_partner, payerLabel) else stringResource(R.string.personal_label)
                val nextDueLabel = stringResource(R.string.next_occurrence_prefix, formatShortDate(item.nextOccurrence()))
                val reminderLabel = item.reminderDaysBefore?.let { pluralStringResource(R.plurals.reminder_summary, it, it) }
                val subtitle = listOfNotNull("${frequencyLabel(item)} · $nextDueLabel", sharedLabel, reminderLabel)
                    .joinToString(" · ")
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                formatMoney(item.amount, item.currencyCode),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Switch(checked = item.active, onCheckedChange = { onToggle() })
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun frequencyLabel(item: RecurringExpense): String = when (item.frequency) {
    RecurrenceFrequency.MONTHLY -> stringResource(R.string.every_month_day, item.dayOfPeriod)
    RecurrenceFrequency.WEEKLY -> {
        val dayName = DayOfWeek.of(item.dayOfPeriod).getDisplayName(TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
        stringResource(R.string.every_week_day, dayName)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddRecurringDialog(viewModel: RecurringViewModel, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val editing = remember { viewModel.editingRecurring.value }
    val isEditing = editing != null

    var amountText by remember { mutableStateOf(editing?.amount?.let { formatAmountInput(it) } ?: "") }
    var selectedCategoryId by remember { mutableStateOf(editing?.categoryId) }
    var selectedCurrency by remember { mutableStateOf(editing?.currencyCode ?: uiState.defaultCurrency) }
    var frequency by remember { mutableStateOf(editing?.frequency ?: RecurrenceFrequency.MONTHLY) }
    var dayOfMonthText by remember { mutableStateOf(if (editing?.frequency == RecurrenceFrequency.MONTHLY) editing.dayOfPeriod.toString() else "1") }
    var selectedWeekday by remember {
        mutableStateOf(if (editing?.frequency == RecurrenceFrequency.WEEKLY) DayOfWeek.of(editing.dayOfPeriod) else DayOfWeek.MONDAY)
    }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var startDate by remember { mutableStateOf(editing?.localStartDate ?: LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isShared by remember { mutableStateOf(editing?.isShared ?: uiState.defaultShared) }
    var paidByUid by remember { mutableStateOf(editing?.paidByUid ?: "") }
    var customSplitEnabled by remember { mutableStateOf(editing?.let { it.payerShare != 0.5 } ?: false) }
    var payerShare by remember { mutableStateOf(editing?.payerShare ?: 0.5) }
    var reminderEnabled by remember { mutableStateOf(editing?.reminderDaysBefore != null) }
    var reminderDays by remember { mutableStateOf(editing?.reminderDaysBefore ?: 3) }

    LaunchedEffect(uiState.categories) {
        if (selectedCategoryId == null && uiState.categories.isNotEmpty()) {
            selectedCategoryId = uiState.categories.first().id
        }
    }
    LaunchedEffect(uiState.currencyRates) {
        if (uiState.currencyRates.isNotEmpty() && uiState.currencyRates.none { it.code == selectedCurrency }) {
            selectedCurrency = uiState.currencyRates.first().code
        }
    }
    LaunchedEffect(uiState.defaultCurrency) {
        if (!isEditing) selectedCurrency = uiState.defaultCurrency
    }
    LaunchedEffect(uiState.myUid) {
        if (paidByUid.isEmpty() && uiState.myUid.isNotEmpty()) {
            paidByUid = uiState.myUid
        }
    }
    LaunchedEffect(uiState.defaultShared) {
        if (!isEditing) isShared = uiState.defaultShared
    }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val dayOfMonth = dayOfMonthText.toIntOrNull()?.coerceIn(1, 31)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEditing) R.string.edit_recurring_title else R.string.new_recurring_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.currencyRates.forEach { rate ->
                        FilterChip(
                            selected = selectedCurrency == rate.code,
                            onClick = { selectedCurrency = rate.code },
                            label = { Text(rate.code) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.category_label), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                CategoryPicker(
                    categories = uiState.categories,
                    selectedCategoryId = selectedCategoryId,
                    onSelect = { selectedCategoryId = it }
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.frequency_label), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = frequency == RecurrenceFrequency.MONTHLY,
                        onClick = { frequency = RecurrenceFrequency.MONTHLY },
                        label = { Text(stringResource(R.string.frequency_monthly)) }
                    )
                    FilterChip(
                        selected = frequency == RecurrenceFrequency.WEEKLY,
                        onClick = { frequency = RecurrenceFrequency.WEEKLY },
                        label = { Text(stringResource(R.string.frequency_weekly)) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (frequency == RecurrenceFrequency.MONTHLY) {
                    OutlinedTextField(
                        value = dayOfMonthText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d{0,2}$"))) dayOfMonthText = input
                        },
                        label = { Text(stringResource(R.string.day_of_month_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { dow ->
                            FilterChip(
                                selected = selectedWeekday == dow,
                                onClick = { selectedWeekday = dow },
                                label = {
                                    Text(dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() })
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.start_prefix, formatShortDate(startDate)))
                }
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.reminder_label), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(R.string.reminder_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }
                if (reminderEnabled) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        reminderDayOptions.forEach { days ->
                            FilterChip(
                                selected = reminderDays == days,
                                onClick = { reminderDays = days },
                                label = { Text(pluralStringResource(R.plurals.reminder_days_chip, days, days)) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (uiState.inGroup) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.shared_with, uiState.partnerName), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Switch(checked = isShared, onCheckedChange = { isShared = it })
                    }
                    if (isShared && uiState.partnerUid != null) {
                        Spacer(Modifier.height(12.dp))
                        PaidByAndSplitFields(
                            myUid = uiState.myUid,
                            partnerUid = uiState.partnerUid!!,
                            partnerName = uiState.partnerName,
                            paidByUid = paidByUid,
                            onPaidByChange = { paidByUid = it },
                            customSplitEnabled = customSplitEnabled,
                            onCustomSplitToggle = { customSplitEnabled = it },
                            payerShare = payerShare,
                            onPayerShareChange = { payerShare = it }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

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
                    val categoryId = selectedCategoryId
                    val day = if (frequency == RecurrenceFrequency.MONTHLY) dayOfMonth else selectedWeekday.value
                    if (amount != null && amount > 0 && categoryId != null && day != null) {
                        viewModel.saveRecurring(
                            categoryId = categoryId,
                            amount = amount,
                            currencyCode = selectedCurrency,
                            note = note,
                            frequency = frequency,
                            dayOfPeriod = day,
                            startDate = startDate,
                            paidByUid = if (isShared) paidByUid else uiState.myUid,
                            isShared = isShared,
                            payerShare = if (customSplitEnabled) payerShare else 0.5,
                            reminderDaysBefore = if (reminderEnabled) reminderDays else null
                        )
                        onDismiss()
                    }
                },
                enabled = amount != null && amount > 0 && selectedCategoryId != null && (frequency == RecurrenceFrequency.WEEKLY || dayOfMonth != null)
            ) { Text(stringResource(if (isEditing) R.string.save_changes else R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        startDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatAmountInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else String.format("%.2f", amount)
