package com.example.expensestracker.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.R
import com.example.expensestracker.data.model.Group
import com.example.expensestracker.data.repository.GroupRepository
import com.example.expensestracker.data.repository.JoinGroupException
import com.example.expensestracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Set once a group is created, before it's persisted locally - lets the UI show the
    // invite code so it can be shared before actually switching into that group.
    val createdGroup: Group? = null
)

class OnboardingViewModel(
    private val context: Context,
    private val groupRepository: GroupRepository,
    private val settingsRepository: SettingsRepository,
    private val myUid: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun createGroup(displayName: String) {
        if (displayName.isBlank()) return
        _uiState.value = OnboardingUiState(isLoading = true)
        viewModelScope.launch {
            groupRepository.createGroup(myUid, displayName.trim()).fold(
                onSuccess = { group -> _uiState.value = OnboardingUiState(createdGroup = group) },
                onFailure = { _uiState.value = OnboardingUiState(errorMessage = context.getString(R.string.error_create_group_failed)) }
            )
        }
    }

    /** Called once the user has seen/shared the invite code - this is what actually joins the group. */
    fun confirmGroupCreated(displayName: String) {
        val group = _uiState.value.createdGroup ?: return
        viewModelScope.launch {
            settingsRepository.saveGroup(group.id, displayName.trim())
        }
    }

    fun joinGroup(code: String, displayName: String) {
        if (displayName.isBlank() || code.isBlank()) return
        _uiState.value = OnboardingUiState(isLoading = true)
        viewModelScope.launch {
            groupRepository.joinGroup(code.trim(), myUid, displayName.trim()).fold(
                onSuccess = { settingsRepository.saveGroup(it.id, displayName.trim()) },
                onFailure = { e ->
                    val messageRes = when (e) {
                        is JoinGroupException.NotFound -> R.string.error_group_not_found
                        is JoinGroupException.Full -> R.string.error_group_full
                        else -> R.string.error_group_vanished
                    }
                    _uiState.value = OnboardingUiState(errorMessage = context.getString(messageRes))
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
