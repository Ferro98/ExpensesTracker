package com.example.expensestracker.domain

import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.data.model.RecurringExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpcomingRecurringTest {
    private val today = LocalDate.of(2026, 11, 20)

    private fun monthly(day: Int, last: String? = null, start: String = "2026-01-01", active: Boolean = true) = RecurringExpense(
        id = "r", amount = 100.0, frequency = RecurrenceFrequency.MONTHLY, dayOfPeriod = day,
        startDate = start, lastGeneratedDate = last, paidByUid = "me", active = active
    )

    @Test
    fun monthlyLaterThisMonthIsStillToCome() {
        assertEquals(listOf(LocalDate.of(2026, 11, 28)), UpcomingRecurring.datesLeftInMonth(monthly(28), today))
    }

    @Test
    fun monthlyAlreadyPassedOrDueTodayIsNot() {
        assertTrue(UpcomingRecurring.datesLeftInMonth(monthly(5), today).isEmpty())
        assertTrue(UpcomingRecurring.datesLeftInMonth(monthly(20), today).isEmpty())
    }

    @Test
    fun aDayThatDoesntExistThisMonthLandsOnTheLastDay() {
        assertEquals(listOf(LocalDate.of(2026, 11, 30)), UpcomingRecurring.datesLeftInMonth(monthly(31), today))
    }

    @Test
    fun anOccurrenceAlreadyGeneratedIsNotCountedAgain() {
        assertTrue(UpcomingRecurring.datesLeftInMonth(monthly(28, last = "2026-11-28"), today).isEmpty())
    }

    @Test
    fun aTemplateThatHasntStartedYetOrIsPausedDoesNotCount() {
        assertTrue(UpcomingRecurring.datesLeftInMonth(monthly(28, start = "2026-12-01"), today).isEmpty())
        assertTrue(UpcomingRecurring.datesLeftInMonth(monthly(28, active = false), today).isEmpty())
    }

    @Test
    fun weeklyCountsEachRemainingWeekday() {
        // Fridays after Fri 20 Nov: 27 Nov only.
        val friday = monthly(5).copy(frequency = RecurrenceFrequency.WEEKLY, dayOfPeriod = 5)
        assertEquals(listOf(LocalDate.of(2026, 11, 27)), UpcomingRecurring.datesLeftInMonth(friday, today))
        // Mondays after 20 Nov: 23 and 30.
        val monday = friday.copy(dayOfPeriod = 1)
        assertEquals(listOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 30)), UpcomingRecurring.datesLeftInMonth(monday, today))
    }

    @Test
    fun onlyMyShareCountsAndItIsConvertedToBase() {
        val shared = monthly(28).copy(isShared = true, paidByUid = "her", payerShare = 0.7, currencyCode = "DKK")
        // 100 DKK, I owe 30%, at 0.134 EUR/DKK.
        val total = UpcomingRecurring.remainingShareInBase(listOf(shared), today, "me") { if (it == "DKK") 0.134 else 1.0 }
        assertEquals(100.0 * 0.3 * 0.134, total, 1e-9)
    }

    @Test
    fun aPersonalTemplateIsZeroForSomeoneElse() {
        assertEquals(0.0, UpcomingRecurring.remainingShareInBase(listOf(monthly(28)), today, "her") { 1.0 }, 1e-9)
    }
}
