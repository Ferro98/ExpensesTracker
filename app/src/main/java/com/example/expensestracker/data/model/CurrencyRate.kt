package com.example.expensestracker.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * rateToBase = value of 1 unit of [code] expressed in the group's base currency (EUR).
 * The base currency itself is always stored with rateToBase = 1.0.
 */
data class CurrencyRate(
    @DocumentId val code: String = "",
    val rateToBase: Double = 1.0,
    val updatedAt: Timestamp? = null
)
