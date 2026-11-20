package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.data.model.RecurringExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SubscriptionCalculatorTest {
    private val today = LocalDate.of(2026, 11, 20)

    private fun template(
        amount: Double,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        day: Int = 28,
        note: String? = "Streaming",
        active: Boolean = true
    ) = RecurringExpense(
        id = "r$amount$frequency", amount = amount, note = note, categoryName = "Bollette", frequency = frequency,
        dayOfPeriod = day, startDate = "2026-01-01", active = active, paidByUid = "me"
    )

    private fun pass(amount: Double, start: String, end: String, by: String = "me") = Expense(
        id = "p$amount$start", amountInBaseCurrency = amount, amount = amount, note = "Abbonamento mezzi",
        categoryName = "Trasporti", date = start, periodStart = start, periodEnd = end, paidByUid = by
    )

    private fun compute(recurring: List<RecurringExpense> = emptyList(), expenses: List<Expense> = emptyList(), uid: String = "me") =
        SubscriptionCalculator.compute(recurring, expenses, today, uid) { 1.0 }

    @Test
    fun aMonthlyPlanCostsItsAmountPerMonthAndShowsItsNextCharge() {
        val item = compute(listOf(template(12.0))).recurring.single()
        assertEquals(12.0, item.monthlyCost, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 28), item.date)
        assertEquals("Streaming", item.title)
    }

    @Test
    fun aWeeklyPlanIsConvertedToAMonthlyEquivalent() {
        val item = compute(listOf(template(10.0, RecurrenceFrequency.WEEKLY, day = 1))).recurring.single()
        assertEquals(10.0 * 52 / 12, item.monthlyCost, 1e-9)
    }

    @Test
    fun pausedTemplatesAndOnesThatAreNotMineAreLeftOut() {
        assertTrue(compute(listOf(template(12.0, active = false))).recurring.isEmpty())
        assertTrue(compute(listOf(template(12.0)), uid = "her").recurring.isEmpty())
    }

    @Test
    fun aSharedPlanOnlyCountsMyShare() {
        val shared = template(20.0).copy(isShared = true, paidByUid = "her", payerShare = 0.6)
        assertEquals(8.0, compute(listOf(shared)).recurring.single().monthlyCost, 1e-9)
    }

    @Test
    fun aPrepaidPassIsSpreadOverItsDaysAsAMonthlyCost() {
        // 200 over 29 Oct - 12 Dec = 45 days, covering today.
        val item = compute(expenses = listOf(pass(200.0, "2026-10-29", "2026-12-12"))).prepaid.single()
        assertEquals(200.0 / 45 * 30.4375, item.monthlyCost, 1e-9)
        assertEquals(LocalDate.of(2026, 12, 12), item.date)
        assertEquals(200.0, item.chargeAmount, 1e-9)
    }

    @Test
    fun onlyPeriodsCoveringTodayCount() {
        assertTrue(compute(expenses = listOf(pass(200.0, "2026-08-01", "2026-09-30"))).prepaid.isEmpty())
        assertTrue(compute(expenses = listOf(pass(200.0, "2026-12-01", "2027-01-31"))).prepaid.isEmpty())
        assertTrue(compute(expenses = listOf(Expense(id = "x", amountInBaseCurrency = 5.0, date = "2026-11-01", paidByUid = "me"))).prepaid.isEmpty())
    }

    @Test
    fun totalsAddUpBothKindsAndAnnualIsTwelveTimesMonthly() {
        val overview = compute(listOf(template(12.0)), listOf(pass(30.4375, "2026-11-01", "2026-11-30")))
        // The pass is 30 days: 30.4375/30*30.4375, plus 12.
        assertEquals(12.0 + 30.4375 / 30 * 30.4375, overview.monthlyTotal, 1e-9)
        assertEquals(overview.monthlyTotal * 12, overview.yearlyTotal, 1e-9)
    }

    @Test
    fun soonestChargeComesFirst() {
        val later = template(5.0, day = 29, note = "B")
        val sooner = template(7.0, day = 22, note = "A")
        assertEquals(listOf("A", "B"), compute(listOf(later, sooner)).recurring.map { it.title })
    }
}
