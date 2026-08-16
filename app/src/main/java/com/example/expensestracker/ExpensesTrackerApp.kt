package com.example.expensestracker

import android.app.Application
import com.example.expensestracker.data.remote.CurrencyRateService
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.GroupRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ExpensesTrackerApp : Application() {
    val settingsRepository by lazy { SettingsRepository(this) }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    val groupRepository by lazy { GroupRepository(firestore, auth) }

    /** Cheap to construct (no connection handle to manage) - a fresh instance per call is fine. */
    fun expenseRepositoryFor(groupId: String): ExpenseRepository =
        ExpenseRepository(firestore, groupId, CurrencyRateService())
}
