package com.example.expensestracker.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.time.LocalDate

/**
 * A pot of money being put aside for something ("Disneyland", "new PC"). Lives either under
 * `users/{uid}` (personal) or `groups/{id}` (shared by both members). The goal itself holds no
 * balance - that's the sum of its [SavingsEntry] rows - so two phones adding to it offline can't
 * overwrite each other's total.
 */
data class SavingsGoal(
    @DocumentId val id: String = "",
    val name: String = "",
    val icon: String = "🐷",
    /** In the base currency, like every stored amount. */
    val targetAmount: Double = 0.0,
    /** ISO date, optional. */
    val targetDate: String? = null,
    /**
     * What each member means to put in per month (uid -> base-currency amount). Per person because
     * in a shared pot each one plans their own part ("150 me, 50 her"). A plan is only an intention:
     * it never creates a deposit by itself.
     */
    val monthlyPlans: Map<String, Double> = emptyMap(),
    /** uid -> the "yyyy-MM" of the month that person last answered the "did you put it in?" prompt for. */
    val lastCheckIn: Map<String, String> = emptyMap(),
    val createdAt: Timestamp? = null,
    val archived: Boolean = false
) {
    val localTargetDate: LocalDate? get() = targetDate?.let { LocalDate.parse(it) }
}

enum class SavingsEntryKind { DEPOSIT, WITHDRAWAL }

/** One movement into or out of a [SavingsGoal]. [amount] and [amountInBaseCurrency] are always positive; [kind] gives the direction. */
data class SavingsEntry(
    @DocumentId val id: String = "",
    val goalId: String = "",
    val kind: SavingsEntryKind = SavingsEntryKind.DEPOSIT,
    val amount: Double = 0.0,
    val currencyCode: String = "EUR",
    val amountInBaseCurrency: Double = 0.0,
    val date: String = "",
    val note: String? = null,
    /** Whose money moved - not necessarily who typed it in. */
    val byUid: String = "",
    /**
     * Who recorded it, when that isn't the same person (e.g. spending a shared pot writes one
     * withdrawal per member, all recorded by whoever pressed the button). Null on older entries,
     * which were always recorded by [byUid]. Lets the "what your partner did" notice tell their own
     * actions apart from changes made on their behalf.
     */
    val recordedByUid: String? = null,
    val createdAt: Timestamp? = null
) {
    /** Who actually did this - the recorder if known, else the owner of the money. */
    val doneByUid: String get() = recordedByUid ?: byUid

    val localDate: LocalDate get() = LocalDate.parse(date)

    /** + for a deposit, - for a withdrawal, in the base currency. */
    val signedBase: Double get() = if (kind == SavingsEntryKind.DEPOSIT) amountInBaseCurrency else -amountInBaseCurrency
}
