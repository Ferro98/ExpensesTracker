package com.example.expensestracker.ui.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Category
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.GroupRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AddExpenseUiState(
    val categories: List<Category> = emptyList(),
    val currencyRates: List<CurrencyRate> = emptyList(),
    val myUid: String = "",
    val partnerUid: String? = null,
    val partnerName: String = "Partner"
)

class AddExpenseViewModel(
    private val repository: ExpenseRepository,
    groupRepository: GroupRepository,
    groupId: String,
    private val myUid: String
) : ViewModel() {
    val uiState: StateFlow<AddExpenseUiState> = combine(
        repository.observeCategories(),
        repository.observeCurrencyRates(),
        groupRepository.observeGroup(groupId)
    ) { categories, currencyRates, group ->
        val partnerUid = group?.otherMemberUid(myUid)
        AddExpenseUiState(
            categories = categories,
            currencyRates = currencyRates,
            myUid = myUid,
            partnerUid = partnerUid,
            partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner"
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
            repository.addExpense(
                categoryId = categoryId,
                amount = amount,
                currencyCode = currencyCode,
                date = date,
                note = note?.takeIf { it.isNotBlank() },
                paidByUid = paidByUid,
                isShared = isShared,
                payerShare = payerShare
            )
            onSaved()
        }
    }
}
