package com.example.expensestracker.domain

import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.data.model.RecurringExpense
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Turns active recurring expense templates ("fixed expenses") into concrete expense rows
 * whenever their due date has passed since the last time the app generated one.
 * Caps catch-up generation so re-opening the app after a long time doesn't flood
 * the expense list with dozens of backdated entries.
 *
 * One instance covers one scope (personal or group) - [repository] reads/writes that scope's
 * expenses+recurring collections, while [personalDataRepository] always supplies currency
 * conversion (rates are personal even when the expense being generated is group-scoped).
 *
 * Generated occurrences use a deterministic Firestore document id (see
 * [ExpenseRepository.insertGeneratedExpense]), so two devices independently generating the
 * same overdue occurrence while offline converge on one document instead of duplicating it.
 */
class RecurringExpenseGenerator(
    private val repository: ExpenseRepository,
    private val personalDataRepository: PersonalDataRepository
) {

    suspend fun generateDueExpenses(today: LocalDate = LocalDate.now()) {
        val active = repository.getActiveRecurring()
        for (recurring in active) {
            val dueDates = computeDueDates(recurring, today)
            if (dueDates.isEmpty()) continue
            val amountInBaseCurrency = personalDataRepository.convertToBase(recurring.amount, recurring.currencyCode)
            for (date in dueDates) {
                repository.insertGeneratedExpense(recurring, date, amountInBaseCurrency)
            }
            repository.updateRecurring(recurring.copy(lastGeneratedDate = dueDates.last().toString()))
        }
    }

    private fun computeDueDates(recurring: RecurringExpense, today: LocalDate): List<LocalDate> {
        val maxCatchUp = 12
        val dates = mutableListOf<LocalDate>()
        val startDate = recurring.localStartDate
        val lastGeneratedDate = recurring.localLastGeneratedDate
        when (recurring.frequency) {
            RecurrenceFrequency.MONTHLY -> {
                var yearMonth = lastGeneratedDate
                    ?.let { YearMonth.from(it).plusMonths(1) }
                    ?: YearMonth.from(startDate)
                while (!yearMonth.atDay(1).isAfter(today) && dates.size < maxCatchUp) {
                    val day = minOf(recurring.dayOfPeriod, yearMonth.lengthOfMonth())
                    val due = yearMonth.atDay(day)
                    if (!due.isBefore(startDate) && !due.isAfter(today)) {
                        dates.add(due)
                    }
                    yearMonth = yearMonth.plusMonths(1)
                }
            }

            RecurrenceFrequency.WEEKLY -> {
                val targetDow = DayOfWeek.of(recurring.dayOfPeriod)
                val cursorStart = lastGeneratedDate?.plusDays(1) ?: startDate
                var due = cursorStart.with(TemporalAdjusters.nextOrSame(targetDow))
                if (due.isBefore(startDate)) {
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
