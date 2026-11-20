package com.example.expensestracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import androidx.compose.ui.res.stringResource
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMoney

/**
 * The "hero" of both Home and Stats: how much of the month is gone, against the personal budget.
 * [daysLeft] adds the pace line ("X/day left to stay in budget") and is only meaningful for the
 * month in progress - pass null when showing a month that's already over or hasn't started.
 */
@Composable
fun MonthSummaryCard(
    title: String,
    totalSpent: Double,
    monthlyBudget: Double?,
    daysLeft: Int? = null,
    modifier: Modifier = Modifier,
    savedThisMonth: Double = 0.0,
    upcomingFixed: Double = 0.0,
    plannedSavings: Double = 0.0
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Animates the crossfade when totalSpent changes under the same card - swiping months,
            // or a save/delete landing while it's on screen - rather than the number just jumping.
            AnimatedContent(targetState = totalSpent, label = "monthTotal") { animatedTotal ->
                Text(
                    formatMoney(animatedTotal),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (monthlyBudget == null || monthlyBudget <= 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.no_personal_budget),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Spacer(modifier = Modifier.height(14.dp))
            // "Spent" stays the real spending; money put aside is budget already spoken for, so it
            // takes up room on the bar and comes off what's left, without inflating the total above.
            // A net withdrawal (negative) frees budget instead.
            val used = (totalSpent + savedThisMonth).coerceAtLeast(0.0)
            val progress = (used / monthlyBudget).toFloat().coerceIn(0f, 1f)
            val overBudget = used > monthlyBudget
            val progressColor = when {
                overBudget -> MaterialTheme.semanticColors.negative
                progress > 0.8f -> MaterialTheme.semanticColors.warning
                else -> MaterialTheme.colorScheme.primary
            }
            if (savedThisMonth > 0) {
                val spentFraction = (totalSpent / monthlyBudget).toFloat().coerceIn(0f, 1f)
                val savedFraction = (savedThisMonth / monthlyBudget).toFloat().coerceIn(0f, 1f - spentFraction)
                SegmentedBudgetBar(
                    spentFraction = spentFraction,
                    savedFraction = savedFraction,
                    spentColor = progressColor,
                    savedColor = MaterialTheme.semanticColors.positive,
                    height = 10.dp
                )
            } else {
                BudgetProgressBar(progress = (totalSpent / monthlyBudget).toFloat().coerceIn(0f, 1f), color = progressColor, height = 10.dp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            val remaining = monthlyBudget - used
            Text(
                text = if (remaining >= 0)
                    stringResource(R.string.budget_left, formatMoney(monthlyBudget), formatMoney(remaining))
                else
                    stringResource(R.string.budget_over_by, formatMoney(monthlyBudget), formatMoney(-remaining)),
                style = MaterialTheme.typography.bodySmall,
                color = if (overBudget) MaterialTheme.semanticColors.negative else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (savedThisMonth != 0.0) {
                Text(
                    if (savedThisMonth > 0)
                        stringResource(R.string.budget_saved_note, formatMoney(savedThisMonth))
                    else
                        stringResource(R.string.budget_withdrawn_note, formatMoney(-savedThisMonth)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (savedThisMonth > 0) MaterialTheme.semanticColors.positive else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val ahead = upcomingFixed + plannedSavings
            if (daysLeft != null && daysLeft > 0) {
                if (ahead > 0.005) {
                    // "Left" counts only what's already gone out. Fixed expenses that haven't hit yet and
                    // savings I've planned but not put in are just as spoken for, so the figure to
                    // plan the rest of the month by takes them off too.
                    val free = remaining - ahead
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (free >= 0)
                            stringResource(R.string.budget_really_free, formatMoney(free), formatMoney(free / daysLeft))
                        else
                            stringResource(R.string.budget_really_free_negative, formatMoney(-free)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (free >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.semanticColors.negative
                    )
                    val parts = listOfNotNull(
                        stringResource(R.string.budget_ahead_fixed, formatMoney(upcomingFixed)).takeIf { upcomingFixed > 0.005 },
                        stringResource(R.string.budget_ahead_savings, formatMoney(plannedSavings)).takeIf { plannedSavings > 0.005 }
                    )
                    Text(
                        stringResource(R.string.budget_ahead_note, parts.joinToString(" + ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (remaining > 0) {
                    Text(
                        stringResource(R.string.budget_daily_pace, formatMoney(remaining / daysLeft)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }
    }
}
