package com.example.expensestracker.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.ui.month.MonthlyTrend
import com.example.expensestracker.util.formatCompactAmount
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.formatMonthShort
import java.time.YearMonth

private val BarAreaHeight = 112.dp
// Room above the tallest bar for its value label.
private val LabelSlotHeight = 22.dp

/**
 * The last months' totals as bars, tap-to-jump to that month. The month being viewed (always the
 * last bar) is drawn solid and the earlier ones muted, each bar carries its amount, and the personal
 * monthly budget - when there is one - is a dashed line across them, so "was that month over?" reads
 * straight off the chart. Plain Compose layout rather than a charting library: six bars and a line
 * don't need one, and it keeps full control over the highlight and labels.
 */
@Composable
fun MonthlyTrendChart(
    trend: MonthlyTrend,
    onMonthClick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val months = trend.months
    if (months.isEmpty()) return

    val budget = trend.monthlyBudget?.takeIf { it > 0 }
    val scaleMax = maxOf(months.maxOf { it.totalSpent }, budget ?: 0.0).coerceAtLeast(1.0)

    val primary = MaterialTheme.colorScheme.primary
    val mutedBar = primary.copy(alpha = 0.32f)
    // Only once there are essential categories to split by: every bar then shows needs (darker)
    // under everything else (lighter), instead of one flat tone.
    val showSplit = months.any { it.essentialSpent > 0 }
    val extraSelected = primary.copy(alpha = 0.5f)
    val essentialMuted = primary.copy(alpha = 0.45f)
    val extraMuted = primary.copy(alpha = 0.22f)
    val baseline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val budgetColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                months.forEachIndexed { index, month ->
                    val selected = index == months.lastIndex
                    val spent = month.totalSpent
                    val fraction = (spent / scaleMax).toFloat().coerceIn(0f, 1f)
                    val description = "${formatMonthShort(month.yearMonth)}: ${formatMoney(spent)}"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onMonthClick(month.yearMonth) }
                            .clearAndSetSemantics { contentDescription = description }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.height(BarAreaHeight + LabelSlotHeight)
                        ) {
                            if (spent > 0) {
                                Text(
                                    formatCompactAmount(spent),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(3.dp))
                            }
                            val barShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            val barWidth = if (spent > 0) 26.dp else 18.dp
                            val barHeight = if (spent > 0) (BarAreaHeight * fraction).coerceAtLeast(4.dp) else 2.dp
                            if (showSplit && spent > 0) {
                                // Two tones in one bar: needs at the bottom, the rest on top.
                                val essentialFraction = (month.essentialSpent / spent).toFloat().coerceIn(0f, 1f)
                                Column(modifier = Modifier.width(barWidth).height(barHeight).clip(barShape)) {
                                    if (essentialFraction < 1f) {
                                        Box(
                                            modifier = Modifier
                                                .weight((1f - essentialFraction).coerceAtLeast(0.001f))
                                                .fillMaxWidth()
                                                .background(if (selected) extraSelected else extraMuted)
                                        )
                                    }
                                    if (essentialFraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .weight(essentialFraction.coerceAtLeast(0.001f))
                                                .fillMaxWidth()
                                                .background(if (selected) primary else essentialMuted)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(barWidth)
                                        .height(barHeight)
                                        .background(
                                            color = if (spent <= 0) baseline else if (selected) primary else mutedBar,
                                            shape = barShape
                                        )
                                )
                            }
                        }
                        Text(
                            formatMonthShort(month.yearMonth),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            if (budget != null) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val fractionAbove = 1f - (budget / scaleMax).toFloat().coerceIn(0f, 1f)
                    val y = LabelSlotHeight.toPx() + BarAreaHeight.toPx() * fractionAbove
                    drawLine(
                        color = budgetColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
                    )
                }
            }
        }

        if (showSplit) {
            Text(
                stringResource(R.string.stats_trend_essentials_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp)
            )
        }
        if (budget != null) {
            Text(
                stringResource(R.string.stats_trend_budget_line, formatMoney(budget)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = if (showSplit) 2.dp else 8.dp, start = 8.dp)
            )
        }
    }
}
