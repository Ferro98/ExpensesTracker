package com.example.expensestracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Snapshot of who's currently signed in - reactive so the UI picks up an account switch (e.g. [AuthRepository.linkWithGoogle]'s restore path) without a manual refresh. */
data class AuthState(val uid: String, val isAnonymous: Boolean, val email: String?)

/** Firebase Anonymous Auth persists identity locally on its own - no DataStore copy needed. */
class AuthRepository(private val auth: FirebaseAuth) {
    suspend fun ensureSignedIn(): String =
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid

    fun observeAuthState(): Flow<AuthState?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            trySend(user?.let { AuthState(it.uid, it.isAnonymous, it.email) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Upgrades the current anonymous session to a permanent Google-backed one, so the same data
     * survives a reinstall or a new device - just sign in with the same Google account again.
     *
     * If this Google account was already linked to a *different* Firebase user (the expected case
     * right after a reinstall, where a fresh anonymous session was created before the user got a
     * chance to sign back in), falls back to signing into that existing account instead of failing
     * with a collision - which is exactly the "restore my old data" path.
     */
    suspend fun linkWithGoogle(idToken: String): Result<String> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = checkNotNull(auth.currentUser) { "No signed-in user to link" }
        val user = try {
            currentUser.linkWithCredential(credential).await().user
        } catch (e: FirebaseAuthUserCollisionException) {
            auth.signInWithCredential(credential).await().user
        }
        checkNotNull(user) { "Sign-in returned no user" }.uid
    }
}
