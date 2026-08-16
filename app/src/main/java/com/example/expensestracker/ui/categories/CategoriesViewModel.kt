package com.example.expensestracker.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.local.entity.CategoryEntity
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val monthlyBudget: Double? = null
)

class CategoriesViewModel(
    private val repository: ExpenseRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val uiState: StateFlow<CategoriesUiState> = combine(
        repository.observeCategories(),
        settingsRepository.monthlyBudget
    ) { categories, budget ->
        CategoriesUiState(categories, budget)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoriesUiState())

    fun setMonthlyBudget(amount: Double?) {
        viewModelScope.launch { settingsRepository.setMonthlyBudget(amount) }
    }

    fun addCategory(name: String, icon: String, colorHex: String, budget: Double?) {
        viewModelScope.launch {
            repository.addCategory(
                CategoryEntity(
                    name = name,
                    icon = icon,
                    colorHex = colorHex,
                    monthlyBudget = budget,
                    sortOrder = uiState.value.categories.size
                )
            )
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch { repository.updateCategory(category) }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }
}
