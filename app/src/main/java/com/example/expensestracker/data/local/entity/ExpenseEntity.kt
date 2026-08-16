package com.example.expensestracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecurringExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringExpenseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [androidx.room.Index("categoryId"), androidx.room.Index("recurringExpenseId")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val amount: Double,
    val currencyCode: String,
    val amountInBaseCurrency: Double,
    val date: LocalDate,
    val note: String? = null,
    val recurringExpenseId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
