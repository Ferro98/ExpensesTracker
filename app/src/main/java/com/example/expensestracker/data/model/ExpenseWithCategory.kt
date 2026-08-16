package com.example.expensestracker.data.model

import java.time.LocalDate

/** [Expense] flattened with its category's display fields, computed client-side from the live streams. */
data class ExpenseWithCategory(
    val id: String,
    val categoryId: String,
    val amount: Double,
    val currencyCode: String,
    val amountInBaseCurrency: Double,
    val date: LocalDate,
    val note: String?,
    val recurringExpenseId: String?,
    val paidByUid: String,
    val isShared: Boolean,
    val payerShare: Double,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColorHex: String
)

/** Per-category spend for the current month, against the viewing user's own personal budget. */
data class CategorySpending(
    val categoryId: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val monthlyBudget: Double?,
    val spent: Double
)
