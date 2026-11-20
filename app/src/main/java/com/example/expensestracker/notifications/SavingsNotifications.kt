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
import com.example.expensestracker.util.formatMoney

private const val CHANNEL_ID = "savings_checkin"
private const val NOTIFICATION_ID = 3001

/** Intent extra MainActivity looks for to open straight onto the Savings screen. */
const val EXTRA_OPEN_SAVINGS = "open_savings"

private fun ensureSavingsChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.savings_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = context.getString(R.string.savings_channel_description) }
    manager.createNotificationChannel(channel)
}

/**
 * "Did you put this month's planned savings in?" - one notification however many pots are due, whose
 * tap opens the Savings screen where each can be answered. Returns whether it was actually posted:
 * without the notification permission nothing is shown, and the caller must not mark the month as
 * reminded or the reminder would be lost for good.
 */
fun showSavingsCheckInNotification(context: Context, due: List<Pair<String, Double>>): Boolean {
    if (due.isEmpty()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    ensureSavingsChannel(context)

    val body = if (due.size == 1) {
        context.getString(R.string.savings_checkin_text, due.first().first, formatMoney(due.first().second))
    } else {
        context.getString(R.string.savings_reminder_body_many, due.size, formatMoney(due.sumOf { it.second }))
    }

    val openIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(EXTRA_OPEN_SAVINGS, true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_reminder)
        .setContentTitle(context.getString(R.string.title_savings))
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    return true
}
