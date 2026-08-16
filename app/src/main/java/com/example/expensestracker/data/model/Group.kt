package com.example.expensestracker.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Group(
    @DocumentId val id: String = "",
    val createdAt: Timestamp? = null,
    val createdByUid: String = "",
    val memberUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val baseCurrency: String = "EUR"
) {
    fun otherMemberUid(myUid: String): String? = memberUids.firstOrNull { it != myUid }
    fun nameOf(uid: String): String = memberNames[uid] ?: "Partner"
}
