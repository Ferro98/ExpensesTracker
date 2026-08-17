package com.example.expensestracker.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
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
    val payerShare: Double = 0.5
) {
    val localDate: LocalDate get() = LocalDate.parse(date)

    /**
     * This expense's contribution to [uid]'s own effective spend, for personal-budget purposes.
     * A personal expense only ever counts against its owner; a shared expense counts against
     * both members, each for their own split share, regardless of who physically paid.
     */
    fun shareFor(uid: String): Double = when {
        !isShared -> if (paidByUid == uid) amountInBaseCurrency else 0.0
        paidByUid == uid -> amountInBaseCurrency * payerShare
        else -> amountInBaseCurrency * (1 - payerShare)
    }
}
