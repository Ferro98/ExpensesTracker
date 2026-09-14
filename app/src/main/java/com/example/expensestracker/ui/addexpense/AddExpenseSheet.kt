package com.example.expensestracker.ui.addexpense

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.domain.CategoryResolver
import com.example.expensestracker.ui.components.AmountKeypad
import com.example.expensestracker.ui.components.CategoryPicker
import com.example.expensestracker.ui.components.PaidByAndSplitFields
import com.example.expensestracker.ui.components.appendAmountKey
import com.example.expensestracker.ui.theme.MoneyStyle
import com.example.expensestracker.util.currencySymbol
import com.example.expensestracker.util.decimalSeparator
import com.example.expensestracker.util.formatShortDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Amount-first entry: amount, category and note are all on screen together, since in practice a
 * note is filled in almost every time and reads more like a short description than an optional
 * extra - hiding it behind a tap lost it exactly when it mattered. Only date and sharing, which
 * are genuinely "change this sometimes" fields, hide behind the one-line summary below the note.
 * See docs/UX_REDESIGN_PLAN.md 3.3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(viewModel: AddExpenseViewModel, onDismiss: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    // Captured once when the sheet is composed (it's only ever entered fresh - see the
    // `if (showAddExpense)` gate in ExpensesTrackerRoot) so prefill values don't get clobbered
    // by recomposition while the user is editing the fields below.
    val prefill = remember { viewModel.prefill.value }
    val source = prefill?.source
    val isEditing = prefill?.isEdit == true
    val separator = decimalSeparator()

    val initialAmountText = remember { source?.amount?.let { formatAmountInput(it) } ?: "" }
    val initialNote = remember { source?.note ?: "" }
    var amountText by remember { mutableStateOf(initialAmountText) }
    var selectedCategoryId by remember { mutableStateOf(source?.categoryId) }
    var userPickedCategory by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf(source?.currencyCode ?: uiState.defaultCurrency) }
    var note by remember { mutableStateOf(initialNote) }
    // An edit keeps the expense's own date; a duplicate is a thing you're buying again now.
    var selectedDate by remember { mutableStateOf(if (isEditing) source!!.localDate else LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isShared by remember { mutableStateOf(source?.isShared ?: uiState.defaultShared) }
    var paidByUid by remember { mutableStateOf(source?.paidByUid ?: "") }
    var customSplitEnabled by remember { mutableStateOf(source?.let { it.payerShare != 0.5 } ?: false) }
    var payerShare by remember { mutableStateOf(source?.payerShare ?: 0.5) }
    var showDetails by remember { mutableStateOf(false) }

    // Keeps re-deciding the category until the user picks one themselves, so a slow DataStore read
    // still beats the first-in-list guess made a moment earlier. In order of preference:
    //  - the prefilled expense's own category. CategoryResolver, not a raw id lookup: an expense
    //    the OTHER group member created carries THEIR category id, which exists in no list here,
    //    and saving with it would silently fail to resolve a category. It matches by name instead.
    //  - the category used last, which is usually the one wanted again.
    //  - the first in the list, when there's nothing better to go on.
    LaunchedEffect(uiState.categories, uiState.lastUsedCategoryId) {
        if (uiState.categories.isEmpty() || userPickedCategory) return@LaunchedEffect
        selectedCategoryId = source?.let { CategoryResolver.resolve(it, uiState.categories)?.id }
            ?: uiState.lastUsedCategoryId?.takeIf { id -> uiState.categories.any { it.id == id } }
                    ?: uiState.categories.first().id
    }
    LaunchedEffect(uiState.currencyRates) {
        if (uiState.currencyRates.isNotEmpty() && uiState.currencyRates.none { it.code == selectedCurrency }) {
            selectedCurrency = uiState.currencyRates.first().code
        }
    }
    // These two initial reads may have raced the real stored preference (the StateFlow starts at
    // hardcoded defaults before Settings' DataStore values resolve) - correct them once, but only
    // for a blank sheet: anything opened on top of an existing expense keeps that expense's values.
    LaunchedEffect(uiState.defaultCurrency) {
        if (prefill == null) selectedCurrency = uiState.defaultCurrency
    }
    LaunchedEffect(uiState.defaultShared) {
        if (prefill == null) isShared = uiState.defaultShared
    }
    LaunchedEffect(uiState.myUid) {
        if (paidByUid.isEmpty() && uiState.myUid.isNotEmpty()) {
            paidByUid = uiState.myUid
        }
    }

    val dismiss = { viewModel.clearPrefill(); onDismiss() }

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
                // Everything is meant to fit without scrolling; this is only the safety net for
                // short screens, landscape, and large font scales.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
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

            Text(
                text = "${currencySymbol(selectedCurrency)} ${amountText.ifEmpty { "0" }}",
                style = MoneyStyle.Large,
                color = if (amountText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // One currency means nothing to choose - the chip row would just be a label.
            if (uiState.currencyRates.size > 1) {
                CurrencyChips(
                    codes = uiState.currencyRates.map { it.code },
                    selected = selectedCurrency,
                    onSelect = { selectedCurrency = it }
                )
                Spacer(Modifier.height(12.dp))
            }

            CategoryPicker(
                categories = uiState.categories,
                selectedCategoryId = selectedCategoryId,
                onSelect = { selectedCategoryId = it; userPickedCategory = true }
            )
            Spacer(Modifier.height(14.dp))

            // Always on screen, not gated behind "details": in practice this is filled in almost
            // every time and works more like a short description of the expense than an aside.
            // Up to two lines rather than singleLine, since a real description sometimes runs long.
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.label_note_optional)) },
                placeholder = { Text(stringResource(R.string.add_note)) },
                minLines = 1,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))

            DetailsSummaryRow(
                date = selectedDate,
                isShared = isShared,
                inGroup = uiState.inGroup,
                partnerName = uiState.partnerName,
                expanded = showDetails,
                onToggle = { showDetails = !showDetails }
            )
            Spacer(Modifier.height(14.dp))

            if (showDetails) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.date_prefix, formatShortDate(selectedDate)))
                }
                Spacer(Modifier.height(12.dp))

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
                }
            } else {
                AmountKeypad(
                    onKey = { key -> amountText = appendAmountKey(amountText, key, separator) },
                    onBackspace = { amountText = amountText.dropLast(1) }
                )
            }

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
            Spacer(Modifier.height(16.dp))

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyChips(codes: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        codes.forEach { code ->
            FilterChip(
                selected = selected == code,
                onClick = { onSelect(code) },
                label = { Text(code) }
            )
        }
    }
}

/**
 * "Today · Personal" - shows the state of the two fields that genuinely change only sometimes
 * (date, sharing), so the defaults are visible rather than implied, and opens the fields that
 * change them. The note has its own always-visible field above and isn't part of this summary.
 */
@Composable
private fun DetailsSummaryRow(
    date: LocalDate,
    isShared: Boolean,
    inGroup: Boolean,
    partnerName: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val today = LocalDate.now()
    val dateLabel = when (date) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> formatShortDate(date)
    }
    val sharedLabel = if (isShared) stringResource(R.string.shared_with, partnerName) else stringResource(R.string.personal_label)
    val summary = buildList {
        add(dateLabel)
        if (inGroup) add(sharedLabel)
    }.joinToString(" · ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.cd_hide_details else R.string.cd_show_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Prefill text for the amount field: whole numbers show without decimals, otherwise 2dp. */
private fun formatAmountInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else String.format("%.2f", amount)
