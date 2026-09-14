package com.example.expensestracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensestracker.data.model.CategorySpending
import com.example.expensestracker.util.formatMoney
import com.example.expensestracker.util.toColor
import kotlin.math.roundToInt

/**
 * The month's category mix as a ring (each category's share of the total, drawn as an arc) with
 * the total in the middle, and a tappable legend below - name, amount and percentage, biggest
 * spender first. Hand-drawn with Canvas rather than a charting library, matching the donut in the
 * original mockups (see docs/UX_REDESIGN_PLAN.md 3.2/3.5): a ring of arcs is simple enough that a
 * library adds dependency weight without saving real effort.
 */
@Composable
fun CategoryDonutChart(
    categories: List<CategorySpending>,
    onCategoryClick: (CategorySpending) -> Unit,
    modifier: Modifier = Modifier
) {
    val slices = categories.filter { it.spent > 0 }.sortedByDescending { it.spent }
    val total = slices.sumOf { it.spent }
    if (slices.isEmpty() || total <= 0) return

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            Canvas(modifier = Modifier.size(160.dp)) {
                val strokeWidth = size.minDimension * 0.22f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                // A full track first, so a single-category month still reads as a ring rather than
                // a bare arc floating on nothing.
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
                    drawArc(
                        color = category.colorHex.toColor(),
                        startAngle = startAngle,
                        // A hairline gap between slices, but never so wide it eats a sliver-thin one.
                        sweepAngle = (sweep - 2f).coerceAtLeast(sweep * 0.9f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatMoney(total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            slices.forEach { category ->
                val percent = (category.spent / total * 100).roundToInt()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(category) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(category.colorHex.toColor())
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${category.icon} ${category.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatMoney(category.spent),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}
