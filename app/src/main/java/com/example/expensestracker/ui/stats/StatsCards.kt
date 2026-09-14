package com.example.expensestracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.domain.MonthPace
import com.example.expensestracker.ui.month.CategoryDelta
import com.example.expensestracker.ui.month.MonthComparison
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatMonthName
import kotlin.math.roundToInt

/**
 * "Rispetto ad agosto": total change plus the categories that moved the most, up or down. A
 * spending *increase* reads as the semantic "negative" colour and a decrease as "positive" - the
 * opposite of the balance card's convention, since here more spending is the unwelcome direction.
 */
@Composable
fun MonthComparisonCard(comparison: MonthComparison, modifier: Modifier = Modifier) {
    if (comparison.previousTotal <= 0 && comparison.currentTotal <= 0) return

    val semantic = MaterialTheme.semanticColors
    val percent = comparison.percentChange
    val increased = comparison.currentTotal > comparison.previousTotal
    val changeColor = when {
        percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        increased -> semantic.negative
        comparison.currentTotal < comparison.previousTotal -> semantic.positive
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_vs_last_month, formatMonthName(comparison.previousMonth)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (percent == null) {
                    formatMoney(comparison.currentTotal)
                } else {
                    val arrow = if (increased) "▲" else "▼"
                    "$arrow ${kotlin.math.abs(percent).roundToInt()}%"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = changeColor
            )
            Text(
                stringResource(R.string.stats_previous_total, formatMoney(comparison.previousTotal)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (comparison.topMovers.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.stats_biggest_movers), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    comparison.topMovers.forEach { mover -> MoverRow(mover) }
                }
            }
        }
    }
}

@Composable
private fun MoverRow(mover: CategoryDelta) {
    val semantic = MaterialTheme.semanticColors
    val deltaColor = when {
        mover.delta > 0 -> semantic.negative
        mover.delta < 0 -> semantic.positive
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "${mover.icon} ${mover.name}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${if (mover.delta > 0) "+" else if (mover.delta < 0) "-" else ""}${formatMoney(kotlin.math.abs(mover.delta))}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = deltaColor
        )
    }
}

/**
 * "Ritmo": daily average and a naive end-of-month projection (spend so far / days elapsed × days
 * in the month). Only meaningful for the month actually in progress - a past or future month has
 * no "so far" to project from.
 */
@Composable
fun PaceCard(pace: MonthPace, monthlyBudget: Double?, modifier: Modifier = Modifier) {
    val semantic = MaterialTheme.semanticColors
    val overBudget = monthlyBudget != null && monthlyBudget > 0 && pace.projectedTotal > monthlyBudget

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_pace_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.stats_daily_average, formatMoney(pace.dailyAverage)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.stats_projected_total, formatMoney(pace.projectedTotal)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (overBudget) semantic.negative else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (overBudget) {
                Text(
                    stringResource(R.string.stats_projected_over_budget, formatMoney(monthlyBudget)),
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.negative
                )
            }
            // Surfaces the exclusion instead of leaving it a silent black box - otherwise "12€/day
            // on average" next to a month with a 400€ hotel booking just looks wrong.
            if (pace.excludedCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (pace.excludedCount == 1)
                        stringResource(R.string.stats_pace_excludes_one, formatMoney(pace.excludedTotal))
                    else
                        stringResource(R.string.stats_pace_excludes_many, pace.excludedCount, formatMoney(pace.excludedTotal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
