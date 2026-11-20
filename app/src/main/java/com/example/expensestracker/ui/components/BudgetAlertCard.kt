package com.example.expensestracker.ui.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.ui.theme.semanticColors
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.localizedCategoryName
import kotlin.math.roundToInt

/** Share of its budget at which a category starts showing up in the Home warning. */
const val BUDGET_ALERT_THRESHOLD = 0.9

/**
 * Categories whose budget is nearly or fully used up, for the month in progress. Essential ones are
 * left out by the caller: rent sits at 100% of its budget every month by design, and a warning that
 * is always on is one nobody reads. What's left are the flexible budgets you can still do something about.
 */
fun budgetAlertsFor(categories: List<CategorySpending>, essentialIds: Set<String>, excludedId: String): List<CategorySpending> =
    categories
        .filter { c ->
            val budget = c.monthlyBudget
            budget != null && budget > 0 && c.categoryId != excludedId && c.categoryId !in essentialIds &&
                c.spent / budget >= BUDGET_ALERT_THRESHOLD
        }
        .sortedByDescending { it.spent / (it.monthlyBudget ?: 1.0) }
        .take(3)

@Composable
fun BudgetAlertCard(alerts: List<CategorySpending>, modifier: Modifier = Modifier) {
    if (alerts.isEmpty()) return
    val semantic = MaterialTheme.semanticColors
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = semantic.warningContainer, contentColor = semantic.onWarningContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.budget_alert_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            alerts.forEach { category ->
                val budget = category.monthlyBudget ?: return@forEach
                val label = "${category.icon} ${localizedCategoryName(category.name)}"
                Spacer(Modifier.height(6.dp))
                Text(
                    if (category.spent > budget)
                        stringResource(R.string.budget_alert_over, label, formatMoney(category.spent - budget))
                    else
                        stringResource(
                            R.string.budget_alert_near, label, (category.spent / budget * 100).roundToInt(),
                            formatMoney(category.spent), formatMoney(budget)
                        ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
