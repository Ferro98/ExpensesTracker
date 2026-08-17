package com.example.expensestracker.data.repository

import android.content.Context
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.data.remote.CurrencyRateService
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Always-private, per-user data: categories and currency rates live under `users/{uid}` and
 * are never shared with anyone, whether or not this user is in a group. Also owns currency
 * conversion, since rates are personal even when the expense being converted is group-scoped.
 */
class PersonalDataRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val uid: String,
    private val currencyRateService: CurrencyRateService
) {
    private val userRef get() = firestore.collection("users").document(uid)
    private val categoriesRef get() = userRef.collection("categories")
    private val currencyRatesRef get() = userRef.collection("currencyRates")

    // Categories
    fun observeCategories(): Flow<List<Category>> = categoriesRef.observeAsFlow()
    suspend fun getCategories(): List<Category> = categoriesRef.get().await().toObjects(Category::class.java)

    suspend fun addCategory(category: Category): String {
        val ref = categoriesRef.document()
        ref.set(category).await()
        return ref.id
    }

    suspend fun updateCategory(category: Category) {
        categoriesRef.document(category.id).set(category).await()
    }

    suspend fun deleteCategory(categoryId: String) {
        categoriesRef.document(categoryId).delete().await()
    }

    // Currency rates
    fun observeCurrencyRates(): Flow<List<CurrencyRate>> = currencyRatesRef.observeAsFlow()
    suspend fun getCurrencyRates(): List<CurrencyRate> = currencyRatesRef.get().await().toObjects(CurrencyRate::class.java)

    suspend fun setCurrencyRate(code: String, rateToBase: Double) {
        currencyRatesRef.document(code.uppercase()).set(
            CurrencyRate(code = code.uppercase(), rateToBase = rateToBase, updatedAt = Timestamp.now())
        ).await()
    }

    suspend fun deleteCurrencyRate(code: String) {
        currencyRatesRef.document(code).delete().await()
    }

    suspend fun refreshRatesFromNetwork(): Result<Int> {
        val codes = getCurrencyRates().map { it.code }
        if (codes.isEmpty()) return Result.success(0)
        return currencyRateService.fetchRatesInEur(codes).map { fetched ->
            val batch = firestore.batch()
            var count = 0
            codes.forEach { code ->
                fetched[code]?.let { rate ->
                    batch.set(currencyRatesRef.document(code), CurrencyRate(code = code, rateToBase = rate, updatedAt = Timestamp.now()))
                    count++
                }
            }
            batch.commit().await()
            count
        }
    }

    suspend fun convertToBase(amount: Double, currencyCode: String): Double {
        val rate = currencyRatesRef.document(currencyCode.uppercase()).get().await()
            .toObject(CurrencyRate::class.java)?.rateToBase ?: 1.0
        return amount * rate
    }

    /** Runs once per uid on first-ever sign-in; seeds already-localized category names for a brand-new user. */
    suspend fun seedDefaultsIfNeeded() {
        val existing = getCategories()
        if (existing.isNotEmpty()) {
            migrateDefaultCategoryNames(existing)
            return
        }
        val batch = firestore.batch()
        DefaultUserData.categories(context).forEach { batch.set(categoriesRef.document(), it) }
        DefaultUserData.currencyRates.forEach { batch.set(currencyRatesRef.document(it.code), it) }
        batch.commit().await()
    }

    /**
     * Category names are plain stored text, not string resources - they don't follow the device
     * locale just by translating the app. This renames any category that still holds one of the
     * literal default names from an earlier seeding (in any language this app has shipped) to
     * match the current locale, in place - same document id, so existing expenses/budgets
     * referencing it by categoryId are unaffected. Categories the user renamed themselves (name
     * no longer matches any known default) are left untouched.
     */
    private suspend fun migrateDefaultCategoryNames(existing: List<Category>) {
        val batch = firestore.batch()
        var changed = false
        existing.forEach { category ->
            val labelRes = DefaultUserData.knownDefaultNameVariants[category.name] ?: return@forEach
            val localizedName = context.getString(labelRes)
            if (category.name != localizedName) {
                batch.update(categoriesRef.document(category.id), "name", localizedName)
                changed = true
            }
        }
        if (changed) batch.commit().await()
    }
}
