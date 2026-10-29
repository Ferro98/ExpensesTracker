package com.example.expensestracker.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.time.LocalDate

data class Expense(
    @DocumentId val id: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val categoryIcon: String = "",
    val categoryColorHex: String = "",
    val amount: Double = 0.0,
    val currencyCode: String = "EUR",
    val amountInBaseCurrency: Double = 0.0,
    val date: String = "",
    val note: String? = null,
    val recurringExpenseId: String? = null,
    val createdAt: Timestamp? = null,
    val paidByUid: String = "",
    // Without a pinned name, Firestore's reflection strips the "is" prefix when writing
    // (serializes as field "shared") but looks for the literal property name "isShared" when
    // deserializing via the Kotlin constructor - so every write silently round-tripped back as
    // false. Pinning the name makes both directions agree.
    @get:PropertyName("isShared") @set:PropertyName("isShared")
    var isShared: Boolean = false,
    val payerShare: Double = 0.5,
    /**
     * Set together (inclusive ISO dates) when this is a prepaid subscription/pass: the cost is
     * spread over these days instead of landing entirely on [date]. Null for an ordinary expense.
     * [date] stays the day it was actually paid - that is what the group balance goes by.
     */
    val periodStart: String? = null,
    val periodEnd: String? = null,
    /**
     * Never stored. The slice (0..1) of a [periodStart]..[periodEnd] expense that falls inside the
     * month being viewed, set by [com.example.expensestracker.domain.attributedTo]. Deliberately
     * a multiplier rather than a rewritten [amount]: edit, duplicate and undo-delete all read the
     * expense they were handed, and must keep seeing the real amount that was paid.
     */
    @get:Exclude val periodFactor: Double = 1.0
) {
    val localDate: LocalDate get() = LocalDate.parse(date)

    val localPeriodStart: LocalDate? get() = periodStart?.let { LocalDate.parse(it) }
    val localPeriodEnd: LocalDate? get() = periodEnd?.let { LocalDate.parse(it) }
    val hasPeriod: Boolean get() = periodStart != null && periodEnd != null

    /**
     * This expense's contribution to [uid]'s own effective spend, for personal-budget purposes.
     * A personal expense only ever counts against its owner; a shared expense counts against
     * both members, each for their own split share, regardless of who physically paid. Scaled by
     * [periodFactor], so a subscription only counts the part belonging to the month being summed.
     */
    fun shareFor(uid: String): Double = periodFactor * when {
        !isShared -> if (paidByUid == uid) amountInBaseCurrency else 0.0
        paidByUid == uid -> amountInBaseCurrency * payerShare
        else -> amountInBaseCurrency * (1 - payerShare)
    }
}
