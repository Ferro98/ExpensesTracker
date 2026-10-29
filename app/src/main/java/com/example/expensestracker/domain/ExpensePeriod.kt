package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Expense
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The expense as it counts toward the window [windowStart]..[windowEnd] (inclusive), or null if it
 * doesn't count there at all. An ordinary expense counts in full in the window containing its
 * date. A subscription counts in every window its period overlaps, for the share of its days that
 * fall inside - so a 200 pass over 45 days shows 3/45 of itself in the month it starts and the
 * rest in the next. Group balances and settlements deliberately don't go through this: they follow
 * the money actually paid, on the day it was paid.
 */
fun Expense.attributedTo(windowStart: LocalDate, windowEnd: LocalDate): Expense? {
    val start = localPeriodStart
    val end = localPeriodEnd
    if (start == null || end == null || end.isBefore(start)) {
        return if (!localDate.isBefore(windowStart) && !localDate.isAfter(windowEnd)) this else null
    }
    val overlapStart = maxOf(start, windowStart)
    val overlapEnd = minOf(end, windowEnd)
    if (overlapStart.isAfter(overlapEnd)) return null
    val overlapDays = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1
    val totalDays = ChronoUnit.DAYS.between(start, end) + 1
    return if (overlapDays == totalDays) this else copy(periodFactor = overlapDays.toDouble() / totalDays)
}

fun List<Expense>.attributedTo(windowStart: LocalDate, windowEnd: LocalDate): List<Expense> =
    mapNotNull { it.attributedTo(windowStart, windowEnd) }
