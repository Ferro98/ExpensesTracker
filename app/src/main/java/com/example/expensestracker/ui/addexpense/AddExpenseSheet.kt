package com.example.expensestracker.ui.addexpense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.example.expensestracker.R
import com.example.expensestracker.ui.components.CategoryPicker
import com.example.expensestracker.ui.components.PaidByAndSplitFields
import com.example.expensestracker.util.formatShortDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseSheet(viewModel: AddExpenseViewModel, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    // Captured once when the sheet is composed (it's only ever entered fresh - see the
    // `if (showAddExpense)` gate in ExpensesTrackerRoot) so prefill values don't get clobbered
    // by recomposition while the user is editing the fields below.
    val editingExpense = remember { viewModel.editingExpense.value }
    val isEditing = editingExpense != null

    val initialAmountText = remember { editingExpense?.amount?.let { formatAmountInput(it) } ?: "" }
    val initialNote = remember { editingExpense?.note ?: "" }
    var amountText by remember { mutableStateOf(initialAmountText) }
    var selectedCategoryId by remember { mutableStateOf(editingExpense?.categoryId) }
    var selectedCurrency by remember { mutableStateOf(editingExpense?.currencyCode ?: uiState.defaultCurrency) }
    var note by remember { mutableStateOf(initialNote) }
    var selectedDate by remember { mutableStateOf(editingExpense?.localDate ?: LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isShared by remember { mutableStateOf(editingExpense?.isShared ?: uiState.defaultShared) }
    var paidByUid by remember { mutableStateOf(editingExpense?.paidByUid ?: "") }
    var customSplitEnabled by remember { mutableStateOf(editingExpense?.let { it.payerShare != 0.5 } ?: false) }
    var payerShare by remember { mutableStateOf(editingExpense?.payerShare ?: 0.5) }

    // Also self-corrects a categoryId that isn't in this list at all: happens when editing a
    // shared expense the OTHER group member created, since categories are private per-user and
    // that id only ever existed in their own list - saving with it would silently fail to resolve
    // a category. Only fires when the currently selected id truly doesn't match anything, so it
    // never overrides a category the user has since (validly) picked themselves.
    LaunchedEffect(uiState.categories) {
        if (uiState.categories.isEmpty()) return@LaunchedEffect
        val currentId = selectedCategoryId
        if (currentId == null || uiState.categories.none { it.id == currentId }) {
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
    // The initial `isShared` read above may have raced the real stored preference (its StateFlow
    // starts at a hardcoded false before Settings' DataStore value resolves) - correct it once,
    // but only for a brand new expense; an edit always keeps the expense's own stored value.
    LaunchedEffect(uiState.defaultShared) {
        if (!isEditing) isShared = uiState.defaultShared
    }

    val dismiss = { viewModel.clearEdit(); onDismiss() }

    // Guards against losing typed data to an accidental swipe-down or scrim tap: a hide is only
    // allowed through untouched, everything else routes through the discard-confirmation dialog.
    var showDiscardDialog by remember { mutableStateOf(false) }
    val hasUnsavedChanges = remember {
        { amountText != initialAmountText || note != initialNote }
    }
    val confirmValueChange = remember {
        { target: SheetValue ->
            if (target == SheetValue.Hidden && hasUnsavedChanges()) {
                showDiscardDialog = true
                false
            } else true
        }
    }
    val requestDismiss = { if (hasUnsavedChanges()) showDiscardDialog = true else dismiss() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirmValueChange)

    ModalBottomSheet(onDismissRequest = requestDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(if (isEditing) R.string.edit_expense_title else R.string.new_expense_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = requestDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d{0,7}([.,]\\d{0,2})?$"))) {
                        amountText = input
                    }
                },
                label = { Text(stringResource(R.string.label_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.currency_label), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.currencyRates.forEach { rate ->
                    FilterChip(
                        selected = selectedCurrency == rate.code,
                        onClick = { selectedCurrency = rate.code },
                        label = { Text(rate.code) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.category_label), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            CategoryPicker(
                categories = uiState.categories,
                selectedCategoryId = selectedCategoryId,
                onSelect = { selectedCategoryId = it }
            )
            Spacer(Modifier.height(16.dp))

            if (uiState.inGroup) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.shared_with, uiState.partnerName), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(if (isShared) R.string.shared_splits_balance else R.string.shared_counts_own_budget),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isShared, onCheckedChange = { isShared = it })
                }

                if (isShared && uiState.partnerUid != null) {
                    Spacer(Modifier.height(16.dp))
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
                Spacer(Modifier.height(16.dp))
            }

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.date_prefix, formatShortDate(selectedDate)))
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.label_note_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        when (error) {
                            AddExpenseError.CATEGORY_NOT_FOUND -> R.string.error_category_not_found
                            else -> R.string.error_save_failed
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(20.dp))

            val amount = amountText.replace(',', '.').toDoubleOrNull()
            Button(
                onClick = {
                    val categoryId = selectedCategoryId
                    if (amount != null && amount > 0 && categoryId != null) {
                        viewModel.saveExpense(
                            categoryId = categoryId,
                            amount = amount,
                            currencyCode = selectedCurrency,
                            date = selectedDate,
                            note = note,
                            paidByUid = if (isShared) paidByUid else uiState.myUid,
                            isShared = isShared,
                            payerShare = if (customSplitEnabled) payerShare else 0.5,
                            onSaved = dismiss
                        )
                    }
                },
                enabled = amount != null && amount > 0 && selectedCategoryId != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (isEditing) R.string.save_changes else R.string.save_expense))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
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

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_expense_title)) },
            text = { Text(stringResource(R.string.discard_expense_text)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; dismiss() }) {
                    Text(stringResource(R.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_keep_editing))
                }
            }
        )
    }
}

/** Prefill text for the amount field: whole numbers show without decimals, otherwise 2dp. */
private fun formatAmountInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else String.format("%.2f", amount)
