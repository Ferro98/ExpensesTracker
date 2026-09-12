package com.example.expensestracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.expensestracker.data.model.DefaultUserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Per-device settings only: which group/person this device is, personal (not shared)
 * budget targets, and UI theme. Everything else lives in Firestore ([GroupRepository]/[ExpenseRepository]).
 */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val MY_MONTHLY_BUDGET = doublePreferencesKey("my_monthly_budget")
        val MY_CATEGORY_BUDGETS = stringPreferencesKey("my_category_budgets")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GROUP_ID = stringPreferencesKey("group_id")
        val MY_DISPLAY_NAME = stringPreferencesKey("my_display_name")
        val DEFAULT_SHARED_EXPENSE = booleanPreferencesKey("default_shared_expense")
        val DEFAULT_SHARED_RECURRING = booleanPreferencesKey("default_shared_recurring")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    }

    val myMonthlyBudget: Flow<Double?> = context.dataStore.data.map { prefs -> prefs[Keys.MY_MONTHLY_BUDGET] }

    suspend fun setMyMonthlyBudget(amount: Double?) {
        context.dataStore.edit { prefs ->
            if (amount == null) prefs.remove(Keys.MY_MONTHLY_BUDGET) else prefs[Keys.MY_MONTHLY_BUDGET] = amount
        }
    }

    val myCategoryBudgets: Flow<Map<String, Double>> = context.dataStore.data.map { prefs ->
        decodeCategoryBudgets(prefs[Keys.MY_CATEGORY_BUDGETS])
    }

    suspend fun setCategoryBudget(categoryId: String, amount: Double?) {
        context.dataStore.edit { prefs ->
            val current = decodeCategoryBudgets(prefs[Keys.MY_CATEGORY_BUDGETS]).toMutableMap()
            if (amount == null) current.remove(categoryId) else current[categoryId] = amount
            prefs[Keys.MY_CATEGORY_BUDGETS] = encodeCategoryBudgets(current)
        }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }

    val groupId: Flow<String?> = context.dataStore.data.map { prefs -> prefs[Keys.GROUP_ID] }
    val myDisplayName: Flow<String?> = context.dataStore.data.map { prefs -> prefs[Keys.MY_DISPLAY_NAME] }

    /** Prefilled state of the "shared" switch when starting a brand new expense/recurring template - editing an existing one always keeps its own stored value instead. */
    val defaultSharedForExpense: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.DEFAULT_SHARED_EXPENSE] ?: false }
    val defaultSharedForRecurring: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[Keys.DEFAULT_SHARED_RECURRING] ?: false }

    suspend fun setDefaultSharedForExpense(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.DEFAULT_SHARED_EXPENSE] = value }
    }

    suspend fun setDefaultSharedForRecurring(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.DEFAULT_SHARED_RECURRING] = value }
    }

    /** Prefilled currency for every amount-entry screen (expense, recurring, budgets, settlements) - falls back to the base currency. */
    val defaultCurrency: Flow<String> = context.dataStore.data.map { prefs -> prefs[Keys.DEFAULT_CURRENCY] ?: DefaultUserData.BASE_CURRENCY }

    suspend fun setDefaultCurrency(code: String) {
        context.dataStore.edit { prefs -> prefs[Keys.DEFAULT_CURRENCY] = code }
    }

    suspend fun saveGroup(groupId: String, displayName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GROUP_ID] = groupId
            prefs[Keys.MY_DISPLAY_NAME] = displayName
        }
    }

    suspend fun clearGroup() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.GROUP_ID)
        }
    }

    private fun encodeCategoryBudgets(map: Map<String, Double>): String {
        val json = JSONObject()
        map.forEach { (id, amount) -> json.put(id, amount) }
        return json.toString()
    }

    private fun decodeCategoryBudgets(json: String?): Map<String, Double> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getDouble(it) }
        }.getOrDefault(emptyMap())
    }
}
