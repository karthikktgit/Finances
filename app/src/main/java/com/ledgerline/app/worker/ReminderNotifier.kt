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
        ).forEach { (id, name) ->
            manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    suspend fun deliver(snapshot: Dashboard, preferences: UiPreferences) {
        if (!canPost()) return
        ensureChannels()
        if (preferences.reminderPayments.first()) {
            snapshot.upcoming.filter { it.date == snapshot.asOf.plusDays(1) && it.direction == CashFlowDirection.OUTFLOW }.forEachIndexed { index, item ->
                show(1000 + index, "payments", "Payment expected tomorrow", "${item.name} for ${MoneyFormat.format(item.amountMinor, snapshot.currency)} is expected tomorrow.")
            }
        }
        if (preferences.reminderSubscription.first()) {
            snapshot.upcoming.filter { it.date == snapshot.asOf.plusDays(1) && it.kind == ScheduledKind.SUBSCRIPTION }.forEachIndexed { index, item ->
                show(2000 + index, "subscription", "Subscription expected tomorrow", "${item.name} for ${MoneyFormat.format(item.amountMinor, snapshot.currency)} is expected tomorrow.")
            }
        }
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
}
