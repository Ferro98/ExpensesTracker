package com.example.expensestracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.util.formatMoney

/**
 * The month's category mix as a ring (each category's share of the total, drawn as an arc) with
 * the total in the middle. Deliberately no legend of its own: the category rows right below it
 * carry the same colours ([colors], see distinctChartColors) plus amount, share and budget, so a
 * second copy of the list here would only repeat them. Hand-drawn with Canvas rather than a
 * charting library - a ring of arcs is simple enough that a library adds weight without saving effort.
 */
@Composable
fun CategoryDonutChart(
    categories: List<CategorySpending>,
    colors: Map<String, Color>,
    modifier: Modifier = Modifier
) {
    val slices = categories.filter { it.spent > 0 }.sortedByDescending { it.spent }
    val total = slices.sumOf { it.spent }
    if (slices.isEmpty() || total <= 0) return

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(196.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(188.dp)) {
            // Thin enough that the hole is wide: the total in the middle has to fit inside it
            // without touching the ring, even at a large system font size.
            val strokeWidth = size.minDimension * 0.14f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            var startAngle = -90f
            slices.forEach { category ->
                val sweep = (category.spent / total * 360.0).toFloat()
                // Flat ends with a thin gap between slices (never so wide it eats a sliver-thin
                // one). Round caps used to make neighbours overlap into blobs.
                val gap = if (slices.size > 1) 2f else 0f
                drawArc(
                    color = colors[category.categoryId] ?: trackColor,
                    startAngle = startAngle + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(sweep * 0.9f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
                startAngle += sweep
            }
        }
        Text(
            formatMoney(total),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp)
        )
    }
}
