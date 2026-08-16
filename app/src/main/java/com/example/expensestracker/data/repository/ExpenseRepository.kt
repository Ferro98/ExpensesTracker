package com.example.expensestracker.data.repository

import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.RecurringExpense
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Expenses + recurring templates for one scope - either `users/{uid}` (personal, private) or
 * `groups/{groupId}` (shared, both members). The caller decides which scope root to construct
 * this against; nothing in here assumes one or the other, so it can't be misused to read/write
 * the wrong collection type. Currency conversion is NOT done here (rates are always personal -
 * see [PersonalDataRepository]) - callers pass the already-converted [amountInBaseCurrency] in.
 */
class ExpenseRepository(private val scopeRef: DocumentReference) {
    private val expensesRef get() = scopeRef.collection("expenses")
    private val recurringRef get() = scopeRef.collection("recurringExpenses")

    // Expenses
    fun observeExpensesBetween(start: LocalDate, end: LocalDate): Flow<List<Expense>> =
        expensesRef
            .whereGreaterThanOrEqualTo("date", start.toString())
            .whereLessThanOrEqualTo("date", end.toString())
            .orderBy("date", Query.Direction.DESCENDING)
            .observeAsFlow()

    /** Every expense in this scope, unfiltered - needed for lifetime balance and budget attribution. */
    fun observeAllExpenses(): Flow<List<Expense>> = expensesRef.observeAsFlow()

    fun observeRecent(limit: Int): Flow<List<Expense>> =
        expensesRef.orderBy("date", Query.Direction.DESCENDING).limit(limit.toLong()).observeAsFlow()

    suspend fun addExpense(
        categoryId: String,
        categoryName: String,
        categoryIcon: String,
        categoryColorHex: String,
        amount: Double,
        currencyCode: String,
        amountInBaseCurrency: Double,
        date: LocalDate,
        note: String?,
        paidByUid: String,
        isShared: Boolean,
        payerShare: Double
    ): String {
        val ref = expensesRef.document()
        ref.set(
            Expense(
                categoryId = categoryId,
                categoryName = categoryName,
                categoryIcon = categoryIcon,
                categoryColorHex = categoryColorHex,
                amount = amount,
                currencyCode = currencyCode,
                amountInBaseCurrency = amountInBaseCurrency,
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
    suspend fun insertGeneratedExpense(recurring: RecurringExpense, date: LocalDate, amountInBaseCurrency: Double) {
        expensesRef.document("${recurring.id}_$date").set(
            Expense(
                categoryId = recurring.categoryId,
                categoryName = recurring.categoryName,
                categoryIcon = recurring.categoryIcon,
                categoryColorHex = recurring.categoryColorHex,
                amount = recurring.amount,
                currencyCode = recurring.currencyCode,
                amountInBaseCurrency = amountInBaseCurrency,
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
}
