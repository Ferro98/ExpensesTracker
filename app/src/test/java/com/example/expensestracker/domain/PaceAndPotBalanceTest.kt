package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaceAndPotBalanceTest {
    private fun expense(id: String, amount: Double) =
        Expense(id = id, amountInBaseCurrency = amount, amount = amount, date = "2026-11-05", paidByUid = "me")

    private val rent = expense("rent", 600.0)
    private val groceries = expense("groceries", 30.0)
    private val lunch = expense("lunch", 20.0)
    private val coffee = expense("coffee", 10.0)
    private val month = listOf(coffee, lunch, groceries, rent)

    @Test
    fun theExtrasFigureLeavesOutEssentialsAndOneOffsButTheMainFigureKeepsEssentials() {
        // Rent is the outlier (600 vs a median of 25). Groceries and rent are essential.
        val pace = PaceCalculator.compute(month, "me", 660.0, 10, 30) { it.id in setOf("groceries", "rent") }
        // Main rate: everything but the one-off rent = 60 over 10 days.
        assertEquals(6.0, pace.dailyAverage, 1e-9)
        assertEquals(660.0 + 6.0 * 20, pace.projectedTotal, 1e-9)
        // Extras: just coffee + lunch = 30 over 10 days.
        assertEquals(3.0, pace.extraDailyAverage!!, 1e-9)
    }

    @Test
    fun withoutEssentialsSetUpThereIsNoExtrasFigure() {
        assertNull(PaceCalculator.compute(month, "me", 660.0, 10, 30).extraDailyAverage)
    }

    @Test
    fun tripAndSubscriptionExpensesAreLeftOutOfTheRate() {
        val trip = expense("trip", 40.0).copy(tripId = "t")
        val pace = PaceCalculator.compute(listOf(coffee, lunch, groceries, trip), "me", 100.0, 10, 30)
        // The 40 spent on the trip isn't a daily habit: 60 / 10 days.
        assertEquals(6.0, pace.dailyAverage, 1e-9)
        assertEquals(1, pace.excludedCount)
    }

    @Test
    fun anExpensePaidFromASharedPotNeverChangesTheBalance() {
        val members = listOf("me", "her")
        val fronted = Expense(id = "a", amountInBaseCurrency = 100.0, amount = 100.0, isShared = true, paidByUid = "me", payerShare = 0.5)
        val fromPot = fronted.copy(id = "b", paidFromPot = true, payerShare = 0.75)

        val withBoth = BalanceCalculator.compute(listOf(fronted, fromPot), emptyList(), members)
        val onlyFronted = BalanceCalculator.compute(listOf(fronted), emptyList(), members)
        assertEquals(onlyFronted, withBoth)
        // Sanity: the fronted one does create a debt (she owes me half).
        assertEquals(50.0, onlyFronted.netAmount, 1e-9)
    }

    @Test
    fun aPotExpenseStillCountsEachPersonsOwnShareOfTheCost() {
        // 200 from a pot I filled 150 of and she filled 50 of: I carry 150 of the cost, she 50.
        val fromPot = Expense(
            id = "p", amountInBaseCurrency = 200.0, amount = 200.0, isShared = true, paidByUid = "me",
            payerShare = 0.75, paidFromPot = true
        )
        assertEquals(150.0, fromPot.shareFor("me"), 1e-9)
        assertEquals(50.0, fromPot.shareFor("her"), 1e-9)
    }
}
