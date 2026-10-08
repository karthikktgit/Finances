package com.ledgerline.app.data.repository

import com.ledgerline.app.data.Ids
import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.entity.DebtEntity
import com.ledgerline.app.data.local.entity.NotificationSourceEntity
import com.ledgerline.app.data.local.entity.SyncRecordEntity
import com.ledgerline.app.detection.parser.InstitutionCatalog
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtDraft
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.NotificationSource
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringDraft
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncRecord
import com.ledgerline.app.domain.repository.DebtRepository
import com.ledgerline.app.domain.repository.NotificationSourceRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SyncLogRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.recurring.RecurringDetector
import com.ledgerline.app.finance.subscription.SubscriptionRollup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebtRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
) : DebtRepository {
    override fun observe(): Flow<List<Debt>> = database.debts().observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(draft: DebtDraft): SaveResult {
        val name = draft.name.trim()
        if (name.isEmpty()) return SaveResult.Invalid("Name the debt.")
        if (draft.balanceMinor <= 0) return SaveResult.Invalid("Enter the current balance.")
        if (draft.aprBasisPoints < 0 || draft.aprBasisPoints > 10_000) return SaveResult.Invalid("Enter an APR from 0 to 100.")
        if (draft.minimumPaymentMinor < 0) return SaveResult.Invalid("The minimum payment cannot be negative.")
        if (draft.dueDayOfMonth !in 1..31) return SaveResult.Invalid("The due day must be from 1 to 31.")
        val currency = database.settings().get()?.primaryCurrency ?: "USD"
        val now = clock.now().toEpochMilli()
        val existing = draft.existingId?.let { database.debts().get(it) }
        if (existing == null) {
            database.debts().insert(
                DebtEntity(
                    id = Ids.new(),
                    name = name,
                    accountId = null,
                    principalMinor = draft.balanceMinor,
                    balanceMinor = draft.balanceMinor,
                    aprBasisPoints = draft.aprBasisPoints,
                    minimumPaymentMinor = draft.minimumPaymentMinor,
                    dueDayOfMonth = draft.dueDayOfMonth,
                    frequency = RecurrenceFrequency.MONTHLY.name,
                    priority = draft.priority.name,
                    currency = currency,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        } else {
            database.debts().update(
                existing.copy(
                    name = name,
                    balanceMinor = draft.balanceMinor,
                    aprBasisPoints = draft.aprBasisPoints,
                    minimumPaymentMinor = draft.minimumPaymentMinor,
                    dueDayOfMonth = draft.dueDayOfMonth,
                    priority = draft.priority.name,
                    updatedAtEpochMillis = now,
                ),
            )
        }
        return SaveResult.Saved
    }

    override suspend fun delete(id: String) {
        database.debts().delete(id)
    }
}

private fun DebtEntity.toDomain() = Debt(
    id = id,
    name = name,
    accountId = accountId,
    principalMinor = principalMinor,
    balanceMinor = balanceMinor,
    aprBasisPoints = aprBasisPoints,
    minimumPaymentMinor = minimumPaymentMinor,
    dueDayOfMonth = dueDayOfMonth,
    frequency = enumValueOf<RecurrenceFrequency>(frequency),
    priority = enumValueOf<PaymentPriority>(priority),
    currency = currency,
)

@Singleton
class NotificationSourceRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
) : NotificationSourceRepository {
    override fun observe(): Flow<List<NotificationSource>> =
        database.notificationSources().observe().map { rows ->
            rows.map { NotificationSource(it.id, it.packageName, it.label, it.enabled) }
        }

    override suspend fun ensureCatalog() {
        InstitutionCatalog.knownApps.forEach { (packageName, label) ->
            database.notificationSources().insert(
                NotificationSourceEntity(Ids.new(), packageName, label, enabled = false),
            )
        }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        val row = database.notificationSources().observeOnce(id) ?: return
        database.notificationSources().update(row.copy(enabled = enabled))
    }

    override suspend fun isEnabled(packageName: String): Boolean =
        database.notificationSources().find(packageName)?.enabled == true
}

private suspend fun com.ledgerline.app.data.local.dao.NotificationSourceDao.observeOnce(id: String): NotificationSourceEntity? {
    return findById(id)
}

@Singleton
class SyncLogRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
) : SyncLogRepository {
    override fun observe(): Flow<List<SyncRecord>> = database.syncRecords().observe().map { rows ->
        rows.map { SyncRecord(it.id, it.providerId, it.status, it.message, it.createdAtEpochMillis) }
    }

    override suspend fun record(providerId: String, status: String, message: String) {
        database.syncRecords().insert(
            SyncRecordEntity(Ids.new(), providerId, status, message.take(280), clock.now().toEpochMilli()),
        )
    }
}

@Singleton
class RecurringSuggestionStore @Inject constructor(
    private val transactions: TransactionRepository,
    private val recurring: RecurringRepository,
    private val clock: AppClock,
) {
    suspend fun storeNew(): Int {
        val today = clock.today()
        val suggestions = RecurringDetector.detect(transactions.observeAll().first(), today)
        val existing = recurring.observe().first()
        var created = 0
        suggestions.forEach { suggestion ->
            val already = existing.any {
                it.name.equals(suggestion.merchantNormalized, ignoreCase = true) && it.accountId == suggestion.accountId
            }
            if (already) return@forEach
            val kind = if (SubscriptionRollup.isKnownSubscription(suggestion.merchantNormalized)) {
                ScheduledKind.SUBSCRIPTION
            } else {
                ScheduledKind.BILL
            }
            val start = today
            val result = recurring.save(
                RecurringDraft(
                    existingId = null,
                    name = suggestion.merchantNormalized,
                    accountId = suggestion.accountId,
                    categoryId = suggestion.categoryId,
                    direction = CashFlowDirection.OUTFLOW,
                    kind = kind,
                    amountMinor = suggestion.typicalAmountMinor,
                    frequency = suggestion.frequency,
                    intervalDays = null,
                    dayOfMonth = suggestion.dayOfMonth,
                    dayOfWeek = if (suggestion.frequency == RecurrenceFrequency.WEEKLY) DayOfWeek.from(start).value else null,
                    startOn = start,
                    priority = PaymentPriority.FLEXIBLE,
                    incomeConfidence = IncomeConfidence.NONE,
                    active = false,
                    note = "Detected from ${suggestion.sampleCount} posted transactions. Inactive until you confirm it.",
                ),
            )
            if (result is SaveResult.Saved) created += 1
        }
        return created
    }
}
