package com.example.expensestracker.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.Group
import com.example.expensestracker.data.model.Settlement
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.domain.Balance
import com.example.expensestracker.domain.BalanceCalculator
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One row of the activity feed - either a shared expense or a settlement, ordered together by date. */
sealed interface GroupActivityItem {
    val localDate: LocalDate
    val createdAtSeconds: Long

    data class ExpenseActivity(val expense: Expense) : GroupActivityItem {
        override val localDate get() = expense.localDate
        override val createdAtSeconds get() = expense.createdAt?.seconds ?: 0
    }

    data class SettlementActivity(val settlement: Settlement) : GroupActivityItem {
        override val localDate get() = settlement.localDate
        override val createdAtSeconds get() = settlement.createdAt?.seconds ?: 0
    }
}

data class GroupUiState(
    val inGroup: Boolean = false,
    val group: Group? = null,
    val myUid: String = "",
    val partnerUid: String? = null,
    val partnerName: String = "Partner",
    val balance: Balance = Balance(0.0, null, null),
    /** Lifetime totals - not scoped to a month, unlike everything in ui/month. */
    val paidByMe: Double = 0.0,
    val paidByPartner: Double = 0.0,
    val activity: List<GroupActivityItem> = emptyList(),
    val currencyRates: List<CurrencyRate> = emptyList(),
    val defaultCurrency: String = "EUR"
)

/**
 * The couple's shared history: who's paid what, the running balance, and every shared expense and
 * settlement in one chronological feed. Unlike [com.example.expensestracker.ui.month.MonthViewModel]
 * this is intentionally not month-scoped - "who owes who" only makes sense as a lifetime figure.
 */
class GroupViewModel(
    private val groupContext: GroupContext?,
    private val personalDataRepository: PersonalDataRepository,
    private val settingsRepository: SettingsRepository,
    private val myUid: String
) : ViewModel() {
    val uiState: StateFlow<GroupUiState> = buildUiStateFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GroupUiState(inGroup = groupContext != null, myUid = myUid))

    private fun buildUiStateFlow(): Flow<GroupUiState> {
        val context = groupContext ?: return flowOf(GroupUiState(inGroup = false, myUid = myUid))
        return combine(
            context.groupRepository.observeGroup(context.groupId),
            context.expenseRepository.observeAllExpenses(),
            context.settlementRepository.observeSettlements(),
            personalDataRepository.observeCurrencyRates(),
            settingsRepository.defaultCurrency
        ) { group, sharedExpenses, settlements, rates, defaultCurrency ->
            val partnerUid = group?.otherMemberUid(myUid)
            val partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner"
            val balance = group?.let { BalanceCalculator.compute(sharedExpenses, settlements, it.memberUids) } ?: Balance(0.0, null, null)

            val activity = (sharedExpenses.map { GroupActivityItem.ExpenseActivity(it) } + settlements.map { GroupActivityItem.SettlementActivity(it) })
                .sortedWith(compareByDescending<GroupActivityItem> { it.localDate }.thenByDescending { it.createdAtSeconds })

            GroupUiState(
                inGroup = true,
                group = group,
                myUid = myUid,
                partnerUid = partnerUid,
                partnerName = partnerName,
                balance = balance,
                paidByMe = sharedExpenses.filter { it.paidByUid == myUid }.sumOf { it.amountInBaseCurrency },
                paidByPartner = sharedExpenses.filter { it.paidByUid == partnerUid }.sumOf { it.amountInBaseCurrency },
                activity = activity,
                currencyRates = rates,
                defaultCurrency = defaultCurrency
            )
        }
    }

    fun addSettlement(fromUid: String, toUid: String, amount: Double, currencyCode: String, date: LocalDate, note: String?) {
        val context = groupContext ?: return
        viewModelScope.launch {
            val amountInBaseCurrency = personalDataRepository.convertToBase(amount, currencyCode)
            context.settlementRepository.addSettlement(fromUid, toUid, amount, currencyCode, amountInBaseCurrency, date, note)
        }
    }

    /** Every expense in this feed lives in the group's own collection (that's what "shared" means here), so there's only one place to delete it from - unlike MonthViewModel.deleteExpense, which has to guess between two scopes. */
    fun deleteExpense(expenseId: String) {
        val context = groupContext ?: return
        viewModelScope.launch { context.expenseRepository.deleteExpense(expenseId) }
    }

    private val _isLeaving = MutableStateFlow(false)
    val isLeaving: StateFlow<Boolean> = _isLeaving

    private val _leaveFailed = MutableStateFlow(false)
    val leaveFailed: StateFlow<Boolean> = _leaveFailed

    fun leaveGroup(onLeft: () -> Unit) {
        val context = groupContext ?: return
        viewModelScope.launch {
            _isLeaving.value = true
            context.groupRepository.leaveGroup(context.groupId, myUid).fold(
                onSuccess = {
                    settingsRepository.clearGroup()
                    _isLeaving.value = false
                    onLeft()
                },
                onFailure = {
                    _isLeaving.value = false
                    _leaveFailed.value = true
                }
            )
        }
    }

    fun clearLeaveFailed() {
        _leaveFailed.value = false
    }
}
