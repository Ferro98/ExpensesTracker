package com.example.expensestracker.util

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.YearMonth
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

// Resolved fresh on every call (not cached) so it follows the device's current locale even if
// it changes while the app is running, rather than freezing whatever locale was active at
// first use.
private val appLocale: Locale get() = Locale.getDefault()

/** "€", "kr" - falls back to the code itself for anything the JDK doesn't know. */
fun currencySymbol(currencyCode: String): String = try {
    Currency.getInstance(currencyCode).getSymbol(appLocale)
} catch (e: IllegalArgumentException) {
    currencyCode
}

fun formatMoney(amount: Double, currencyCode: String = "EUR"): String {
    val formatted = String.format(appLocale, "%,.2f", amount)
    return "${currencySymbol(currencyCode)} $formatted"
}

/** The character this locale types between units and cents - the keypad's separator key. */
fun decimalSeparator(): Char = DecimalFormatSymbols.getInstance(appLocale).decimalSeparator

fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", appLocale))

fun formatWeekdayShort(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale).replaceFirstChar { it.uppercase() }

/** "Settembre 2026" - the month-picker header. */
fun formatMonthLabel(yearMonth: YearMonth): String =
    yearMonth.month.getDisplayName(TextStyle.FULL, appLocale).replaceFirstChar { it.uppercase() } + " " + yearMonth.year

/**
 * Just the month name, cased the way the locale writes it mid-sentence ("settembre", "September"),
 * for slotting into "Speso a %1$s" / "Spent in %1$s".
 */
fun formatMonthName(yearMonth: YearMonth): String =
    yearMonth.month.getDisplayName(TextStyle.FULL, appLocale)

fun String.toColor(): Color = Color(android.graphics.Color.parseColor(this))
