package com.example.expensestracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.ui.theme.MoneyStyle
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatShortDate
import com.example.expensestracker.util.formatWeekdayShort
import java.time.LocalDate

/** Date on the left, that day's total on the right - the divider between days in History. */
@Composable
fun DayHeader(date: LocalDate, total: Double, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> "${formatWeekdayShort(date)} ${formatShortDate(date)}"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            formatMoney(total),
            style = MoneyStyle.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
