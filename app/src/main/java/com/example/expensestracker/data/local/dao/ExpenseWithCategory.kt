package com.example.expensestracker.data.local.dao

import java.time.LocalDate

data class ExpenseWithCategory(
    val id: Long,
    val categoryId: Long,
    val amount: Double,
    val currencyCode: String,
    val amountInBaseCurrency: Double,
    val date: LocalDate,
    val note: String?,
    val recurringExpenseId: Long?,
    val createdAt: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColorHex: String
)

data class CategorySpending(
    val categoryId: Long,
    val name: String,
    val icon: String,
    val colorHex: String,
    val monthlyBudget: Double?,
    val spent: Double
)
