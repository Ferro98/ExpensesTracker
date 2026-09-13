package com.example.expensestracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A list section's title, optionally with a trailing text action ("See all") - Home leans on that
 * action to stay short, showing only the top rows of each section and linking out to the full list.
 */
@Composable
fun SectionHeader(
    title: String,
    topPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    if (actionLabel == null || onActionClick == null) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier.padding(top = topPadding, bottom = 2.dp)
        )
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The TextButton brings its own vertical padding, so the row needs less than the
            // plain-title branch to end up optically aligned with it.
            .padding(top = topPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onActionClick, contentPadding = SectionActionPadding) {
            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val SectionActionPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
