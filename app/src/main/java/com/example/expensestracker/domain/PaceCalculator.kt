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
    val excludedTotal: Double
)

object PaceCalculator {
    /** An expense counts as a one-off if it's more than this many times the month's median expense. */
    private const val OUTLIER_MULTIPLIER = 3.0

    /** Below this many expenses, a median is too noisy to call anything an "outlier" against it - everything counts as regular. */
    private const val MIN_EXPENSES_FOR_OUTLIER_DETECTION = 3

    fun compute(monthExpenses: List<Expense>, myUid: String, totalSpent: Double, daysElapsed: Int, daysInMonth: Int): MonthPace {
        val shares = monthExpenses.map { it.shareFor(myUid) }.filter { it > 0 }.sorted()
        val median = when {
            shares.isEmpty() -> 0.0
            shares.size % 2 == 1 -> shares[shares.size / 2]
            else -> (shares[shares.size / 2 - 1] + shares[shares.size / 2]) / 2
        }

        val canDetectOutliers = shares.size >= MIN_EXPENSES_FOR_OUTLIER_DETECTION && median > 0
        val threshold = median * OUTLIER_MULTIPLIER
        val lumpExpenses = if (canDetectOutliers) monthExpenses.filter { it.shareFor(myUid) > threshold } else emptyList()
        val lumpTotal = lumpExpenses.sumOf { it.shareFor(myUid) }
        val regularSpent = totalSpent - lumpTotal

        val dailyAverage = if (daysElapsed > 0) regularSpent / daysElapsed else 0.0
        val daysRemaining = (daysInMonth - daysElapsed).coerceAtLeast(0)
        // Already-incurred spend (lump sums included) plus only the *regular* rate projected
        // forward - never re-projects a rent payment as if it recurred every remaining day.
        val projectedTotal = totalSpent + dailyAverage * daysRemaining

        return MonthPace(
            dailyAverage = dailyAverage,
            projectedTotal = projectedTotal,
            excludedCount = lumpExpenses.size,
            excludedTotal = lumpTotal
        )
    }
}
