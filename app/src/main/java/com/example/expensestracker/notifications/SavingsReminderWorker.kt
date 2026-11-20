package com.example.expensestracker.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expensestracker.ExpensesTrackerApp
import com.example.expensestracker.data.repository.SavingsRepository
import com.example.expensestracker.domain.SavingsMath
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

/** The reminder only goes out in the first days of a month - it's about "this month's" deposit, not a nag all month long. */
private const val REMINDER_WINDOW_DAYS = 5

/**
 * Runs about once a day (see [ExpensesTrackerApp]) but only does anything in the first days of the
 * month, and at most once per month: if any savings pot has a planned monthly deposit of mine that I
 * haven't answered or fulfilled yet, posts one notification that opens the Savings screen. Anyone
 * who creates a pot mid-month doesn't get a late reminder; the prompt is on the Savings screen
 * regardless. Local only - no server involved.
 */
class SavingsReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        if (today.dayOfMonth > REMINDER_WINDOW_DAYS) return Result.success()
        val app = applicationContext as ExpensesTrackerApp
        val month = YearMonth.from(today).toString()
        return try {
            if (app.settingsRepository.lastSavingsReminderMonth.first() == month) return Result.success()

            val uid = app.authRepository.ensureSignedIn()
            val groupId = app.settingsRepository.groupId.first()
            val repositories = listOfNotNull(
                app.personalSavingsRepositoryFor(uid),
                groupId?.let { app.groupSavingsRepositoryFor(it) }
            )
            val due = repositories.flatMap { dueIn(it, uid, today) }

            // Only mark the month done if the notification really went out; with the permission
            // off it didn't, and the next run (or granting it later) should still be able to.
            if (showSavingsCheckInNotification(applicationContext, due)) {
                app.settingsRepository.setLastSavingsReminderMonth(month)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun dueIn(repository: SavingsRepository, uid: String, today: LocalDate): List<Pair<String, Double>> {
        val entries = repository.getEntries()
        return repository.getGoals().mapNotNull { goal ->
            SavingsMath.checkInDue(goal, entries.filter { it.goalId == goal.id }, uid, today)?.let { goal.name to it }
        }
    }
}
