package com.example.expensestracker.domain

import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.data.model.RecurringExpense
import java.time.LocalDate
import java.time.YearMonth

/**
 * The fixed expenses that haven't hit yet this month. The recurring generator turns an occurrence
 * into a real expense once its date arrives, so until then it's money that's already spoken for but
 * invisible in "spent" - which is exactly what makes a "budget left" figure look rosier than it is.
 */
object UpcomingRecurring {
    /** Dates after [today] and up to the end of its month on which [recurring] is still due and not yet generated. */
    fun datesLeftInMonth(recurring: RecurringExpense, today: LocalDate): List<LocalDate> {
        if (!recurring.active) return emptyList()
        val month = YearMonth.from(today)
        val monthEnd = month.atEndOfMonth()
        // Never earlier than tomorrow, the template's own start, or the day after what's been generated.
        val earliest = listOfNotNull(
            today.plusDays(1),
            recurring.localStartDate,
            recurring.localLastGeneratedDate?.plusDays(1)
        ).reduce { a, b -> if (a.isAfter(b)) a else b }
        if (earliest.isAfter(monthEnd)) return emptyList()

        return when (recurring.frequency) {
            RecurrenceFrequency.MONTHLY -> {
                val due = month.atDay(minOf(recurring.dayOfPeriod, month.lengthOfMonth()))
                if (!due.isBefore(earliest) && !due.isAfter(monthEnd)) listOf(due) else emptyList()
            }
            RecurrenceFrequency.WEEKLY ->
                generateSequence(earliest) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(monthEnd) }
                    .filter { it.dayOfWeek.value == recurring.dayOfPeriod }
                    .toList()
        }
    }

    /** The viewer's own share of one occurrence, in the template's own currency - same split rule as [com.example.expensestracker.data.model.Expense.shareFor]. */
    fun shareFor(recurring: RecurringExpense, uid: String): Double = when {
        !recurring.isShared -> if (recurring.paidByUid == uid) recurring.amount else 0.0
        recurring.paidByUid == uid -> recurring.amount * recurring.payerShare
        else -> recurring.amount * (1 - recurring.payerShare)
    }

    /** What's still to come this month for [uid], in the base currency ([rateToBase] gives each currency's rate). */
    fun remainingShareInBase(
        recurring: List<RecurringExpense>,
        today: LocalDate,
        uid: String,
        rateToBase: (String) -> Double
    ): Double = recurring.sumOf { template ->
        datesLeftInMonth(template, today).size * shareFor(template, uid) * rateToBase(template.currencyCode)
    }
}
