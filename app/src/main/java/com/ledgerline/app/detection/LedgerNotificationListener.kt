package com.ledgerline.app.detection

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LedgerNotificationListener : NotificationListenerService() {
    @Inject lateinit var ingestor: NotificationIngestor

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        val extras = posted.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val combined = listOf(title, text, big).filter { it.isNotBlank() }.joinToString("\n")
        ingestor.offer(posted.packageName, combined)
    }
}
