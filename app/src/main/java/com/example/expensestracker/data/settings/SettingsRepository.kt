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
        val MY_UID = stringPreferencesKey("my_uid")
        val MY_DISPLAY_NAME = stringPreferencesKey("my_display_name")
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
    val myUid: Flow<String?> = context.dataStore.data.map { prefs -> prefs[Keys.MY_UID] }
    val myDisplayName: Flow<String?> = context.dataStore.data.map { prefs -> prefs[Keys.MY_DISPLAY_NAME] }

    suspend fun saveGroup(groupId: String, uid: String, displayName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GROUP_ID] = groupId
            prefs[Keys.MY_UID] = uid
            prefs[Keys.MY_DISPLAY_NAME] = displayName
        }
    }

    suspend fun clearGroup() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.GROUP_ID)
            prefs.remove(Keys.MY_UID)
            prefs.remove(Keys.MY_DISPLAY_NAME)
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
