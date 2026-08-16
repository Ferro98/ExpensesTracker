package com.example.expensestracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.expensestracker.ExpensesTrackerApp
import com.example.expensestracker.ui.addexpense.AddExpenseViewModel
import com.example.expensestracker.ui.categories.CategoriesViewModel
import com.example.expensestracker.ui.dashboard.DashboardViewModel
import com.example.expensestracker.ui.recurring.RecurringViewModel
import com.example.expensestracker.ui.settings.SettingsViewModel

class AppViewModelFactory(private val app: ExpensesTrackerApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(app.expenseRepository, app.settingsRepository, app.recurringExpenseGenerator) as T

            modelClass.isAssignableFrom(AddExpenseViewModel::class.java) ->
                AddExpenseViewModel(app.expenseRepository) as T

            modelClass.isAssignableFrom(CategoriesViewModel::class.java) ->
                CategoriesViewModel(app.expenseRepository, app.settingsRepository) as T

            modelClass.isAssignableFrom(RecurringViewModel::class.java) ->
                RecurringViewModel(app.expenseRepository) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(app.expenseRepository, app.settingsRepository) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
