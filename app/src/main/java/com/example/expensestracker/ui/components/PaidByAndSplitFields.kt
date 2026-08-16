package com.example.expensestracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * "Who paid" + optional custom split, shared by the quick-add expense sheet and the
 * recurring-expense dialog so the two never drift apart. [payerShare] is the fraction
 * (0..1) of the expense the *payer* is responsible for; the other member owes the rest.
 */
@Composable
fun PaidByAndSplitFields(
    myUid: String,
    partnerUid: String,
    partnerName: String,
    paidByUid: String,
    onPaidByChange: (String) -> Unit,
    customSplitEnabled: Boolean,
    onCustomSplitToggle: (Boolean) -> Unit,
    payerShare: Double,
    onPayerShareChange: (Double) -> Unit
) {
    Column {
        Text("Paid by", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = paidByUid == myUid, onClick = { onPaidByChange(myUid) }, label = { Text("Me") })
            FilterChip(selected = paidByUid == partnerUid, onClick = { onPaidByChange(partnerUid) }, label = { Text(partnerName) })
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = customSplitEnabled, onCheckedChange = onCustomSplitToggle)
            Text("Custom split (default 50/50)", style = MaterialTheme.typography.bodyMedium)
        }
        if (customSplitEnabled) {
            val payerName = if (paidByUid == myUid) "You" else partnerName
            val otherName = if (paidByUid == myUid) partnerName else "You"
            val payerPercent = (payerShare * 100).roundToInt()
            Text(
                "$payerName: $payerPercent% · $otherName: ${100 - payerPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = payerShare.toFloat(),
                onValueChange = { onPayerShareChange(it.toDouble()) },
                valueRange = 0f..1f
            )
        }
    }
}
