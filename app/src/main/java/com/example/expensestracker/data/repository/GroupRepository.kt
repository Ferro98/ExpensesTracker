package com.example.expensestracker.data.repository

import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.data.model.Group
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** Thrown by [GroupRepository.joinGroup] for specific, user-facing failure reasons - kept free of
 *  hardcoded English text so the ViewModel layer can map each case to a localized string. */
sealed class JoinGroupException : Exception() {
    class NotFound : JoinGroupException()
    class Full : JoinGroupException()
    class Vanished : JoinGroupException()
}

class GroupRepository(private val firestore: FirebaseFirestore) {
    private val groupsRef get() = firestore.collection("groups")

    fun observeGroup(groupId: String): Flow<Group?> = groupsRef.document(groupId).observeAsFlow()

    suspend fun createGroup(uid: String, displayName: String): Result<Group> = runCatching {
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
            baseCurrency = DefaultUserData.BASE_CURRENCY
        )
        ref.set(group).await()
        group
    }

    suspend fun joinGroup(code: String, uid: String, displayName: String): Result<Group> = runCatching {
        val ref = groupsRef.document(code.trim().uppercase())
        firestore.runTransaction { txn ->
            val snapshot = txn.get(ref)
            if (!snapshot.exists()) throw JoinGroupException.NotFound()
            val members = (snapshot.get("memberUids") as? List<*>).orEmpty()
            if (uid !in members) {
                if (members.size >= 2) throw JoinGroupException.Full()
                txn.update(
                    ref,
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(uid),
                        "memberNames.$uid" to displayName
                    )
                )
            }
        }.await()
        ref.get().await().toObject(Group::class.java) ?: throw JoinGroupException.Vanished()
    }

    suspend fun leaveGroup(groupId: String, uid: String): Result<Unit> = runCatching {
        groupsRef.document(groupId).update(
            mapOf(
                "memberUids" to FieldValue.arrayRemove(uid),
                "memberNames.$uid" to FieldValue.delete()
            )
        ).await()
    }
}
