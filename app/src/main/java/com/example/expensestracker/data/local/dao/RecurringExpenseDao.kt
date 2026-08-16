package com.example.expensestracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.expensestracker.data.local.entity.RecurringExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {
    @Query("SELECT * FROM recurring_expenses ORDER BY active DESC, dayOfPeriod ASC")
    fun observeAll(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE active = 1")
    suspend fun getActive(): List<RecurringExpenseEntity>

    @Insert
    suspend fun insert(recurringExpense: RecurringExpenseEntity): Long

    @Update
    suspend fun update(recurringExpense: RecurringExpenseEntity)

    @Delete
    suspend fun delete(recurringExpense: RecurringExpenseEntity)
}
