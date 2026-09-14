package com.example.expensestracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R

/**
 * "Record a settlement", shared by Home's balance card and the Group screen so they behave
 * identically. [initialAmount]/[initialIPaid] let a caller prefill it from the current balance -
 * settling up almost always means paying exactly what's owed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettlementDialog(
    myUid: String,
    partnerUid: String,
    partnerName: String,
    currencyRates: List<String>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (fromUid: String, toUid: String, amount: Double, currencyCode: String, note: String?) -> Unit,
    initialAmount: Double? = null,
    initialIPaid: Boolean = true
) {
    var amountText by remember { mutableStateOf(initialAmount?.let { formatAmountInput(it) } ?: "") }
    var iPaid by remember { mutableStateOf(initialIPaid) }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var note by remember { mutableStateOf("") }

    val amount = amountText.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_settlement)) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = iPaid, onClick = { iPaid = true }, label = { Text(stringResource(R.string.settlement_i_paid, partnerName)) })
                    FilterChip(selected = !iPaid, onClick = { iPaid = false }, label = { Text(stringResource(R.string.settlement_partner_paid_me, partnerName)) })
                }
                Spacer(Modifier.height(12.dp))
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
                    label = { Text(stringResource(R.string.label_note_optional)) },
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
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Prefill text for the amount field: whole numbers show without decimals, otherwise 2dp. */
private fun formatAmountInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else String.format("%.2f", amount)
