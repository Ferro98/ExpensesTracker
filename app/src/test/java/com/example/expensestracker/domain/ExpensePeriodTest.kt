package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpensePeriodTest {
    private fun pass(amount: Double, start: String, end: String) = Expense(
        id = "pass",
        amount = amount,
        amountInBaseCurrency = amount,
        date = start,
        paidByUid = "me",
        periodStart = start,
        periodEnd = end
    )

    private fun share(expense: Expense, ym: YearMonth): Double =
        expense.attributedTo(ym.atDay(1), ym.atEndOfMonth())?.shareFor("me") ?: 0.0

    @Test
    fun splitsAcrossMonthsByDaysAndSumsBackToTheAmountPaid() {
        // 200 over 29 Oct - 12 Dec 2026 = 45 days: 3 in October, 30 in November, 12 in December.
        val expense = pass(200.0, "2026-10-29", "2026-12-12")
        assertEquals(200.0 * 3 / 45, share(expense, YearMonth.of(2026, 10)), 1e-9)
        assertEquals(200.0 * 30 / 45, share(expense, YearMonth.of(2026, 11)), 1e-9)
        assertEquals(200.0 * 12 / 45, share(expense, YearMonth.of(2026, 12)), 1e-9)
        val total = (10..12).sumOf { share(expense, YearMonth.of(2026, it)) }
        assertEquals(200.0, total, 1e-9)
    }

    @Test
    fun monthsOutsideThePeriodGetNothing() {
        val expense = pass(200.0, "2026-10-29", "2026-12-12")
        assertNull(expense.attributedTo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        assertNull(expense.attributedTo(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)))
    }

    @Test
    fun periodInsideOneMonthCountsInFullAndIsNotCopied() {
        val expense = pass(30.0, "2026-10-05", "2026-10-20")
        val attributed = expense.attributedTo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertSame(expense, attributed)
    }

    @Test
    fun ordinaryExpenseStillFollowsItsDate() {
        val expense = Expense(id = "x", amountInBaseCurrency = 10.0, date = "2026-10-15", paidByUid = "me")
        assertNotNull(expense.attributedTo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
        assertNull(expense.attributedTo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun slicingNeverRewritesTheRealAmountPaid() {
        val attributed = pass(200.0, "2026-10-29", "2026-12-12")
            .attributedTo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))!!
        assertEquals(200.0, attributed.amount, 0.0)
        assertEquals(200.0, attributed.amountInBaseCurrency, 0.0)
    }

    @Test
    fun sharedSubscriptionSlicesTheOwnShareToo() {
        val shared = pass(200.0, "2026-10-29", "2026-12-12").copy(isShared = true, payerShare = 0.5)
        assertEquals(100.0 * 3 / 45, share(shared, YearMonth.of(2026, 10)), 1e-9)
    }
}
