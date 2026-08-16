package com.example.expensestracker.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.local.entity.CategoryEntity
import com.example.expensestracker.data.local.entity.CurrencyRateEntity
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val monthlyBudget: Double? = null,
    val currencyRates: List<CurrencyRateEntity> = emptyList()
)

class CategoriesViewModel(
    private val repository: ExpenseRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val uiState: StateFlow<CategoriesUiState> = combine(
        repository.observeCategories(),
        settingsRepository.monthlyBudget,
        repository.observeCurrencyRates()
    ) { categories, budget, currencyRates ->
        CategoriesUiState(categories, budget, currencyRates)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoriesUiState())

    /** Converts an amount entered in [currencyCode] to the app's base currency (EUR). */
    private fun toBase(amount: Double, currencyCode: String): Double {
        val rate = uiState.value.currencyRates.firstOrNull { it.code == currencyCode }?.rateToBase ?: 1.0
        return amount * rate
    }

    fun setMonthlyBudget(amount: Double?, currencyCode: String) {
        viewModelScope.launch {
            settingsRepository.setMonthlyBudget(amount?.let { toBase(it, currencyCode) })
        }
    }

    fun addCategory(name: String, icon: String, colorHex: String, budget: Double?, budgetCurrency: String) {
        viewModelScope.launch {
            repository.addCategory(
                CategoryEntity(
                    name = name,
                    icon = icon,
                    colorHex = colorHex,
                    monthlyBudget = budget?.let { toBase(it, budgetCurrency) },
                    sortOrder = uiState.value.categories.size
                )
            )
        }
    }

    fun updateCategory(category: CategoryEntity, name: String, icon: String, colorHex: String, budget: Double?, budgetCurrency: String) {
        viewModelScope.launch {
            repository.updateCategory(
                category.copy(
                    name = name,
                    icon = icon,
                    colorHex = colorHex,
                    monthlyBudget = budget?.let { toBase(it, budgetCurrency) }
                )
            )
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }
}
