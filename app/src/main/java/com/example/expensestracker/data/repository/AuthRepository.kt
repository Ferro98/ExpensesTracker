package com.example.expensestracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Firebase Anonymous Auth persists identity locally on its own - no DataStore copy needed. */
class AuthRepository(private val auth: FirebaseAuth) {
    suspend fun ensureSignedIn(): String =
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
}
