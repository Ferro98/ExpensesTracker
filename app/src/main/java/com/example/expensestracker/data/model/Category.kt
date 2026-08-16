package com.example.expensestracker.data.model

import com.google.firebase.firestore.DocumentId

data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    val icon: String = "",
    val colorHex: String = "",
    val sortOrder: Int = 0
)
