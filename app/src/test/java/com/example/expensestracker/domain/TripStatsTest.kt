package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripStatsTest {
    private val trip = Trip(id = "t", name = "Disneyland", startDate = "2026-12-12", endDate = "2026-12-19", budget = 1600.0)
    private val categories = listOf(Category(id = "food", name = "Cibo e Ristoranti"), Category(id = "other", name = "Altro"))

    private fun expense(amount: Double, category: String = "food", name: String = "Cibo e Ristoranti", shared: Boolean = false, by: String = "me") =
        Expense(
            id = "$amount$category", categoryId = category, categoryName = name, amountInBaseCurrency = amount, amount = amount,
            date = "2026-12-13", isShared = shared, paidByUid = by, tripId = "t"
        )

    private fun stats(expenses: List<Expense>, today: String, group: Boolean = false, uid: String = "me", t: Trip = trip) =
        TripCalculator.compute(t, expenses, group, uid, LocalDate.parse(today), categories, emptyMap())

    @Test
    fun phaseFollowsTheDates() {
        assertEquals(TripPhase.UPCOMING, TripCalculator.phase(trip, LocalDate.of(2026, 12, 11)))
        assertEquals(TripPhase.ONGOING, TripCalculator.phase(trip, LocalDate.of(2026, 12, 12)))
        assertEquals(TripPhase.ONGOING, TripCalculator.phase(trip, LocalDate.of(2026, 12, 19)))
        assertEquals(TripPhase.ENDED, TripCalculator.phase(trip, LocalDate.of(2026, 12, 20)))
    }

    @Test
    fun midTripShowsSpentRemainingAndWhatCanStillBeSpentPerDay() {
        // 8 days, today is the 3rd; 600 spent of 1600.
        val s = stats(listOf(expense(400.0), expense(200.0)), "2026-12-14")
        assertEquals(8, s.totalDays)
        assertEquals(3, s.daysElapsed)
        assertEquals(6, s.daysLeft)
        assertEquals(600.0, s.spent, 1e-9)
        assertEquals(1000.0, s.remaining!!, 1e-9)
        assertEquals(200.0, s.dailyBudget!!, 1e-9)
        assertEquals(200.0, s.averagePerDay!!, 1e-9)
        assertEquals(1000.0 / 6, s.remainingPerDay!!, 1e-9)
        assertFalse(s.overBudget)
    }

    @Test
    fun goingOverBudgetIsNegativeRemainingAndNoDailyAllowance() {
        val s = stats(listOf(expense(1700.0)), "2026-12-14")
        assertEquals(-100.0, s.remaining!!, 1e-9)
        assertTrue(s.overBudget)
        assertNull(s.remainingPerDay)
    }

    @Test
    fun beforeTheTripThereIsNoAveragePerDay() {
        val s = stats(emptyList(), "2026-12-01")
        assertEquals(TripPhase.UPCOMING, s.phase)
        assertEquals(0, s.daysElapsed)
        assertNull(s.averagePerDay)
        assertEquals(8, s.daysLeft)
    }

    @Test
    fun aTripWithNoBudgetOnlyCollectsSpending() {
        val s = stats(listOf(expense(50.0)), "2026-12-14", t = trip.copy(budget = 0.0))
        assertNull(s.remaining)
        assertNull(s.dailyBudget)
        assertNull(s.remainingPerDay)
        assertEquals(50.0, s.spent, 1e-9)
    }

    @Test
    fun aSharedTripAddsUpFullAmountsButKeepsMyShareSeparate() {
        val shared = listOf(expense(100.0, shared = true, by = "her"), expense(60.0, shared = true, by = "me"))
        val s = stats(shared, "2026-12-14", group = true)
        assertEquals(160.0, s.spent, 1e-9)
        // 50% of each.
        assertEquals(80.0, s.myShare, 1e-9)
    }

    @Test
    fun spendingIsGroupedOntoMyCategoriesAndBiggestFirst() {
        val s = stats(
            listOf(
                expense(30.0),
                expense(70.0, category = "theirs", name = "Food & Dining"),
                expense(10.0, category = "zzz", name = "Boh")
            ),
            "2026-12-14"
        )
        // Their "Food & Dining" resolves by name onto my food category; the unknown one falls to "Altro".
        assertEquals(listOf("food", "other"), s.byCategory.map { it.categoryId })
        assertEquals(100.0, s.byCategory.first().amount, 1e-9)
        assertEquals(10.0, s.byCategory.last().amount, 1e-9)
    }
}
