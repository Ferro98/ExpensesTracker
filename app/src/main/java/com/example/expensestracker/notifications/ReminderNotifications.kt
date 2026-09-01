package com.example.expensestracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.expensestracker.R
import com.example.expensestracker.data.model.RecurringExpense
import com.example.expensestracker.util.formatMoney

private const val CHANNEL_ID = "recurring_reminders"

fun ensureReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.reminder_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = context.getString(R.string.reminder_channel_description)
    }
    manager.createNotificationChannel(channel)
}

/** No-ops silently if the user hasn't granted POST_NOTIFICATIONS (Android 13+). */
fun showReminderNotification(context: Context, recurring: RecurringExpense, daysUntilDue: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    ensureReminderChannel(context)

    val amount = formatMoney(recurring.amount, recurring.currencyCode)
    val body = if (daysUntilDue == 0) {
        context.getString(R.string.reminder_notification_body_today, amount)
    } else {
        context.resources.getQuantityString(R.plurals.reminder_notification_body_days, daysUntilDue, daysUntilDue, amount)
    }

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_reminder)
        .setContentTitle(context.getString(R.string.reminder_notification_title, recurring.categoryName))
        .setContentText(body)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(recurring.id.hashCode(), notification)
}
