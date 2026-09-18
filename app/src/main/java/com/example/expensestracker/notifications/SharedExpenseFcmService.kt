package com.example.expensestracker.notifications

import com.example.expensestracker.ExpensesTrackerApp
import com.example.expensestracker.R
import com.example.expensestracker.data.model.DefaultUserData
import com.example.expensestracker.util.formatMoney
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the "someone added a shared expense" push sent by the Cloud Function in functions/
 * (see docs/UX_REDESIGN_PLAN.md 8) and re-registers this device's token whenever FCM rotates it.
 *
 * The Cloud Function sends raw fields (payer name, category, amount, currency), not a finished
 * title/body: it has no reliable way to know the recipient's device locale, so localizing there
 * would mean guessing. Building the text here means it always comes out in the device's own
 * language, the same as everything else in the app.
 *
 * It's also a data-only message (no "notification" key) specifically so [onMessageReceived] fires
 * every time - a notification payload is only delivered here while the app is foregrounded; in
 * the background the system tray would show it automatically, bypassing the app's own icon,
 * channel and tap target entirely.
 */
class SharedExpenseFcmService : FirebaseMessagingService() {
    // No ViewModel/lifecycle to hang this off - the service instance itself outlives each
    // individual call, so a SupervisorJob tied to onDestroy is the right scope for it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val app = application as ExpensesTrackerApp
        scope.launch {
            try {
                val uid = app.authRepository.ensureSignedIn()
                app.personalDataRepositoryFor(uid).registerFcmToken(token)
            } catch (e: Exception) {
                // Best-effort: MainActivity re-registers the current token on every sign-in too,
                // so a failure here isn't the only chance to record it.
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payerName = message.data["payerName"] ?: return
        val rawCategoryName = message.data["categoryName"] ?: ""
        val categoryIcon = message.data["categoryIcon"] ?: ""
        val amount = message.data["amount"]?.toDoubleOrNull() ?: return
        val currencyCode = message.data["currencyCode"] ?: "EUR"

        // Same lookup util/CategoryDisplay.kt's localizedCategoryName does, just without the
        // @Composable/stringResource wrapper that only works inside Compose - a plain
        // getString off the same table works identically outside it.
        val categoryName = DefaultUserData.knownDefaultNameVariants[rawCategoryName]
            ?.let { getString(it) }
            ?: rawCategoryName

        val title = getString(R.string.shared_expense_notification_title, payerName)
        val body = getString(
            R.string.shared_expense_notification_body,
            "$categoryIcon $categoryName".trim(),
            formatMoney(amount, currencyCode)
        )
        showSharedExpenseNotification(applicationContext, title, body)
    }
}
