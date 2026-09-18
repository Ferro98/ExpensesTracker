package com.example.expensestracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.Expense

/**
 * Stands in for a real push notification (see NewSharedExpensesViewModel) - shown once whenever
 * the app catches up and finds shared expenses the partner added since the last time this was
 * dismissed. Rows are the same ExpenseRow used everywhere else, just non-clickable here: this is
 * a heads-up, not a place to start editing from.
 */
@Composable
fun NewSharedExpensesDialog(
    expenses: List<Expense>,
    myUid: String,
    partnerName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
        title = {
            Text(pluralStringResource(R.plurals.new_shared_expenses_title, expenses.size, partnerName, expenses.size))
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses, key = { it.id }) { expense ->
                    ExpenseRow(expense = expense, myUid = myUid, partnerName = partnerName, onClick = {})
                }
            }
        }
    )
}
