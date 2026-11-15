package com.example.expensestracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = FjordBlue,
    onPrimary = Color.White,
    primaryContainer = FjordBlueLight,
    onPrimaryContainer = FjordBlueDark,
    // Coral is reserved for the add-expense button and "the partner" (payer stripes in Group).
    // Everything Material tints with secondaryContainer - selected chips above all - follows the
    // primary teal instead, so the accent has one job rather than three. White on this coral only
    // reaches ~2.3:1, hence the dark ink for what sits on it.
    secondary = CoralAccent,
    onSecondary = CoralAccentInk,
    secondaryContainer = FjordBlueLight,
    onSecondaryContainer = FjordBlueDark,
    tertiary = SeaGreen,
    onTertiary = Color.White,
    tertiaryContainer = SeaGreenLight,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SandVariant,
    onSurfaceVariant = InkVariant,
    outline = InkVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SandContainerLow,
    surfaceContainer = SandContainer,
    surfaceContainerHigh = SandContainerHigh,
    surfaceContainerHighest = SandContainerHighest
)

private val DarkColorScheme = darkColorScheme(
    primary = FjordBlueLight,
    onPrimary = FjordBlueDark,
    primaryContainer = FjordBlueDark,
    onPrimaryContainer = FjordBlueLight,
    secondary = CoralAccent,
    onSecondary = CoralAccentInk,
    secondaryContainer = FjordBlueDark,
    onSecondaryContainer = FjordBlueLight,
    tertiary = SeaGreenLight,
    onTertiary = Color(0xFF12291F),
    background = Midnight,
    onBackground = Mist,
    surface = MidnightSurface,
    onSurface = Mist,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = MistMuted,
    outline = MistMuted,
    surfaceContainerLowest = MidnightContainerLowest,
    surfaceContainerLow = MidnightContainerLow,
    surfaceContainer = MidnightSurface,
    surfaceContainerHigh = MidnightContainerHigh,
    surfaceContainerHighest = MidnightSurfaceVariant
)

@Composable
fun ExpensesTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: this app leans on its own Nordic-inspired palette rather than
    // per-device dynamic color, so the brand look stays consistent across phones.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalSemanticColors provides semanticColorsFor(darkTheme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
