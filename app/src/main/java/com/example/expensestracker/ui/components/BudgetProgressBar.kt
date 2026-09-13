package com.example.expensestracker.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spent/allocated-vs-budget bar shape (height + rounded ends + track), which was repeated at
 * every one of its five call sites across Dashboard and Categories. Callers keep deciding their
 * own progress fraction and color - those actually differ by call site today (a category's own
 * brand color here, a 3-tier blue/amber/red elsewhere) - this only removes the boilerplate around
 * `LinearProgressIndicator` itself.
 */
@Composable
fun BudgetProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2)),
        color = color,
        trackColor = trackColor
    )
}
