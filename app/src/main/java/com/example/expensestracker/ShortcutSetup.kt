package com.example.expensestracker

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Action on the launcher shortcut's intent; MainActivity opens the add-expense sheet when it sees it. */
const val ACTION_NEW_EXPENSE = "com.example.expensestracker.action.NEW_EXPENSE"

/**
 * The "New expense" entry when long-pressing the app icon - logging an expense is by far the most
 * frequent thing done in the app, so it shouldn't need the app to be opened and a button found
 * first. Registered from code (not a static shortcuts.xml) so the intent targets whatever package
 * name the app was actually installed under, and re-registered on every start so the label follows
 * the device language.
 */
fun registerAppShortcuts(context: Context) {
    val shortcut = ShortcutInfoCompat.Builder(context, "new_expense")
        .setShortLabel(context.getString(R.string.new_expense_title))
        .setLongLabel(context.getString(R.string.new_expense_title))
        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_add))
        .setIntent(Intent(context, MainActivity::class.java).setAction(ACTION_NEW_EXPENSE))
        .build()
    ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut))
}
