package com.example.expensestracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.domain.EssentialSplit
import com.example.expensestracker.ui.month.MonthViewModel
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.localizedCategoryName
import kotlin.math.roundToInt

/**
 * Needs versus everything else, for the month on screen. Until the user has marked at least one
 * category essential there's nothing to split by, so the card is an invitation to do that instead
 * of a bar that would read "100% extra" and mean nothing.
 */
@Composable
fun EssentialSplitCard(
    split: EssentialSplit,
    configured: Boolean,
    onChooseEssentials: () -> Unit,
    modifier: Modifier = Modifier,
    // Net put into savings goals this month. Not spending, but part of where the budget went, so
    // it's the third slice: needs, wants, savings.
    saved: Double = 0.0
) {
    val savedAmount = saved.coerceAtLeast(0.0)
    if (configured && split.total <= 0 && savedAmount <= 0) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_essentials_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            if (!configured) {
                Text(
                    stringResource(R.string.stats_essentials_setup_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onChooseEssentials) { Text(stringResource(R.string.stats_essentials_setup_action)) }
                return@Column
            }

            val essentialColor = MaterialTheme.colorScheme.primary
            val extraColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
            val savedColor = MaterialTheme.semanticColors.positive
            val outflow = split.total + savedAmount

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(extraColor)
            ) {
                listOf(split.essential to essentialColor, split.extra to extraColor, savedAmount to savedColor).forEach { (amount, color) ->
                    if (amount > 0 && outflow > 0) {
                        Box(
                            modifier = Modifier
                                .weight((amount / outflow).toFloat().coerceAtLeast(0.001f))
                                .height(12.dp)
                                .background(color)
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SplitLegendRow(stringResource(R.string.stats_essentials_label), split.essential, outflow, essentialColor)
            Spacer(Modifier.height(8.dp))
            SplitLegendRow(stringResource(R.string.stats_extra_label), split.extra, outflow, extraColor)
            if (savedAmount > 0) {
                Spacer(Modifier.height(8.dp))
                SplitLegendRow(stringResource(R.string.stats_saved_label), savedAmount, outflow, savedColor)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onChooseEssentials) { Text(stringResource(R.string.stats_essentials_edit)) }
        }
    }
}

@Composable
private fun SplitLegendRow(label: String, amount: Double, total: Double, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(formatMoney(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Text(
            "${if (total > 0) (amount / total * 100).roundToInt() else 0}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
    }
}

/**
 * One checklist for every category, so setting this up is a single pass rather than editing nine
 * categories one by one. When nothing is essential yet it opens with the defaults that usually are
 * (rent, bills, health, transport) already ticked - a suggestion to confirm, not a decision made
 * for the user.
 */
@Composable
fun EssentialCategoriesDialog(
    categories: List<CategorySpending>,
    currentEssentialIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val choices = categories.filter { it.categoryId != MonthViewModel.SHARED_BUCKET_ID }
    var selected by remember {
        mutableStateOf(
            if (currentEssentialIds.isNotEmpty()) currentEssentialIds
            else choices.filter { DefaultUserData.isSuggestedEssential(it.name) }.map { it.categoryId }.toSet()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.essentials_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.essentials_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                ) {
                    choices.forEach { category ->
                        val checked = category.categoryId in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - category.categoryId else selected + category.categoryId
                                }
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Text(
                                "${category.icon} ${localizedCategoryName(category.name)}",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
