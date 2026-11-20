package com.example.expensestracker.ui.trips

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
import com.example.expensestracker.ui.components.AppDatePickerDialog
import com.example.expensestracker.util.formatLongDate
import java.time.LocalDate

private val tripIcons = listOf("✈️", "🏖️", "🏰", "🏔️", "🚗", "🏕️", "🎡", "🏙️", "⛵", "🎒", "🏠", "🎁")

private val amountPattern = Regex("^\\d{0,7}([.,]\\d{0,2})?$")

private enum class DateField { START, END }

/**
 * New or edited trip. Moving the start past the end pulls the end along (and the other way round),
 * so a trip can never end before it begins. When editing, the budget is shown in the base currency
 * it's stored in - the currency it was typed in isn't kept.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripEditDialog(
    initial: TripItem?,
    inGroup: Boolean,
    partnerName: String,
    currencyRates: List<CurrencyRate>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (isGroup: Boolean, name: String, icon: String, start: LocalDate, end: LocalDate, budget: Double?, currency: String) -> Unit
) {
    val trip = initial?.trip
    var name by remember { mutableStateOf(trip?.name ?: "") }
    var icon by remember { mutableStateOf(trip?.icon ?: tripIcons.first()) }
    var isGroup by remember { mutableStateOf(initial?.isGroup ?: false) }
    var start by remember { mutableStateOf(trip?.localStart ?: LocalDate.now()) }
    var end by remember { mutableStateOf(trip?.localEnd ?: LocalDate.now().plusDays(6)) }
    var currency by remember { mutableStateOf(if (trip != null) DefaultUserData.BASE_CURRENCY else defaultCurrency) }
    var budgetText by remember {
        mutableStateOf(trip?.budget?.takeIf { it > 0 }?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else String.format("%.2f", it) } ?: "")
    }
    var picking by remember { mutableStateOf<DateField?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (trip == null) R.string.trip_new_title else R.string.trip_edit_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.trip_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tripIcons.forEach { candidate ->
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
                if (trip == null && inGroup) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.trip_scope), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isGroup, onClick = { isGroup = false }, label = { Text(stringResource(R.string.savings_scope_personal)) })
                        FilterChip(selected = isGroup, onClick = { isGroup = true }, label = { Text(stringResource(R.string.savings_scope_group, partnerName)) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { picking = DateField.START }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.period_from_prefix, formatLongDate(start)), maxLines = 1)
                    }
                    OutlinedButton(onClick = { picking = DateField.END }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.period_to_prefix, formatLongDate(end)), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { if (it.isEmpty() || it.matches(amountPattern)) budgetText = it },
                    label = { Text(stringResource(R.string.trip_budget)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (currencyRates.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        currencyRates.forEach { rate ->
                            FilterChip(selected = currency == rate.code, onClick = { currency = rate.code }, label = { Text(rate.code) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(isGroup, name.trim(), icon, start, end, budgetText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }, currency)
                },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    picking?.let { field ->
        AppDatePickerDialog(
            initial = if (field == DateField.START) start else end,
            onDismiss = { picking = null },
            onPick = { picked ->
                if (field == DateField.START) {
                    start = picked
                    if (end.isBefore(picked)) end = picked
                } else {
                    end = picked
                    if (start.isAfter(picked)) start = picked
                }
            }
        )
    }
}
