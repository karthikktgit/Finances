package com.ledgerline.app.detection

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LedgerNotificationListener : NotificationListenerService() {
    @Inject lateinit var ingestor: NotificationIngestor

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (posted.packageName == packageName) return
        val notification = posted.notification
        // Group summaries repeat the child notifications, and ongoing ones are players or progress bars.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (posted.isOngoing) return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
        // The expanded text usually contains the short text, so prefer it to avoid reading the amount twice.
        val body = when {
            big.isNotBlank() -> big
            text.isNotBlank() -> text
            else -> lines
        }
        if (com.ledgerline.app.detection.capture.CaptureSources.isHiddenByAndroid(title, body)) {
            ingestor.noteHidden(posted.packageName)
            return
        }
        if (com.ledgerline.app.BuildConfig.DEBUG) android.util.Log.d("LedgerCapture", "posted by ${posted.packageName}")
        ingestor.offer(posted.packageName, title, body)
    }
}
