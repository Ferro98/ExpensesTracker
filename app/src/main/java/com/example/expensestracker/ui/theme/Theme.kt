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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = FjordBlue,
    onPrimary = Color.White,
    primaryContainer = FjordBlueLight,
    onPrimaryContainer = FjordBlueDark,
    secondary = CoralAccent,
    onSecondary = Color.White,
    secondaryContainer = CoralAccentLight,
    onSecondaryContainer = CoralAccentDark,
    tertiary = SeaGreen,
    onTertiary = Color.White,
    tertiaryContainer = SeaGreenLight,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SandVariant,
    onSurfaceVariant = InkVariant,
    outline = InkVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = FjordBlueLight,
    onPrimary = FjordBlueDark,
    primaryContainer = FjordBlueDark,
    onPrimaryContainer = FjordBlueLight,
    secondary = CoralAccent,
    onSecondary = CoralAccentInk,
    secondaryContainer = CoralAccentDark,
    onSecondaryContainer = CoralAccentLight,
    tertiary = SeaGreenLight,
    onTertiary = Color(0xFF12291F),
    background = Midnight,
    onBackground = Mist,
    surface = MidnightSurface,
    onSurface = Mist,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = MistMuted,
    outline = MistMuted
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
