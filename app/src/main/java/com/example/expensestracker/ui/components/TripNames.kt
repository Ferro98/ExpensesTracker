package com.example.expensestracker.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Trip id -> "✈️ Name" for every trip the viewer can see, provided once at the app root so any
 * expense row can say which trip it belongs to without each list having to load trips itself. An id
 * with no entry (a deleted trip) simply shows nothing. Not static: only rows that read it recompose
 * when a trip is renamed.
 */
val LocalTripNames = compositionLocalOf<Map<String, String>> { emptyMap() }
