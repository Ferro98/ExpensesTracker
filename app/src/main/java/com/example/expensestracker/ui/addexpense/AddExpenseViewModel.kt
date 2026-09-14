package com.example.expensestracker.ui.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AddExpenseUiState(
    val categories: List<Category> = emptyList(),
    val currencyRates: List<CurrencyRate> = emptyList(),
    val myUid: String = "",
    val partnerUid: String? = null,
    val partnerName: String = "Partner",
    val inGroup: Boolean = false,
    val defaultShared: Boolean = false,
    val defaultCurrency: String = "EUR",
    val lastUsedCategoryId: String? = null
)

/**
 * The expense a freshly opened sheet copies its values from. [isEdit] separates the two things
 * that look identical while typing but differ entirely on save: an edit overwrites [source],
 * a duplicate leaves it alone and creates a new expense next to it.
 */
data class ExpensePrefill(val source: Expense, val isEdit: Boolean)

/** Kept UI-string-free (resolved to text in the Composable) so the ViewModel doesn't need a Context. */
enum class AddExpenseError {
    CATEGORY_NOT_FOUND,
    SAVE_FAILED
}

class AddExpenseViewModel(
    private val personalExpenseRepository: ExpenseRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val groupContext: GroupContext?,
    private val settingsRepository: SettingsRepository,
    private val myUid: String
) : ViewModel() {
    // combine tops out at 5 typed flows, so the three DataStore preferences travel bundled.
    private val preferences = combine(
        settingsRepository.defaultSharedForExpense,
        settingsRepository.defaultCurrency,
        settingsRepository.lastUsedCategoryId
    ) { defaultShared, defaultCurrency, lastUsedCategoryId ->
        Triple(defaultShared, defaultCurrency, lastUsedCategoryId)
    }

    val uiState: StateFlow<AddExpenseUiState> = combine(
        personalDataRepository.observeCategories(),
        personalDataRepository.observeCurrencyRates(),
        groupContext?.let { it.groupRepository.observeGroup(it.groupId) } ?: flowOf(null),
        preferences
    ) { categories, currencyRates, group, prefs ->
        val (defaultShared, defaultCurrency, lastUsedCategoryId) = prefs
        val partnerUid = group?.otherMemberUid(myUid)
        AddExpenseUiState(
            categories = categories,
            currencyRates = currencyRates,
            myUid = myUid,
            partnerUid = partnerUid,
            partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner",
            inGroup = groupContext != null,
            defaultShared = defaultShared,
            defaultCurrency = defaultCurrency,
            lastUsedCategoryId = lastUsedCategoryId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddExpenseUiState(myUid = myUid))

    // Set when the sheet is opened on top of an existing expense rather than empty; the sheet
    // reads this once to prefill its fields, and saveExpense() branches on it to update (or move
    // between personal/group scope) instead of creating a fresh document.
    private val _prefill = MutableStateFlow<ExpensePrefill?>(null)
    val prefill: StateFlow<ExpensePrefill?> = _prefill.asStateFlow()

    fun startEdit(expense: Expense) {
        _prefill.value = ExpensePrefill(expense, isEdit = true)
    }

    /**
     * Opens the sheet on a copy of [expense] - same amount, category, currency, note and sharing.
     * The sheet dates it today rather than reusing [expense]'s own date, since duplicating is how
     * you log the thing you buy again and again.
     */
    fun startDuplicate(expense: Expense) {
        _prefill.value = ExpensePrefill(expense, isEdit = false)
    }

    fun clearPrefill() {
        _prefill.value = null
        _errorMessage.value = null
    }

    private val _errorMessage = MutableStateFlow<AddExpenseError?>(null)
    val errorMessage: StateFlow<AddExpenseError?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    private fun repositoryFor(shared: Boolean): ExpenseRepository =
        if (shared && groupContext != null) groupContext.expenseRepository else personalExpenseRepository

    fun saveExpense(
        categoryId: String,
        amount: Double,
        currencyCode: String,
        date: LocalDate,
        note: String?,
        paidByUid: String,
        isShared: Boolean,
        payerShare: Double,
        // Non-null only when this created a brand-new expense (not an edit) - the id the
        // "Expense saved / Undo" snackbar needs to be able to delete it again. Edits (including
        // the shared-flag-flip path, which technically creates a new document too) don't offer
        // this: from the user's perspective they corrected an existing expense, not created one.
        onSaved: (createdExpenseId: String?) -> Unit
    ) {
        viewModelScope.launch {
            _errorMessage.value = null
            // categoryId always comes from the currently-selected chip in uiState.categories (the
            // sheet self-corrects to one of those the moment it notices a mismatch - see the
            // LaunchedEffect in AddExpenseSheet), so this should always resolve; the fallback
            // error only fires if that self-correction hasn't run yet, e.g. a very fast double-tap.
            val category = uiState.value.categories.firstOrNull { it.id == categoryId }
            if (category == null) {
                _errorMessage.value = AddExpenseError.CATEGORY_NOT_FOUND
                return@launch
            }
            try {
                val amountInBaseCurrency = personalDataRepository.convertToBase(amount, currencyCode)
                val shared = isShared && groupContext != null
                // A duplicate carries a prefill too, but saving it must create a new document -
                // only a real edit overwrites the one it came from.
                val editing = _prefill.value?.takeIf { it.isEdit }?.source

                val newExpenseId: String? = when {
                    editing == null -> repositoryFor(shared).addExpense(
                        categoryId = categoryId,
                        categoryName = category.name,
                        categoryIcon = category.icon,
                        categoryColorHex = category.colorHex,
                        amount = amount,
                        currencyCode = currencyCode,
                        amountInBaseCurrency = amountInBaseCurrency,
                        date = date,
                        note = note?.takeIf { it.isNotBlank() },
                        paidByUid = paidByUid,
                        isShared = shared,
                        payerShare = payerShare
                    )
                    // Same scope as before editing - overwrite the existing document in place.
                    editing.isShared == shared -> {
                        repositoryFor(shared).updateExpense(
                            expenseId = editing.id,
                            categoryId = categoryId,
                            categoryName = category.name,
                            categoryIcon = category.icon,
                            categoryColorHex = category.colorHex,
                            amount = amount,
                            currencyCode = currencyCode,
                            amountInBaseCurrency = amountInBaseCurrency,
                            date = date,
                            note = note?.takeIf { it.isNotBlank() },
                            paidByUid = paidByUid,
                            isShared = shared,
                            payerShare = payerShare,
                            createdAt = editing.createdAt
                        )
                        null
                    }
                    // Shared flag flipped - personal and group expenses live in different Firestore
                    // collections, so "editing" here means deleting the old document and creating a
                    // fresh one in the new scope.
                    else -> {
                        repositoryFor(editing.isShared).deleteExpense(editing.id)
                        repositoryFor(shared).addExpense(
                            categoryId = categoryId,
                            categoryName = category.name,
                            categoryIcon = category.icon,
                            categoryColorHex = category.colorHex,
                            amount = amount,
                            currencyCode = currencyCode,
                            amountInBaseCurrency = amountInBaseCurrency,
                            date = date,
                            note = note?.takeIf { it.isNotBlank() },
                            paidByUid = paidByUid,
                            isShared = shared,
                            payerShare = payerShare
                        )
                        null
                    }
                }
                // Only for a new expense: while editing an old one you're correcting the past, and
                // that category shouldn't become the default for what you buy next.
                if (editing == null) settingsRepository.setLastUsedCategoryId(categoryId)
                _prefill.value = null
                onSaved(newExpenseId)
            } catch (e: Exception) {
                _errorMessage.value = AddExpenseError.SAVE_FAILED
            }
        }
    }
}
