package com.example.expensestracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.expensestracker.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query(
        """
        SELECT e.id, e.categoryId, e.amount, e.currencyCode, e.amountInBaseCurrency, e.date, e.note,
               e.recurringExpenseId, e.createdAt,
               c.name AS categoryName, c.icon AS categoryIcon, c.colorHex AS categoryColorHex
        FROM expenses e
        JOIN categories c ON c.id = e.categoryId
        WHERE e.date BETWEEN :start AND :end
        ORDER BY e.date DESC, e.createdAt DESC
        """
    )
    fun observeExpensesBetween(start: LocalDate, end: LocalDate): Flow<List<ExpenseWithCategory>>

    @Query(
        """
        SELECT e.id, e.categoryId, e.amount, e.currencyCode, e.amountInBaseCurrency, e.date, e.note,
               e.recurringExpenseId, e.createdAt,
               c.name AS categoryName, c.icon AS categoryIcon, c.colorHex AS categoryColorHex
        FROM expenses e
        JOIN categories c ON c.id = e.categoryId
        ORDER BY e.date DESC, e.createdAt DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<ExpenseWithCategory>>

    @Query("SELECT COALESCE(SUM(amountInBaseCurrency), 0) FROM expenses WHERE date BETWEEN :start AND :end")
    fun observeTotalBetween(start: LocalDate, end: LocalDate): Flow<Double>

    @Query(
        """
        SELECT c.id AS categoryId, c.name AS name, c.icon AS icon, c.colorHex AS colorHex,
               c.monthlyBudget AS monthlyBudget,
               COALESCE(SUM(CASE WHEN e.date BETWEEN :start AND :end THEN e.amountInBaseCurrency ELSE NULL END), 0) AS spent
        FROM categories c
        LEFT JOIN expenses e ON e.categoryId = c.id
        GROUP BY c.id
        ORDER BY c.sortOrder ASC, c.name ASC
        """
    )
    fun observeSpendingByCategory(start: LocalDate, end: LocalDate): Flow<List<CategorySpending>>
}
