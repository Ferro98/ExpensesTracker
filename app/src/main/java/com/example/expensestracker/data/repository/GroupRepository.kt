package com.example.expensestracker.data.repository

import com.example.expensestracker.data.model.DefaultGroupData
import com.example.expensestracker.data.model.Group
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class GroupRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val groupsRef get() = firestore.collection("groups")

    suspend fun ensureSignedIn(): String =
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid

    fun observeGroup(groupId: String): Flow<Group?> = groupsRef.document(groupId).observeAsFlow()

    suspend fun createGroup(displayName: String): Result<Group> = runCatching {
        val uid = ensureSignedIn()
        val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        lateinit var ref: com.google.firebase.firestore.DocumentReference
        do {
            val code = (1..6).map { codeChars.random() }.joinToString("")
            ref = groupsRef.document(code)
        } while (ref.get().await().exists())

        val group = Group(
            id = ref.id,
            createdAt = Timestamp.now(),
            createdByUid = uid,
            memberUids = listOf(uid),
            memberNames = mapOf(uid to displayName),
            baseCurrency = DefaultGroupData.BASE_CURRENCY
        )
        ref.set(group).await()
        seedDefaults(ref.id)
        group
    }

    suspend fun joinGroup(code: String, displayName: String): Result<Group> = runCatching {
        val uid = ensureSignedIn()
        val ref = groupsRef.document(code.trim().uppercase())
        firestore.runTransaction { txn ->
            val snapshot = txn.get(ref)
            if (!snapshot.exists()) error("No group found for code ${code.uppercase()}")
            val members = (snapshot.get("memberUids") as? List<*>).orEmpty()
            if (uid !in members) {
                if (members.size >= 2) error("This group already has two members")
                txn.update(
                    ref,
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(uid),
                        "memberNames.$uid" to displayName
                    )
                )
            }
        }.await()
        ref.get().await().toObject(Group::class.java)
            ?: error("Group ${code.uppercase()} disappeared after joining")
    }

    private suspend fun seedDefaults(groupId: String) {
        val groupRef = groupsRef.document(groupId)
        val batch = firestore.batch()
        DefaultGroupData.categories.forEach { category ->
            batch.set(groupRef.collection("categories").document(), category)
        }
        DefaultGroupData.currencyRates.forEach { rate ->
            batch.set(groupRef.collection("currencyRates").document(rate.code), rate)
        }
        batch.commit().await()
    }
}
