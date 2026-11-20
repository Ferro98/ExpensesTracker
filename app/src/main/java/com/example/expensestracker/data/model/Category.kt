package com.example.expensestracker.data.model

import com.google.firebase.firestore.DocumentId

data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    val icon: String = "",
    val colorHex: String = "",
    val sortOrder: Int = 0,
    /**
     * A need rather than a want (rent, bills, health, commuting). A property of the category, not
     * of each expense, so flipping it re-classifies all history at once, and a shared expense the
     * partner filed under their own category counts by *my* category it resolves to
     * (see CategoryResolver) - the same way budgets already do.
     */
    val essential: Boolean = false
)
