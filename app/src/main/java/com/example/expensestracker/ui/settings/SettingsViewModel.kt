package com.example.expensestracker.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.R
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.Group
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.data.settings.ThemeMode
import com.example.expensestracker.ui.GroupContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val androidContext: Context,
    private val repository: PersonalDataRepository,
    private val groupContext: GroupContext?,
    private val settingsRepository: SettingsRepository,
    private val myUid: String
) : ViewModel() {
    val currencyRates: StateFlow<List<CurrencyRate>> = repository.observeCurrencyRates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val group: StateFlow<Group?> = (groupContext?.let { it.groupRepository.observeGroup(it.groupId) } ?: flowOf(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val inGroup: Boolean = groupContext != null

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLeaving = MutableStateFlow(false)
    val isLeaving: StateFlow<Boolean> = _isLeaving

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setRate(code: String, rate: Double) {
        viewModelScope.launch { repository.setCurrencyRate(code, rate) }
    }

    fun addCurrency(code: String, rate: Double) {
        viewModelScope.launch { repository.setCurrencyRate(code, rate) }
    }

    fun deleteCurrency(code: String) {
        viewModelScope.launch { repository.deleteCurrencyRate(code) }
    }

    fun refreshRates() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.refreshRatesFromNetwork()
            _statusMessage.value = result.fold(
                onSuccess = { count -> androidContext.getString(R.string.rates_updated, count) },
                onFailure = { androidContext.getString(R.string.rates_update_failed) }
            )
            _isRefreshing.value = false
        }
    }

    fun leaveGroup(onLeft: () -> Unit) {
        val activeGroup = groupContext ?: return
        viewModelScope.launch {
            _isLeaving.value = true
            activeGroup.groupRepository.leaveGroup(activeGroup.groupId, myUid).fold(
                onSuccess = {
                    settingsRepository.clearGroup()
                    _isLeaving.value = false
                    onLeft()
                },
                onFailure = {
                    _isLeaving.value = false
                    _statusMessage.value = androidContext.getString(R.string.leave_group_failed)
                }
            )
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}
