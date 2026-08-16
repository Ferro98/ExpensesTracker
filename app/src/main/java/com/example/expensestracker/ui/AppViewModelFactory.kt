package com.example.expensestracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.expensestracker.ExpensesTrackerApp
import com.example.expensestracker.domain.RecurringExpenseGenerator
import com.example.expensestracker.ui.addexpense.AddExpenseViewModel
import com.example.expensestracker.ui.categories.CategoriesViewModel
import com.example.expensestracker.ui.dashboard.DashboardViewModel
import com.example.expensestracker.ui.onboarding.OnboardingViewModel
import com.example.expensestracker.ui.recurring.RecurringViewModel
import com.example.expensestracker.ui.settings.SettingsViewModel

/**
 * [groupId]/[myUid] are null only while onboarding (no group joined yet); every other
 * ViewModel requires both to be resolved first - see MainActivity's gating.
 */
class AppViewModelFactory(
    private val app: ExpensesTrackerApp,
    private val groupId: String?,
    private val myUid: String?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            return OnboardingViewModel(app.groupRepository, app.settingsRepository) as T
        }

        val groupId = requireNotNull(groupId) { "groupId is required for ${modelClass.name}" }
        val myUid = requireNotNull(myUid) { "myUid is required for ${modelClass.name}" }
        val repository = app.expenseRepositoryFor(groupId)

        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(
                    repository, app.groupRepository, app.settingsRepository,
                    groupId, myUid, RecurringExpenseGenerator(repository)
                ) as T

            modelClass.isAssignableFrom(AddExpenseViewModel::class.java) ->
                AddExpenseViewModel(repository, app.groupRepository, groupId, myUid) as T

            modelClass.isAssignableFrom(CategoriesViewModel::class.java) ->
                CategoriesViewModel(repository, app.settingsRepository) as T

            modelClass.isAssignableFrom(RecurringViewModel::class.java) ->
                RecurringViewModel(repository, app.groupRepository, groupId, myUid) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(repository, app.groupRepository, app.settingsRepository, groupId) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
