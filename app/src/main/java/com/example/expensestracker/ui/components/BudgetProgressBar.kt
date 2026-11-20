package com.example.expensestracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
 * A budget bar in two parts: what's been spent, then - right after it - what's been put aside into
 * savings, so the whole budget's commitments read at a glance. Fractions are of the budget; the rest
 * of the track is what's still free.
 */
@Composable
fun SegmentedBudgetBar(
    spentFraction: Float,
    savedFraction: Float,
    spentColor: Color,
    savedColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val rest = 1f - spentFraction - savedFraction
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
    ) {
        if (spentFraction > 0.001f) Box(modifier = Modifier.weight(spentFraction).fillMaxHeight().background(spentColor))
        if (savedFraction > 0.001f) Box(modifier = Modifier.weight(savedFraction).fillMaxHeight().background(savedColor))
        if (rest > 0.001f) Spacer(modifier = Modifier.weight(rest))
    }
}

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
        trackColor = trackColor,
        // The default M3 indicator leaves a gap before the track and ends it with a stray dot;
        // on a thin bar both just read as specks of dirt.
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}
