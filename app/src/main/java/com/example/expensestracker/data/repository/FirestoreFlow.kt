package com.example.expensestracker.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Live-observes a query's results as a Flow, backed by Firestore's own snapshot listener + offline
 * cache. A listener error (e.g. a permissions rule rejecting the query) is logged and reported as an
 * empty result rather than thrown - letting one broken data source crash the whole app is worse than
 * that screen showing nothing until the underlying issue (rules, connectivity) is resolved.
 */
inline fun <reified T> Query.observeAsFlow(): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.w("FirestoreFlow", "Query listener failed", error)
            trySend(emptyList())
            return@addSnapshotListener
        }
        trySend(snapshot?.toObjects(T::class.java) ?: emptyList())
    }
    awaitClose { registration.remove() }
}

/** Live-observes a single document as a Flow; null once the document doesn't exist or the listener errors. */
inline fun <reified T> DocumentReference.observeAsFlow(): Flow<T?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.w("FirestoreFlow", "Document listener failed", error)
            trySend(null)
            return@addSnapshotListener
        }
        trySend(snapshot?.toObject(T::class.java))
    }
    awaitClose { registration.remove() }
}
