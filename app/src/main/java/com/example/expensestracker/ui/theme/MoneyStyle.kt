package com.example.expensestracker.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Text styles for money, with tabular numerals ("tnum") so a column of amounts lines up on their
 * digits instead of each number taking its natural (proportional) width.
 */
object MoneyStyle {
    private const val TABULAR_NUMS = "tnum"

    /** Hero totals - dashboard "spent this month", detail-sheet amount. */
    val Large = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, fontFeatureSettings = TABULAR_NUMS)

    /** Row-level amounts - expense/recurring list rows, category spend. */
    val Medium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR_NUMS)

    /** Secondary amounts - the original currency shown under the converted one. */
    val Small = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, fontFeatureSettings = TABULAR_NUMS)
}
