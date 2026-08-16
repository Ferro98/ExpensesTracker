package com.example.expensestracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.data.local.entity.CurrencyRateEntity
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.data.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: ExpenseRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val currencyRates: StateFlow<List<CurrencyRateEntity>> = repository.observeCurrencyRates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

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
                onSuccess = { count -> "Rates updated ($count currencies)." },
                onFailure = { "Couldn't update rates. Check your connection." }
            )
            _isRefreshing.value = false
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}
