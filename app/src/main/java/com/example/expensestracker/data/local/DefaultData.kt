package com.example.expensestracker.data.local

import com.example.expensestracker.data.local.entity.CategoryEntity
import com.example.expensestracker.data.local.entity.CurrencyRateEntity

/**
 * Seed data inserted the first time the database is created.
 * The app's base currency is fixed to EUR; all other rates are expressed
 * as "value of 1 unit of that currency in EUR".
 */
object DefaultData {
    const val BASE_CURRENCY = "EUR"

    val categories = listOf(
        CategoryEntity(name = "Food & Dining", icon = "🍔", colorHex = "#FF7043", sortOrder = 0),
        CategoryEntity(name = "Transport", icon = "🚌", colorHex = "#42A5F5", sortOrder = 1),
        CategoryEntity(name = "Accommodation", icon = "🏨", colorHex = "#AB47BC", sortOrder = 2),
        CategoryEntity(name = "Shopping", icon = "🛍️", colorHex = "#EC407A", sortOrder = 3),
        CategoryEntity(name = "Entertainment", icon = "🎉", colorHex = "#FFA726", sortOrder = 4),
        CategoryEntity(name = "Bills & Subscriptions", icon = "📱", colorHex = "#5C6BC0", sortOrder = 5),
        CategoryEntity(name = "Health", icon = "💊", colorHex = "#26A69A", sortOrder = 6),
        CategoryEntity(name = "Travel", icon = "✈️", colorHex = "#29B6F6", sortOrder = 7),
        CategoryEntity(name = "Other", icon = "📦", colorHex = "#78909C", sortOrder = 8)
    )

    val currencyRates = listOf(
        CurrencyRateEntity(code = "EUR", rateToBase = 1.0),
        CurrencyRateEntity(code = "DKK", rateToBase = 0.134)
    )
}
