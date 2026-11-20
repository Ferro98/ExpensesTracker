package com.example.expensestracker.data.repository

import com.example.expensestracker.data.model.Trip
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Trips for one scope - `users/{uid}` or `groups/{id}`. A flat `trips` collection like the others,
 * so the existing Firestore rules already cover it. Deleting a trip leaves the expenses that
 * pointed at it untouched: an id that matches no trip is simply read as "not part of a trip".
 */
class TripRepository(private val scopeRef: DocumentReference) {
    private val tripsRef get() = scopeRef.collection("trips")

    fun observeTrips(): Flow<List<Trip>> = tripsRef.observeAsFlow()

    suspend fun addTrip(name: String, icon: String, start: LocalDate, end: LocalDate, budget: Double, goalId: String?): String {
        val ref = tripsRef.document()
        ref.set(
            Trip(
                name = name,
                icon = icon,
                startDate = start.toString(),
                endDate = end.toString(),
                budget = budget,
                goalId = goalId,
                createdAt = Timestamp.now()
            )
        ).await()
        return ref.id
    }

    suspend fun updateTrip(tripId: String, name: String, icon: String, start: LocalDate, end: LocalDate, budget: Double, goalId: String?) {
        tripsRef.document(tripId).update(
            mapOf(
                "name" to name,
                "icon" to icon,
                "startDate" to start.toString(),
                "endDate" to end.toString(),
                "budget" to budget,
                "goalId" to (goalId ?: FieldValue.delete())
            )
        ).await()
    }

    suspend fun deleteTrip(tripId: String) {
        tripsRef.document(tripId).delete().await()
    }
}
