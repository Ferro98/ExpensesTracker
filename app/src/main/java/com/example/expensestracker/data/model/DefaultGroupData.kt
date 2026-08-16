package com.example.expensestracker.data.model

/**
 * Seed data written once into a new group when it's created.
 * The group's base currency is fixed to EUR; all other rates are expressed
 * as "value of 1 unit of that currency in EUR".
 */
object DefaultGroupData {
    const val BASE_CURRENCY = "EUR"

    val categories = listOf(
        Category(name = "Food & Dining", icon = "🍔", colorHex = "#FF7043", sortOrder = 0),
        Category(name = "Transport", icon = "🚌", colorHex = "#42A5F5", sortOrder = 1),
        Category(name = "Accommodation", icon = "🏨", colorHex = "#AB47BC", sortOrder = 2),
        Category(name = "Shopping", icon = "🛍️", colorHex = "#EC407A", sortOrder = 3),
        Category(name = "Entertainment", icon = "🎉", colorHex = "#FFA726", sortOrder = 4),
        Category(name = "Bills & Subscriptions", icon = "📱", colorHex = "#5C6BC0", sortOrder = 5),
        Category(name = "Health", icon = "💊", colorHex = "#26A69A", sortOrder = 6),
        Category(name = "Travel", icon = "✈️", colorHex = "#29B6F6", sortOrder = 7),
        Category(name = "Other", icon = "📦", colorHex = "#78909C", sortOrder = 8)
    )

    val currencyRates = listOf(
        CurrencyRate(code = "EUR", rateToBase = 1.0),
        CurrencyRate(code = "DKK", rateToBase = 0.134)
    )
}
