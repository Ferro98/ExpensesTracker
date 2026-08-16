package com.example.expensestracker.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.example.expensestracker.data.local.dao.CategoryDao
import com.example.expensestracker.data.local.dao.CurrencyRateDao
import com.example.expensestracker.data.local.dao.ExpenseDao
import com.example.expensestracker.data.local.dao.RecurringExpenseDao
import com.example.expensestracker.data.local.entity.CategoryEntity
import com.example.expensestracker.data.local.entity.CurrencyRateEntity
import com.example.expensestracker.data.local.entity.ExpenseEntity
import com.example.expensestracker.data.local.entity.RecurringExpenseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CategoryEntity::class,
        ExpenseEntity::class,
        RecurringExpenseEntity::class,
        CurrencyRateEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun currencyRateDao(): CurrencyRateDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context, scope: CoroutineScope): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expenses_tracker.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch(Dispatchers.IO) {
                            instance?.let { seedDefaults(it) }
                        }
                    }
                }).build().also { instance = it }
            }
        }

        private suspend fun seedDefaults(db: AppDatabase) {
            db.categoryDao().insertAll(DefaultData.categories)
            db.currencyRateDao().upsertAll(DefaultData.currencyRates)
        }
    }
}
