package com.ledgerline.app.detection.capture

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
import com.ledgerline.app.MainActivity
import com.ledgerline.app.R
import com.ledgerline.app.domain.model.CapturedSpend
import com.ledgerline.app.finance.money.MoneyFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Tells the person a spend was captured or added. */
interface CaptureAlerts {
    fun captured(capture: CapturedSpend)

    fun added(capture: CapturedSpend)
}

/** "Tap to add" notices for captured spends. They open the inbox; nothing is added from the notice itself. */
@Singleton
class CaptureNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : CaptureAlerts {
    override fun captured(capture: CapturedSpend) {
        val what = describe(capture)
        show(capture, "$what · tap to add", "Captured from ${capture.sourceLabel}. It is not in your balances until you add it.")
    }

    override fun added(capture: CapturedSpend) {
        show(capture, "Added: ${describe(capture)}", "Recorded from ${capture.sourceLabel}. Open Ledgerline to change the category or undo.")
    }

    private fun describe(capture: CapturedSpend): String {
        val amount = MoneyFormat.format(capture.amountMinor, capture.currency)
        val verb = if (capture.direction == MoneyDirection.OUT) "Spent" else "Received"
        val where = capture.merchant.takeIf { it.isNotBlank() }?.let { if (capture.direction == MoneyDirection.OUT) " at $it" else " from $it" }.orEmpty()
        return "$verb $amount$where"
    }

    private fun show(capture: CapturedSpend, title: String, body: String) {
        if (!canPost()) return
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_capture)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openInbox())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP)
            .build()
        val manager = NotificationManagerCompat.from(context)
        manager.notify(capture.id.hashCode(), notification)
        // Our own bundle summary. Without it Android bundles the notices itself, and tapping that bundle opens
        // the app's home screen instead of the inbox.
        val summary = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_capture)
            .setContentTitle("Captured spends")
            .setContentText("Tap to add them")
            .setContentIntent(openInbox())
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }

    private fun openInbox(): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(OPEN_EXTRA, OPEN_CAPTURES)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Captured spends", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL = "captures"
        private const val GROUP = "ledgerline.captures"
        private const val SUMMARY_ID = 0x6c65
        const val OPEN_EXTRA = "ledgerline.open"
        const val OPEN_CAPTURES = "captures"
    }
}
