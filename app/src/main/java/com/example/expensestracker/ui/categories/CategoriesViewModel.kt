package com.example.expensestracker.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val monthlyBudget: Double? = null,
    val categoryBudgets: Map<String, Double> = emptyMap(),
    val currencyRates: List<CurrencyRate> = emptyList()
)

class CategoriesViewModel(
    private val repository: ExpenseRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val uiState: StateFlow<CategoriesUiState> = combine(
        repository.observeCategories(),
        settingsRepository.myMonthlyBudget,
        settingsRepository.myCategoryBudgets,
        repository.observeCurrencyRates()
    ) { categories, budget, categoryBudgets, rates ->
        CategoriesUiState(categories, budget, categoryBudgets, rates)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoriesUiState())

    /** Converts an amount entered in [currencyCode] to the group's base currency (EUR). */
    private fun toBase(amount: Double, currencyCode: String): Double {
        val rate = uiState.value.currencyRates.firstOrNull { it.code == currencyCode }?.rateToBase ?: 1.0
        return amount * rate
    }

    fun setMonthlyBudget(amount: Double?, currencyCode: String) {
        viewModelScope.launch {
            settingsRepository.setMyMonthlyBudget(amount?.let { toBase(it, currencyCode) })
        }
    }

    fun addCategory(name: String, icon: String, colorHex: String, budget: Double?, budgetCurrency: String) {
        viewModelScope.launch {
            val id = repository.addCategory(
                Category(name = name, icon = icon, colorHex = colorHex, sortOrder = uiState.value.categories.size)
            )
            settingsRepository.setCategoryBudget(id, budget?.let { toBase(it, budgetCurrency) })
        }
    }

    fun updateCategory(category: Category, name: String, icon: String, colorHex: String, budget: Double?, budgetCurrency: String) {
        viewModelScope.launch {
            repository.updateCategory(category.copy(name = name, icon = icon, colorHex = colorHex))
            settingsRepository.setCategoryBudget(category.id, budget?.let { toBase(it, budgetCurrency) })
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category.id)
            settingsRepository.setCategoryBudget(category.id, null)
        }
    }
}
