package com.example.expensestracker.data.model

import com.google.firebase.firestore.DocumentId
import java.time.LocalDate

data class RecurringExpense(
    @DocumentId val id: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val categoryIcon: String = "",
    val categoryColorHex: String = "",
    val amount: Double = 0.0,
    val currencyCode: String = "EUR",
    val note: String? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    // 1-31 for MONTHLY (clamped to last day of shorter months), 1(Mon)-7(Sun) for WEEKLY
    val dayOfPeriod: Int = 1,
    val startDate: String = "",
    val active: Boolean = true,
    val lastGeneratedDate: String? = null,
    val paidByUid: String = "",
    val isShared: Boolean = false,
    val payerShare: Double = 0.5
) {
    val localStartDate: LocalDate get() = LocalDate.parse(startDate)
    val localLastGeneratedDate: LocalDate? get() = lastGeneratedDate?.let { LocalDate.parse(it) }
}
