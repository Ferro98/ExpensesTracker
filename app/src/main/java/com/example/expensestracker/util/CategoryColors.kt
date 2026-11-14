package com.example.expensestracker.util

import androidx.compose.ui.graphics.Color
import com.example.expensestracker.data.model.CategorySpending
import kotlin.math.sqrt

/** The colours offered when creating a category - also the pool [distinctChartColors] draws replacements from. */
val categoryPresetColors = listOf(
    "#FF7043", "#42A5F5", "#AB47BC", "#EC407A", "#FFA726", "#5C6BC0",
    "#26A69A", "#29B6F6", "#78909C", "#8D6E63", "#66BB6A", "#EF5350"
)

/** A new category's starting colour: the first preset nobody uses yet, so two categories don't silently start out identical. */
fun nextUnusedCategoryColor(usedColors: Collection<String>): String {
    val used = usedColors.map { it.uppercase() }.toSet()
    return categoryPresetColors.firstOrNull { it.uppercase() !in used } ?: categoryPresetColors.first()
}

private fun distance(a: Color, b: Color): Float {
    val dr = (a.red - b.red) * 255f
    val dg = (a.green - b.green) * 255f
    val db = (a.blue - b.blue) * 255f
    return sqrt(dr * dr + dg * dg + db * db)
}

private const val TOO_SIMILAR = 50f

/**
 * One colour per category for a chart, where categories that ended up with the same (or nearly
 * the same) stored colour are told apart: in a ring or a legend, colour is the only thing linking a
 * slice to its row, so two identical oranges make both unreadable. Only the chart's colour changes -
 * the category itself keeps the one the user picked everywhere else. The first category with a
 * colour always keeps it; later look-alikes take the first preset not already close to something used.
 */
fun distinctChartColors(categories: List<CategorySpending>): Map<String, Color> {
    val result = LinkedHashMap<String, Color>()
    val used = mutableListOf<Color>()
    categories.forEach { category ->
        val own = category.colorHex.toColor()
        val chosen = if (used.none { distance(it, own) < TOO_SIMILAR }) {
            own
        } else {
            categoryPresetColors.map { it.toColor() }.firstOrNull { candidate -> used.none { distance(it, candidate) < TOO_SIMILAR } }
                ?: own
        }
        used += chosen
        result[category.categoryId] = chosen
    }
    return result
}
