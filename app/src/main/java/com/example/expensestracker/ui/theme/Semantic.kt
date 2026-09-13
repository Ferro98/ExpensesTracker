package com.example.expensestracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * "Is this number good or bad news" was previously decided ad hoc per screen - budget bars used
 * a hardcoded amber and M3's `error`, the balance card borrowed `secondaryContainer`/
 * `tertiaryContainer` for owe/owed, `CategorySpendingRow` used the raw category color for
 * "on track" - inconsistent color-to-meaning mapping across otherwise-identical states. This is
 * one small vocabulary for all of it, tinted to sit with the Scandi palette rather than injecting
 * generic Material red/green.
 */
data class SemanticColors(
    val positive: Color,
    val onPositive: Color,
    val positiveContainer: Color,
    val onPositiveContainer: Color,
    val negative: Color,
    val onNegative: Color,
    val negativeContainer: Color,
    val onNegativeContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val neutral: Color,
    val onNeutral: Color
)

private val LightSemanticColors = SemanticColors(
    positive = SeaGreen,
    onPositive = Color.White,
    positiveContainer = SeaGreenLight,
    onPositiveContainer = Color(0xFF12291F),
    negative = Color(0xFFC1473F),
    onNegative = Color.White,
    negativeContainer = Color(0xFFF7DAD6),
    onNegativeContainer = Color(0xFF5C1A14),
    warning = Color(0xFFF9A825),
    onWarning = Color(0xFF3D2C00),
    warningContainer = Color(0xFFFFF2CF),
    onWarningContainer = Color(0xFF5C4200),
    neutral = InkVariant,
    onNeutral = Color.White
)

private val DarkSemanticColors = SemanticColors(
    positive = SeaGreenLight,
    onPositive = Color(0xFF12291F),
    positiveContainer = Color(0xFF1F3A2E),
    onPositiveContainer = SeaGreenLight,
    negative = Color(0xFFE8897E),
    onNegative = Color(0xFF3F0A05),
    negativeContainer = Color(0xFF5C231C),
    onNegativeContainer = Color(0xFFF7DAD6),
    warning = Color(0xFFFFCB66),
    onWarning = Color(0xFF3D2C00),
    warningContainer = Color(0xFF4A3800),
    onWarningContainer = Color(0xFFFFF2CF),
    neutral = MistMuted,
    onNeutral = Color(0xFF12191C)
)

internal val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** e.g. `MaterialTheme.semanticColors.positive`, mirroring how `MaterialTheme.colorScheme` reads. */
val MaterialTheme.semanticColors: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current

internal fun semanticColorsFor(darkTheme: Boolean): SemanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
