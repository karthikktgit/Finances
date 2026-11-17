package com.ledgerline.app.data.repository

import androidx.room.withTransaction
import com.ledgerline.app.data.Ids
import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.entity.AccountEntity
import com.ledgerline.app.data.local.entity.BudgetEntity
import com.ledgerline.app.data.local.entity.CategoryEntity
import com.ledgerline.app.data.local.entity.MerchantCategoryRuleEntity
import com.ledgerline.app.data.local.entity.MerchantEntity
import com.ledgerline.app.data.local.entity.RecurringOccurrenceEntity
import com.ledgerline.app.data.local.entity.RecurringPaymentEntity
import com.ledgerline.app.data.local.entity.TransactionEntity
import com.ledgerline.app.data.local.seed.CategoryCatalog
import com.ledgerline.app.data.mapper.defaultSettings
import com.ledgerline.app.data.mapper.toDomain
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountDraft
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetDraft
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.DetectedDraft
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IngestResult
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.MerchantCategoryRule
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringDraft
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.SeriesScope
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionDraft
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.BudgetRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.LedgerReadModel
import com.ledgerline.app.domain.repository.MerchantRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.category.StandardCategories
import com.ledgerline.app.finance.duplicate.DuplicateCandidate
import com.ledgerline.app.finance.duplicate.DuplicateDetector
import com.ledgerline.app.finance.fingerprint.TransactionFingerprint
import com.ledgerline.app.finance.merchant.MerchantNormalizer
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.policy.FinancePolicy
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import com.ledgerline.app.finance.schedule.SeriesEdit
import com.ledgerline.app.finance.validation.TransactionRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
    private val undoCenter: com.ledgerline.app.ui.undo.UndoCenter,
) : AccountRepository {
    override fun observe(): Flow<List<Account>> = database.accounts().observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): Account? = database.accounts().get(id)?.toDomain()

    override suspend fun save(draft: AccountDraft): SaveResult {
        TransactionRules.validate(draft)?.let { return SaveResult.Invalid(it) }
        val now = clock.now().toEpochMilli()
        val currency = database.settings().get()?.primaryCurrency ?: "USD"
        val target = signedBalance(draft)
        var adjustmentId: String? = null
        val retired = draft.existingId?.let { id ->
            database.accounts().get(id) == null && database.accounts().getAllIncludingDeleted().any { it.id == id }
        } ?: false
        if (retired) return SaveResult.Invalid("This account was deleted. Nothing was saved.")
        val result = database.withTransaction {
            val existing = draft.existingId?.let { database.accounts().get(it) }
            if (existing == null) {
                val id = draft.existingId ?: Ids.new()
                database.accounts().insert(
                    AccountEntity(
                        id = id,
                        name = draft.name.trim(),
                        institution = draft.institution.trim(),
                        type = draft.type.name,
                        lastFour = draft.lastFour.ifBlank { null },
                        openingBalanceMinor = target,
                        creditLimitMinor = if (draft.type == com.ledgerline.app.domain.model.AccountType.CREDIT_CARD) draft.creditLimitMinor else null,
                        currency = currency,
                        active = draft.active,
                        includeInNetWorth = draft.includeInNetWorth,
                        includeInSafeToSpend = draft.includeInSafeToSpend,
                        syncStatus = SyncStatus.MANUAL.name,
                        lastSyncedAtEpochMillis = null,
                        syncError = null,
                        sortOrder = database.accounts().maxSortOrder() + 1,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                )
            } else {
                database.accounts().update(
                    existing.copy(
                        name = draft.name.trim(),
                        institution = draft.institution.trim(),
                        type = draft.type.name,
                        lastFour = draft.lastFour.ifBlank { null },
                        creditLimitMinor = if (draft.type == com.ledgerline.app.domain.model.AccountType.CREDIT_CARD) draft.creditLimitMinor else null,
                        active = draft.active,
                        includeInNetWorth = draft.includeInNetWorth,
                        includeInSafeToSpend = draft.includeInSafeToSpend,
                        updatedAtEpochMillis = now,
                    ),
                )
                if (draft.applyBalance) {
                    val domain = existing.toDomain().copy(
                        type = draft.type,
                        openingBalanceMinor = existing.openingBalanceMinor,
                    )
                    val transactions = database.transactions().getAll().map { it.toDomain() }
                    val current = BalanceMath.currentBalanceMinor(domain, transactions)
                    val delta = target - current
                    if (delta != 0L) {
                        adjustmentId = insertAdjustment(
                            accountId = existing.id,
                            currency = existing.currency,
                            delta = delta,
                            now = now,
                            today = clock.today(),
                        )
                    }
                }
            }
            SaveResult.Saved
        }
        val createdAdjustment = adjustmentId
        if (result is SaveResult.Saved && createdAdjustment != null) {
            undoCenter.offer("Balance updated") {
                val row = database.transactions().get(createdAdjustment) ?: return@offer
                val stamp = clock.now().toEpochMilli()
                database.transactions().update(
                    row.copy(deletedAtEpochMillis = stamp, updatedAtEpochMillis = stamp),
                )
            }
        }
        return result
    }

    override suspend fun deleteIfUnused(id: String): SaveResult {
        val transactions = database.transactions().countForAccount(id)
        val recurring = database.recurring().countForAccount(id)
        val budgets = database.budgets().countForAccount(id)
        if (transactions + recurring + budgets > 0) {
            return SaveResult.Invalid(
                "This account still has transactions, scheduled items, or budgets. Deactivate it instead of deleting it. Nothing was removed.",
            )
        }
        database.accounts().delete(id)
        return SaveResult.Saved
    }

    override fun observeIncludingDeleted(): Flow<List<Account>> =
        database.accounts().observeIncludingDeleted().map { rows -> rows.map { it.toDomain() } }

    override suspend fun deletionImpact(id: String): com.ledgerline.app.finance.account.AccountDeletionImpact =
        com.ledgerline.app.finance.account.AccountDeletion.impact(
            accountId = id,
            transactions = database.transactions().getAll().map { it.toDomain() },
            recurring = database.recurring().getAll().map { it.toDomain() },
            debts = database.debts().getAll().map { com.ledgerline.app.finance.account.DebtLink(it.id, it.accountId, it.paymentAccountId) },
            budgets = database.budgets().getAll().map { it.toDomain() },
        )

    override suspend fun delete(id: String): SaveResult {
        val account = database.accounts().get(id)
            ?: return SaveResult.Invalid("This account is already deleted or no longer on this device. Nothing changed.")
        val now = clock.now().toEpochMilli()
        val today = clock.today()
        database.withTransaction {
            val recurring = database.recurring().getAll()
            val debts = database.debts().getAll()
            val budgets = database.budgets().getAll()
            val plan = com.ledgerline.app.finance.account.AccountDeletion.plan(
                accountId = id,
                recurring = recurring.map { it.toDomain() },
                debts = debts.map { com.ledgerline.app.finance.account.DebtLink(it.id, it.accountId, it.paymentAccountId) },
                budgets = budgets.map { it.toDomain() },
            )
            val note = { existing: String -> com.ledgerline.app.finance.account.AccountDeletion.reviewNote(existing, account.name, today) }
            recurring.filter { it.id in plan.pausedScheduleIds }.forEach { row ->
                database.recurring().update(row.copy(active = false, note = note(row.note), updatedAtEpochMillis = now))
            }
            debts.filter { it.id in plan.unlinkedDebtIds }.forEach { row ->
                database.debts().update(
                    row.copy(
                        accountId = row.accountId.takeUnless { it == id },
                        paymentAccountId = row.paymentAccountId.takeUnless { it == id },
                        note = note(row.note),
                        updatedAtEpochMillis = now,
                    ),
                )
            }
            budgets.filter { it.id in plan.pausedBudgetIds }.forEach { row ->
                database.budgets().update(row.copy(active = false, updatedAtEpochMillis = now))
            }
            database.accounts().markDeleted(id, now)
        }
        return SaveResult.Saved
    }

    private suspend fun insertAdjustment(accountId: String, currency: String, delta: Long, now: Long, today: LocalDate): String {
        val normalized = "Balance Adjustment"
        val id = Ids.new()
        database.transactions().insert(
            TransactionEntity(
                id = id,
                accountId = accountId,
                counterAccountId = null,
                linkId = null,
                amountMinor = abs(delta),
                balanceEffectMinor = delta,
                currency = currency,
                type = TransactionType.ADJUSTMENT.name,
                merchantRaw = "Balance adjustment",
                merchantNormalized = normalized,
                merchantId = null,
                description = "Balance adjustment",
                categoryId = null,
                occurredOn = today.toString(),
                postedOn = today.toString(),
                detectionSource = DetectionSource.MANUAL.name,
                confidencePercent = FinancePolicy.MANUAL_CONFIDENCE_PERCENT,
                recurringPaymentId = null,
                status = TransactionStatus.CONFIRMED.name,
                userConfirmed = true,
                note = "Entered to match the balance you set.",
                fingerprint = TransactionFingerprint.of(currency, abs(delta), accountId, today, TransactionType.ADJUSTMENT, normalized),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = null,
            ),
        )
        return id
    }

    private fun signedBalance(draft: AccountDraft): Long =
        if (BalanceMath.isDebtType(draft.type)) -draft.enteredBalanceMinor else draft.enteredBalanceMinor
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
    private val undoCenter: com.ledgerline.app.ui.undo.UndoCenter,
) : TransactionRepository {
    override fun observeAll(): Flow<List<LedgerTransaction>> =
        database.transactions().observeLive().map { rows -> rows.map { it.toDomain() } }

    override fun observeHistory(): Flow<List<LedgerTransaction>> =
        database.transactions().observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): LedgerTransaction? = database.transactions().get(id)?.toDomain()

    override suspend fun editingSource(id: String): LedgerTransaction? {
        val row = database.transactions().get(id) ?: return null
        val group = row.linkId?.let { database.transactions().byLink(it) } ?: listOf(row)
        val source = group.filter { it.deletedAtEpochMillis == null }.minByOrNull { it.balanceEffectMinor } ?: row
        return source.toDomain()
    }

    override suspend fun save(draft: TransactionDraft): SaveResult {
        val accounts = database.accounts().getAll()
        val account = accounts.firstOrNull { it.id == draft.accountId }?.toDomain()
        val counter = draft.counterAccountId?.let { id -> accounts.firstOrNull { it.id == id }?.toDomain() }
        TransactionRules.validate(draft, account, counter)?.let { return SaveResult.Invalid(it) }
        val source = checkNotNull(account)
        val normalized = MerchantNormalizer.normalize(draft.merchant)
        val start = draft.occurredOn.minusDays(FinancePolicy.DUPLICATE_WINDOW_DAYS).toString()
        val end = draft.occurredOn.plusDays(FinancePolicy.DUPLICATE_WINDOW_DAYS).toString()
        val nearby = database.transactions().activeBetween(start, end)
        val candidate = DuplicateCandidate(
            id = draft.existingId ?: "",
            accountId = source.id,
            counterAccountId = counter?.id,
            linkId = draft.existingLinkId,
            amountMinor = draft.amountMinor,
            currency = source.currency,
            type = draft.type,
            occurredOn = draft.occurredOn,
            merchantNormalized = normalized,
            lastFour = source.lastFour,
            deleted = false,
        )
        val matches = nearby.map { row ->
            val rowAccount = accounts.firstOrNull { it.id == row.accountId }
            DuplicateCandidate(
                id = row.id,
                accountId = row.accountId,
                counterAccountId = row.counterAccountId,
                linkId = row.linkId,
                amountMinor = row.amountMinor,
                currency = row.currency,
                type = enumValueOf<TransactionType>(row.type),
                occurredOn = LocalDate.parse(row.occurredOn),
                merchantNormalized = row.merchantNormalized,
                lastFour = rowAccount?.lastFour,
                deleted = row.deletedAtEpochMillis != null,
            )
        }
        val duplicate = DuplicateDetector.bestMatch(candidate, matches)
        if (duplicate != null && !draft.acknowledgeDuplicate) {
            val existing = nearby.first { it.id == duplicate.existingId }
            val summary = "${duplicate.score}% match with ${existing.merchantRaw.ifBlank { existing.type }} " +
                "on ${existing.occurredOn} for ${MoneyFormat.format(existing.amountMinor, existing.currency)}. " +
                duplicate.reasons.joinToString(", ")
            return SaveResult.PossibleDuplicate(duplicate.score, duplicate.existingId, summary)
        }
        val now = clock.now().toEpochMilli()
        val prior = draft.existingId?.let { id ->
            val row = database.transactions().get(id) ?: return SaveResult.Invalid("That transaction is no longer on this device.")
            row.linkId?.let { database.transactions().byLink(it) } ?: listOf(row)
        }
        val createdIds = mutableListOf<String>()
        val result = database.withTransaction {
            val merchantId = upsertMerchant(normalized, draft.merchant, now)
            val paired = draft.type == TransactionType.TRANSFER || draft.type == TransactionType.PAYMENT
            if (draft.existingId == null) {
                val linkId = if (paired) Ids.new() else null
                val primaryId = Ids.new()
                createdIds += primaryId
                insertRow(
                    id = primaryId,
                    draft = draft,
                    account = source,
                    counter = counter,
                    effect = effect(draft),
                    merchantLabel = draft.merchant.ifBlank { defaultPairLabel(draft.type, counter?.name, outgoing = true) },
                    normalized = normalized,
                    merchantId = merchantId,
                    linkId = linkId,
                    now = now,
                    created = now,
                    status = TransactionStatus.CONFIRMED,
                    sourceKind = DetectionSource.MANUAL,
                )
                if (paired && counter != null) {
                    val secondaryId = Ids.new()
                    createdIds += secondaryId
                    insertRow(
                        id = secondaryId,
                        draft = draft,
                        account = counter,
                        counter = source,
                        effect = -effect(draft),
                        merchantLabel = defaultPairLabel(draft.type, source.name, outgoing = false),
                        normalized = normalized,
                        merchantId = merchantId,
                        linkId = linkId,
                        now = now,
                        created = now,
                        status = TransactionStatus.CONFIRMED,
                        sourceKind = DetectionSource.MANUAL,
                    )
                }
                if (merchantId != null && draft.categoryId != null) learn(merchantId, draft.categoryId, now)
                SaveResult.Saved
            } else {
                val current = database.transactions().get(draft.existingId)
                if (current == null) {
                    SaveResult.Invalid("That transaction is no longer on this device.")
                } else {
                    val siblings = current.linkId?.let { database.transactions().byLink(it) }.orEmpty()
                    val primary = siblings.firstOrNull { it.balanceEffectMinor <= 0 } ?: current
                    val secondary = siblings.firstOrNull { it.id != primary.id }
                    val linkId = if (paired) primary.linkId ?: Ids.new() else null
                    updateRow(primary, draft, source, counter, effect(draft), draft.merchant.ifBlank { primary.merchantRaw }, normalized, merchantId, linkId, now)
                    if (paired && counter != null) {
                        if (secondary != null && secondary.deletedAtEpochMillis == null) {
                            updateRow(
                                secondary,
                                draft,
                                counter,
                                source,
                                -effect(draft),
                                defaultPairLabel(draft.type, source.name, outgoing = false),
                                normalized,
                                merchantId,
                                linkId,
                                now,
                            )
                        } else {
                            insertRow(
                                id = Ids.new(),
                                draft = draft,
                                account = counter,
                                counter = source,
                                effect = -effect(draft),
                                merchantLabel = defaultPairLabel(draft.type, source.name, outgoing = false),
                                normalized = normalized,
                                merchantId = merchantId,
                                linkId = linkId,
                                now = now,
                                created = now,
                                status = TransactionStatus.CONFIRMED,
                                sourceKind = DetectionSource.MANUAL,
                            )
                        }
                    } else if (secondary != null && secondary.deletedAtEpochMillis == null) {
                        database.transactions().update(secondary.copy(deletedAtEpochMillis = now, updatedAtEpochMillis = now))
                    }
                    if (merchantId != null && draft.categoryId != null) learn(merchantId, draft.categoryId, now)
                    SaveResult.Saved
                }
            }
        }
        if (result is SaveResult.Saved) {
            if (prior != null) {
                undoCenter.offer("Transaction saved") {
                    database.withTransaction {
                        prior.forEach { database.transactions().update(it) }
                    }
                }
            } else if (createdIds.isNotEmpty()) {
                undoCenter.offer("Transaction added") {
                    createdIds.forEach { markDeleted(it) }
                }
            }
        }
        return result
    }

    override suspend fun softDelete(id: String) {
        val row = database.transactions().get(id) ?: return
        val rows = row.linkId?.let { database.transactions().byLink(it) } ?: listOf(row)
        markDeleted(id)
        undoCenter.offer("Transaction deleted") {
            database.withTransaction {
                rows.forEach { database.transactions().update(it) }
            }
        }
    }

    private suspend fun markDeleted(id: String) {
        val now = clock.now().toEpochMilli()
        database.withTransaction {
            val row = database.transactions().get(id) ?: return@withTransaction
            val rows = row.linkId?.let { database.transactions().byLink(it) } ?: listOf(row)
            rows.forEach { item ->
                database.transactions().update(item.copy(deletedAtEpochMillis = now, updatedAtEpochMillis = now))
            }
        }
    }

    override suspend fun restore(id: String) {
        val now = clock.now().toEpochMilli()
        database.withTransaction {
            val row = database.transactions().get(id) ?: return@withTransaction
            val rows = row.linkId?.let { database.transactions().byLink(it) } ?: listOf(row)
            rows.forEach { item ->
                database.transactions().update(
                    item.copy(
                        deletedAtEpochMillis = null,
                        updatedAtEpochMillis = now,
                        status = TransactionStatus.MODIFIED.name,
                    ),
                )
            }
        }
    }

    override suspend fun ingestDetected(draft: DetectedDraft): IngestResult {
        if (draft.amountMinor <= 0) return IngestResult.Rejected("The amount could not be identified.")
        val account = database.accounts().get(draft.accountId)?.toDomain()
            ?: return IngestResult.Rejected("No matching account is on this device.")
        val normalized = MerchantNormalizer.normalize(draft.merchant)
        val start = draft.occurredOn.minusDays(FinancePolicy.DUPLICATE_WINDOW_DAYS).toString()
        val end = draft.occurredOn.plusDays(FinancePolicy.DUPLICATE_WINDOW_DAYS).toString()
        val nearby = database.transactions().activeBetween(start, end)
        val accounts = database.accounts().getAll()
        val candidate = DuplicateCandidate(
            id = "",
            accountId = account.id,
            counterAccountId = null,
            linkId = null,
            amountMinor = draft.amountMinor,
            currency = account.currency,
            type = draft.type,
            occurredOn = draft.occurredOn,
            merchantNormalized = normalized,
            lastFour = account.lastFour,
            deleted = false,
        )
        val matches = nearby.map { row ->
            DuplicateCandidate(
                id = row.id,
                accountId = row.accountId,
                counterAccountId = row.counterAccountId,
                linkId = row.linkId,
                amountMinor = row.amountMinor,
                currency = row.currency,
                type = enumValueOf<TransactionType>(row.type),
                occurredOn = LocalDate.parse(row.occurredOn),
                merchantNormalized = row.merchantNormalized,
                lastFour = accounts.firstOrNull { it.id == row.accountId }?.lastFour,
                deleted = row.deletedAtEpochMillis != null || row.status == TransactionStatus.DUPLICATE.name,
            )
        }
        val duplicate = DuplicateDetector.bestMatch(candidate, matches)
        val decision = com.ledgerline.app.detection.TransactionDetectionEngine.decide(
            com.ledgerline.app.detection.parser.ParsedTransaction(
                amountMinor = draft.amountMinor,
                merchant = draft.merchant.ifBlank { null },
                lastFour = account.lastFour,
                type = draft.type,
                parserId = draft.source.name,
            ),
            account,
            duplicate?.score,
        )
        if (decision.disposition == com.ledgerline.app.detection.DetectionDisposition.REJECT || decision.status == null) {
            return IngestResult.Rejected(decision.message)
        }
        val now = clock.now().toEpochMilli()
        val categoryId = if (normalized.isBlank()) {
            null
        } else {
            database.merchants().suggestedCategory(normalized, FinancePolicy.CATEGORY_LEARN_CONFIRMATIONS)
        }
        val effect = if (decision.status == TransactionStatus.DUPLICATE) {
            0L
        } else {
            BalanceMath.effectFor(draft.type, draft.amountMinor)
        }
        val id = Ids.new()
        return database.withTransaction {
            val merchantId = upsertMerchant(normalized, draft.merchant, now)
            insertRow(
                id = id,
                draft = TransactionDraft(
                    existingId = null,
                    existingLinkId = null,
                    type = draft.type,
                    amountMinor = draft.amountMinor,
                    merchant = draft.merchant,
                    accountId = account.id,
                    counterAccountId = null,
                    categoryId = categoryId,
                    occurredOn = draft.occurredOn,
                    note = "",
                    acknowledgeDuplicate = true,
                ),
                account = account,
                counter = null,
                effect = effect,
                merchantLabel = draft.merchant,
                normalized = normalized,
                merchantId = merchantId,
                linkId = null,
                now = now,
                created = now,
                status = decision.status,
                sourceKind = draft.source,
                confidence = decision.confidencePercent,
                userConfirmed = decision.status == TransactionStatus.CONFIRMED,
            )
            if (duplicate != null && decision.status != TransactionStatus.CONFIRMED) {
                database.matches().insert(
                    com.ledgerline.app.data.local.entity.TransactionMatchEntity(
                        id = Ids.new(),
                        survivorId = duplicate.existingId,
                        incomingId = id,
                        score = duplicate.score,
                        reasons = duplicate.reasons.joinToString(", "),
                        createdAtEpochMillis = now,
                    ),
                )
            }
            when (decision.disposition) {
                com.ledgerline.app.detection.DetectionDisposition.LINK -> IngestResult.Linked(duplicate?.existingId ?: id, duplicate?.score ?: decision.confidencePercent)
                com.ledgerline.app.detection.DetectionDisposition.REVIEW -> IngestResult.NeedsReview(id, duplicate?.score ?: 0)
                else -> IngestResult.Saved(id, decision.status)
            }
        }
    }

    override suspend fun setStatus(id: String, status: TransactionStatus) {
        val row = database.transactions().get(id) ?: return
        val now = clock.now().toEpochMilli()
        val type = enumValueOf<TransactionType>(row.type)
        val effect = when (status) {
            TransactionStatus.DUPLICATE, TransactionStatus.IGNORED -> 0L
            else -> if (row.balanceEffectMinor != 0L) row.balanceEffectMinor else BalanceMath.effectFor(type, row.amountMinor)
        }
        database.transactions().update(
            row.copy(
                status = status.name,
                balanceEffectMinor = effect,
                userConfirmed = status != TransactionStatus.DETECTED,
                updatedAtEpochMillis = now,
            ),
        )
    }

    private fun effect(draft: TransactionDraft): Long {
        if (draft.type == TransactionType.ADJUSTMENT) {
            return if (draft.adjustmentDecreases) -kotlin.math.abs(draft.amountMinor) else kotlin.math.abs(draft.amountMinor)
        }
        return BalanceMath.effectFor(draft.type, draft.amountMinor)
    }

    private suspend fun insertRow(
        id: String,
        draft: TransactionDraft,
        account: Account,
        counter: Account?,
        effect: Long,
        merchantLabel: String,
        normalized: String,
        merchantId: String?,
        linkId: String?,
        now: Long,
        created: Long,
        status: TransactionStatus,
        sourceKind: DetectionSource,
        confidence: Int = FinancePolicy.MANUAL_CONFIDENCE_PERCENT,
        userConfirmed: Boolean = true,
    ) {
        val normalizedForRow = MerchantNormalizer.normalize(merchantLabel).ifBlank { normalized }
        database.transactions().insert(
            TransactionEntity(
                id = id,
                accountId = account.id,
                counterAccountId = counter?.id,
                linkId = linkId,
                amountMinor = draft.amountMinor,
                balanceEffectMinor = effect,
                currency = account.currency,
                type = draft.type.name,
                merchantRaw = merchantLabel.trim(),
                merchantNormalized = normalizedForRow,
                merchantId = merchantId,
                description = draft.note.ifBlank { merchantLabel.trim() },
                categoryId = draft.categoryId,
                occurredOn = draft.occurredOn.toString(),
                postedOn = draft.occurredOn.toString(),
                detectionSource = sourceKind.name,
                confidencePercent = confidence,
                recurringPaymentId = null,
                status = status.name,
                userConfirmed = userConfirmed,
                note = draft.note.trim(),
                fingerprint = TransactionFingerprint.of(
                    account.currency,
                    draft.amountMinor,
                    account.id,
                    draft.occurredOn,
                    draft.type,
                    normalizedForRow,
                ),
                createdAtEpochMillis = created,
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = null,
                unplanned = draft.type == TransactionType.EXPENSE && draft.unplanned ||
                    draft.type == TransactionType.REFUND && draft.unplanned,
                paid = !(draft.notPaidYet && draft.type == TransactionType.EXPENSE),
                plannedOn = if (draft.notPaidYet && draft.type == TransactionType.EXPENSE) draft.occurredOn.toString() else null,
            ),
        )
    }

    private suspend fun updateRow(
        existing: TransactionEntity,
        draft: TransactionDraft,
        account: Account,
        counter: Account?,
        effect: Long,
        merchantLabel: String,
        normalized: String,
        merchantId: String?,
        linkId: String?,
        now: Long,
    ) {
        val normalizedForRow = MerchantNormalizer.normalize(merchantLabel).ifBlank { normalized }
        val sourceKind = if (existing.detectionSource == DetectionSource.MANUAL.name) {
            DetectionSource.MANUAL
        } else {
            DetectionSource.USER_CORRECTION
        }
        database.transactions().update(
            existing.copy(
                accountId = account.id,
                counterAccountId = counter?.id,
                linkId = linkId,
                amountMinor = draft.amountMinor,
                balanceEffectMinor = effect,
                currency = account.currency,
                type = draft.type.name,
                merchantRaw = merchantLabel.trim(),
                merchantNormalized = normalizedForRow,
                merchantId = merchantId,
                description = draft.note.ifBlank { merchantLabel.trim() },
                categoryId = draft.categoryId,
                occurredOn = draft.occurredOn.toString(),
                postedOn = draft.occurredOn.toString(),
                detectionSource = sourceKind.name,
                status = TransactionStatus.MODIFIED.name,
                userConfirmed = true,
                note = draft.note.trim(),
                fingerprint = TransactionFingerprint.of(
                    account.currency,
                    draft.amountMinor,
                    account.id,
                    draft.occurredOn,
                    draft.type,
                    normalizedForRow,
                ),
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = null,
                unplanned = draft.type == TransactionType.EXPENSE && draft.unplanned ||
                    draft.type == TransactionType.REFUND && draft.unplanned,
                paid = !(draft.notPaidYet && draft.type == TransactionType.EXPENSE),
                plannedOn = existing.plannedOn ?: if (draft.notPaidYet && draft.type == TransactionType.EXPENSE) draft.occurredOn.toString() else null,
            ),
        )
    }

    /** Marks a planned spend paid on [on]. It starts to count from that day; the planned day is kept. */
    override suspend fun markSpendPaid(id: String, on: java.time.LocalDate) {
        val row = database.transactions().get(id) ?: return
        if (row.paid) return
        database.transactions().update(
            row.copy(
                paid = true,
                plannedOn = row.plannedOn ?: row.occurredOn,
                occurredOn = on.toString(),
                postedOn = on.toString(),
                updatedAtEpochMillis = clock.now().toEpochMilli(),
            ),
        )
    }

    private suspend fun upsertMerchant(normalized: String, raw: String, now: Long): String? {
        if (normalized.isBlank()) return null
        val found = database.merchants().findByNormalized(normalized)
        if (found != null) return found.id
        val id = Ids.new()
        database.merchants().insert(
            MerchantEntity(
                id = id,
                displayName = raw.trim().ifBlank { normalized },
                normalizedName = normalized,
                createdAtEpochMillis = now,
            ),
        )
        return id
    }

    private suspend fun learn(merchantId: String, categoryId: String, now: Long) {
        val existing = database.merchants().ruleForMerchant(merchantId)
        if (existing == null) {
            database.merchants().upsertRule(
                MerchantCategoryRuleEntity(Ids.new(), merchantId, categoryId, 1, now),
            )
        } else if (existing.categoryId == categoryId) {
            database.merchants().upsertRule(existing.copy(confirmations = existing.confirmations + 1, updatedAtEpochMillis = now))
        } else {
            database.merchants().upsertRule(existing.copy(categoryId = categoryId, confirmations = 1, updatedAtEpochMillis = now))
        }
    }

    private fun defaultPairLabel(type: TransactionType, otherName: String?, outgoing: Boolean): String {
        val other = otherName ?: "account"
        val verb = if (type == TransactionType.PAYMENT) "Payment" else "Transfer"
        return if (outgoing) "$verb to $other" else "$verb from $other"
    }
}

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
) : CategoryRepository {
    override fun observe(): Flow<List<Category>> = database.categories().observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun ensureDefaults() {
        val now = clock.now().toEpochMilli()
        fun entity(seed: CategoryCatalog.Seed) = CategoryEntity(
            id = seed.id,
            parentId = seed.parentId,
            name = seed.name,
            necessity = seed.necessity.name,
            archived = seed.archived,
            sortOrder = seed.order,
            createdAtEpochMillis = now,
        )
        if (database.categories().count() == 0) {
            database.categories().insertAll(CategoryCatalog.ALL.map(::entity))
            return
        }
        // Existing data: add the standard categories, rename the ones that match, hide the old defaults.
        // Nothing is deleted and no transaction is re-pointed, so history keeps its original category.
        val plan = StandardCategories.plan(database.categories().getAll().map { it.toDomain() })
        if (plan.empty) return
        database.withTransaction {
            database.categories().insertAll(plan.insert.map(::entity))
            plan.reshape.forEach { seed ->
                database.categories().reshape(seed.id, seed.name, seed.parentId, seed.necessity.name, seed.order)
            }
            plan.archiveIds.forEach { id -> database.categories().setArchived(id, true) }
        }
    }

    override suspend fun addCustom(name: String, parentId: String?, necessity: Necessity) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val now = clock.now().toEpochMilli()
        database.categories().insert(
            CategoryEntity(
                id = Ids.new(),
                parentId = parentId,
                name = trimmed,
                necessity = necessity.name,
                archived = false,
                sortOrder = 1_000,
                createdAtEpochMillis = now,
            ),
        )
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        database.categories().setArchived(id, archived)
    }
}

@Singleton
class RecurringRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
    private val undoCenter: com.ledgerline.app.ui.undo.UndoCenter,
) : RecurringRepository {
    override fun observe(): Flow<List<RecurringPayment>> =
        database.recurring().observe().map { rows -> rows.map { it.toDomain() } }

    override fun observeAdjustments(): Flow<List<com.ledgerline.app.domain.model.OccurrenceAdjustment>> =
        database.occurrenceRecords().observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): RecurringPayment? = database.recurring().get(id)?.toDomain()

    override suspend fun save(
        draft: RecurringDraft,
        scope: SeriesScope,
        occurrenceOn: LocalDate?,
    ): SaveResult {
        val account = database.accounts().get(draft.accountId)?.toDomain()
        TransactionRules.validate(draft, account)?.let { return SaveResult.Invalid(it) }
        val source = checkNotNull(account)
        val now = clock.now().toEpochMilli()
        val existing = draft.existingId?.let { database.recurring().get(it) }
        if (existing != null && scope == SeriesScope.THIS_OCCURRENCE) {
            val date = occurrenceOn ?: return SaveResult.Invalid("Choose the payment date to change.")
            upsertOccurrence(
                recurringId = existing.id,
                date = date,
                state = OccurrenceState.SCHEDULED,
                amountMinor = draft.amountMinor,
                name = draft.name.trim(),
                paidTransactionId = database.occurrenceRecords().get(existing.id, date.toString())?.paidTransactionId,
                now = now,
            )
            return SaveResult.Saved
        }
        if (existing != null && scope == SeriesScope.THIS_AND_FUTURE && occurrenceOn != null && occurrenceOn.isAfter(existing.toDomain().startOn)) {
            val current = existing.toDomain()
            val ended = SeriesEdit.endBefore(current, occurrenceOn)
            database.recurring().update(
                existing.copy(
                    endOn = ended.endOn?.toString(),
                    active = ended.active,
                    updatedAtEpochMillis = now,
                ),
            )
            database.recurring().insert(
                entityFrom(draft, source, now, id = Ids.new(), startOn = occurrenceOn, maxOccurrences = draft.maxOccurrences),
            )
            return SaveResult.Saved
        }
        val entity = entityFrom(draft, source, now, id = existing?.id ?: draft.existingId ?: Ids.new(), startOn = draft.startOn, maxOccurrences = draft.maxOccurrences)
        if (existing == null) {
            database.recurring().insert(entity)
        } else {
            database.recurring().update(entity.copy(createdAtEpochMillis = existing.createdAtEpochMillis))
        }
        return SaveResult.Saved
    }

    override suspend fun delete(id: String, scope: SeriesScope, occurrenceOn: LocalDate?) {
        when (scope) {
            SeriesScope.ENTIRE_SERIES -> {
                val existing = database.recurring().get(id) ?: return
                database.recurring().delete(id)
                undoCenter.offer("Bill deleted") { database.recurring().insert(existing) }
            }
            SeriesScope.THIS_OCCURRENCE -> {
                val date = occurrenceOn ?: return
                skip(id, date)
            }
            SeriesScope.THIS_AND_FUTURE -> {
                val date = occurrenceOn ?: return
                val existing = database.recurring().get(id) ?: return
                val ended = SeriesEdit.endBefore(existing.toDomain(), date)
                database.recurring().update(
                    existing.copy(
                        endOn = ended.endOn?.toString(),
                        active = ended.active,
                        updatedAtEpochMillis = clock.now().toEpochMilli(),
                    ),
                )
                undoCenter.offer("Bill updated") { database.recurring().update(existing) }
            }
        }
    }

    override suspend fun markPaid(id: String, occurrenceOn: LocalDate, automatic: Boolean): SaveResult {
        val existing = database.recurring().get(id) ?: return SaveResult.Invalid("That payment is no longer on this device.")
        val item = existing.toDomain()
        val dateKey = occurrenceOn.toString()
        val current = database.occurrenceRecords().get(id, dateKey)
        if (current?.state == OccurrenceState.PAID.name) return SaveResult.Saved
        val now = clock.now().toEpochMilli()
        val adjustment = current?.toDomain()
        val amount = adjustment?.amountMinor?.takeIf { it > 0 } ?: item.amountMinor
        val name = adjustment?.name?.takeIf { it.isNotBlank() } ?: item.name
        val result = database.withTransaction {
            val linked = database.transactions().forOccurrence(id, dateKey)
            val transactionId = if (linked != null && linked.status in POSTED_STATUSES) {
                linked.id
            } else {
                val type = OccurrenceMaterializer.postedTransactionType(item)
                val createdId = Ids.new()
                val normalized = MerchantNormalizer.normalize(name)
                database.transactions().insert(
                    TransactionEntity(
                        id = createdId,
                        accountId = item.accountId,
                        counterAccountId = null,
                        linkId = null,
                        amountMinor = amount,
                        balanceEffectMinor = BalanceMath.effectFor(type, amount),
                        currency = item.currency,
                        type = type.name,
                        merchantRaw = name,
                        merchantNormalized = normalized.ifBlank { name },
                        merchantId = null,
                        description = name,
                        categoryId = item.categoryId,
                        occurredOn = dateKey,
                        postedOn = dateKey,
                        detectionSource = DetectionSource.MANUAL.name,
                        confidencePercent = FinancePolicy.MANUAL_CONFIDENCE_PERCENT,
                        recurringPaymentId = id,
                        status = TransactionStatus.CONFIRMED.name,
                        userConfirmed = true,
                        note = if (automatic) "Paid automatically on the due date (autopay)." else "Marked paid from the payment calendar.",
                        fingerprint = TransactionFingerprint.of(item.currency, amount, item.accountId, occurrenceOn, type, normalized.ifBlank { name }),
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        deletedAtEpochMillis = null,
                    ),
                )
                createdId
            }
            upsertOccurrence(
                recurringId = id,
                date = occurrenceOn,
                state = OccurrenceState.PAID,
                amountMinor = amount,
                name = name,
                paidTransactionId = transactionId,
                now = now,
            )
            SaveResult.Saved
        }
        // Automatic payments happen in the background; an undo notice for them would appear out of nowhere.
        if (result is SaveResult.Saved && !automatic) {
            val posted = database.transactions().forOccurrence(id, dateKey)
            undoCenter.offer("Marked paid") {
                val stamp = clock.now().toEpochMilli()
                database.withTransaction {
                    if (posted != null && current?.paidTransactionId != posted.id) {
                        database.transactions().update(posted.copy(deletedAtEpochMillis = stamp, updatedAtEpochMillis = stamp))
                    }
                    if (current != null) {
                        database.occurrenceRecords().upsert(current)
                    } else {
                        upsertOccurrence(id, occurrenceOn, OccurrenceState.SCHEDULED, amount, name, null, stamp)
                    }
                }
            }
        }
        return result
    }

    override suspend fun skip(id: String, occurrenceOn: LocalDate): SaveResult {
        val existing = database.recurring().get(id) ?: return SaveResult.Invalid("That payment is no longer on this device.")
        val current = database.occurrenceRecords().get(id, occurrenceOn.toString())
        if (current?.state == OccurrenceState.PAID.name) {
            return SaveResult.Invalid("That date is already paid. The posted transaction stays on the account.")
        }
        upsertOccurrence(
            recurringId = existing.id,
            date = occurrenceOn,
            state = OccurrenceState.SKIPPED,
            amountMinor = current?.amountMinor,
            name = current?.name,
            paidTransactionId = null,
            now = clock.now().toEpochMilli(),
        )
        undoCenter.offer("Skipped date restored") {
            if (current != null) {
                database.occurrenceRecords().upsert(current)
            } else {
                upsertOccurrence(existing.id, occurrenceOn, OccurrenceState.SCHEDULED, null, null, null, clock.now().toEpochMilli())
            }
        }
        return SaveResult.Saved
    }

    override suspend fun setOccurrence(id: String, occurrenceOn: LocalDate, amountMinor: Long?, skipped: Boolean): SaveResult {
        val existing = database.recurring().get(id) ?: return SaveResult.Invalid("That payment is no longer on this device.")
        val current = database.occurrenceRecords().get(id, occurrenceOn.toString())
        if (current?.state == OccurrenceState.PAID.name) return SaveResult.Invalid("That date is already paid.")
        upsertOccurrence(
            recurringId = existing.id,
            date = occurrenceOn,
            state = if (skipped) OccurrenceState.SKIPPED else OccurrenceState.SCHEDULED,
            amountMinor = amountMinor?.takeIf { it > 0 },
            name = current?.name,
            paidTransactionId = null,
            now = clock.now().toEpochMilli(),
        )
        return SaveResult.Saved
    }

    private fun entityFrom(
        draft: RecurringDraft,
        source: com.ledgerline.app.domain.model.Account,
        now: Long,
        id: String,
        startOn: LocalDate,
        maxOccurrences: Int?,
    ) = RecurringPaymentEntity(
        id = id,
        name = draft.name.trim(),
        accountId = source.id,
        categoryId = draft.categoryId,
        direction = draft.direction.name,
        kind = draft.kind.name,
        amountMinor = draft.amountMinor,
        currency = source.currency,
        frequency = draft.frequency.name,
        intervalDays = if (draft.frequency == RecurrenceFrequency.CUSTOM) draft.intervalDays else null,
        dayOfMonth = startOn.dayOfMonth,
        dayOfWeek = startOn.dayOfWeek.value,
        startOn = startOn.toString(),
        endOn = if (draft.frequency == RecurrenceFrequency.ONCE) null else draft.endOn?.toString(),
        priority = draft.priority.name,
        incomeConfidence = if (draft.direction == com.ledgerline.app.domain.model.CashFlowDirection.INFLOW) {
            draft.incomeConfidence.name
        } else {
            com.ledgerline.app.domain.model.IncomeConfidence.NONE.name
        },
        active = draft.active,
        note = draft.note.trim(),
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
        maxOccurrences = if (draft.frequency == RecurrenceFrequency.ONCE) 1 else maxOccurrences,
        autopaySince = draft.autopaySince?.toString(),
    )

    private suspend fun upsertOccurrence(
        recurringId: String,
        date: LocalDate,
        state: OccurrenceState,
        amountMinor: Long?,
        name: String?,
        paidTransactionId: String?,
        now: Long,
    ) {
        val key = date.toString()
        val previous = database.occurrenceRecords().get(recurringId, key)
        database.occurrenceRecords().upsert(
            RecurringOccurrenceEntity(
                id = previous?.id ?: "$recurringId|$key",
                recurringPaymentId = recurringId,
                occurrenceOn = key,
                state = state.name,
                amountMinor = amountMinor,
                name = name,
                paidTransactionId = paidTransactionId,
                note = "",
                createdAtEpochMillis = previous?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    private companion object {
        val POSTED_STATUSES = setOf(
            TransactionStatus.CONFIRMED.name,
            TransactionStatus.RECONCILED.name,
            TransactionStatus.MODIFIED.name,
        )
    }
}

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
) : BudgetRepository {
    override fun observe(): Flow<List<Budget>> = database.budgets().observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): Budget? = database.budgets().getAll().firstOrNull { it.id == id }?.toDomain()

    override suspend fun save(draft: BudgetDraft): SaveResult {
        TransactionRules.validate(draft)?.let { return SaveResult.Invalid(it) }
        val currency = database.settings().get()?.primaryCurrency ?: "USD"
        val now = clock.now().toEpochMilli()
        val existing = draft.existingId?.let { id -> database.budgets().getAll().firstOrNull { it.id == id } }
        val entity = BudgetEntity(
            id = existing?.id ?: Ids.new(),
            name = draft.name.trim(),
            categoryId = draft.categoryId,
            accountId = if (draft.setAccount) draft.accountId else existing?.accountId,
            period = draft.period.name,
            amountMinor = draft.amountMinor,
            currency = currency,
            reservesSafeToSpend = draft.reservesSafeToSpend,
            active = draft.active,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            startOn = if (draft.setSchedule) draft.startOn?.toString() else existing?.startOn,
            endOn = if (draft.setSchedule) draft.endOn?.toString() else existing?.endOn,
            maxCycles = if (draft.setSchedule) draft.maxCycles else existing?.maxCycles,
        )
        if (existing == null) database.budgets().insert(entity) else database.budgets().update(entity)
        return SaveResult.Saved
    }

    override suspend fun delete(id: String) {
        database.budgets().delete(id)
    }
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
) : SettingsRepository {
    override fun observe(): Flow<UserSettings> = database.settings().observe().map { row ->
        (row ?: defaultSettings(clock.now().toEpochMilli())).toDomain()
    }

    override suspend fun ensureDefault() {
        if (database.settings().get() == null) {
            database.settings().upsert(defaultSettings(clock.now().toEpochMilli()))
        }
    }

    override suspend fun update(settings: UserSettings) {
        val current = database.settings().get()
        database.settings().upsert(
            defaultSettings(clock.now().toEpochMilli()).copy(
                primaryCurrency = settings.primaryCurrency,
                minimumBufferMinor = settings.minimumBufferMinor.coerceAtLeast(0),
                horizonDays = TransactionRules.clampHorizon(settings.horizonDays),
                onboardingCompleted = settings.onboardingCompleted,
                cycleType = settings.cycleType.name,
                cycleAnchor = settings.cycleAnchor.toString(),
                cycleStartDay = settings.cycleStartDay.coerceIn(1, 31),
                cycleSecondDay = settings.cycleSecondDay.coerceIn(1, 31),
                updatedAtEpochMillis = clock.now().toEpochMilli(),
                id = current?.id ?: 1,
            ),
        )
    }
}

@Singleton
class MerchantRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
) : MerchantRepository {
    override suspend fun suggestion(normalizedName: String, minimumConfirmations: Int): String? {
        if (normalizedName.isBlank()) return null
        return database.merchants().suggestedCategory(normalizedName, minimumConfirmations)
    }
}

@Singleton
class LedgerReadModelImpl @Inject constructor(
    private val database: LedgerDatabase,
) : LedgerReadModel {
    override suspend fun accounts(): List<Account> = database.accounts().getAll().map { it.toDomain() }

    override suspend fun accountsIncludingDeleted(): List<Account> =
        database.accounts().getAllIncludingDeleted().map { it.toDomain() }

    override suspend fun transactions(): List<LedgerTransaction> = database.transactions().getAll().map { it.toDomain() }

    override suspend fun categories(): List<Category> = database.categories().getAll().map { it.toDomain() }

    override suspend fun recurring(): List<RecurringPayment> = database.recurring().getAll().map { it.toDomain() }

    override suspend fun budgets(): List<Budget> = database.budgets().getAll().map { it.toDomain() }

    override suspend fun settings(): UserSettings = (database.settings().get() ?: defaultSettings(0)).toDomain()

    override suspend fun rules(): List<MerchantCategoryRule> = emptyList()
}
