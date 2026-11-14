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
    modifier: Modifier = Modifier
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
            val progress = (totalSpent / monthlyBudget).toFloat().coerceIn(0f, 1f)
            val overBudget = totalSpent > monthlyBudget
            val progressColor = when {
                overBudget -> MaterialTheme.semanticColors.negative
                progress > 0.8f -> MaterialTheme.semanticColors.warning
                else -> MaterialTheme.colorScheme.primary
            }
            BudgetProgressBar(progress = progress, color = progressColor, height = 10.dp)
            Spacer(modifier = Modifier.height(8.dp))
            val remaining = monthlyBudget - totalSpent
            Text(
                text = if (remaining >= 0)
                    stringResource(R.string.budget_left, formatMoney(monthlyBudget), formatMoney(remaining))
                else
                    stringResource(R.string.budget_over_by, formatMoney(monthlyBudget), formatMoney(-remaining)),
                style = MaterialTheme.typography.bodySmall,
                color = if (overBudget) MaterialTheme.semanticColors.negative else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (daysLeft != null && daysLeft > 0 && remaining > 0) {
                Text(
                    stringResource(R.string.budget_daily_pace, formatMoney(remaining / daysLeft)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        }
    }
}
