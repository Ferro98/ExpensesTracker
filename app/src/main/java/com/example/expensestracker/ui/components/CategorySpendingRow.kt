package com.example.expensestracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.toColor

/**
 * One line of the "by category" breakdown, tappable to drill into that category's expenses.
 * Shared by Home (top spenders only) and Stats (every category), which is the whole point of it
 * living here: the user asked for the same familiar list in both places.
 */
@Composable
fun CategorySpendingRow(category: CategorySpending, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(category.colorHex.toColor().copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(category.icon)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (category.monthlyBudget != null)
                        "${formatMoney(category.spent)} / ${formatMoney(category.monthlyBudget)}"
                    else
                        formatMoney(category.spent),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            if (category.monthlyBudget != null && category.monthlyBudget > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val progress = (category.spent / category.monthlyBudget).toFloat().coerceIn(0f, 1f)
                val overBudget = category.spent > category.monthlyBudget
                BudgetProgressBar(
                    progress = progress,
                    color = if (overBudget) MaterialTheme.colorScheme.error else category.colorHex.toColor(),
                    trackColor = category.colorHex.toColor().copy(alpha = 0.15f),
                    height = 6.dp
                )
            }
        }
    }
}
