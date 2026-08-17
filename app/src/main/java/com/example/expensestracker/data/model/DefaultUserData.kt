package com.example.expensestracker.data.model

import android.content.Context
import com.example.expensestracker.R

/**
 * Seed data written once for a new user (first sign-in), regardless of whether they're
 * ever part of a group. The base currency is fixed to EUR; all other rates are expressed
 * as "value of 1 unit of that currency in EUR".
 */
object DefaultUserData {
    const val BASE_CURRENCY = "EUR"

    /**
     * Category names are resolved from string resources (device locale) rather than hardcoded,
     * so a fresh install seeds already-localized names. [englishNameRes] is kept alongside each
     * localized name so already-seeded categories (created before this, or on a device that was
     * in English at the time) can be recognized and migrated - see
     * [PersonalDataRepository.migrateDefaultCategoryNames].
     */
    fun categories(context: Context): List<Category> = listOf(
        Category(name = context.getString(R.string.default_category_food), icon = "🍔", colorHex = "#FF7043", sortOrder = 0),
        Category(name = context.getString(R.string.default_category_transport), icon = "🚌", colorHex = "#42A5F5", sortOrder = 1),
        Category(name = context.getString(R.string.default_category_accommodation), icon = "🏨", colorHex = "#AB47BC", sortOrder = 2),
        Category(name = context.getString(R.string.default_category_shopping), icon = "🛍️", colorHex = "#EC407A", sortOrder = 3),
        Category(name = context.getString(R.string.default_category_entertainment), icon = "🎉", colorHex = "#FFA726", sortOrder = 4),
        Category(name = context.getString(R.string.default_category_bills), icon = "📱", colorHex = "#5C6BC0", sortOrder = 5),
        Category(name = context.getString(R.string.default_category_health), icon = "💊", colorHex = "#26A69A", sortOrder = 6),
        Category(name = context.getString(R.string.default_category_travel), icon = "✈️", colorHex = "#29B6F6", sortOrder = 7),
        Category(name = context.getString(R.string.default_category_other), icon = "📦", colorHex = "#78909C", sortOrder = 8)
    )

    /**
     * Every literal name this app has ever seeded a default category with, in any supported
     * language, mapped to that category's string resource id - lets the migration recognize a
     * default category regardless of which locale was active when it was first seeded, and
     * rename it to match whatever locale is active now.
     */
    val knownDefaultNameVariants: Map<String, Int> = mapOf(
        "Food & Dining" to R.string.default_category_food,
        "Cibo e Ristoranti" to R.string.default_category_food,
        "Transport" to R.string.default_category_transport,
        "Trasporti" to R.string.default_category_transport,
        "Accommodation" to R.string.default_category_accommodation,
        "Alloggio" to R.string.default_category_accommodation,
        "Shopping" to R.string.default_category_shopping,
        "Entertainment" to R.string.default_category_entertainment,
        "Intrattenimento" to R.string.default_category_entertainment,
        "Bills & Subscriptions" to R.string.default_category_bills,
        "Bollette e Abbonamenti" to R.string.default_category_bills,
        "Health" to R.string.default_category_health,
        "Salute" to R.string.default_category_health,
        "Travel" to R.string.default_category_travel,
        "Viaggi" to R.string.default_category_travel,
        "Other" to R.string.default_category_other,
        "Altro" to R.string.default_category_other
    )

    val currencyRates = listOf(
        CurrencyRate(code = "EUR", rateToBase = 1.0),
        CurrencyRate(code = "DKK", rateToBase = 0.134)
    )
}
