package com.example.expensestracker.domain

import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.data.model.RecurringExpense
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * The next date on or after [today] that [recurring] is due, based purely on its schedule
 * (frequency/dayOfPeriod/startDate) - unlike [RecurringExpenseGenerator], this ignores
 * [RecurringExpense.lastGeneratedDate] since it's used for reminders, not catch-up generation.
 */
fun RecurringExpense.nextOccurrence(today: LocalDate = LocalDate.now()): LocalDate {
    val from = maxOf(today, localStartDate)
    return when (frequency) {
        RecurrenceFrequency.MONTHLY -> nextMonthlyOccurrence(from)
        // Temporal.with(TemporalAdjuster) is typed to return Temporal in Java, so the platform
        // type needs an explicit cast back to LocalDate even though that's what it returns here.
        RecurrenceFrequency.WEEKLY -> from.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfPeriod))) as LocalDate
    }
}

private fun RecurringExpense.nextMonthlyOccurrence(from: LocalDate): LocalDate {
    var yearMonth = YearMonth.from(from)
    while (true) {
        val day = minOf(dayOfPeriod, yearMonth.lengthOfMonth())
        val due = yearMonth.atDay(day)
        if (!due.isBefore(from)) return due
        yearMonth = yearMonth.plusMonths(1)
    }
}
