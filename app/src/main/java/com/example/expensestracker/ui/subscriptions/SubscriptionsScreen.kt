package com.example.expensestracker.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.domain.SubscriptionItem
import com.example.expensestracker.domain.SubscriptionKind
import com.example.expensestracker.domain.SubscriptionsOverview
import com.example.expensestracker.ui.components.EmptyState
import com.example.expensestracker.ui.components.SectionHeader
import com.example.expensestracker.ui.month.MonthViewModel
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatShortDate
import com.example.expensestracker.util.localizedCategoryName
import com.example.expensestracker.util.toColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A prepaid period ending within this many days is flagged - it's about to need renewing. */
private const val EXPIRING_SOON_DAYS = 7L

/**
 * Everything currently costing money on a schedule, with one comparable figure for each (its
 * monthly cost) and a total. Read-only: recurring templates are managed on their own screen, and a
 * prepaid period is just an expense with dates.
 */
@Composable
fun SubscriptionsScreen(viewModel: MonthViewModel, onManageRecurring: () -> Unit) {
    val overview by remember { viewModel.subscriptionsOverview() }.collectAsState(initial = SubscriptionsOverview())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (overview.isEmpty) {
            item {
                EmptyState(icon = "🔁", title = stringResource(R.string.subscriptions_empty))
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.subscriptions_monthly_total),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatMoney(overview.monthlyTotal),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.subscriptions_yearly_total, formatMoney(overview.yearlyTotal)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (overview.recurring.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.subscriptions_recurring),
                    topPadding = 6.dp,
                    actionLabel = stringResource(R.string.subscriptions_manage),
                    onActionClick = onManageRecurring
                )
            }
            items(overview.recurring, key = { "recurring-${it.id}" }) { SubscriptionRow(it) }
        }
        if (overview.prepaid.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.subscriptions_prepaid), topPadding = 6.dp) }
            items(overview.prepaid, key = { "prepaid-${it.id}" }) { SubscriptionRow(it) }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SubscriptionRow(item: SubscriptionItem) {
    val today = LocalDate.now()
    val daysLeft = ChronoUnit.DAYS.between(today, item.date)
    val expiringSoon = item.kind == SubscriptionKind.PREPAID && daysLeft <= EXPIRING_SOON_DAYS

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(item.colorHex.toColor().copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Text(item.icon) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    localizedCategoryName(item.title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val whenText = when (item.kind) {
                    SubscriptionKind.RECURRING -> stringResource(
                        if (item.frequency == RecurrenceFrequency.WEEKLY) R.string.subscriptions_every_week else R.string.subscriptions_every_month,
                        formatShortDate(item.date)
                    )
                    SubscriptionKind.PREPAID -> stringResource(
                        R.string.subscriptions_until,
                        formatShortDate(item.date),
                        pluralStringResource(R.plurals.period_days, daysLeft.toInt().coerceAtLeast(0), daysLeft.toInt().coerceAtLeast(0))
                    )
                }
                Text(
                    if (item.isShared) "$whenText · ${stringResource(R.string.subscriptions_shared)}" else whenText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expiringSoon) MaterialTheme.semanticColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.subscriptions_per_month, formatMoney(item.monthlyCost)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    formatMoney(item.chargeAmount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
