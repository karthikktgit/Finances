package com.ledgerline.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Dashboard
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.WarningSeverity
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.preferences.UiPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            "payments" to "Upcoming payments",
            "buffer" to "Balance buffer",
            "budget" to "Budgets",
            "anomaly" to "Unusual spending",
            "subscription" to "Subscriptions",
            "today" to "Today's payments",
            "cycle" to "New cycle and statements",
        ).forEach { (id, name) ->
            manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    suspend fun deliver(snapshot: Dashboard, preferences: UiPreferences) {
        if (!canPost()) return
        ensureChannels()
        // Payments due tomorrow (bills and subscriptions alike) come in the daily reminder at your chosen time.
        if (preferences.reminderBuffer.first()) {
            snapshot.warnings.filter { it.severity == WarningSeverity.HIGH || it.severity == WarningSeverity.CRITICAL }.take(2).forEachIndexed { index, warning ->
                show(3000 + index, "buffer", warning.title, warning.detail)
            }
        }
        if (preferences.reminderBudget.first()) {
            snapshot.budgets.filter { it.remainingMinor in 0..5_000 }.take(2).forEachIndexed { index, budget ->
                show(4000 + index, "budget", budget.budget.name, "${MoneyFormat.format(budget.remainingMinor, snapshot.currency)} left in this budget.")
            }
        }
    }

    /** Today's (or tomorrow's) payments, one per line. Tapping it opens the app. */
    fun today(title: String, lines: List<String>, id: Int = 7000) {
        if (!canPost()) return
        ensureChannels()
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            android.app.PendingIntent.getActivity(context, 7000, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val style = NotificationCompat.InboxStyle().also { inbox -> lines.take(6).forEach(inbox::addLine) }
        val notification = NotificationCompat.Builder(context, "today")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setAutoCancel(true)
            .apply { open?.let(::setContentIntent) }
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** The start-of-cycle reminder: download every statement and review it. Tapping opens the new-cycle checklist. */
    fun newCycle(title: String, body: String) {
        if (!canPost()) return
        ensureChannels()
        val open = android.content.Intent(context, com.ledgerline.app.MainActivity::class.java).apply {
            putExtra(com.ledgerline.app.detection.capture.CaptureNotifier.OPEN_EXTRA, OPEN_CYCLE)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = android.app.PendingIntent.getActivity(context, 7100, open, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, "cycle")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(7100, notification)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun show(id: Int, channel: String, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val OPEN_CYCLE = "cycle"
    }
}
