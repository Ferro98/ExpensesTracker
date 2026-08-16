package com.example.expensestracker.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.time.LocalDate

/** A real-world reimbursement between the two members ("X paid Y €N"), separate from expenses. */
data class Settlement(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val amount: Double = 0.0,
    val currencyCode: String = "EUR",
    val amountInBaseCurrency: Double = 0.0,
    val date: String = "",
    val note: String? = null,
    val createdAt: Timestamp? = null
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
}
