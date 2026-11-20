package com.example.expensestracker.domain

import com.example.expensestracker.data.model.SavingsEntry
import com.example.expensestracker.data.model.SavingsEntryKind
import com.example.expensestracker.data.model.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SavingsMathTest {
    private val today = LocalDate.of(2026, 11, 20)

    private fun entry(
        amount: Double,
        date: String,
        by: String = "me",
        kind: SavingsEntryKind = SavingsEntryKind.DEPOSIT
    ) = SavingsEntry(
        id = "$date-$amount-$by-$kind",
        goalId = "g",
        kind = kind,
        amount = amount,
        amountInBaseCurrency = amount,
        date = date,
        byUid = by
    )

    private val goal = SavingsGoal(id = "g", name = "Disneyland", targetAmount = 1000.0)

    @Test
    fun balanceIsDepositsMinusWithdrawals() {
        val entries = listOf(
            entry(300.0, "2026-09-05"),
            entry(200.0, "2026-10-05"),
            entry(150.0, "2026-10-20", kind = SavingsEntryKind.WITHDRAWAL)
        )
        assertEquals(350.0, SavingsMath.balance(entries), 1e-9)
    }

    @Test
    fun netByPersonKeepsEachMembersShareApart() {
        val entries = listOf(entry(300.0, "2026-09-05", "me"), entry(100.0, "2026-09-06", "her"), entry(50.0, "2026-10-01", "her", SavingsEntryKind.WITHDRAWAL))
        val net = SavingsMath.netByUid(entries)
        assertEquals(300.0, net["me"]!!, 1e-9)
        assertEquals(50.0, net["her"]!!, 1e-9)
    }

    @Test
    fun monthNetCountsOnlyMyMovementsInThatMonth() {
        val entries = listOf(
            entry(200.0, "2026-11-02", "me"),
            entry(80.0, "2026-11-10", "me", SavingsEntryKind.WITHDRAWAL),
            entry(500.0, "2026-11-03", "her"),
            entry(999.0, "2026-10-30", "me")
        )
        assertEquals(120.0, SavingsMath.netInMonth(entries, YearMonth.of(2026, 11), "me"), 1e-9)
    }

    @Test
    fun withdrawalsDoNotCountAsDepositsForThePlan() {
        val entries = listOf(entry(200.0, "2026-11-02"), entry(50.0, "2026-11-04", kind = SavingsEntryKind.WITHDRAWAL))
        assertEquals(200.0, SavingsMath.depositedInMonth(entries, YearMonth.of(2026, 11), "me"), 1e-9)
    }

    @Test
    fun projectionRoundsUpFromTheCurrentMonth() {
        assertEquals(YearMonth.of(2027, 2), SavingsMath.projectedFinish(600.0, 200.0, YearMonth.of(2026, 11)))
        assertEquals(YearMonth.of(2027, 2), SavingsMath.projectedFinish(450.0, 200.0, YearMonth.of(2026, 11)))
        assertNull(SavingsMath.projectedFinish(0.0, 200.0, YearMonth.of(2026, 11)))
        assertNull(SavingsMath.projectedFinish(600.0, 0.0, YearMonth.of(2026, 11)))
    }

    @Test
    fun requiredMonthlySpreadsWhatsLeftOverTheMonthsToTheDate() {
        // 20 Nov -> 15 Mar is 4 calendar months ahead.
        assertEquals(250.0, SavingsMath.requiredMonthly(1000.0, LocalDate.of(2027, 3, 15), today)!!, 1e-9)
        // Same month as today still needs everything, never a division by zero.
        assertEquals(1000.0, SavingsMath.requiredMonthly(1000.0, LocalDate.of(2026, 11, 30), today)!!, 1e-9)
        assertNull(SavingsMath.requiredMonthly(1000.0, LocalDate.of(2026, 10, 1), today))
        assertNull(SavingsMath.requiredMonthly(0.0, LocalDate.of(2027, 3, 15), today))
    }

    @Test
    fun ratePrefersThePlanOverHistory() {
        val planned = goal.copy(monthlyPlans = mapOf("me" to 150.0, "her" to 50.0))
        assertEquals(200.0, SavingsMath.monthlyRate(planned, listOf(entry(999.0, "2026-10-01")), today)!!, 1e-9)
    }

    @Test
    fun rateFallsBackToTheRecentAverageAndIsNullWithNothingToGoOn() {
        assertNull(SavingsMath.monthlyRate(goal, emptyList(), today))
        // 300 over Sep-Nov (3 calendar months of history) = 100/month.
        val entries = listOf(entry(100.0, "2026-09-10"), entry(100.0, "2026-10-10"), entry(100.0, "2026-11-10"))
        assertEquals(100.0, SavingsMath.monthlyRate(goal, entries, today)!!, 1e-9)
    }

    @Test
    fun checkInAsksForWhatsStillMissingThisMonth() {
        val planned = goal.copy(monthlyPlans = mapOf("me" to 200.0))
        assertEquals(200.0, SavingsMath.checkInDue(planned, emptyList(), "me", today)!!, 1e-9)
        assertEquals(120.0, SavingsMath.checkInDue(planned, listOf(entry(80.0, "2026-11-03")), "me", today)!!, 1e-9)
        assertNull(SavingsMath.checkInDue(planned, listOf(entry(200.0, "2026-11-03")), "me", today))
    }

    @Test
    fun checkInStaysQuietWhenAnsweredNoPlanOrDone() {
        val planned = goal.copy(monthlyPlans = mapOf("me" to 200.0))
        assertNull(SavingsMath.checkInDue(planned.copy(lastCheckIn = mapOf("me" to "2026-11")), emptyList(), "me", today))
        assertNotNull(SavingsMath.checkInDue(planned.copy(lastCheckIn = mapOf("me" to "2026-10")), emptyList(), "me", today))
        assertNull(SavingsMath.checkInDue(goal, emptyList(), "me", today))
        assertNull(SavingsMath.checkInDue(planned, emptyList(), "her", today))
        assertNull(SavingsMath.checkInDue(planned.copy(archived = true), emptyList(), "me", today))
        assertNull(SavingsMath.checkInDue(planned, listOf(entry(1000.0, "2026-09-01")), "me", today))
    }

    @Test
    fun completeMeansTheTargetIsReached() {
        assertFalse(SavingsMath.isComplete(goal, listOf(entry(999.0, "2026-10-01"))))
        assertTrue(SavingsMath.isComplete(goal, listOf(entry(1000.0, "2026-10-01"))))
        assertFalse(SavingsMath.isComplete(goal.copy(targetAmount = 0.0), listOf(entry(10.0, "2026-10-01"))))
    }
}
