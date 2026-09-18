package com.example.expensestracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.expensestracker.MainActivity
import com.example.expensestracker.R

private const val CHANNEL_ID = "shared_expenses"
/** One rolling notification id, not one per expense (contrast ReminderNotifications' per-item ids) - a burst of expenses added while offline collapses into "N new shared expenses" rather than N separate lines. */
private const val NOTIFICATION_ID = 2001

/** Set by MainActivity when launched from this notification, so it knows to jump to the Group screen instead of Home. */
const val EXTRA_OPEN_GROUP = "open_group"

fun ensureSharedExpenseChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.shared_expense_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = context.getString(R.string.shared_expense_channel_description)
    }
    manager.createNotificationChannel(channel)
}

/** No-ops silently if the user hasn't granted POST_NOTIFICATIONS (Android 13+). */
fun showSharedExpenseNotification(context: Context, title: String, body: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    ensureSharedExpenseChannel(context)

    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_GROUP, true)
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_reminder)
        .setContentTitle(title)
        .setContentText(body)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
}
