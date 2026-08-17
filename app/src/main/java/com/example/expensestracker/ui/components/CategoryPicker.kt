package com.example.expensestracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.util.toColor

/**
 * Horizontal-scroll row of icon-only circles instead of a wrapping row of labeled chips - with
 * 8-9 categories the labeled-chip version wraps to several uneven rows and reads as clutter. The
 * selected category's full name is shown as a single label below the row instead of on every chip.
 */
@Composable
fun CategoryPicker(
    categories: List<Category>,
    selectedCategoryId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(categories, key = { it.id }) { category ->
                val selected = category.id == selectedCategoryId
                val ringColor = category.colorHex.toColor()
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(ringColor.copy(alpha = if (selected) 0.28f else 0.14f))
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) ringColor else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onSelect(category.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(category.icon, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }
        if (selectedCategory != null) {
            Text(
                selectedCategory.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
