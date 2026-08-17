package com.example.expensestracker

import android.app.Application
import com.example.expensestracker.data.remote.CurrencyRateService
import com.example.expensestracker.data.repository.AuthRepository
import com.example.expensestracker.data.repository.ExpenseRepository
import com.example.expensestracker.data.repository.GroupRepository
import com.example.expensestracker.data.repository.PersonalDataRepository
import com.example.expensestracker.data.repository.SettlementRepository
import com.example.expensestracker.data.settings.SettingsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ExpensesTrackerApp : Application() {
    val settingsRepository by lazy { SettingsRepository(this) }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    val authRepository by lazy { AuthRepository(auth) }
    val groupRepository by lazy { GroupRepository(firestore) }

    // Cheap to construct (no connection handle to manage) - a fresh instance per call is fine.
    fun personalDataRepositoryFor(uid: String): PersonalDataRepository =
        PersonalDataRepository(this, firestore, uid, CurrencyRateService())

    fun personalExpenseRepositoryFor(uid: String): ExpenseRepository =
        ExpenseRepository(firestore.collection("users").document(uid))

    fun groupExpenseRepositoryFor(groupId: String): ExpenseRepository =
        ExpenseRepository(firestore.collection("groups").document(groupId))

    fun settlementRepositoryFor(groupId: String): SettlementRepository =
        SettlementRepository(firestore, groupId)
}
