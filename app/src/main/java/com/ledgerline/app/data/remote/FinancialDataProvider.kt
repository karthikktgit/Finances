package com.ledgerline.app.data.remote

import com.ledgerline.app.detection.TransactionDetectionEngine
import com.ledgerline.app.domain.model.DetectedDraft
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IngestResult
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.SyncLogRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface FinancialDataProvider {
    val id: String
    val displayName: String

    suspend fun describe(): String

    suspend fun refresh()
}

@Singleton
class FixtureFinancialDataProvider @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val transactions: TransactionRepository,
    private val syncLog: SyncLogRepository,
    private val clock: AppClock,
) : FinancialDataProvider {
    override val id = "development-fixture"
    override val displayName = "Development fixture"

    override suspend fun describe(): String =
        "No aggregation provider is configured. Ledgerline does not ask for a banking password. " +
            "Importing the development fixture writes sample rows onto this device and reconciles them with transactions you already have."

    override suspend fun refresh() {
        val currency = settings.observe().first().primaryCurrency
        val accountRows = accounts.observe().first()
        var saved = 0
        var linked = 0
        var review = 0
        var skipped = 0
        fixtures.forEach { row ->
            val account = TransactionDetectionEngine.matchAccount(accountRows, row.lastFour, currency)
            if (account == null) {
                skipped += 1
                return@forEach
            }
            val result = transactions.ingestDetected(
                DetectedDraft(
                    accountId = account.id,
                    amountMinor = row.amountMinor,
                    merchant = row.merchant,
                    type = TransactionType.EXPENSE,
                    occurredOn = clock.today().minusDays(row.daysAgo),
                    source = DetectionSource.BANK_SYNC,
                    confidencePercent = 90,
                ),
            )
            when (result) {
                is IngestResult.Saved -> saved += 1
                is IngestResult.Linked -> linked += 1
                is IngestResult.NeedsReview -> review += 1
                is IngestResult.Rejected -> skipped += 1
            }
        }
        syncLog.record(
            id,
            "FIXTURE",
            "Development fixture finished. Saved $saved, linked $linked, needs review $review, skipped $skipped. No institution was contacted.",
        )
    }

    private data class FixtureRow(val merchant: String, val amountMinor: Long, val lastFour: String, val daysAgo: Long)

    private val fixtures = listOf(
        FixtureRow("Amazon", 4_329, "1234", 1),
        FixtureRow("Shell", 3_500, "4521", 2),
        FixtureRow("Whole Foods", 6_420, "4521", 4),
    )
}
