package com.example.expensestracker.data.model

/** Per-category spend for the current month, against the viewing user's own personal budget. */
data class CategorySpending(
    val categoryId: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val monthlyBudget: Double?,
    val spent: Double
)
