package com.example.expensestracker.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.time.LocalDate

data class Expense(
    @DocumentId val id: String = "",
    val categoryId: String = "",
    val amount: Double = 0.0,
    val currencyCode: String = "EUR",
    val amountInBaseCurrency: Double = 0.0,
    val date: String = "",
    val note: String? = null,
    val recurringExpenseId: String? = null,
    val createdAt: Timestamp? = null,
    val paidByUid: String = "",
    val isShared: Boolean = false,
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
