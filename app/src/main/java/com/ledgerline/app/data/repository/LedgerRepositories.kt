package com.ledgerline.app.data.repository

import androidx.room.withTransaction
import com.ledgerline.app.data.Ids
import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.entity.AccountEntity
import com.ledgerline.app.data.local.entity.BudgetEntity
import com.ledgerline.app.data.local.entity.CategoryEntity
import com.ledgerline.app.data.local.entity.MerchantCategoryRuleEntity
import com.ledgerline.app.data.local.entity.MerchantEntity
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
import com.ledgerline.app.domain.model.RecurringDraft
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.SaveResult
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
import com.ledgerline.app.finance.duplicate.DuplicateCandidate
import com.ledgerline.app.finance.duplicate.DuplicateDetector
import com.ledgerline.app.finance.fingerprint.TransactionFingerprint
import com.ledgerline.app.finance.merchant.MerchantNormalizer
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.policy.FinancePolicy
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
) : AccountRepository {
    override fun observe(): Flow<List<Account>> = database.accounts().observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): Account? = database.accounts().get(id)?.toDomain()

    override suspend fun save(draft: AccountDraft): SaveResult {
        TransactionRules.validate(draft)?.let { return SaveResult.Invalid(it) }
        val now = clock.now().toEpochMilli()
        val currency = database.settings().get()?.primaryCurrency ?: "USD"
        val target = signedBalance(draft)
        return database.withTransaction {
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
                        insertAdjustment(
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

    private suspend fun insertAdjustment(accountId: String, currency: String, delta: Long, now: Long, today: LocalDate) {
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
    }

    private fun signedBalance(draft: AccountDraft): Long =
        if (BalanceMath.isDebtType(draft.type)) -draft.enteredBalanceMinor else draft.enteredBalanceMinor
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
) : TransactionRepository {
    override fun observeAll(): Flow<List<LedgerTransaction>> =
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
        return database.withTransaction {
            val merchantId = upsertMerchant(normalized, draft.merchant, now)
            val paired = draft.type == TransactionType.TRANSFER || draft.type == TransactionType.PAYMENT
            if (draft.existingId == null) {
                val linkId = if (paired) Ids.new() else null
                insertRow(
                    id = Ids.new(),
                    draft = draft,
                    account = source,
                    counter = counter,
                    effect = BalanceMath.effectFor(draft.type, draft.amountMinor),
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
                    insertRow(
                        id = Ids.new(),
                        draft = draft,
                        account = counter,
                        counter = source,
                        effect = -BalanceMath.effectFor(draft.type, draft.amountMinor),
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
                    updateRow(primary, draft, source, counter, BalanceMath.effectFor(draft.type, draft.amountMinor), draft.merchant.ifBlank { primary.merchantRaw }, normalized, merchantId, linkId, now)
                    if (paired && counter != null) {
                        if (secondary != null && secondary.deletedAtEpochMillis == null) {
                            updateRow(
                                secondary,
                                draft,
                                counter,
                                source,
                                -BalanceMath.effectFor(draft.type, draft.amountMinor),
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
                                effect = -BalanceMath.effectFor(draft.type, draft.amountMinor),
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
    }

    override suspend fun softDelete(id: String) {
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
        if (database.categories().count() > 0) return
        val now = clock.now().toEpochMilli()
        database.categories().insertAll(
            CategoryCatalog.ALL.map { seed ->
                CategoryEntity(
                    id = seed.id,
                    parentId = seed.parentId,
                    name = seed.name,
                    necessity = seed.necessity.name,
                    archived = false,
                    sortOrder = seed.order,
                    createdAtEpochMillis = now,
                )
            },
        )
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
) : RecurringRepository {
    override fun observe(): Flow<List<RecurringPayment>> =
        database.recurring().observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): RecurringPayment? = database.recurring().get(id)?.toDomain()

    override suspend fun save(draft: RecurringDraft): SaveResult {
        val account = database.accounts().get(draft.accountId)?.toDomain()
        TransactionRules.validate(draft, account)?.let { return SaveResult.Invalid(it) }
        val source = checkNotNull(account)
        val now = clock.now().toEpochMilli()
        val entity = RecurringPaymentEntity(
            id = draft.existingId ?: Ids.new(),
            name = draft.name.trim(),
            accountId = source.id,
            categoryId = draft.categoryId,
            direction = draft.direction.name,
            kind = draft.kind.name,
            amountMinor = draft.amountMinor,
            currency = source.currency,
            frequency = draft.frequency.name,
            intervalDays = draft.intervalDays,
            dayOfMonth = draft.dayOfMonth,
            dayOfWeek = draft.dayOfWeek,
            startOn = draft.startOn.toString(),
            endOn = null,
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
        )
        val existing = draft.existingId?.let { database.recurring().get(it) }
        if (existing == null) {
            database.recurring().insert(entity)
        } else {
            database.recurring().update(entity.copy(createdAtEpochMillis = existing.createdAtEpochMillis))
        }
        return SaveResult.Saved
    }

    override suspend fun delete(id: String) {
        database.recurring().delete(id)
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
            accountId = null,
            period = draft.period.name,
            amountMinor = draft.amountMinor,
            currency = currency,
            reservesSafeToSpend = draft.reservesSafeToSpend,
            active = draft.active,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
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

    override suspend fun transactions(): List<LedgerTransaction> = database.transactions().getAll().map { it.toDomain() }

    override suspend fun categories(): List<Category> = database.categories().getAll().map { it.toDomain() }

    override suspend fun recurring(): List<RecurringPayment> = database.recurring().getAll().map { it.toDomain() }

    override suspend fun budgets(): List<Budget> = database.budgets().getAll().map { it.toDomain() }

    override suspend fun settings(): UserSettings = (database.settings().get() ?: defaultSettings(0)).toDomain()

    override suspend fun rules(): List<MerchantCategoryRule> = emptyList()
}
