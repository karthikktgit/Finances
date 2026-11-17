package com.ledgerline.app.data.repository

import androidx.room.withTransaction
import com.ledgerline.app.data.Ids
import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.entity.DebtEntity
import com.ledgerline.app.data.local.entity.DebtMonthPlanEntity
import com.ledgerline.app.data.local.entity.DebtRateChangeEntity
import com.ledgerline.app.data.local.entity.DebtSettlementEntity
import com.ledgerline.app.data.local.entity.NotificationSourceEntity
import com.ledgerline.app.data.local.entity.SyncRecordEntity
import com.ledgerline.app.data.local.entity.TransactionEntity
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtDraft
import com.ledgerline.app.domain.model.DebtMonthPlan
import com.ledgerline.app.domain.model.DebtRateChange
import com.ledgerline.app.domain.model.DebtSettlement
import com.ledgerline.app.domain.model.DebtType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.NotificationSource
import com.ledgerline.app.domain.model.PayChoice
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RateType
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringDraft
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncRecord
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.repository.DebtRepository
import com.ledgerline.app.domain.repository.NotificationSourceRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SyncLogRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.debt.DebtForecast
import com.ledgerline.app.finance.debt.ForecastInput
import com.ledgerline.app.finance.debt.RatePoint
import com.ledgerline.app.finance.fingerprint.TransactionFingerprint
import com.ledgerline.app.finance.merchant.MerchantNormalizer
import com.ledgerline.app.finance.policy.FinancePolicy
import com.ledgerline.app.finance.recurring.RecurringDetector
import com.ledgerline.app.finance.subscription.SubscriptionRollup
import com.ledgerline.app.ui.undo.UndoCenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebtRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
    private val undoCenter: UndoCenter,
) : DebtRepository {
    override fun observe(): Flow<List<Debt>> = database.debts().observe().map { rows -> rows.map { it.toDomain() } }

    override fun observePlans(): Flow<List<DebtMonthPlan>> =
        database.debtPlans().observe().map { rows -> rows.map { it.toDomain() } }

    override fun observeRates(): Flow<List<DebtRateChange>> =
        database.debtRates().observe().map { rows -> rows.map { it.toDomain() } }

    override fun observeSettlements(): Flow<List<DebtSettlement>> =
        database.debtSettlements().observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(draft: DebtDraft): SaveResult {
        val name = draft.name.trim()
        if (name.isEmpty()) return SaveResult.Invalid("Name the debt.")
        if (draft.balanceMinor <= 0) return SaveResult.Invalid("Enter the current balance owed.")
        if (draft.dueDayOfMonth !in 1..31) return SaveResult.Invalid("The due day must be from 1 to 31.")
        if (draft.statementCloseDay != null && draft.statementCloseDay !in 1..31) {
            return SaveResult.Invalid("The statement closing day must be from 1 to 31.")
        }
        if (draft.aprKnown) {
            val apr = draft.aprBasisPoints
            if (apr == null || apr !in 0..10_000) return SaveResult.Invalid("Enter an APR from 0 to 100, or leave it unknown.")
        }
        if (draft.minimumKnown && (draft.minimumPaymentMinor == null || draft.minimumPaymentMinor < 0)) {
            return SaveResult.Invalid("Enter the minimum payment from the statement, or leave it unknown.")
        }
        if (draft.dayCountBasis !in 360..366) return SaveResult.Invalid("The day-count basis must be from 360 to 366.")
        val currency = database.settings().get()?.primaryCurrency ?: "USD"
        val now = clock.now().toEpochMilli()
        val existing = draft.existingId?.let { database.debts().get(it) }
        val entity = debtEntity(draft, name, currency, now, existing)
        if (existing == null) database.debts().insert(entity) else database.debts().update(entity)
        return SaveResult.Saved
    }

    override suspend fun delete(id: String) {
        database.debts().delete(id)
    }

    override suspend fun savePlan(debtId: String, yearMonth: YearMonth, plannedPaymentMinor: Long, dueOn: LocalDate?): SaveResult {
        if (database.debts().get(debtId) == null) return SaveResult.Invalid("That debt is no longer on this device.")
        if (plannedPaymentMinor < 0) return SaveResult.Invalid("The planned payment cannot be negative.")
        val key = yearMonth.toString()
        if (database.debtSettlements().forMonth(debtId, key) != null) {
            return SaveResult.Invalid("That month is already marked paid. The completed payment stays as it was.")
        }
        if (dueOn != null && YearMonth.from(dueOn) != yearMonth) {
            return SaveResult.Invalid("The payment date has to fall in $yearMonth.")
        }
        val now = clock.now().toEpochMilli()
        val current = database.debtPlans().forMonth(debtId, key)
        database.debtPlans().upsert(
            DebtMonthPlanEntity(
                id = current?.id ?: Ids.new(),
                debtId = debtId,
                yearMonth = key,
                plannedPaymentMinor = plannedPaymentMinor,
                dueOn = dueOn?.toString(),
                updatedAtEpochMillis = now,
            ),
        )
        return SaveResult.Saved
    }

    override suspend fun addRateChange(debtId: String, effectiveOn: LocalDate, aprBasisPoints: Int): SaveResult {
        if (database.debts().get(debtId) == null) return SaveResult.Invalid("That debt is no longer on this device.")
        if (aprBasisPoints !in 0..10_000) return SaveResult.Invalid("Enter an APR from 0 to 100.")
        database.debtRates().insert(
            DebtRateChangeEntity(Ids.new(), debtId, effectiveOn.toString(), aprBasisPoints, ""),
        )
        return SaveResult.Saved
    }

    override suspend fun setShowInCalendar(debtId: String, show: Boolean) {
        val existing = database.debts().get(debtId) ?: return
        database.debts().update(existing.copy(showInCalendar = show, updatedAtEpochMillis = clock.now().toEpochMilli()))
    }

    override suspend fun markMonthPaid(debtId: String, yearMonth: YearMonth): SaveResult {
        val debt = database.debts().get(debtId)?.toDomain() ?: return SaveResult.Invalid("That debt is no longer on this device.")
        val key = yearMonth.toString()
        if (database.debtSettlements().forMonth(debtId, key) != null) {
            return SaveResult.Invalid("That month is already linked to a payment.")
        }
        val sourceId = debt.paymentAccountId ?: return SaveResult.Invalid("Choose the account the payment comes from.")
        val liabilityId = debt.accountId ?: return SaveResult.Invalid("Link the card or loan account before recording a payment.")
        if (sourceId == liabilityId) return SaveResult.Invalid("The paying account and the debt account have to be different.")
        val source = database.accounts().get(sourceId) ?: return SaveResult.Invalid("The paying account is no longer on this device.")
        val liability = database.accounts().get(liabilityId) ?: return SaveResult.Invalid("The linked debt account is no longer on this device.")
        val plan = database.debtPlans().forMonth(debtId, key)
        val amount = plan?.plannedPaymentMinor ?: debt.minimumPaymentMinor.takeIf { debt.minimumKnown }
            ?: return SaveResult.Invalid("Enter this month's planned payment, or the minimum from the statement.")
        if (amount <= 0) return SaveResult.Invalid("Enter a payment amount greater than zero.")
        val due = plan?.dueOn?.let(LocalDate::parse) ?: DebtForecast.project(
            forecastInput(debt, yearMonth, emptyList(), amount),
        ).months.firstOrNull()?.dueOn ?: yearMonth.atDay(debt.dueDayOfMonth.coerceIn(1, yearMonth.lengthOfMonth()))
        val rates = database.debtRates().observe().first().filter { it.debtId == debtId }.map {
            RatePoint(LocalDate.parse(it.effectiveOn), it.aprBasisPoints)
        }
        val projection = DebtForecast.project(forecastInput(debt, yearMonth, rates, amount))
        val line = projection.months.firstOrNull()
        val splitKnown = line?.interestPaidMinor != null && line.principalPaidMinor != null && debt.aprKnown
        val now = clock.now().toEpochMilli()
        val linkId = Ids.new()
        val sourceTx = Ids.new()
        val liabilityTx = Ids.new()
        val settlementId = Ids.new()
        val name = "${debt.name} payment"
        val normalized = MerchantNormalizer.normalize(name)
        database.withTransaction {
            insertPaymentLeg(sourceTx, source.id, liability.id, amount, BalanceMath.effectFor(TransactionType.PAYMENT, amount), debt.currency, name, normalized, due, linkId, now, sourceTx)
            insertPaymentLeg(liabilityTx, liability.id, source.id, amount, -BalanceMath.effectFor(TransactionType.PAYMENT, amount), debt.currency, name, normalized, due, linkId, now, liabilityTx)
            database.debtSettlements().insert(
                DebtSettlementEntity(
                    id = settlementId,
                    debtId = debtId,
                    yearMonth = key,
                    paidOn = due.toString(),
                    amountMinor = amount,
                    transactionId = sourceTx,
                    linkId = linkId,
                    interestMinor = if (splitKnown) line!!.interestPaidMinor!! else 0,
                    principalMinor = if (splitKnown) line!!.principalPaidMinor!! else 0,
                    feeMinor = debt.feeMinor.coerceAtLeast(0),
                    splitKnown = splitKnown,
                ),
            )
        }
        undoCenter.offer("Payment recorded") {
            database.withTransaction {
                val stamp = clock.now().toEpochMilli()
                listOf(sourceTx, liabilityTx).forEach { id ->
                    database.transactions().get(id)?.let { row ->
                        database.transactions().update(row.copy(deletedAtEpochMillis = stamp, updatedAtEpochMillis = stamp))
                    }
                }
                database.debtSettlements().delete(settlementId)
            }
        }
        return SaveResult.Saved
    }

    override suspend fun recordInterest(debtId: String, amountMinor: Long, on: LocalDate): SaveResult {
        if (amountMinor <= 0) return SaveResult.Invalid("Enter the interest charged on the statement.")
        val existing = database.debts().get(debtId) ?: return SaveResult.Invalid("That debt is no longer on this device.")
        val liabilityId = existing.accountId ?: return SaveResult.Invalid("Link the card or loan account before recording interest.")
        val liability = database.accounts().get(liabilityId) ?: return SaveResult.Invalid("The linked debt account is no longer on this device.")
        val now = clock.now().toEpochMilli()
        val txId = Ids.new()
        val name = "${existing.name} interest"
        val normalized = MerchantNormalizer.normalize(name)
        val updatedDebt = existing.copy(
            balanceMinor = existing.balanceMinor + amountMinor,
            updatedAtEpochMillis = now,
        )
        database.withTransaction {
            database.transactions().insert(
                TransactionEntity(
                    id = txId,
                    accountId = liability.id,
                    counterAccountId = null,
                    linkId = null,
                    amountMinor = amountMinor,
                    balanceEffectMinor = -amountMinor,
                    currency = existing.currency,
                    type = TransactionType.ADJUSTMENT.name,
                    merchantRaw = name,
                    merchantNormalized = normalized.ifBlank { name },
                    merchantId = null,
                    description = name,
                    categoryId = null,
                    occurredOn = on.toString(),
                    postedOn = on.toString(),
                    detectionSource = DetectionSource.MANUAL.name,
                    confidencePercent = FinancePolicy.MANUAL_CONFIDENCE_PERCENT,
                    recurringPaymentId = null,
                    status = TransactionStatus.CONFIRMED.name,
                    userConfirmed = true,
                    note = "Interest confirmed from a statement. This was not a projection.",
                    fingerprint = TransactionFingerprint.of(existing.currency, amountMinor, liability.id, on, TransactionType.ADJUSTMENT, normalized.ifBlank { name }),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    deletedAtEpochMillis = null,
                ),
            )
            database.debts().update(updatedDebt)
        }
        undoCenter.offer("Interest added") {
            database.withTransaction {
                val stamp = clock.now().toEpochMilli()
                database.transactions().get(txId)?.let { row ->
                    database.transactions().update(row.copy(deletedAtEpochMillis = stamp, updatedAtEpochMillis = stamp))
                }
                database.debts().update(existing)
            }
        }
        return SaveResult.Saved
    }

    override suspend fun adoptAccountOwed(debtId: String): SaveResult {
        val existing = database.debts().get(debtId) ?: return SaveResult.Invalid("That debt is no longer on this device.")
        val accountId = existing.accountId ?: return SaveResult.Invalid("Link an account before matching balances.")
        val account = database.accounts().get(accountId) ?: return SaveResult.Invalid("The linked account is no longer on this device.")
        val owed = BalanceMath.amountOwedMinor(signedBalance(account.openingBalanceMinor, accountId))
        if (owed <= 0) return SaveResult.Invalid("The linked account does not show an amount owed.")
        database.debts().update(existing.copy(balanceMinor = owed, updatedAtEpochMillis = clock.now().toEpochMilli()))
        return SaveResult.Saved
    }

    override suspend fun postAccountCorrection(debtId: String): SaveResult {
        val existing = database.debts().get(debtId) ?: return SaveResult.Invalid("That debt is no longer on this device.")
        val accountId = existing.accountId ?: return SaveResult.Invalid("Link an account before correcting it.")
        val account = database.accounts().get(accountId) ?: return SaveResult.Invalid("The linked account is no longer on this device.")
        val current = signedBalance(account.openingBalanceMinor, accountId)
        val target = -existing.balanceMinor
        val effect = target - current
        if (effect == 0L) return SaveResult.Invalid("The account already matches this debt balance.")
        val now = clock.now().toEpochMilli()
        val txId = Ids.new()
        val on = clock.today()
        val name = "${existing.name} balance correction"
        val normalized = MerchantNormalizer.normalize(name)
        database.transactions().insert(
            TransactionEntity(
                id = txId,
                accountId = accountId,
                counterAccountId = null,
                linkId = null,
                amountMinor = kotlin.math.abs(effect),
                balanceEffectMinor = effect,
                currency = existing.currency,
                type = TransactionType.ADJUSTMENT.name,
                merchantRaw = name,
                merchantNormalized = normalized.ifBlank { name },
                merchantId = null,
                description = name,
                categoryId = null,
                occurredOn = on.toString(),
                postedOn = on.toString(),
                detectionSource = DetectionSource.MANUAL.name,
                confidencePercent = FinancePolicy.MANUAL_CONFIDENCE_PERCENT,
                recurringPaymentId = null,
                status = TransactionStatus.CONFIRMED.name,
                userConfirmed = true,
                note = "Correction so the account matches the debt balance you confirmed.",
                fingerprint = TransactionFingerprint.of(existing.currency, kotlin.math.abs(effect), accountId, on, TransactionType.ADJUSTMENT, normalized.ifBlank { name } + txId),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = null,
            ),
        )
        undoCenter.offer("Balance corrected") {
            val stamp = clock.now().toEpochMilli()
            database.transactions().get(txId)?.let { row ->
                database.transactions().update(row.copy(deletedAtEpochMillis = stamp, updatedAtEpochMillis = stamp))
            }
        }
        return SaveResult.Saved
    }

    private suspend fun signedBalance(opening: Long, accountId: String): Long {
        val movement = database.transactions().activeForAccount(accountId).sumOf { it.balanceEffectMinor }
        return opening + movement
    }

    private fun debtEntity(draft: DebtDraft, name: String, currency: String, now: Long, existing: DebtEntity?): DebtEntity {
        val id = existing?.id ?: Ids.new()
        return DebtEntity(
            id = id,
            name = name,
            accountId = draft.accountId,
            principalMinor = existing?.principalMinor ?: draft.balanceMinor,
            balanceMinor = draft.balanceMinor,
            aprBasisPoints = if (draft.aprKnown) draft.aprBasisPoints ?: 0 else 0,
            minimumPaymentMinor = if (draft.minimumKnown) draft.minimumPaymentMinor ?: 0 else 0,
            dueDayOfMonth = draft.dueDayOfMonth,
            frequency = existing?.frequency ?: RecurrenceFrequency.MONTHLY.name,
            priority = draft.priority.name,
            currency = existing?.currency ?: currency,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            debtType = draft.debtType.name,
            rateType = draft.rateType.name,
            paymentAccountId = draft.paymentAccountId,
            statementCloseDay = draft.statementCloseDay,
            originationOn = draft.originationOn?.toString(),
            maturityOn = draft.maturityOn?.toString(),
            remainingTermMonths = draft.remainingTermMonths,
            feeMinor = draft.feeMinor.coerceAtLeast(0),
            rateUpdatedOn = draft.rateUpdatedOn?.toString(),
            accruedInterestMinor = draft.accruedInterestMinor,
            showInCalendar = draft.showInCalendar,
            aprKnown = draft.aprKnown,
            minimumKnown = draft.minimumKnown,
            purchaseAprBasisPoints = draft.purchaseAprBasisPoints,
            balanceTransferAprBasisPoints = draft.balanceTransferAprBasisPoints,
            cashAdvanceAprBasisPoints = draft.cashAdvanceAprBasisPoints,
            graceOnPurchases = draft.graceOnPurchases,
            dayCountBasis = draft.dayCountBasis,
            recurringPaymentId = draft.recurringPaymentId,
            note = draft.note.trim(),
        )
    }

    private fun forecastInput(debt: Debt, month: YearMonth, rates: List<RatePoint>, payment: Long) = ForecastInput(
        openingMinor = debt.balanceMinor,
        start = month,
        monthCount = 1,
        dueDayOfMonth = debt.dueDayOfMonth,
        aprKnown = debt.aprKnown,
        aprBasisPoints = debt.aprBasisPoints,
        rateUpdatedOn = debt.rateUpdatedOn,
        rateChanges = rates,
        dayCountBasis = debt.dayCountBasis,
        minimumKnown = true,
        minimumMinor = payment,
        feeMinor = debt.feeMinor,
        accruedInterestMinor = debt.accruedInterestMinor,
        plans = mapOf(month to payment),
        dueOverrides = emptyMap(),
        choice = PayChoice.FIXED,
        fixedMinor = payment,
        statementCloseDay = debt.statementCloseDay,
    )

    private suspend fun insertPaymentLeg(
        id: String,
        accountId: String,
        counterAccountId: String,
        amount: Long,
        effect: Long,
        currency: String,
        name: String,
        normalized: String,
        due: LocalDate,
        linkId: String,
        now: Long,
        fingerprintSalt: String,
    ) {
        database.transactions().insert(
            TransactionEntity(
                id = id,
                accountId = accountId,
                counterAccountId = counterAccountId,
                linkId = linkId,
                amountMinor = amount,
                balanceEffectMinor = effect,
                currency = currency,
                type = TransactionType.PAYMENT.name,
                merchantRaw = name,
                merchantNormalized = normalized.ifBlank { name },
                merchantId = null,
                description = name,
                categoryId = null,
                occurredOn = due.toString(),
                postedOn = due.toString(),
                detectionSource = DetectionSource.MANUAL.name,
                confidencePercent = FinancePolicy.MANUAL_CONFIDENCE_PERCENT,
                recurringPaymentId = null,
                status = TransactionStatus.CONFIRMED.name,
                userConfirmed = true,
                note = "Debt payment. One linked pair updates the paying account and the amount owed.",
                fingerprint = TransactionFingerprint.of(currency, amount, accountId, due, TransactionType.PAYMENT, fingerprintSalt),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = null,
            ),
        )
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
    debtType = enumValueOf<DebtType>(debtType),
    rateType = enumValueOf<RateType>(rateType),
    paymentAccountId = paymentAccountId,
    statementCloseDay = statementCloseDay,
    originationOn = originationOn?.let(LocalDate::parse),
    maturityOn = maturityOn?.let(LocalDate::parse),
    remainingTermMonths = remainingTermMonths,
    feeMinor = feeMinor,
    rateUpdatedOn = rateUpdatedOn?.let(LocalDate::parse),
    accruedInterestMinor = accruedInterestMinor,
    showInCalendar = showInCalendar,
    aprKnown = aprKnown,
    minimumKnown = minimumKnown,
    purchaseAprBasisPoints = purchaseAprBasisPoints,
    balanceTransferAprBasisPoints = balanceTransferAprBasisPoints,
    cashAdvanceAprBasisPoints = cashAdvanceAprBasisPoints,
    graceOnPurchases = graceOnPurchases,
    dayCountBasis = dayCountBasis,
    recurringPaymentId = recurringPaymentId,
    note = note,
)

private fun DebtMonthPlanEntity.toDomain() = DebtMonthPlan(
    id = id,
    debtId = debtId,
    yearMonth = YearMonth.parse(yearMonth),
    plannedPaymentMinor = plannedPaymentMinor,
    dueOn = dueOn?.let(LocalDate::parse),
)

private fun DebtRateChangeEntity.toDomain() = DebtRateChange(
    id = id,
    debtId = debtId,
    effectiveOn = LocalDate.parse(effectiveOn),
    aprBasisPoints = aprBasisPoints,
    note = note,
)

private fun DebtSettlementEntity.toDomain() = DebtSettlement(
    id = id,
    debtId = debtId,
    yearMonth = YearMonth.parse(yearMonth),
    paidOn = LocalDate.parse(paidOn),
    amountMinor = amountMinor,
    transactionId = transactionId,
    linkId = linkId,
    interestMinor = interestMinor,
    principalMinor = principalMinor,
    feeMinor = feeMinor,
    splitKnown = splitKnown,
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
        com.ledgerline.app.detection.capture.CaptureSources.apps.forEach { app ->
            database.notificationSources().insert(
                NotificationSourceEntity(Ids.new(), app.packageName, app.label, enabled = false),
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
