package com.ledgerline.app.finance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ledgerline.app.detection.NotificationIngestor
import com.ledgerline.app.detection.capture.CaptureAccounts
import com.ledgerline.app.detection.capture.CaptureActions
import com.ledgerline.app.detection.capture.CaptureAddResult
import com.ledgerline.app.detection.capture.CaptureAlerts
import com.ledgerline.app.detection.capture.CaptureChannel
import com.ledgerline.app.detection.capture.CaptureDedupe
import com.ledgerline.app.detection.capture.MoneyDirection
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountDraft
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CaptureStatus
import com.ledgerline.app.domain.model.CapturedSpend
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.DetectedDraft
import com.ledgerline.app.domain.model.IngestResult
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.NotificationSource
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.SyncRecord
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionDraft
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.CaptureRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.MerchantRepository
import com.ledgerline.app.domain.repository.NotificationSourceRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.SyncLogRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.preferences.UiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The whole path from a posted notification to the inbox and on to a transaction, with the real parser, ingestor,
 * actions, and preferences. Only storage is replaced by in-memory fakes.
 */
class CapturePipelineTest {
    private val messages = "com.google.android.apps.messaging"
    private val gmail = "com.google.android.gm"
    private val wallet = "com.google.android.apps.walletnfcrel"
    private val unknownApp = "com.example.game"

    private val checking = account("chk", "Checking", AccountType.CHECKING, lastFour = "1234")
    private val card = account("card", "Sapphire", AccountType.CREDIT_CARD, lastFour = "5555", safeToSpend = false)

    private val dir: File = Files.createTempDirectory("prefs").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = UiPreferences(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "test.preferences_pb") })
    private val clock = FakeClock()
    private val accounts = FakeAccounts(listOf(checking, card))
    private val transactions = FakeTransactions()
    private val captures = FakeCaptures()
    private val sources = FakeSources(setOf(messages, gmail, wallet))
    private val alerts = FakeAlerts()
    private val log = FakeLog()
    private val actions = CaptureActions(accounts, transactions, FakeMerchants(), FakeCategories(), captures)
    private val ingestor = NotificationIngestor(preferences, sources, accounts, FakeSettings(), captures, actions, alerts, log, clock)

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun on(block: suspend () -> Unit) = runBlocking {
        preferences.setNotificationDetection(true)
        block()
    }

    @Test
    fun nothingIsReadWhileCaptureIsOff() = runBlocking {
        ingestor.ingest(messages, "Chase", "You made a \$45.67 transaction with STARBUCKS on your card ending in 1234.")
        assertTrue(captures.rows.isEmpty())
        assertTrue(alerts.captured.isEmpty())
    }

    @Test
    fun appsYouDidNotPickAreNeverRead() = on {
        ingestor.ingest(unknownApp, "Chase", "You made a \$45.67 transaction with STARBUCKS on your card ending in 1234.")
        assertTrue(captures.rows.isEmpty())
    }

    @Test
    fun personalTextsLeaveNoTrace() = on {
        ingestor.ingest(messages, "Alex", "I paid \$12 for lunch, you can pay me back later")
        ingestor.ingest(messages, "Mom", "Call me when you land")
        assertTrue(captures.rows.isEmpty())
        // Not even a log line: nothing about personal messages is stored.
        assertTrue(log.records.isEmpty())
    }

    @Test
    fun aBankTextBecomesAPendingCaptureAndNothingMoreYet() = on {
        ingestor.ingest(messages, "Chase", "You made a \$45.67 transaction with STARBUCKS on your card ending in 1234. Ref 512345678901")
        val capture = captures.rows.single()
        assertEquals(CaptureStatus.PENDING, capture.status)
        assertEquals(4_567L, capture.amountMinor)
        assertEquals("USD", capture.currency)
        assertEquals("Starbucks", capture.merchant)
        assertEquals("1234", capture.lastFour)
        assertEquals(MoneyDirection.OUT, capture.direction)
        assertEquals(CaptureChannel.SMS, capture.channel)
        assertEquals("Google Messages", capture.sourceLabel)
        assertEquals(clock.today(), capture.occurredOn)
        assertTrue("long numbers are masked", !capture.preview.contains("512345678901"))
        // A capture alone never touches the ledger.
        assertTrue(transactions.saved.isEmpty())
        assertEquals(listOf(capture.id), alerts.captured)
    }

    @Test
    fun theAppCurrencyIsUsedWhenTheTextHasNone() = on {
        ingestor.ingest(messages, "SBI", "Dear UPI user A/C X1234 debited by 250.0 on date 02Nov26 trf to SWIGGY Refno 512345678901. -SBI")
        assertEquals("USD", captures.rows.single().currency)
        assertEquals(25_000L, captures.rows.single().amountMinor)
    }

    @Test
    fun theSameNotificationPostedTwiceIsCapturedOnce() = on {
        val text = "You made a \$45.67 transaction with STARBUCKS on your card ending in 1234."
        ingestor.ingest(messages, "Chase", text)
        ingestor.ingest(messages, "Chase", text)
        assertEquals(1, captures.rows.size)
    }

    @Test
    fun aTextAndAWalletNoticeForOnePaymentAreCapturedOnce() = on {
        ingestor.ingest(messages, "Chase", "You made a \$4.50 transaction with BLUE BOTTLE on your card ending in 1234.")
        clock.advanceMinutes(1)
        ingestor.ingest(wallet, "Blue Bottle Coffee", "\$4.50 with Visa •••• 1234")
        assertEquals(1, captures.rows.size)
    }

    @Test
    fun theSameAmountLaterInTheDayIsANewPayment() = on {
        ingestor.ingest(messages, "Chase", "You made a \$4.50 transaction with BLUE BOTTLE on your card ending in 1234.")
        clock.advanceMinutes(CaptureDedupe.WINDOW_MILLIS / 60_000 + 1)
        ingestor.ingest(messages, "Chase", "You made a \$4.50 transaction with BLUE BOTTLE on your card ending in 1234.")
        assertEquals(2, captures.rows.size)
    }

    @Test
    fun autoAddPostsTheTransactionWhenTheCardMatchesOneAccount() = on {
        preferences.setCaptureAutoAdd(true)
        ingestor.ingest(messages, "Chase", "You made a \$45.67 transaction with STARBUCKS on your card ending in 5555.")
        val saved = transactions.saved.single()
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(4_567L, saved.amountMinor)
        assertEquals("card", saved.accountId)
        assertEquals("Starbucks", saved.merchant)
        assertEquals("From Google Messages", saved.note)
        assertEquals(CaptureStatus.ADDED, captures.rows.single().status)
        assertEquals(1, alerts.added.size)
        assertTrue(alerts.captured.isEmpty())
    }

    @Test
    fun autoAddWaitsForYouWhenNoAccountMatches() = on {
        preferences.setCaptureAutoAdd(true)
        ingestor.ingest(messages, "Chase", "You made a \$45.67 transaction with STARBUCKS on your card ending in 9999.")
        assertTrue(transactions.saved.isEmpty())
        assertEquals(CaptureStatus.PENDING, captures.rows.single().status)
    }

    @Test
    fun autoAddWaitsForYouWhenItLooksAlreadyRecorded() = on {
        preferences.setCaptureAutoAdd(true)
        transactions.nextIsDuplicate = true
        ingestor.ingest(messages, "Chase", "You made a \$45.67 transaction with STARBUCKS on your card ending in 1234.")
        assertTrue(transactions.saved.isEmpty())
        assertEquals(CaptureStatus.PENDING, captures.rows.single().status)
    }

    @Test
    fun turningOffTheNoticeKeepsCapturingQuietly() = on {
        preferences.setCaptureNotify(false)
        ingestor.ingest(messages, "Chase", "You made a \$45.67 transaction with STARBUCKS on your card ending in 1234.")
        assertEquals(1, captures.rows.size)
        assertTrue(alerts.captured.isEmpty())
    }

    @Test
    fun addingFromTheInboxCreatesTheRightTransactionType() = on {
        ingestor.ingest(messages, "Chase", "Your direct deposit of \$2,500.00 from ACME INC is available.")
        clock.advanceMinutes(30)
        ingestor.ingest(gmail, "Target", "Refund of \$12.99 from Target has been credited to your card ending 5555")
        val (salary, refund) = captures.rows
        assertEquals(CaptureAddResult.Added, actions.add(salary))
        assertEquals(CaptureAddResult.Added, actions.add(refund))
        assertEquals(TransactionType.INCOME, transactions.saved[0].type)
        assertEquals("chk", transactions.saved[0].accountId)
        assertEquals(TransactionType.REFUND, transactions.saved[1].type)
        assertEquals("card", transactions.saved[1].accountId)
        assertTrue(captures.rows.all { it.status == CaptureStatus.ADDED })
    }

    @Test
    fun aLikelyDuplicateIsReportedNotSavedUntilYouSaySo() = on {
        ingestor.ingest(messages, "Chase", "You made a \$45.67 transaction with STARBUCKS on your card ending in 1234.")
        val capture = captures.rows.single()
        transactions.nextIsDuplicate = true
        assertTrue(actions.add(capture) is CaptureAddResult.PossibleDuplicate)
        assertEquals(CaptureStatus.PENDING, captures.rows.single().status)
        assertEquals(CaptureAddResult.Added, actions.add(capture, acknowledgeDuplicate = true))
        assertEquals(1, transactions.saved.size)
    }

    @Test
    fun dismissAndUndo() = on {
        ingestor.ingest(messages, "Chase", "You made a \$45.67 transaction with STARBUCKS on your card ending in 1234.")
        val id = captures.rows.single().id
        actions.dismiss(id)
        assertEquals(CaptureStatus.DISMISSED, captures.rows.single().status)
        actions.restore(id)
        assertEquals(CaptureStatus.PENDING, captures.rows.single().status)
    }

    @Test
    fun withNoAccountsAddingAsksForOne() = on {
        ingestor.ingest(messages, "Chase", "You made a \$45.67 transaction with STARBUCKS on your card ending in 1234.")
        accounts.rows.value = emptyList()
        assertEquals(CaptureAddResult.NoAccount, actions.add(captures.rows.single()))
    }

    @Test
    fun accountChoiceFollowsTheCardThenYourMainAccount() {
        val capture = captures.sample(lastFour = "5555")
        assertEquals("card", CaptureAccounts.suggested(capture, listOf(checking, card))?.id)
        assertEquals("chk", CaptureAccounts.suggested(capture.copy(lastFour = null), listOf(card, checking))?.id)
        // Two accounts sharing an ending are ambiguous, so neither counts as a match.
        val twin = checking.copy(id = "chk2", lastFour = "5555")
        assertNull(CaptureAccounts.matched(capture, listOf(card, twin)))
        // Deleted and inactive accounts are never chosen.
        assertNull(CaptureAccounts.matched(capture, listOf(card.copy(deletedAtEpochMillis = 1L))))
        assertEquals("chk", CaptureAccounts.suggested(capture, listOf(card.copy(active = false), checking))?.id)
    }

    // ---- fakes ----

    private fun account(id: String, name: String, type: AccountType, lastFour: String?, safeToSpend: Boolean = true) = Account(
        id, name, "", type, lastFour, 0, null, "USD", true, true, safeToSpend, SyncStatus.MANUAL, null, null, 0,
    )

    private class FakeClock : AppClock {
        var instant: Instant = Instant.parse("2026-11-02T15:00:00Z")
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun today(): LocalDate = instant.atZone(zone()).toLocalDate()
        override fun now(): Instant = instant
        fun advanceMinutes(minutes: Long) {
            instant = instant.plusSeconds(minutes * 60)
        }
    }

    private class FakeAccounts(initial: List<Account>) : AccountRepository {
        val rows = MutableStateFlow(initial)
        override fun observe(): Flow<List<Account>> = rows
        override suspend fun get(id: String) = rows.value.firstOrNull { it.id == id }
        override suspend fun save(draft: AccountDraft): SaveResult = SaveResult.Saved
        override suspend fun deleteIfUnused(id: String): SaveResult = SaveResult.Saved
        override fun observeIncludingDeleted(): Flow<List<Account>> = rows
        override suspend fun deletionImpact(id: String) = TODO()
        override suspend fun delete(id: String): SaveResult = SaveResult.Saved
    }

    private class FakeTransactions : TransactionRepository {
        val saved = mutableListOf<TransactionDraft>()
        var nextIsDuplicate = false
        override fun observeAll(): Flow<List<LedgerTransaction>> = flowOf(emptyList())
        override fun observeHistory(): Flow<List<LedgerTransaction>> = flowOf(emptyList())
        override suspend fun get(id: String): LedgerTransaction? = null
        override suspend fun editingSource(id: String): LedgerTransaction? = null
        override suspend fun save(draft: TransactionDraft): SaveResult {
            if (nextIsDuplicate && !draft.acknowledgeDuplicate) {
                return SaveResult.PossibleDuplicate(90, "existing", "Starbucks, \$45.67 on Nov 2.")
            }
            saved += draft
            return SaveResult.Saved
        }
        override suspend fun softDelete(id: String) = Unit
        override suspend fun restore(id: String) = Unit
        override suspend fun ingestDetected(draft: DetectedDraft): IngestResult = IngestResult.Rejected("unused")
        override suspend fun setStatus(id: String, status: TransactionStatus) = Unit
        override suspend fun markSpendPaid(id: String, on: java.time.LocalDate) = Unit
    }

    private class FakeCaptures : CaptureRepository {
        val rows = mutableListOf<CapturedSpend>()
        private val fingerprints = mutableSetOf<String>()
        override fun observePending(): Flow<List<CapturedSpend>> = flowOf(rows.filter { it.status == CaptureStatus.PENDING })
        override fun observeRecent(): Flow<List<CapturedSpend>> = flowOf(rows)
        override suspend fun get(id: String) = rows.firstOrNull { it.id == id }
        override suspend fun record(capture: CapturedSpend, fingerprint: String): Boolean {
            if (rows.any { CaptureDedupe.isSamePayment(it, capture) }) return false
            if (!fingerprints.add(fingerprint)) return false
            rows += capture
            return true
        }
        override suspend fun setStatus(id: String, status: CaptureStatus, transactionId: String?) {
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = rows[index].copy(status = status, transactionId = transactionId)
        }
        fun sample(lastFour: String?) = CapturedSpend(
            "s", "pkg", "Messages", CaptureChannel.SMS, 100, "USD", "Shop", lastFour, MoneyDirection.OUT, false,
            LocalDate.of(2026, 11, 2), 0, "", CaptureStatus.PENDING, null,
        )
    }

    private class FakeSources(private val enabled: Set<String>) : NotificationSourceRepository {
        override fun observe(): Flow<List<NotificationSource>> = flowOf(emptyList())
        override suspend fun ensureCatalog() = Unit
        override suspend fun setEnabled(id: String, enabled: Boolean) = Unit
        override suspend fun isEnabled(packageName: String) = packageName in enabled
    }

    private class FakeSettings : SettingsRepository {
        override fun observe(): Flow<UserSettings> = flowOf(UserSettings("USD", 0, 30, true))
        override suspend fun ensureDefault() = Unit
        override suspend fun update(settings: UserSettings) = Unit
    }

    private class FakeLog : SyncLogRepository {
        val records = mutableListOf<String>()
        override fun observe(): Flow<List<SyncRecord>> = flowOf(emptyList())
        override suspend fun record(providerId: String, status: String, message: String) {
            records += "$status $message"
        }
    }

    private class FakeAlerts : CaptureAlerts {
        val captured = mutableListOf<String>()
        val added = mutableListOf<String>()
        override fun captured(capture: CapturedSpend) {
            captured += capture.id
        }
        override fun added(capture: CapturedSpend) {
            added += capture.id
        }
    }

    private class FakeMerchants : MerchantRepository {
        override suspend fun suggestion(normalizedName: String, minimumConfirmations: Int): String? = null
    }

    private class FakeCategories : CategoryRepository {
        override fun observe(): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun ensureDefaults() = Unit
        override suspend fun addCustom(name: String, parentId: String?, necessity: Necessity) = Unit
        override suspend fun setArchived(id: String, archived: Boolean) = Unit
    }
}
