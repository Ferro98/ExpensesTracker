package com.example.expensestracker.ui.theme

import androidx.compose.ui.graphics.Color

// "Scandi" palette — Nordic blue/teal with a warm coral accent.
val FjordBlue = Color(0xFF1C6E8C)
val FjordBlueLight = Color(0xFFCFE7EF)
val FjordBlueDark = Color(0xFF0E4A5F)

val CoralAccent = Color(0xFFF2935C)
val CoralAccentLight = Color(0xFFFFDFC7)
val CoralAccentDark = Color(0xFFB35F2E)
val CoralAccentInk = Color(0xFF2E1608)

val SeaGreen = Color(0xFF3F8C6B)
val SeaGreenLight = Color(0xFFCFE9DD)
// A touch darker than SeaGreen: white text on plain SeaGreen only reaches a 4.06:1 contrast
// ratio (just under WCAG AA's 4.5:1 for normal text). Not SeaGreen itself, which is also
// colorScheme.tertiary and already visible elsewhere - this is only for solid-fill pairings
// like Semantic.kt's positive/onPositive.
val SeaGreenDeep = Color(0xFF3B8264)

val Sand = Color(0xFFF7F5F0)
val SandVariant = Color(0xFFEAE6DC)
val Ink = Color(0xFF1E2224)
val InkVariant = Color(0xFF52605F)

val Midnight = Color(0xFF12191C)
val MidnightSurface = Color(0xFF1A2327)
val MidnightSurfaceVariant = Color(0xFF2A363B)
val Mist = Color(0xFFDCE4E5)
val MistMuted = Color(0xFF9FB0B2)

// Surface "container" steps (bottom sheets, dialogs, menus). Left unset, Material falls back to its
// own purple-grey baseline, which made the add-expense sheet a visibly different colour from the
// blue-black screens behind it.
val MidnightContainerLowest = Color(0xFF0E1417)
val MidnightContainerLow = Color(0xFF161F23)
val MidnightContainerHigh = Color(0xFF1F2A2F)

val SandContainerLow = Color(0xFFFBF9F5)
val SandContainer = Color(0xFFF3F0E9)
val SandContainerHigh = Color(0xFFEDE9E0)
val SandContainerHighest = Color(0xFFE6E2D8)
