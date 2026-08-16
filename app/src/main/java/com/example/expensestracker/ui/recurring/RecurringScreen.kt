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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensestracker.data.local.entity.RecurrenceFrequency
import com.example.expensestracker.data.local.entity.RecurringExpenseEntity
import com.example.expensestracker.ui.AppViewModelFactory
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatShortDate
import com.example.expensestracker.util.toColor
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(factory: AppViewModelFactory) {
    val viewModel: RecurringViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Recurring expense") }
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
                    "Fixed & recurring expenses",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "These are automatically added to this month's expenses when their due date arrives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.items.isEmpty()) {
                item {
                    Text(
                        "No recurring expenses set up yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(uiState.items, key = { it.id }) { item ->
                val category = uiState.categories.firstOrNull { it.id == item.categoryId }
                RecurringRow(
                    item = item,
                    categoryName = category?.name ?: "—",
                    categoryIcon = category?.icon ?: "📦",
                    categoryColor = category?.colorHex ?: "#78909C",
                    onToggle = { viewModel.toggleActive(item) },
                    onDelete = { viewModel.deleteRecurring(item) }
                )
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showAddDialog) {
        AddRecurringDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun RecurringRow(
    item: RecurringExpenseEntity,
    categoryName: String,
    categoryIcon: String,
    categoryColor: String,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(categoryColor.toColor().copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(categoryIcon)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(categoryName, fontWeight = FontWeight.Medium)
                Text(
                    text = "${formatMoney(item.amount, item.currencyCode)} · ${frequencyLabel(item)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = item.active, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

private fun frequencyLabel(item: RecurringExpenseEntity): String = when (item.frequency) {
    RecurrenceFrequency.MONTHLY -> "Every month, day ${item.dayOfPeriod}"
    RecurrenceFrequency.WEEKLY -> {
        val dayName = DayOfWeek.of(item.dayOfPeriod).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            .replaceFirstChar { it.uppercase() }
        "Every week, $dayName"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddRecurringDialog(viewModel: RecurringViewModel, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedCurrency by remember { mutableStateOf("EUR") }
    var frequency by remember { mutableStateOf(RecurrenceFrequency.MONTHLY) }
    var dayOfMonthText by remember { mutableStateOf("1") }
    var selectedWeekday by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var note by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

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

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val dayOfMonth = dayOfMonthText.toIntOrNull()?.coerceIn(1, 31)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New recurring expense") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { selectedCategoryId = category.id },
                            label = { Text("${category.icon} ${category.name}") }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Frequency", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = frequency == RecurrenceFrequency.MONTHLY,
                        onClick = { frequency = RecurrenceFrequency.MONTHLY },
                        label = { Text("Monthly") }
                    )
                    FilterChip(
                        selected = frequency == RecurrenceFrequency.WEEKLY,
                        onClick = { frequency = RecurrenceFrequency.WEEKLY },
                        label = { Text("Weekly") }
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (frequency == RecurrenceFrequency.MONTHLY) {
                    OutlinedTextField(
                        value = dayOfMonthText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d{0,2}$"))) dayOfMonthText = input
                        },
                        label = { Text("Day of month (1-31)") },
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
                                    Text(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).replaceFirstChar { it.uppercase() })
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start: ${formatShortDate(startDate)}")
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
                    val categoryId = selectedCategoryId
                    val day = if (frequency == RecurrenceFrequency.MONTHLY) dayOfMonth else selectedWeekday.value
                    if (amount != null && amount > 0 && categoryId != null && day != null) {
                        viewModel.addRecurring(
                            categoryId = categoryId,
                            amount = amount,
                            currencyCode = selectedCurrency,
                            note = note,
                            frequency = frequency,
                            dayOfPeriod = day,
                            startDate = startDate
                        )
                        onDismiss()
                    }
                },
                enabled = amount != null && amount > 0 && selectedCategoryId != null && (frequency == RecurrenceFrequency.WEEKLY || dayOfMonth != null)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
