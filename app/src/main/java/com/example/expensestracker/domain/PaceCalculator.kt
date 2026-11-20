package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Expense

/**
 * [dailyAverage]/[projectedTotal] are computed with one-off large expenses (rent, a booked trip, a
 * big one-time purchase) pulled out of the *rate* - they already happened and are real spend, so
 * they're still folded into [projectedTotal] as a flat amount, but averaging them across every day
 * of the month would wildly overstate what a normal day costs. [excludedCount]/[excludedTotal]
 * describe what got pulled out, so the UI can say so instead of leaving it a silent black box.
 */
data class MonthPace(
    val dailyAverage: Double,
    val projectedTotal: Double,
    val excludedCount: Int,
    val excludedTotal: Double,
    /** The same daily average counting only non-essential spending - the part you can actually steer. Null when essentials aren't set up. */
    val extraDailyAverage: Double? = null
)

object PaceCalculator {
    /** An expense counts as a one-off if it's more than this many times the month's median expense. */
    private const val OUTLIER_MULTIPLIER = 3.0

    /** Below this many expenses, a median is too noisy to call anything an "outlier" against it - everything counts as regular. */
    private const val MIN_EXPENSES_FOR_OUTLIER_DETECTION = 3

    /**
     * [isEssential], when given, adds [MonthPace.extraDailyAverage]. It's a second figure rather than a
     * change to the main one: essential spending (groceries, transport) keeps happening for the rest
     * of the month, so leaving it out of the projection would make that too rosy - it only makes
     * sense as "how much am I spending per day on things I could skip".
     */
    fun compute(
        monthExpenses: List<Expense>,
        myUid: String,
        totalSpent: Double,
        daysElapsed: Int,
        daysInMonth: Int,
        isEssential: ((Expense) -> Boolean)? = null
    ): MonthPace {
        val shares = monthExpenses.map { it.shareFor(myUid) }.filter { it > 0 }.sorted()
        val median = when {
            shares.isEmpty() -> 0.0
            shares.size % 2 == 1 -> shares[shares.size / 2]
            else -> (shares[shares.size / 2 - 1] + shares[shares.size / 2]) / 2
        }

        val canDetectOutliers = shares.size >= MIN_EXPENSES_FOR_OUTLIER_DETECTION && median > 0
        val threshold = median * OUTLIER_MULTIPLIER
        // A subscription's share of the month is a known fixed cost, not a daily habit, whatever its
        // size - projecting 14 of a prepaid pass forward as a per-day rate would be plainly wrong.
        // Spending on a trip is likewise out of the ordinary: a week away isn't how a normal day goes.
        val lumpExpenses = monthExpenses.filter { expense ->
            val share = expense.shareFor(myUid)
            share > 0 && (expense.hasPeriod || expense.tripId != null || (canDetectOutliers && share > threshold))
        }
        val lumpTotal = lumpExpenses.sumOf { it.shareFor(myUid) }
        val regularSpent = totalSpent - lumpTotal

        val dailyAverage = if (daysElapsed > 0) regularSpent / daysElapsed else 0.0
        val daysRemaining = (daysInMonth - daysElapsed).coerceAtLeast(0)
        // Already-incurred spend (lump sums included) plus only the *regular* rate projected
        // forward - never re-projects a rent payment as if it recurred every remaining day.
        val projectedTotal = totalSpent + dailyAverage * daysRemaining

        val extraDailyAverage = if (isEssential != null && daysElapsed > 0) {
            val lumpIds = lumpExpenses.map { it.id }.toSet()
            monthExpenses.filter { it.id !in lumpIds && !isEssential(it) }.sumOf { it.shareFor(myUid) } / daysElapsed
        } else null

        return MonthPace(
            dailyAverage = dailyAverage,
            projectedTotal = projectedTotal,
            excludedCount = lumpExpenses.size,
            excludedTotal = lumpTotal,
            extraDailyAverage = extraDailyAverage
        )
    }
}
