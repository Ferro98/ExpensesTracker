package com.example.expensestracker.ui.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.data.model.SavingsEntryKind
import com.example.expensestracker.ui.components.AppDatePickerDialog
import com.example.expensestracker.util.formatLongDate
import java.time.LocalDate

private val goalIcons = listOf("🐷", "🏰", "💻", "✈️", "🏖️", "🚗", "🏠", "🎁", "📱", "🎓", "💍", "🛠️")

private val amountPattern = Regex("^\\d{0,7}([.,]\\d{0,2})?$")

private fun parseAmount(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

private fun formatAmountField(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else String.format("%.2f", amount)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyChipRow(rates: List<CurrencyRate>, selected: String, onSelect: (String) -> Unit) {
    if (rates.size <= 1) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        rates.forEach { rate ->
            FilterChip(selected = selected == rate.code, onClick = { onSelect(rate.code) }, label = { Text(rate.code) })
        }
    }
}

/**
 * New or edited savings pot. When editing, amounts are shown in the base currency they're stored
 * in (the original currency of a goal isn't kept), and saving only changes the shared fields plus
 * *this* person's own monthly plan - in a shared pot the other member's plan is theirs to set.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalEditDialog(
    initial: GoalItem?,
    inGroup: Boolean,
    partnerUid: String?,
    partnerName: String,
    myUid: String,
    currencyRates: List<CurrencyRate>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (isGroup: Boolean, name: String, icon: String, target: Double, plan: Double?, currency: String, date: LocalDate?) -> Unit
) {
    val goal = initial?.goal
    var name by remember { mutableStateOf(goal?.name ?: "") }
    var icon by remember { mutableStateOf(goal?.icon ?: goalIcons.first()) }
    var isGroup by remember { mutableStateOf(initial?.isGroup ?: false) }
    var currency by remember { mutableStateOf(if (goal != null) DefaultUserData.BASE_CURRENCY else defaultCurrency) }
    var targetText by remember { mutableStateOf(goal?.targetAmount?.let(::formatAmountField) ?: "") }
    var planText by remember { mutableStateOf(goal?.monthlyPlans?.get(myUid)?.let(::formatAmountField) ?: "") }
    var targetDate by remember { mutableStateOf(goal?.localTargetDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    val target = parseAmount(targetText)
    val canSave = name.isNotBlank() && target != null && target > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (goal == null) R.string.savings_goal_new_title else R.string.savings_goal_edit_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.savings_goal_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    goalIcons.forEach { candidate ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (icon == candidate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (icon == candidate) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .clickable { icon = candidate },
                            contentAlignment = Alignment.Center
                        ) { Text(candidate) }
                    }
                }
                if (goal == null && inGroup) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.savings_goal_scope), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isGroup, onClick = { isGroup = false }, label = { Text(stringResource(R.string.savings_scope_personal)) })
                        FilterChip(selected = isGroup, onClick = { isGroup = true }, label = { Text(stringResource(R.string.savings_scope_group, partnerName)) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { if (it.isEmpty() || it.matches(amountPattern)) targetText = it },
                    label = { Text(stringResource(R.string.savings_goal_target)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                CurrencyChipRow(currencyRates, currency) { currency = it }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = planText,
                    onValueChange = { if (it.isEmpty() || it.matches(amountPattern)) planText = it },
                    label = { Text(stringResource(R.string.savings_goal_plan)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                val partnerPlan = partnerUid?.let { goal?.monthlyPlans?.get(it) }
                if (initial?.isGroup == true && partnerPlan != null && partnerPlan > 0) {
                    Text(
                        stringResource(R.string.savings_partner_plan, partnerName, com.example.expensestracker.util.formatMoney(partnerPlan)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text(
                            targetDate?.let { stringResource(R.string.savings_goal_deadline, formatLongDate(it)) }
                                ?: stringResource(R.string.savings_goal_no_deadline)
                        )
                    }
                    if (targetDate != null) {
                        TextButton(onClick = { targetDate = null }) { Text(stringResource(R.string.savings_goal_clear_deadline)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(isGroup, name.trim(), icon, target!!, parseAmount(planText)?.takeIf { it > 0 }, currency, targetDate) },
                enabled = canSave
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (showDatePicker) {
        AppDatePickerDialog(
            initial = targetDate ?: LocalDate.now().plusMonths(6),
            onDismiss = { showDatePicker = false },
            onPick = { targetDate = it }
        )
    }
}

/**
 * Adds to or takes out of a pot. The date can be backdated (the "I put 200 in on the 3rd" case);
 * in a shared pot the money can be marked as either member's, since whoever types it in isn't
 * always whose money moved. [suggestedAmount] pre-fills the amount (the unfulfilled part of a plan).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavingsEntryDialog(
    kind: SavingsEntryKind,
    goalName: String,
    inGroup: Boolean,
    myUid: String,
    partnerUid: String?,
    partnerName: String,
    currencyRates: List<CurrencyRate>,
    defaultCurrency: String,
    suggestedAmount: Double?,
    onDismiss: () -> Unit,
    onSave: (amount: Double, currency: String, date: LocalDate, note: String?, byUid: String) -> Unit
) {
    var amountText by remember { mutableStateOf(suggestedAmount?.let(::formatAmountField) ?: "") }
    var currency by remember { mutableStateOf(if (suggestedAmount != null) DefaultUserData.BASE_CURRENCY else defaultCurrency) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var byUid by remember { mutableStateOf(myUid) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amount = parseAmount(amountText)
    val deposit = kind == SavingsEntryKind.DEPOSIT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (deposit) R.string.savings_entry_deposit_title else R.string.savings_entry_withdraw_title, goalName))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.isEmpty() || it.matches(amountPattern)) amountText = it },
                    label = { Text(stringResource(R.string.label_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                CurrencyChipRow(currencyRates, currency) { currency = it }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.date_prefix, formatLongDate(date)))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(if (deposit) R.string.label_note_optional else R.string.savings_withdraw_reason)) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (inGroup && partnerUid != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.savings_entry_who), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = byUid == myUid, onClick = { byUid = myUid }, label = { Text(stringResource(R.string.you)) })
                        FilterChip(selected = byUid == partnerUid, onClick = { byUid = partnerUid }, label = { Text(partnerName) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(amount!!, currency, date, note, byUid) },
                enabled = amount != null && amount > 0
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (showDatePicker) {
        AppDatePickerDialog(initial = date, onDismiss = { showDatePicker = false }, onPick = { date = it })
    }
}
