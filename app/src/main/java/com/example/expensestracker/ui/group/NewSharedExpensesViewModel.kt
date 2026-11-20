package com.example.expensestracker.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.model.SavingsEntryKind
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Something the partner did to a shared savings pot since last time. */
data class PartnerSavingsMove(val goalName: String, val kind: SavingsEntryKind, val amountBase: Double)

data class NewSharedExpensesUiState(
    val newExpenses: List<Expense> = emptyList(),
    val savingsMoves: List<PartnerSavingsMove> = emptyList(),
    val myUid: String = "",
    val partnerName: String = "Partner"
) {
    val hasNews: Boolean get() = newExpenses.isNotEmpty() || savingsMoves.isNotEmpty()
}

/**
 * A real push notification for something the partner does in the shared space would need a paid
 * Cloud Functions plan (see docs/UX_REDESIGN_PLAN.md 8) - this is the free alternative: every time
 * the app comes back to the foreground, catch up on anything the partner added since the last time
 * this was dismissed - shared expenses, and money they put into or took out of a shared savings pot
 * - and surface it once in a dialog, so nothing shared is silently missed between sessions.
 */
class NewSharedExpensesViewModel(
    private val groupContext: GroupContext?,
    private val settingsRepository: SettingsRepository,
    private val myUid: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewSharedExpensesUiState(myUid = myUid))
    val uiState: StateFlow<NewSharedExpensesUiState> = _uiState

    init {
        checkForNewExpenses()
    }

    fun checkForNewExpenses() {
        val context = groupContext ?: return
        viewModelScope.launch {
            val lastSeenAt = settingsRepository.lastSeenSharedExpensesAt.first()
            if (lastSeenAt == null) {
                // First time this check has ever run on this device - nothing to compare against
                // yet, so start the clock now instead of dumping the group's entire shared
                // history as "new".
                settingsRepository.setLastSeenSharedExpensesAt(System.currentTimeMillis())
                return@launch
            }

            val group = context.groupRepository.getGroup(context.groupId)
            val partnerUid = group?.otherMemberUid(myUid)
            val partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner"

            val allShared = context.expenseRepository.getAllExpenses()
            val fresh = allShared
                // Spent out of a shared pot is the pair's doing, not something the partner added on their own.
                .filter { it.isShared && !it.paidFromPot && it.paidByUid == partnerUid && (it.createdAt?.toDate()?.time ?: 0L) > lastSeenAt }
                .sortedByDescending { it.createdAt?.seconds ?: 0 }

            // Only what the partner themselves did: spending a shared pot writes a withdrawal under
            // each member's name, recorded by whoever pressed the button.
            val goals = context.savingsRepository.getGoals().associateBy { it.id }
            val moves = context.savingsRepository.getEntries()
                .filter { it.doneByUid == partnerUid && (it.createdAt?.toDate()?.time ?: 0L) > lastSeenAt && it.goalId in goals }
                .sortedByDescending { it.createdAt?.seconds ?: 0 }
                .map { PartnerSavingsMove(goals.getValue(it.goalId).name, it.kind, it.amountInBaseCurrency) }

            if (fresh.isNotEmpty() || moves.isNotEmpty()) {
                _uiState.value = NewSharedExpensesUiState(newExpenses = fresh, savingsMoves = moves, myUid = myUid, partnerName = partnerName)
            }
        }
    }

    /** Marks everything caught up to now - called once the user has acknowledged the dialog. */
    fun dismiss() {
        viewModelScope.launch {
            settingsRepository.setLastSeenSharedExpensesAt(System.currentTimeMillis())
        }
        _uiState.value = _uiState.value.copy(newExpenses = emptyList(), savingsMoves = emptyList())
    }
}
