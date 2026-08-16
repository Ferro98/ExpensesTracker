package com.example.expensestracker.data.repository

import com.example.expensestracker.data.local.dao.CategoryDao
import com.example.expensestracker.data.local.dao.CategorySpending
import com.example.expensestracker.data.local.dao.CurrencyRateDao
import com.example.expensestracker.data.local.dao.ExpenseDao
import com.example.expensestracker.data.local.dao.ExpenseWithCategory
import com.example.expensestracker.data.local.dao.RecurringExpenseDao
import com.example.expensestracker.data.local.entity.CategoryEntity
import com.example.expensestracker.data.local.entity.CurrencyRateEntity
import com.example.expensestracker.data.local.entity.ExpenseEntity
import com.example.expensestracker.data.local.entity.RecurringExpenseEntity
import com.example.expensestracker.data.remote.CurrencyRateService
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class ExpenseRepository(
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val recurringExpenseDao: RecurringExpenseDao,
    private val currencyRateDao: CurrencyRateDao,
    private val currencyRateService: CurrencyRateService
) {
    // Categories
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()
    suspend fun getCategories(): List<CategoryEntity> = categoryDao.getAll()
    suspend fun addCategory(category: CategoryEntity): Long = categoryDao.insert(category)
    suspend fun updateCategory(category: CategoryEntity) = categoryDao.update(category)
    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    // Expenses
    fun observeExpensesBetween(start: LocalDate, end: LocalDate): Flow<List<ExpenseWithCategory>> =
        expenseDao.observeExpensesBetween(start, end)

    fun observeRecent(limit: Int = 10): Flow<List<ExpenseWithCategory>> = expenseDao.observeRecent(limit)

    fun observeTotalBetween(start: LocalDate, end: LocalDate): Flow<Double> =
        expenseDao.observeTotalBetween(start, end)

    fun observeSpendingByCategory(start: LocalDate, end: LocalDate): Flow<List<CategorySpending>> =
        expenseDao.observeSpendingByCategory(start, end)

    suspend fun addExpense(
        categoryId: Long,
        amount: Double,
        currencyCode: String,
        date: LocalDate,
        note: String?,
        recurringExpenseId: Long? = null
    ): Long {
        val amountInBase = convertToBase(amount, currencyCode)
        return expenseDao.insert(
            ExpenseEntity(
                categoryId = categoryId,
                amount = amount,
                currencyCode = currencyCode,
                amountInBaseCurrency = amountInBase,
                date = date,
                note = note,
                recurringExpenseId = recurringExpenseId
            )
        )
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        val amountInBase = convertToBase(expense.amount, expense.currencyCode)
        expenseDao.update(expense.copy(amountInBaseCurrency = amountInBase))
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = expenseDao.delete(expense)

    // Recurring expenses
    fun observeRecurring(): Flow<List<RecurringExpenseEntity>> = recurringExpenseDao.observeAll()
    suspend fun getActiveRecurring(): List<RecurringExpenseEntity> = recurringExpenseDao.getActive()
    suspend fun addRecurring(recurring: RecurringExpenseEntity): Long = recurringExpenseDao.insert(recurring)
    suspend fun updateRecurring(recurring: RecurringExpenseEntity) = recurringExpenseDao.update(recurring)
    suspend fun deleteRecurring(recurring: RecurringExpenseEntity) = recurringExpenseDao.delete(recurring)

    suspend fun insertGeneratedExpense(recurring: RecurringExpenseEntity, date: LocalDate): Long {
        val amountInBase = convertToBase(recurring.amount, recurring.currencyCode)
        return expenseDao.insert(
            ExpenseEntity(
                categoryId = recurring.categoryId,
                amount = recurring.amount,
                currencyCode = recurring.currencyCode,
                amountInBaseCurrency = amountInBase,
                date = date,
                note = recurring.note,
                recurringExpenseId = recurring.id
            )
        )
    }

    // Currency rates
    fun observeCurrencyRates(): Flow<List<CurrencyRateEntity>> = currencyRateDao.observeAll()
    suspend fun getCurrencyRates(): List<CurrencyRateEntity> = currencyRateDao.getAll()
    suspend fun setCurrencyRate(code: String, rateToBase: Double) =
        currencyRateDao.upsert(CurrencyRateEntity(code = code.uppercase(), rateToBase = rateToBase))
    suspend fun deleteCurrencyRate(code: String) = currencyRateDao.delete(code)

    suspend fun refreshRatesFromNetwork(): Result<Int> {
        val codes = currencyRateDao.getAll().map { it.code }
        if (codes.isEmpty()) return Result.success(0)
        return currencyRateService.fetchRatesInEur(codes).map { fetched ->
            val updates = codes.mapNotNull { code ->
                fetched[code]?.let { CurrencyRateEntity(code = code, rateToBase = it) }
            }
            currencyRateDao.upsertAll(updates)
            updates.size
        }
    }

    private suspend fun convertToBase(amount: Double, currencyCode: String): Double {
        val rate = currencyRateDao.getByCode(currencyCode.uppercase())?.rateToBase ?: 1.0
        return amount * rate
    }
}
