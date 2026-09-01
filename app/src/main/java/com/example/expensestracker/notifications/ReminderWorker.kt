package com.example.expensestracker.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensestracker.ExpensesTrackerApp
import com.example.expensestracker.domain.nextOccurrence
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Runs roughly once a day (see [ExpensesTrackerApp] scheduling). Checks every active recurring
 * template - personal, plus the current group's if any - that has a reminder configured, and
 * fires a notification on the one day its reminder window matches "today".
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ExpensesTrackerApp
        return try {
            val uid = app.authRepository.ensureSignedIn()
            val today = LocalDate.now()

            val personal = app.personalExpenseRepositoryFor(uid).getActiveRecurring()
            val groupId = app.settingsRepository.groupId.first()
            val group = groupId?.let { app.groupExpenseRepositoryFor(it).getActiveRecurring() } ?: emptyList()

            (personal + group).forEach { recurring ->
                val reminderDays = recurring.reminderDaysBefore ?: return@forEach
                val daysUntilDue = ChronoUnit.DAYS.between(today, recurring.nextOccurrence(today)).toInt()
                if (daysUntilDue == reminderDays) {
                    showReminderNotification(applicationContext, recurring, daysUntilDue)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
