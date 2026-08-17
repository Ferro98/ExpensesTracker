package com.example.expensestracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.expensestracker.ExpensesTrackerApp
import com.example.expensestracker.R
import com.example.expensestracker.ui.addexpense.AddExpenseViewModel
import com.example.expensestracker.ui.categories.CategoriesViewModel
import com.example.expensestracker.ui.dashboard.DashboardViewModel
import com.example.expensestracker.ui.onboarding.OnboardingViewModel
import com.example.expensestracker.ui.recurring.RecurringViewModel
import com.example.expensestracker.ui.settings.SettingsViewModel

/** [myUid] is always resolved before this factory is ever constructed (see MainActivity); only [groupId] is optional. */
class AppViewModelFactory(
    private val app: ExpensesTrackerApp,
    private val groupId: String?,
    private val myUid: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val personalExpenseRepository = app.personalExpenseRepositoryFor(myUid)
        val personalDataRepository = app.personalDataRepositoryFor(myUid)
        val groupContext = groupId?.let {
            GroupContext(it, app.groupRepository, app.groupExpenseRepositoryFor(it), app.settlementRepositoryFor(it))
        }

        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(
                    personalExpenseRepository, personalDataRepository, groupContext, myUid,
                    app.getString(R.string.dashboard_shared_category), app.settingsRepository
                ) as T

            modelClass.isAssignableFrom(AddExpenseViewModel::class.java) ->
                AddExpenseViewModel(personalExpenseRepository, personalDataRepository, groupContext, myUid) as T

            modelClass.isAssignableFrom(CategoriesViewModel::class.java) ->
                CategoriesViewModel(personalDataRepository, app.settingsRepository) as T

            modelClass.isAssignableFrom(RecurringViewModel::class.java) ->
                RecurringViewModel(personalExpenseRepository, personalDataRepository, groupContext, myUid) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(app, personalDataRepository, groupContext, app.settingsRepository, myUid) as T

            modelClass.isAssignableFrom(OnboardingViewModel::class.java) ->
                OnboardingViewModel(app, app.groupRepository, app.settingsRepository, myUid) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
