package com.example.expensestracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.model.Group
import com.example.expensestracker.data.repository.GroupRepository
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
                onFailure = { e -> _uiState.value = OnboardingUiState(errorMessage = e.message ?: "Couldn't create the group.") }
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
                onFailure = { e -> _uiState.value = OnboardingUiState(errorMessage = e.message ?: "Couldn't join that group.") }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
