package com.example.expensestracker.data.repository

import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.RecurringExpense
import com.example.expensestracker.data.model.Settlement
import com.example.expensestracker.data.remote.CurrencyRateService
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** All reads/writes for one group's shared data (categories, expenses, recurring templates, rates, settlements). */
class ExpenseRepository(
    private val firestore: FirebaseFirestore,
    private val groupId: String,
    private val currencyRateService: CurrencyRateService
) {
    private val groupRef get() = firestore.collection("groups").document(groupId)
    private val categoriesRef get() = groupRef.collection("categories")
    private val expensesRef get() = groupRef.collection("expenses")
    private val recurringRef get() = groupRef.collection("recurringExpenses")
    private val currencyRatesRef get() = groupRef.collection("currencyRates")
    private val settlementsRef get() = groupRef.collection("settlements")

    // Categories
    fun observeCategories(): Flow<List<Category>> = categoriesRef.observeAsFlow<Category>()
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

    // Expenses
    fun observeExpensesBetween(start: LocalDate, end: LocalDate): Flow<List<Expense>> =
        expensesRef
            .whereGreaterThanOrEqualTo("date", start.toString())
            .whereLessThanOrEqualTo("date", end.toString())
            .orderBy("date", Query.Direction.DESCENDING)
            .observeAsFlow()

    /** Every expense ever, unfiltered — needed for lifetime balance and personal-budget attribution. */
    fun observeAllExpenses(): Flow<List<Expense>> = expensesRef.observeAsFlow()

    fun observeRecent(limit: Int): Flow<List<Expense>> =
        expensesRef.orderBy("date", Query.Direction.DESCENDING).limit(limit.toLong()).observeAsFlow()

    suspend fun addExpense(
        categoryId: String,
        amount: Double,
        currencyCode: String,
        date: LocalDate,
        note: String?,
        paidByUid: String,
        isShared: Boolean,
        payerShare: Double
    ): String {
        val amountInBase = convertToBase(amount, currencyCode)
        val ref = expensesRef.document()
        ref.set(
            Expense(
                categoryId = categoryId,
                amount = amount,
                currencyCode = currencyCode,
                amountInBaseCurrency = amountInBase,
                date = date.toString(),
                note = note,
                createdAt = Timestamp.now(),
                paidByUid = paidByUid,
                isShared = isShared,
                payerShare = payerShare
            )
        ).await()
        return ref.id
    }

    suspend fun deleteExpense(expenseId: String) {
        expensesRef.document(expenseId).delete().await()
    }

    // Recurring expenses
    fun observeRecurring(): Flow<List<RecurringExpense>> = recurringRef.observeAsFlow()
    suspend fun getActiveRecurring(): List<RecurringExpense> =
        recurringRef.whereEqualTo("active", true).get().await().toObjects(RecurringExpense::class.java)

    suspend fun addRecurring(recurring: RecurringExpense): String {
        val ref = recurringRef.document()
        ref.set(recurring).await()
        return ref.id
    }

    suspend fun updateRecurring(recurring: RecurringExpense) {
        recurringRef.document(recurring.id).set(recurring).await()
    }

    suspend fun deleteRecurring(recurringId: String) {
        recurringRef.document(recurringId).delete().await()
    }

    /**
     * Uses a deterministic document id ("<recurringId>_<date>") instead of an auto id so that
     * two devices independently generating the same overdue occurrence while offline converge
     * on the same document instead of creating a duplicate once both reconnect.
     */
    suspend fun insertGeneratedExpense(recurring: RecurringExpense, date: LocalDate) {
        val amountInBase = convertToBase(recurring.amount, recurring.currencyCode)
        expensesRef.document("${recurring.id}_$date").set(
            Expense(
                categoryId = recurring.categoryId,
                amount = recurring.amount,
                currencyCode = recurring.currencyCode,
                amountInBaseCurrency = amountInBase,
                date = date.toString(),
                note = recurring.note,
                recurringExpenseId = recurring.id,
                createdAt = Timestamp.now(),
                paidByUid = recurring.paidByUid,
                isShared = recurring.isShared,
                payerShare = recurring.payerShare
            )
        ).await()
    }

    // Settlements
    fun observeSettlements(): Flow<List<Settlement>> = settlementsRef.observeAsFlow()

    suspend fun addSettlement(fromUid: String, toUid: String, amount: Double, currencyCode: String, date: LocalDate, note: String?) {
        val amountInBase = convertToBase(amount, currencyCode)
        settlementsRef.document().set(
            Settlement(
                fromUid = fromUid,
                toUid = toUid,
                amount = amount,
                currencyCode = currencyCode,
                amountInBaseCurrency = amountInBase,
                date = date.toString(),
                note = note,
                createdAt = Timestamp.now()
            )
        ).await()
    }

    suspend fun deleteSettlement(settlementId: String) {
        settlementsRef.document(settlementId).delete().await()
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

    private suspend fun convertToBase(amount: Double, currencyCode: String): Double {
        val rate = currencyRatesRef.document(currencyCode.uppercase()).get().await()
            .toObject(CurrencyRate::class.java)?.rateToBase ?: 1.0
        return amount * rate
    }
}
