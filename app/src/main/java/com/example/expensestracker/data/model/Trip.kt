package com.example.expensestracker.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.time.LocalDate

/**
 * A trip or project with its own dates and its own budget - "Disneyland, 12-19 Dec, 1500". Lives
 * under `users/{uid}` (personal) or `groups/{id}` (both members). Expenses opt in by carrying its id
 * ([Expense.tripId]), and only within the same scope: a personal trip collects personal expenses, a
 * group trip collects shared ones, since neither partner can see the other's personal spending.
 */
data class Trip(
    @DocumentId val id: String = "",
    val name: String = "",
    val icon: String = "✈️",
    /** ISO dates, both inclusive. */
    val startDate: String = "",
    val endDate: String = "",
    /** In the base currency; 0 means no budget, just a place to collect the spending. */
    val budget: Double = 0.0,
    /**
     * The savings pot this trip is being paid for with, if any - always one in the same scope (a
     * personal trip links a personal pot, a shared trip a shared one), so whoever can see the trip
     * can also see the pot behind it. An id matching no pot (deleted since) just means "not linked".
     */
    val goalId: String? = null,
    val createdAt: Timestamp? = null
) {
    val localStart: LocalDate get() = LocalDate.parse(startDate)
    val localEnd: LocalDate get() = LocalDate.parse(endDate)
}
