package com.ledgerline.app.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Reads new incoming texts as they arrive. Android 15 and later can hide notification text from Ledgerline, so this
 * is the reliable path for bank texts. It only sees new messages; it never reads the message history. Whether a text
 * is kept at all is decided by the same strict bank-alert check used for notifications, and only when the person
 * has turned on "Read new texts directly".
 */
@AndroidEntryPoint
class SmsCaptureReceiver : BroadcastReceiver() {
    @Inject lateinit var ingestor: NotificationIngestor

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // A long text arrives in several parts from the same sender; join them back into one message.
        parts.filterNotNull()
            .groupBy { it.displayOriginatingAddress.orEmpty() }
            .forEach { (sender, pieces) ->
                ingestor.offerSms(sender, pieces.joinToString("") { it.displayMessageBody.orEmpty() })
            }
    }
}
