package com.example.expensestracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "recurring_expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("categoryId")]
)
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val amount: Double,
    val currencyCode: String,
    val note: String? = null,
    val frequency: RecurrenceFrequency,
    // 1-31 for MONTHLY (clamped to last day of shorter months), 1(Mon)-7(Sun) for WEEKLY
    val dayOfPeriod: Int,
    val startDate: LocalDate,
    val active: Boolean = true,
    @ColumnInfo(defaultValue = "NULL")
    val lastGeneratedDate: LocalDate? = null
)
