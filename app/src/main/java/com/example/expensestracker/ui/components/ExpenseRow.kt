package com.example.expensestracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatShortDate
import com.example.expensestracker.util.localizedCategoryName
import com.example.expensestracker.util.toColor

/**
 * The single expense-row rendering, meant to be reused everywhere a list of expenses is shown
 * (dashboard, category drill-down, History and the Group activity feed) rather than
 * re-implemented per screen. [accentColor], when set, draws a thin left stripe (clipped to the
 * card's own rounded corners) - Group's activity feed uses it to colour-code who paid; every
 * other caller leaves it null and looks exactly as before.
 */
@Composable
fun ExpenseRow(
    expense: Expense,
    myUid: String,
    partnerName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    // Off where rows already sit under a day header (History), which says the date for them.
    showDate: Boolean = true
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (accentColor != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(expense.categoryColorHex.toColor().copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(expense.categoryIcon)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // The note is what the user actually cares about at a glance - the category (already
                // shown as the icon) rides along as a small subtitle instead of pushing the note out.
                val categoryName = localizedCategoryName(expense.categoryName)
                val note = expense.note?.takeIf { it.isNotBlank() }
                val primaryText = note ?: categoryName
                Text(
                    primaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val payerLabel = if (expense.paidByUid == myUid) stringResource(R.string.you) else partnerName
                val sharedLabel = if (expense.isShared) stringResource(R.string.paid_by_partner, payerLabel) else stringResource(R.string.personal_label)
                // The category's emoji is already in the circle on the left, and its name is already
                // the title when there's no note (or the note just repeats it) - saying it a second
                // time only pushed "who paid" off the end of the line.
                val subtitle = listOfNotNull(
                    categoryName.takeIf { note != null && !note.equals(categoryName, ignoreCase = true) },
                    formatShortDate(expense.localDate).takeIf { showDate },
                    sharedLabel,
                    stringResource(R.string.subscription_short).takeIf { expense.hasPeriod }
                ).joinToString(" · ")
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Only when the row is showing a slice of a longer period (i.e. a month view of
                // a subscription): the amount on the right is what was paid, this is what it
                // contributes to the month the row sits in.
                if (expense.periodFactor < 1.0) {
                    Text(
                        stringResource(R.string.expense_month_share, formatMoney(expense.amountInBaseCurrency * expense.periodFactor)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            AmountText(
                amountInBase = expense.amountInBaseCurrency,
                originalAmount = expense.amount,
                originalCurrencyCode = expense.currencyCode
            )
            }
        }
    }
}
