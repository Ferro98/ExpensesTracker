package com.example.expensestracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.example.expensestracker.R
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.ui.theme.MoneyStyle
import com.example.expensestracker.util.formatShortDate
import com.example.expensestracker.util.localizedCategoryName
import com.example.expensestracker.util.toColor

/** Everything about one expense, opened by tapping its row from Home, History, Stats or a category drill-down. */
@Composable
fun ExpenseDetailSheet(
    expense: Expense,
    myUid: String,
    partnerName: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    DetailSheet(
        onDismiss = onDismiss,
        icon = expense.categoryIcon,
        iconTint = expense.categoryColorHex.toColor(),
        title = localizedCategoryName(expense.categoryName),
        subtitle = formatShortDate(expense.localDate),
        onEdit = onEdit,
        onDelete = onDelete,
        onDuplicate = onDuplicate,
        amount = {
            AmountText(
                amountInBase = expense.amountInBaseCurrency,
                originalAmount = expense.amount,
                originalCurrencyCode = expense.currencyCode,
                style = MoneyStyle.Large,
                secondaryStyle = MoneyStyle.Medium,
                horizontalAlignment = Alignment.Start
            )
        }
    ) {
        expense.note?.takeIf { it.isNotBlank() }?.let {
            DetailRow(stringResource(R.string.label_note_optional), it)
        }
        val payerLabel = if (expense.paidByUid == myUid) stringResource(R.string.you) else partnerName
        val sharedLabel = if (expense.isShared) stringResource(R.string.paid_by_partner, payerLabel) else stringResource(R.string.personal_label)
        DetailRow(stringResource(R.string.label_status), sharedLabel)
        if (expense.isShared && expense.payerShare != 0.5) {
            val myPercent = (if (expense.paidByUid == myUid) expense.payerShare else 1 - expense.payerShare) * 100
            DetailRow(stringResource(R.string.custom_split_label), "${myPercent.toInt()}% / ${100 - myPercent.toInt()}%")
        }
    }
}
