package com.example.expensestracker.data.repository

import com.example.expensestracker.data.model.SavingsEntry
import com.example.expensestracker.data.model.SavingsEntryKind
import com.example.expensestracker.data.model.SavingsGoal
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Savings goals + their movements for one scope - `users/{uid}` (personal) or `groups/{id}`
 * (shared). Two flat collections rather than entries nested under each goal: the Firestore rules
 * only cover `…/{collection}/{doc}`, and a deeper path would be denied.
 *
 * Fields shared between two people (a group goal's name, plans, check-ins) are changed with
 * targeted updates, never by rewriting the whole document, so one member's edit can't wipe out the
 * other's plan.
 */
class SavingsRepository(private val scopeRef: DocumentReference) {
    private val goalsRef get() = scopeRef.collection("savingsGoals")
    private val entriesRef get() = scopeRef.collection("savingsEntries")

    fun observeGoals(): Flow<List<SavingsGoal>> = goalsRef.observeAsFlow()
    fun observeEntries(): Flow<List<SavingsEntry>> = entriesRef.observeAsFlow()

    // One-shot reads for background work (the start-of-month reminder), which has no UI to keep live.
    suspend fun getGoals(): List<SavingsGoal> = goalsRef.get().await().toObjects(SavingsGoal::class.java)
    suspend fun getEntries(): List<SavingsEntry> = entriesRef.get().await().toObjects(SavingsEntry::class.java)

    suspend fun addGoal(
        name: String,
        icon: String,
        targetAmount: Double,
        targetDate: LocalDate?,
        ownerUid: String,
        ownerMonthlyPlan: Double?
    ): String {
        val ref = goalsRef.document()
        ref.set(
            SavingsGoal(
                name = name,
                icon = icon,
                targetAmount = targetAmount,
                targetDate = targetDate?.toString(),
                monthlyPlans = if (ownerMonthlyPlan != null && ownerMonthlyPlan > 0) mapOf(ownerUid to ownerMonthlyPlan) else emptyMap(),
                createdAt = Timestamp.now()
            )
        ).await()
        return ref.id
    }

    /** Only the fields everyone shares; each person's own plan goes through [setMonthlyPlan]. */
    suspend fun updateGoalDetails(goalId: String, name: String, icon: String, targetAmount: Double, targetDate: LocalDate?) {
        goalsRef.document(goalId).update(
            mapOf(
                "name" to name,
                "icon" to icon,
                "targetAmount" to targetAmount,
                "targetDate" to (targetDate?.toString() ?: FieldValue.delete())
            )
        ).await()
    }

    suspend fun setMonthlyPlan(goalId: String, uid: String, amount: Double?) {
        goalsRef.document(goalId).update(
            "monthlyPlans.$uid",
            if (amount != null && amount > 0) amount else FieldValue.delete()
        ).await()
    }

    suspend fun markCheckIn(goalId: String, uid: String, month: String) {
        goalsRef.document(goalId).update("lastCheckIn.$uid", month).await()
    }

    suspend fun setArchived(goalId: String, archived: Boolean) {
        goalsRef.document(goalId).update("archived", archived).await()
    }

    /** Removes the goal and every movement in it in one batch, so nothing is left pointing at a goal that's gone. */
    suspend fun deleteGoal(goalId: String) {
        val entries = entriesRef.whereEqualTo("goalId", goalId).get().await()
        val batch = scopeRef.firestore.batch()
        entries.documents.forEach { batch.delete(it.reference) }
        batch.delete(goalsRef.document(goalId))
        batch.commit().await()
    }

    suspend fun addEntry(
        goalId: String,
        kind: SavingsEntryKind,
        amount: Double,
        currencyCode: String,
        amountInBaseCurrency: Double,
        date: LocalDate,
        note: String?,
        byUid: String,
        recordedByUid: String? = null
    ): String {
        val ref = entriesRef.document()
        ref.set(
            SavingsEntry(
                goalId = goalId,
                kind = kind,
                amount = amount,
                currencyCode = currencyCode,
                amountInBaseCurrency = amountInBaseCurrency,
                date = date.toString(),
                note = note,
                byUid = byUid,
                recordedByUid = recordedByUid,
                createdAt = Timestamp.now()
            )
        ).await()
        return ref.id
    }

    suspend fun deleteEntry(entryId: String) {
        entriesRef.document(entryId).delete().await()
    }
}
