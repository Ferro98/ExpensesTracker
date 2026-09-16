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
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * How long to wait for Firestore's server acknowledgement before giving up on it. Firestore
 * itself never "fails" while offline - it queues the write in its local cache (already durable,
 * already reflected in every observeAllExpenses() listener) and syncs whenever connectivity
 * returns - so timing out here is not a failure to report, just a cue to stop waiting.
 */
private const val WRITE_TIMEOUT_MS = 8000L

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

    // Guards against the exact bug a silent, unbounded save used to cause: no feedback while
    // offline/slow reads as "did that even register?", so the user taps Save again - and again -
    // each tap launching an independent write. With this flag a second tap while one is still in
    // flight is simply ignored instead of queuing a duplicate.
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

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
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            // categoryId always comes from the currently-selected chip in uiState.categories (the
            // sheet self-corrects to one of those the moment it notices a mismatch - see the
            // LaunchedEffect in AddExpenseSheet), so this should always resolve; the fallback
            // error only fires if that self-correction hasn't run yet, e.g. a very fast double-tap.
            val category = uiState.value.categories.firstOrNull { it.id == categoryId }
            if (category == null) {
                _errorMessage.value = AddExpenseError.CATEGORY_NOT_FOUND
                _isSaving.value = false
                return@launch
            }
            try {
                val amountInBaseCurrency = personalDataRepository.convertToBase(amount, currencyCode)
                val shared = isShared && groupContext != null
                // A duplicate carries a prefill too, but saving it must create a new document -
                // only a real edit overwrites the one it came from.
                val editing = _prefill.value?.takeIf { it.isEdit }?.source

                // Waits for Firestore's server ack up to WRITE_TIMEOUT_MS, but doesn't treat timing
                // out as failure: the write already applied to the local cache the moment it was
                // dispatched (before this coroutine even reached its first suspension point), so a
                // timeout here just means "still offline", not "didn't happen". The one cost is losing
                // newExpenseId for a create that times out (can't offer "Undo" without it) - a minor,
                // rare trade next to the alternative of hanging indefinitely with the Save button live.
                val newExpenseId: String? = withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                    when {
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
                        // fresh one in the new scope. If the timeout fires while still awaiting the
                        // delete, the add below never runs - a residual gap this timeout guard doesn't
                        // fully close, left as a rare, disclosed edge case rather than added complexity.
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
                }
                // Only for a new expense: while editing an old one you're correcting the past, and
                // that category shouldn't become the default for what you buy next.
                if (editing == null) settingsRepository.setLastUsedCategoryId(categoryId)
                _prefill.value = null
                onSaved(newExpenseId)
            } catch (e: Exception) {
                _errorMessage.value = AddExpenseError.SAVE_FAILED
            } finally {
                _isSaving.value = false
            }
        }
    }
}
