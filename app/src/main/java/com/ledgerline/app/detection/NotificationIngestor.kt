package com.ledgerline.app.detection

import com.ledgerline.app.detection.parser.NotificationData
import com.ledgerline.app.detection.parser.ParserRegistry
import com.ledgerline.app.domain.model.DetectedDraft
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IngestResult
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.NotificationSourceRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.SyncLogRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.preferences.UiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationIngestor @Inject constructor(
    private val preferences: UiPreferences,
    private val sources: NotificationSourceRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val transactions: TransactionRepository,
    private val syncLog: SyncLogRepository,
    private val clock: AppClock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun offer(packageName: String, text: String) {
        if (packageName.isBlank() || text.isBlank()) return
        scope.launch {
            runCatching { ingest(packageName, text) }
                .onFailure { error ->
                    syncLog.record("notification", "FAILED", "Notification processing failed: ${error.javaClass.simpleName}")
                }
        }
    }

    private suspend fun ingest(packageName: String, text: String) {
        if (!preferences.notificationDetection.first()) return
        if (!sources.isEnabled(packageName)) return
        val parsed = ParserRegistry.parse(NotificationData(packageName, text))
        if (parsed == null) {
            syncLog.record("notification", "SKIPPED", "A notification was read, but the amount could not be identified.")
            return
        }
        val currency = settings.observe().first().primaryCurrency
        val account = TransactionDetectionEngine.matchAccount(accounts.observe().first(), parsed.lastFour, currency)
        val decision = TransactionDetectionEngine.decide(parsed, account, duplicateScore = null)
        if (decision.disposition == DetectionDisposition.REJECT || parsed.type == null || account == null || parsed.amountMinor == null) {
            syncLog.record("notification", "SKIPPED", decision.message)
            return
        }
        val result = transactions.ingestDetected(
            DetectedDraft(
                accountId = account.id,
                amountMinor = parsed.amountMinor,
                merchant = parsed.merchant.orEmpty(),
                type = parsed.type,
                occurredOn = clock.today(),
                source = DetectionSource.NOTIFICATION,
                confidencePercent = decision.confidencePercent,
            ),
        )
        val message = when (result) {
            is IngestResult.Saved -> "Recorded ${parsed.merchant ?: "a transaction"} from a notification."
            is IngestResult.NeedsReview -> "A notification needs review before it changes balances."
            is IngestResult.Linked -> "A notification matched an existing transaction and was linked. The original record was kept."
            is IngestResult.Rejected -> result.message
        }
        syncLog.record("notification", result.javaClass.simpleName, message)
    }
}
