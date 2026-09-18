package com.example.expensestracker.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Expense
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class NewSharedExpensesUiState(
    val newExpenses: List<Expense> = emptyList(),
    val myUid: String = "",
    val partnerName: String = "Partner"
)

/**
 * A real push notification for a shared expense the partner adds would need a paid Cloud
 * Functions plan (see docs/UX_REDESIGN_PLAN.md 8) - this is the free alternative: every time the
 * app comes back to the foreground, catch up on anything the partner added since the last time
 * this was dismissed, and surface it once in a dialog, so nothing shared is silently missed
 * between sessions.
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

            val group = context.groupRepository.observeGroup(context.groupId).first()
            val partnerUid = group?.otherMemberUid(myUid)
            val partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else "Partner"

            val allShared = context.expenseRepository.observeAllExpenses().first()
            val fresh = allShared
                .filter { it.isShared && it.paidByUid == partnerUid && (it.createdAt?.toDate()?.time ?: 0L) > lastSeenAt }
                .sortedByDescending { it.createdAt?.seconds ?: 0 }

            if (fresh.isNotEmpty()) {
                _uiState.value = NewSharedExpensesUiState(newExpenses = fresh, myUid = myUid, partnerName = partnerName)
            }
        }
    }

    /** Marks everything caught up to now - called once the user has acknowledged the dialog. */
    fun dismiss() {
        viewModelScope.launch {
            settingsRepository.setLastSeenSharedExpensesAt(System.currentTimeMillis())
        }
        _uiState.value = _uiState.value.copy(newExpenses = emptyList())
    }
}
