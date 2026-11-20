package com.example.expensestracker.domain

import com.example.expensestracker.data.model.SavingsEntry
import com.example.expensestracker.data.model.SavingsEntryKind
import com.example.expensestracker.data.model.SavingsGoal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

object SavingsMath {
    /** What's in the pot: every deposit minus every withdrawal. */
    fun balance(entries: List<SavingsEntry>): Double = entries.sumOf { it.signedBase }

    /** Net contribution per person (deposits minus what they took out) - can go negative if someone took out more than they put in. */
    fun netByUid(entries: List<SavingsEntry>): Map<String, Double> =
        entries.groupBy { it.byUid }.mapValues { (_, list) -> list.sumOf { it.signedBase } }

    fun isComplete(goal: SavingsGoal, entries: List<SavingsEntry>): Boolean =
        goal.targetAmount > 0 && balance(entries) >= goal.targetAmount

    private fun inMonth(entry: SavingsEntry, month: YearMonth) = YearMonth.from(entry.localDate) == month

    /**
     * Net money [uid] moved into savings during [month] (deposits minus withdrawals, by entry
     * date): the number the monthly budget view adds to "spent" to show where the whole budget went.
     * A withdrawal that pays for an unexpected expense nets that expense back out - the spend still
     * counts, but the money didn't come out of the month's budget.
     */
    fun netInMonth(entries: List<SavingsEntry>, month: YearMonth, uid: String): Double =
        entries.filter { it.byUid == uid && inMonth(it, month) }.sumOf { it.signedBase }

    fun depositedInMonth(entries: List<SavingsEntry>, month: YearMonth, uid: String): Double =
        entries.filter { it.byUid == uid && it.kind == SavingsEntryKind.DEPOSIT && inMonth(it, month) }.sumOf { it.amountInBaseCurrency }

    /**
     * The month a goal would be reached at [monthlyRate] per month, counting from the current month
     * (so 600 still to go at 200/month is three months out). Null when there's nothing left to save
     * or no rate to project with. Deliberately a rough, round-up figure.
     */
    fun projectedFinish(remaining: Double, monthlyRate: Double, from: YearMonth): YearMonth? {
        if (remaining <= 0 || monthlyRate <= 0) return null
        return from.plusMonths(ceil(remaining / monthlyRate).toLong())
    }

    /** What would need to go in each month to reach [targetDate] - null when nothing's left, or when that date is past. */
    fun requiredMonthly(remaining: Double, targetDate: LocalDate, today: LocalDate): Double? {
        if (remaining <= 0 || targetDate.isBefore(today)) return null
        val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(targetDate)).coerceAtLeast(1)
        return remaining / months
    }

    /**
     * The pace to project with: the sum of everyone's plan if anyone set one (what they intend), else
     * the recent average of what actually went in. Null when there's neither.
     */
    fun monthlyRate(goal: SavingsGoal, entries: List<SavingsEntry>, today: LocalDate): Double? {
        val planned = goal.monthlyPlans.values.sum()
        if (planned > 0) return planned
        val current = YearMonth.from(today)
        val windowStart = current.minusMonths(3)
        val inWindow = entries.filter { YearMonth.from(it.localDate) >= windowStart }
        if (inWindow.isEmpty()) return null
        val firstMonth = inWindow.minOf { YearMonth.from(it.localDate) }
        val span = (ChronoUnit.MONTHS.between(firstMonth, current) + 1).coerceIn(1, 4)
        val average = inWindow.sumOf { it.signedBase } / span
        return average.takeIf { it > 0 }
    }

    /**
     * The amount still to put in this month if [uid] planned some and hasn't answered the prompt yet,
     * else null (no plan, already answered, already put the planned amount in, or the goal is
     * done/archived). The prompt asks about the unfulfilled part, so a half-made deposit isn't asked
     * about in full.
     */
    fun checkInDue(goal: SavingsGoal, entries: List<SavingsEntry>, uid: String, today: LocalDate): Double? {
        if (goal.archived || isComplete(goal, entries)) return null
        val plan = goal.monthlyPlans[uid]?.takeIf { it > 0 } ?: return null
        val month = YearMonth.from(today)
        if (goal.lastCheckIn[uid] == month.toString()) return null
        val remaining = plan - depositedInMonth(entries, month, uid)
        return remaining.takeIf { it > 0.005 }
    }
}
