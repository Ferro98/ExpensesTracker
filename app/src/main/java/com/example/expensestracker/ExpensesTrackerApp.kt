package com.example.expensestracker

import android.app.Application
import com.example.expensestracker.data.local.AppDatabase
import com.example.expensestracker.data.remote.CurrencyRateService
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.domain.RecurringExpenseGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class ExpensesTrackerApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getInstance(this, applicationScope) }

    val settingsRepository by lazy { SettingsRepository(this) }

    val expenseRepository by lazy {
        ExpenseRepository(
            categoryDao = database.categoryDao(),
            expenseDao = database.expenseDao(),
            recurringExpenseDao = database.recurringExpenseDao(),
            currencyRateDao = database.currencyRateDao(),
            currencyRateService = CurrencyRateService()
        )
    }

    val recurringExpenseGenerator by lazy { RecurringExpenseGenerator(expenseRepository) }
}
