package com.example.expensestracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val MONTHLY_BUDGET = doublePreferencesKey("monthly_budget")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val monthlyBudget: Flow<Double?> = context.dataStore.data.map { prefs ->
        prefs[Keys.MONTHLY_BUDGET]
    }

    suspend fun setMonthlyBudget(amount: Double?) {
        context.dataStore.edit { prefs ->
            if (amount == null) {
                prefs.remove(Keys.MONTHLY_BUDGET)
            } else {
                prefs[Keys.MONTHLY_BUDGET] = amount
            }
        }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }
}
