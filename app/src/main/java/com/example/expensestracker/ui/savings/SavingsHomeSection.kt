package com.example.expensestracker.ui.savings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.domain.SavingsMath
import com.example.expensestracker.ui.components.BudgetProgressBar
import com.example.expensestracker.ui.components.SectionHeader
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMoney
import java.time.LocalDate

/** How many pots Home lists before handing off to the full screen. */
private const val HOME_GOAL_LIMIT = 3

/**
 * Home's savings block: first, a prompt for every pot where the user planned a monthly amount and
 * hasn't said yet whether it went in (one tap to confirm the planned amount, or a different amount,
 * or skip the month); then a short progress list. Nothing here when there are no pots, so people
 * who don't use the feature never see it. A plan never records a deposit by itself - only these
 * answers do, so the pot can't claim money that wasn't actually put aside.
 */
fun LazyListScope.savingsHomeItems(
    state: SavingsUiState,
    onCheckInDeposit: (GoalItem, Double) -> Unit,
    onCheckInOther: (GoalItem, Double) -> Unit,
    onCheckInSkip: (GoalItem) -> Unit,
    onOpenSavings: () -> Unit
) {
    if (state.goals.isEmpty()) return
    val today = LocalDate.now()
    val due = state.goals.mapNotNull { item ->
        SavingsMath.checkInDue(item.goal, item.entries, state.myUid, today)?.let { item to it }
    }

    items(due, key = { "checkin-${it.first.goal.id}" }) { (item, remaining) ->
        CheckInCard(
            item = item,
            remaining = remaining,
            onYes = { onCheckInDeposit(item, remaining) },
            onOther = { onCheckInOther(item, remaining) },
            onSkip = { onCheckInSkip(item) }
        )
    }

    item(key = "savings-header") {
        SectionHeader(
            stringResource(R.string.title_savings),
            topPadding = 10.dp,
            actionLabel = stringResource(R.string.see_all),
            onActionClick = onOpenSavings
        )
    }
    item(key = "savings-progress") {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSavings),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                state.goals.take(HOME_GOAL_LIMIT).forEach { item ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GoalIcon(item.goal.icon, size = 32)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                item.goal.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${formatMoney(item.balance)} / ${formatMoney(item.goal.targetAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        BudgetProgressBar(
                            progress = item.progress,
                            color = if (item.complete) MaterialTheme.semanticColors.positive else MaterialTheme.colorScheme.primary,
                            height = 6.dp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckInCard(
    item: GoalItem,
    remaining: Double,
    onYes: () -> Unit,
    onOther: () -> Unit,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalIcon(item.goal.icon)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.savings_checkin_text, item.goal.name, formatMoney(remaining)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onYes) { Text(stringResource(R.string.savings_checkin_yes, formatMoney(remaining))) }
                OutlinedButton(onClick = onOther) { Text(stringResource(R.string.savings_checkin_other)) }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.savings_checkin_skip)) }
            }
        }
    }
}
