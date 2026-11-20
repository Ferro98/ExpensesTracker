package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.Trip
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class TripPhase { UPCOMING, ONGOING, ENDED }

/** One category's slice of a trip's spending, grouped onto the viewer's own categories. */
data class TripCategorySpend(
    val categoryId: String?,
    val name: String,
    val icon: String,
    val colorHex: String,
    val amount: Double
)

data class TripStats(
    val phase: TripPhase,
    /** What the trip cost in total: everyone's share for a shared trip, the viewer's own for a personal one. */
    val spent: Double,
    /** The viewer's own part of [spent] - equal to it for a personal trip. */
    val myShare: Double,
    val budget: Double,
    /** Budget minus spent, or null when the trip has no budget. Negative once it's over. */
    val remaining: Double?,
    val totalDays: Int,
    val daysElapsed: Int,
    /** Days still ahead counting today while the trip is on; all of them before it starts; 0 after. */
    val daysLeft: Int,
    /** Spent per day so far; null before the first day. */
    val averagePerDay: Double?,
    /** What can still be spent per day to stay in budget; null with no budget, off-trip, or already over. */
    val remainingPerDay: Double?,
    /** The budget spread evenly over the whole trip; null with no budget. */
    val dailyBudget: Double?,
    val byCategory: List<TripCategorySpend>
) {
    val overBudget: Boolean get() = remaining != null && remaining < 0
}

object TripCalculator {
    fun phase(trip: Trip, today: LocalDate): TripPhase = when {
        today.isBefore(trip.localStart) -> TripPhase.UPCOMING
        today.isAfter(trip.localEnd) -> TripPhase.ENDED
        else -> TripPhase.ONGOING
    }

    /**
     * [expenses] are the ones carrying this trip's id, from the trip's own scope. [isGroup] decides
     * what "spent" means: a shared trip's budget is the pair's, so it adds up the full amounts; a
     * personal trip's is the viewer's, so it adds up their share. Subscription periods are ignored
     * here - an expense is part of a trip in full, on the day it was paid.
     */
    fun compute(
        trip: Trip,
        expenses: List<Expense>,
        isGroup: Boolean,
        uid: String,
        today: LocalDate,
        categories: List<Category>,
        aliases: Map<String, String>
    ): TripStats {
        val phase = phase(trip, today)
        val totalDays = (ChronoUnit.DAYS.between(trip.localStart, trip.localEnd) + 1).toInt().coerceAtLeast(1)
        val daysElapsed = when (phase) {
            TripPhase.UPCOMING -> 0
            TripPhase.ONGOING -> (ChronoUnit.DAYS.between(trip.localStart, today) + 1).toInt()
            TripPhase.ENDED -> totalDays
        }
        val daysLeft = when (phase) {
            TripPhase.UPCOMING -> totalDays
            TripPhase.ONGOING -> (ChronoUnit.DAYS.between(today, trip.localEnd) + 1).toInt()
            TripPhase.ENDED -> 0
        }

        fun amountOf(expense: Expense) = if (isGroup) expense.amountInBaseCurrency else expense.shareFor(uid)
        val spent = expenses.sumOf(::amountOf)
        val myShare = expenses.sumOf { it.shareFor(uid) }
        val hasBudget = trip.budget > 0
        val remaining = if (hasBudget) trip.budget - spent else null

        val byCategory = expenses
            .groupBy { CategoryResolver.resolve(it, categories, aliases)?.id }
            .map { (id, list) ->
                val resolved = categories.firstOrNull { it.id == id }
                val sample = list.first()
                TripCategorySpend(
                    categoryId = id,
                    name = resolved?.name ?: sample.categoryName,
                    icon = resolved?.icon ?: sample.categoryIcon,
                    colorHex = resolved?.colorHex ?: sample.categoryColorHex,
                    amount = list.sumOf(::amountOf)
                )
            }
            .sortedByDescending { it.amount }

        return TripStats(
            phase = phase,
            spent = spent,
            myShare = myShare,
            budget = trip.budget,
            remaining = remaining,
            totalDays = totalDays,
            daysElapsed = daysElapsed,
            daysLeft = daysLeft,
            averagePerDay = if (daysElapsed > 0) spent / daysElapsed else null,
            remainingPerDay = if (hasBudget && phase == TripPhase.ONGOING && daysLeft > 0 && remaining != null && remaining > 0) remaining / daysLeft else null,
            dailyBudget = if (hasBudget) trip.budget / totalDays else null,
            byCategory = byCategory
        )
    }
}
