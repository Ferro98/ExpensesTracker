package com.example.expensestracker.ui.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val inGroup: Boolean = false
)

class AddExpenseViewModel(
    private val personalExpenseRepository: ExpenseRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val groupContext: GroupContext?,
    private val myUid: String
) : ViewModel() {
    val uiState: StateFlow<AddExpenseUiState> = combine(
        personalDataRepository.observeCategories(),
        personalDataRepository.observeCurrencyRates(),
        groupContext?.let { it.groupRepository.observeGroup(it.groupId) } ?: flowOf(null)
    ) { categories, currencyRates, group ->
        val partnerUid = group?.otherMemberUid(myUid)
        AddExpenseUiState(
            categories = categories,
            currencyRates = currencyRates,
            myUid = myUid,
            partnerUid = partnerUid,
            partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner",
            inGroup = groupContext != null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddExpenseUiState(myUid = myUid))

    fun saveExpense(
        categoryId: String,
        amount: Double,
        currencyCode: String,
        date: LocalDate,
        note: String?,
        paidByUid: String,
        isShared: Boolean,
        payerShare: Double,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            val category = uiState.value.categories.firstOrNull { it.id == categoryId } ?: return@launch
            val amountInBaseCurrency = personalDataRepository.convertToBase(amount, currencyCode)
            val shared = isShared && groupContext != null
            val repository = if (shared) groupContext.expenseRepository else personalExpenseRepository
            repository.addExpense(
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
            onSaved()
        }
    }
}
