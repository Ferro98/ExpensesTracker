package com.example.expensestracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.expensestracker.data.local.entity.CurrencyRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyRateDao {
    @Query("SELECT * FROM currency_rates ORDER BY code ASC")
    fun observeAll(): Flow<List<CurrencyRateEntity>>

    @Query("SELECT * FROM currency_rates ORDER BY code ASC")
    suspend fun getAll(): List<CurrencyRateEntity>

    @Query("SELECT * FROM currency_rates WHERE code = :code")
    suspend fun getByCode(code: String): CurrencyRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: CurrencyRateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rates: List<CurrencyRateEntity>)

    @Query("DELETE FROM currency_rates WHERE code = :code")
    suspend fun delete(code: String)
}
