package com.example.expensestracker.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensestracker.R
import com.example.expensestracker.data.model.CurrencyRate
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.data.repository.AuthRepository
import com.example.expensestracker.data.repository.AuthState
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.example.expensestracker.data.settings.ThemeMode
import com.example.expensestracker.domain.ExpenseCsv
import com.example.expensestracker.ui.GroupContext
import com.example.expensestracker.util.decimalSeparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val androidContext: Context,
    private val repository: PersonalDataRepository,
    private val groupContext: GroupContext?,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val personalExpenseRepository: ExpenseRepository,
    private val myUid: String
) : ViewModel() {
    val privacyMode: StateFlow<Boolean> = settingsRepository.privacyMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPrivacyMode(hidden: Boolean) {
        viewModelScope.launch { settingsRepository.setPrivacyMode(hidden) }
    }

    /**
     * Writes every expense the user can see (personal, plus the group's shared ones) to [uri] as CSV -
     * the document the user just picked through the system file dialog, so no storage permission is
     * involved. Reports the outcome through the same status message the rest of this screen uses.
     */
    fun exportExpenses(uri: Uri) {
        viewModelScope.launch {
            _statusMessage.value = try {
                val personal = personalExpenseRepository.getAllExpenses()
                val shared = groupContext?.expenseRepository?.getAllExpenses().orEmpty()
                val group = groupContext?.let { it.groupRepository.getGroup(it.groupId) }
                val partnerUid = group?.otherMemberUid(myUid)
                val partnerName = if (group != null && partnerUid != null) group.nameOf(partnerUid) else ""
                val all = personal + shared
                val csv = ExpenseCsv.build(
                    expenses = all,
                    myUid = myUid,
                    partnerName = partnerName,
                    decimalSeparator = decimalSeparator(),
                    header = listOf(
                        R.string.csv_date, R.string.csv_category, R.string.csv_note, R.string.csv_amount, R.string.csv_currency,
                        R.string.csv_amount_base, R.string.csv_type, R.string.csv_paid_by, R.string.csv_my_share,
                        R.string.csv_period_start, R.string.csv_period_end
                    ).map { androidContext.getString(it) },
                    typePersonal = androidContext.getString(R.string.personal_label),
                    typeShared = androidContext.getString(R.string.csv_type_shared),
                    meLabel = androidContext.getString(R.string.you)
                )
                withContext(Dispatchers.IO) {
                    androidContext.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: error("no output stream")
                }
                androidContext.getString(R.string.export_done, all.size)
            } catch (e: Exception) {
                androidContext.getString(R.string.export_failed)
            }
        }
    }

    val currencyRates: StateFlow<List<CurrencyRate>> = repository.observeCurrencyRates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val authState: StateFlow<AuthState?> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val defaultSharedForExpense: StateFlow<Boolean> = settingsRepository.defaultSharedForExpense
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val defaultSharedForRecurring: StateFlow<Boolean> = settingsRepository.defaultSharedForRecurring
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDefaultSharedForExpense(value: Boolean) {
        viewModelScope.launch { settingsRepository.setDefaultSharedForExpense(value) }
    }

    fun setDefaultSharedForRecurring(value: Boolean) {
        viewModelScope.launch { settingsRepository.setDefaultSharedForRecurring(value) }
    }

    val defaultCurrency: StateFlow<String> = settingsRepository.defaultCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultUserData.BASE_CURRENCY)

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setDefaultCurrency(code) }
    }

    private val _isLinkingAccount = MutableStateFlow(false)
    val isLinkingAccount: StateFlow<Boolean> = _isLinkingAccount

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val inGroup: Boolean = groupContext != null

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
                onSuccess = { count -> androidContext.getString(R.string.rates_updated, count) },
                onFailure = { androidContext.getString(R.string.rates_update_failed) }
            )
            _isRefreshing.value = false
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun linkGoogleAccount(idToken: String) {
        viewModelScope.launch {
            _isLinkingAccount.value = true
            authRepository.linkWithGoogle(idToken).fold(
                onSuccess = { _statusMessage.value = androidContext.getString(R.string.account_linked_success) },
                onFailure = { _statusMessage.value = androidContext.getString(R.string.account_link_failed) }
            )
            _isLinkingAccount.value = false
        }
    }
}
