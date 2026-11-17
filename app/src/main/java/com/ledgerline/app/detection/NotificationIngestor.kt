package com.ledgerline.app.detection

import com.ledgerline.app.data.Ids
import com.ledgerline.app.detection.capture.CaptureAccounts
import com.ledgerline.app.detection.capture.CaptureChannel
import com.ledgerline.app.detection.capture.CaptureDedupe
import com.ledgerline.app.detection.capture.CaptureActions
import com.ledgerline.app.detection.capture.CaptureAddResult
import com.ledgerline.app.detection.capture.CaptureAlerts
import com.ledgerline.app.detection.capture.CapturePreview
import com.ledgerline.app.detection.capture.CaptureSources
import com.ledgerline.app.detection.capture.SpendTextParser
import com.ledgerline.app.domain.model.CaptureStatus
import com.ledgerline.app.domain.model.CapturedSpend
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.CaptureRepository
import com.ledgerline.app.domain.repository.NotificationSourceRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.SyncLogRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.preferences.UiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns payment notifications into captured spends waiting in the inbox. A capture never changes a balance by
 * itself; it becomes a transaction only when you add it, or when you turned on auto-add and the card ending
 * matches exactly one account.
 */
@Singleton
class NotificationIngestor @Inject constructor(
    private val preferences: UiPreferences,
    private val sources: NotificationSourceRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val captures: CaptureRepository,
    private val actions: CaptureActions,
    private val notifier: CaptureAlerts,
    private val syncLog: SyncLogRepository,
    private val clock: AppClock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Debug builds log why a notification was or was not captured. Never the notification text. */
    private fun trace(outcome: String) {
        if (com.ledgerline.app.BuildConfig.DEBUG) runCatching { android.util.Log.d("LedgerCapture", outcome) }
    }

    fun offer(packageName: String, title: String, body: String) {
        if (packageName.isBlank() || body.isBlank()) return
        scope.launch {
            runCatching { ingest(packageName, title, body) }
                .onFailure { error ->
                    syncLog.record("capture", "FAILED", "Notification processing failed: ${error.javaClass.simpleName}")
                }
        }
    }

    /** A text read from the incoming-SMS broadcast. [sender] is the short code or name, such as "VM-HDFCBK". */
    fun offerSms(sender: String, body: String) = offer(CaptureSources.SMS_DIRECT, sender, body)

    /** A notification from a chosen app arrived with its text hidden by Android. Counted so Settings can explain it. */
    fun noteHidden(packageName: String) {
        scope.launch {
            if (preferences.notificationDetection.first() && sources.isEnabled(packageName)) {
                preferences.countHiddenNotice()
                trace("hidden by Android: $packageName")
            }
        }
    }

    /** Processes one notification. Called on a background scope by [offer]; tests call it directly. */
    internal suspend fun ingest(packageName: String, title: String, body: String) {
        if (!preferences.notificationDetection.first()) return trace("off")
        val chosen = if (packageName == CaptureSources.SMS_DIRECT) preferences.captureSmsDirect.first() else sources.isEnabled(packageName)
        if (!chosen) return trace("app not chosen: $packageName")
        // Personal messages simply do not parse; nothing is stored or logged for them.
        val channel = CaptureSources.channel(packageName)
        val signal = SpendTextParser.parse(
            body,
            title,
            tapToPay = CaptureSources.isTapToPay(packageName),
            requireBankWording = channel == CaptureChannel.SMS || channel == CaptureChannel.EMAIL,
        ) ?: return trace("not a payment: $packageName")
        val today = clock.today()
        val now = clock.now().toEpochMilli()
        val source = CaptureSources.find(packageName)
        val capture = CapturedSpend(
            id = Ids.new(),
            packageName = packageName,
            sourceLabel = source?.label ?: packageName,
            channel = CaptureSources.channel(packageName),
            amountMinor = signal.amountMinor,
            currency = signal.currency ?: settings.observe().first().primaryCurrency,
            merchant = signal.merchant.orEmpty(),
            lastFour = signal.lastFour,
            direction = signal.direction,
            refund = signal.refund,
            occurredOn = today,
            capturedAtEpochMillis = now,
            preview = CapturePreview.of(title, body),
            status = CaptureStatus.PENDING,
            transactionId = null,
        )
        // Identical text inside one window is the same notification re-posted; the same text later is a new purchase.
        val window = now / CaptureDedupe.WINDOW_MILLIS
        val fingerprint = listOf(packageName, signal.amountMinor, signal.direction, signal.merchant.orEmpty(), window, body.trim().hashCode())
            .joinToString("|")
        if (!captures.record(capture, fingerprint)) return trace("same payment already captured")
        trace("captured from $packageName")

        if (preferences.captureAutoAdd.first()) {
            val matched = CaptureAccounts.matched(capture, accounts.observe().first())
            if (matched != null && actions.add(capture, matched.id) == CaptureAddResult.Added) {
                syncLog.record("capture", "ADDED", "Added ${capture.merchant.ifBlank { "a payment" }} from ${capture.sourceLabel}.")
                if (preferences.captureNotify.first()) notifier.added(capture)
                return
            }
        }
        syncLog.record("capture", "PENDING", "Captured ${capture.merchant.ifBlank { "a payment" }} from ${capture.sourceLabel}.")
        if (preferences.captureNotify.first()) notifier.captured(capture)
    }
}
