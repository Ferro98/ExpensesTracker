package com.example.expensestracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.toColor

/**
 * Snapshot taken at the moment a category is tapped, so the sheet doesn't need to re-derive itself
 * reactively (and doesn't blank out if the underlying month state re-emits while it's open).
 */
data class CategoryDetailData(val name: String, val icon: String, val colorHex: String, val expenses: List<Expense>)

/** The expenses that make up one category's monthly total, reached from any "by category" row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailSheet(
    detail: CategoryDetailData,
    myUid: String,
    partnerName: String,
    onDismiss: () -> Unit,
    onOpenExpense: (Expense) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(detail.colorHex.toColor().copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(detail.icon, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(detail.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatMoney(detail.expenses.sumOf { it.shareFor(myUid) }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            if (detail.expenses.isEmpty()) {
                Text(
                    stringResource(R.string.no_expenses_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.expenses.sortedByDescending { it.localDate }.forEach { expense ->
                        ExpenseRow(
                            expense = expense,
                            myUid = myUid,
                            partnerName = partnerName,
                            onClick = { onOpenExpense(expense) }
                        )
                    }
                }
            }
        }
    }
}
