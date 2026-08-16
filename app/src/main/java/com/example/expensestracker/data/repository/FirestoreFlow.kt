package com.example.expensestracker.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Live-observes a query's results as a Flow, backed by Firestore's own snapshot listener + offline cache. */
inline fun <reified T> Query.observeAsFlow(): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        trySend(snapshot?.toObjects(T::class.java) ?: emptyList())
    }
    awaitClose { registration.remove() }
}

/** Live-observes a single document as a Flow; null once the document doesn't exist. */
inline fun <reified T> DocumentReference.observeAsFlow(): Flow<T?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        trySend(snapshot?.toObject(T::class.java))
    }
    awaitClose { registration.remove() }
}
