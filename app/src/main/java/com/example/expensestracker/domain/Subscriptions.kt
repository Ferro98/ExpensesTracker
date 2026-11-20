package com.example.expensestracker.domain

import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.RecurrenceFrequency
import com.example.expensestracker.data.model.RecurringExpense
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SubscriptionKind { RECURRING, PREPAID }

/**
 * One thing that costs money on a schedule. [monthlyCost] is the viewer's own share spread over a
 * month, in the base currency, so a weekly gym fee, a monthly streaming plan and a 200 transport
 * pass covering 45 days all compare on the same footing. [date] is the next charge for a recurring
 * one, or the last day covered for a prepaid one.
 */
data class SubscriptionItem(
    val id: String,
    val kind: SubscriptionKind,
    val title: String,
    val icon: String,
    val colorHex: String,
    val monthlyCost: Double,
    val chargeAmount: Double,
    val frequency: RecurrenceFrequency?,
    val date: LocalDate,
    val isShared: Boolean
)

data class SubscriptionsOverview(
    val recurring: List<SubscriptionItem> = emptyList(),
    val prepaid: List<SubscriptionItem> = emptyList()
) {
    val monthlyTotal: Double get() = (recurring + prepaid).sumOf { it.monthlyCost }
    val yearlyTotal: Double get() = monthlyTotal * 12
    val isEmpty: Boolean get() = recurring.isEmpty() && prepaid.isEmpty()
}

object SubscriptionCalculator {
    private const val DAYS_PER_MONTH = 30.4375
    private const val WEEKS_PER_MONTH = 52.0 / 12.0

    /**
     * The viewer's active subscriptions: every active recurring template, plus every expense whose
     * prepaid period ([Expense.periodStart]..[Expense.periodEnd]) covers [today]. Anything where
     * the viewer's own share is nothing (a partner's personal template) is left out.
     */
    fun compute(
        recurring: List<RecurringExpense>,
        expenses: List<Expense>,
        today: LocalDate,
        uid: String,
        rateToBase: (String) -> Double
    ): SubscriptionsOverview {
        val recurringItems = recurring
            .filter { it.active }
            .mapNotNull { template ->
                val perCharge = UpcomingRecurring.shareFor(template, uid) * rateToBase(template.currencyCode)
                val monthly = when (template.frequency) {
                    RecurrenceFrequency.MONTHLY -> perCharge
                    RecurrenceFrequency.WEEKLY -> perCharge * WEEKS_PER_MONTH
                }
                if (monthly <= 0.0) return@mapNotNull null
                SubscriptionItem(
                    id = template.id,
                    kind = SubscriptionKind.RECURRING,
                    title = template.note?.takeIf { it.isNotBlank() } ?: template.categoryName,
                    icon = template.categoryIcon,
                    colorHex = template.categoryColorHex,
                    monthlyCost = monthly,
                    chargeAmount = template.amount * rateToBase(template.currencyCode),
                    frequency = template.frequency,
                    date = template.nextOccurrence(today),
                    isShared = template.isShared
                )
            }
            .sortedBy { it.date }

        val prepaidItems = expenses
            .mapNotNull { expense ->
                val start = expense.localPeriodStart ?: return@mapNotNull null
                val end = expense.localPeriodEnd ?: return@mapNotNull null
                if (end.isBefore(start) || today.isBefore(start) || today.isAfter(end)) return@mapNotNull null
                val share = expense.shareFor(uid)
                if (share <= 0.0) return@mapNotNull null
                val days = ChronoUnit.DAYS.between(start, end) + 1
                SubscriptionItem(
                    id = expense.id,
                    kind = SubscriptionKind.PREPAID,
                    title = expense.note?.takeIf { it.isNotBlank() } ?: expense.categoryName,
                    icon = expense.categoryIcon,
                    colorHex = expense.categoryColorHex,
                    monthlyCost = share / days * DAYS_PER_MONTH,
                    chargeAmount = expense.amountInBaseCurrency,
                    frequency = null,
                    date = end,
                    isShared = expense.isShared
                )
            }
            .sortedBy { it.date }

        return SubscriptionsOverview(recurringItems, prepaidItems)
    }
}
