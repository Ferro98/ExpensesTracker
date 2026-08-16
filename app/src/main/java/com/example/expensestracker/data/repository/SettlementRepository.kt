package com.example.expensestracker.data.repository

import com.example.expensestracker.data.model.Settlement
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** Settlements only ever make sense within a group - always `groups/{groupId}/settlements`. */
class SettlementRepository(
    firestore: FirebaseFirestore,
    groupId: String
) {
    private val settlementsRef = firestore.collection("groups").document(groupId).collection("settlements")

    fun observeSettlements(): Flow<List<Settlement>> = settlementsRef.observeAsFlow()

    suspend fun addSettlement(fromUid: String, toUid: String, amount: Double, currencyCode: String, amountInBaseCurrency: Double, date: LocalDate, note: String?) {
        settlementsRef.document().set(
            Settlement(
                fromUid = fromUid,
                toUid = toUid,
                amount = amount,
                currencyCode = currencyCode,
                amountInBaseCurrency = amountInBaseCurrency,
                date = date.toString(),
                note = note,
                createdAt = Timestamp.now()
            )
        ).await()
    }

    suspend fun deleteSettlement(settlementId: String) {
        settlementsRef.document(settlementId).delete().await()
    }
}
