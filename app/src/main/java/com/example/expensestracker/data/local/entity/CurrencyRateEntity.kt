package com.example.expensestracker.data.local.entity

import androidx.room.Entity

/**
 * rateToBase = value of 1 unit of [code] expressed in the app's base currency.
 * The base currency itself is always stored with rateToBase = 1.0.
 */
@Entity(tableName = "currency_rates", primaryKeys = ["code"])
data class CurrencyRateEntity(
    val code: String,
    val rateToBase: Double,
    val updatedAt: Long = System.currentTimeMillis()
)
