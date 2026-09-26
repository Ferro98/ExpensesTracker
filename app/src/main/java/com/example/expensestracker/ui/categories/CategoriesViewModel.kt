package com.example.expensestracker.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.domain.CategoryResolver
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val monthlyBudget: Double? = null,
    val categoryBudgets: Map<String, Double> = emptyMap(),
    val currencyRates: List<CurrencyRate> = emptyList(),
    val defaultCurrency: String = "EUR",
    val partnerCategories: List<PartnerCategory> = emptyList()
)

/**
 * A category id seen on a shared expense the partner created, that isn't one of the viewer's own
 * (see [CategoryResolver]). [resolvedCategoryId]/[resolvedCategoryName] is whatever it *currently*
 * counts toward - the automatic by-name match, an explicit alias, or "Other" - exactly what Stats
 * uses, so the viewer sees the real effect instead of only "has an alias been set or not".
 * [isExplicitMerge] is true only when that resolution came from a user-chosen alias rather than
 * the automatic name match, since only that case can be undone with "remove merge".
 */
data class PartnerCategory(
    val categoryId: String,
    val name: String,
    val icon: String,
    val resolvedCategoryId: String?,
    val resolvedCategoryName: String?,
    val isExplicitMerge: Boolean
)

class CategoriesViewModel(
    private val repository: PersonalDataRepository,
    private val groupContext: GroupContext?,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    // combine tops out at 5 typed flows: categories/aliases are bundled since deriving
    // partnerCategories needs both together anyway.
    private val categoriesAndAliases = combine(
        repository.observeCategories(),
        settingsRepository.categoryAliases
    ) { categories, aliases -> categories to aliases }

    val uiState: StateFlow<CategoriesUiState> = combine(
        categoriesAndAliases,
        settingsRepository.myMonthlyBudget,
        settingsRepository.myCategoryBudgets,
        repository.observeCurrencyRates(),
        combine(
            settingsRepository.defaultCurrency,
            groupContext?.expenseRepository?.observeAllExpenses() ?: flowOf(emptyList())
        ) { defaultCurrency, sharedExpenses -> defaultCurrency to sharedExpenses }
    ) { catsAndAliases, budget, categoryBudgets, rates, currencyAndShared ->
        val (categories, aliases) = catsAndAliases
        val (defaultCurrency, sharedExpenses) = currencyAndShared
        val partnerCategories = sharedExpenses
            .filter { it.categoryId.isNotEmpty() && categories.none { own -> own.id == it.categoryId } }
            .distinctBy { it.categoryId }
            .map { expense ->
                val isExplicitMerge = aliases[expense.categoryId]?.let { aliasedId -> categories.any { it.id == aliasedId } } == true
                val resolved = CategoryResolver.resolve(expense.categoryId, expense.categoryName, categories, aliases)
                PartnerCategory(
                    categoryId = expense.categoryId,
                    name = expense.categoryName,
                    icon = expense.categoryIcon,
                    resolvedCategoryId = resolved?.id,
                    resolvedCategoryName = resolved?.name,
                    isExplicitMerge = isExplicitMerge
                )
            }
            // Ones nobody has confirmed yet (still relying on the automatic name match, or
            // falling to "Other") are the ones worth the viewer's attention first.
            .sortedBy { it.isExplicitMerge }
        CategoriesUiState(categories, budget, categoryBudgets, rates, defaultCurrency, partnerCategories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoriesUiState())

    /** Converts an amount entered in [currencyCode] to the base currency (EUR). */
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

    /** "This partner category is really the same as one of mine" - merges it in for display/aggregation purposes only. */
    fun mergeCategory(partnerCategoryId: String, myCategoryId: String) {
        viewModelScope.launch {
            settingsRepository.setCategoryAlias(partnerCategoryId, myCategoryId)
        }
    }

    /** Reverts to the automatic by-name match (or "Other" if that doesn't match either). */
    fun unmergeCategory(partnerCategoryId: String) {
        viewModelScope.launch {
            settingsRepository.removeCategoryAlias(partnerCategoryId)
        }
    }

    /** Swaps [category] with its neighbor in [direction] (-1 = up, +1 = down) and persists the new order. */
    fun moveCategory(category: Category, direction: Int) {
        val categories = uiState.value.categories
        val index = categories.indexOfFirst { it.id == category.id }
        val targetIndex = index + direction
        if (index < 0 || targetIndex < 0 || targetIndex >= categories.size) return

        val reordered = categories.toMutableList()
        val moved = reordered.removeAt(index)
        reordered.add(targetIndex, moved)
        viewModelScope.launch {
            repository.reorderCategories(reordered.map { it.id })
        }
    }
}
