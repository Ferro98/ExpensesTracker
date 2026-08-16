package com.example.expensestracker.domain

import com.example.expensestracker.data.local.entity.RecurrenceFrequency
import com.example.expensestracker.data.local.entity.RecurringExpenseEntity
import com.example.expensestracker.data.repository.ExpenseRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Turns active recurring expense templates ("spese fisse") into concrete expense rows
 * whenever their due date has passed since the last time the app generated one.
 * Caps catch-up generation so re-opening the app after a long time doesn't flood
 * the expense list with dozens of backdated entries.
 */
class RecurringExpenseGenerator(private val repository: ExpenseRepository) {

    suspend fun generateDueExpenses(today: LocalDate = LocalDate.now()) {
        val active = repository.getActiveRecurring()
        for (recurring in active) {
            val dueDates = computeDueDates(recurring, today)
            if (dueDates.isEmpty()) continue
            for (date in dueDates) {
                repository.insertGeneratedExpense(recurring, date)
            }
            repository.updateRecurring(recurring.copy(lastGeneratedDate = dueDates.last()))
        }
    }

    private fun computeDueDates(recurring: RecurringExpenseEntity, today: LocalDate): List<LocalDate> {
        val maxCatchUp = 12
        val dates = mutableListOf<LocalDate>()
        when (recurring.frequency) {
            RecurrenceFrequency.MONTHLY -> {
                var yearMonth = recurring.lastGeneratedDate
                    ?.let { YearMonth.from(it).plusMonths(1) }
                    ?: YearMonth.from(recurring.startDate)
                while (!yearMonth.atDay(1).isAfter(today) && dates.size < maxCatchUp) {
                    val day = minOf(recurring.dayOfPeriod, yearMonth.lengthOfMonth())
                    val due = yearMonth.atDay(day)
                    if (!due.isBefore(recurring.startDate) && !due.isAfter(today)) {
                        dates.add(due)
                    }
                    yearMonth = yearMonth.plusMonths(1)
                }
            }

            RecurrenceFrequency.WEEKLY -> {
                val targetDow = DayOfWeek.of(recurring.dayOfPeriod)
                val cursorStart = recurring.lastGeneratedDate?.plusDays(1) ?: recurring.startDate
                var due = cursorStart.with(TemporalAdjusters.nextOrSame(targetDow))
                if (due.isBefore(recurring.startDate)) {
                    due = due.plusWeeks(1)
                }
                while (!due.isAfter(today) && dates.size < maxCatchUp) {
                    dates.add(due)
                    due = due.plusWeeks(1)
                }
            }
        }
        return dates
    }
}
