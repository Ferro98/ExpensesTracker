package com.example.expensestracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.SavingsEntryKind
import com.example.expensestracker.ui.group.PartnerSavingsMove
import com.example.expensestracker.util.formatMoney

/**
 * Stands in for a real push notification (see NewSharedExpensesViewModel) - shown once whenever
 * the app catches up and finds shared expenses, or movements in a shared savings pot, that the
 * partner made since the last time this was dismissed. Expense rows are the same ExpenseRow used
 * everywhere else, just non-clickable here: this is a heads-up, not a place to start editing from.
 */
@Composable
fun NewSharedExpensesDialog(
    expenses: List<Expense>,
    savingsMoves: List<PartnerSavingsMove>,
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
            Text(
                if (expenses.isNotEmpty()) pluralStringResource(R.plurals.new_shared_expenses_title, expenses.size, partnerName, expenses.size)
                else stringResource(R.string.new_savings_moves_title, partnerName)
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses, key = { it.id }) { expense ->
                    ExpenseRow(expense = expense, myUid = myUid, partnerName = partnerName, onClick = {})
                }
                if (savingsMoves.isNotEmpty()) {
                    items(savingsMoves.size) { index ->
                        val move = savingsMoves[index]
                        Text(
                            stringResource(
                                if (move.kind == SavingsEntryKind.DEPOSIT) R.string.new_savings_move_deposit else R.string.new_savings_move_withdrawal,
                                partnerName, formatMoney(move.amountBase), move.goalName
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    )
}
