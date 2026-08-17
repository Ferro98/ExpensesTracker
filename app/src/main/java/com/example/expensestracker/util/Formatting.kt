package com.example.expensestracker.util

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

// Resolved fresh on every call (not cached) so it follows the device's current locale even if
// it changes while the app is running, rather than freezing whatever locale was active at
// first use.
private val appLocale: Locale get() = Locale.getDefault()

fun formatMoney(amount: Double, currencyCode: String = "EUR"): String {
    val symbol = try {
        Currency.getInstance(currencyCode).getSymbol(appLocale)
    } catch (e: IllegalArgumentException) {
        currencyCode
    }
    val formatted = String.format(appLocale, "%,.2f", amount)
    return "$symbol $formatted"
}

fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", appLocale))

fun formatWeekdayShort(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale).replaceFirstChar { it.uppercase() }

fun String.toColor(): Color = Color(android.graphics.Color.parseColor(this))
