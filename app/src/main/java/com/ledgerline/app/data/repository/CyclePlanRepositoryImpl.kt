package com.ledgerline.app.data.repository

import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.entity.CycleAmountEntity
import com.ledgerline.app.data.local.entity.CycleReviewEntity
import com.ledgerline.app.domain.model.CycleAmount
import com.ledgerline.app.domain.model.CycleReview
import com.ledgerline.app.domain.repository.CyclePlanRepository
import com.ledgerline.app.domain.time.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import com.ledgerline.app.data.mapper.toDomain
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CyclePlanRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
    private val preferences: com.ledgerline.app.preferences.UiPreferences,
) : CyclePlanRepository {
    private val dao get() = database.cyclePlans()

    override fun observeAmounts(): Flow<List<CycleAmount>> =
        dao.observeAmounts().map { rows -> rows.map { CycleAmount(LocalDate.parse(it.cycleStart), it.itemKey, it.amountMinor) } }

    override fun observeReviews(): Flow<List<CycleReview>> = dao.observeReviews().map { rows -> rows.map { it.toDomain() } }

    override suspend fun setAmount(cycleStart: LocalDate, itemKey: String, amountMinor: Long?) {
        if (amountMinor == null) {
            dao.deleteAmount(cycleStart.toString(), itemKey)
        } else {
            dao.upsertAmount(CycleAmountEntity(cycleStart.toString(), itemKey, amountMinor.coerceAtLeast(0), clock.now().toEpochMilli()))
        }
    }

    override suspend fun review(cycleStart: LocalDate): CycleReview = dao.review(cycleStart.toString())?.toDomain() ?: CycleReview(cycleStart)

    override suspend fun saveReview(review: CycleReview) {
        dao.upsertReview(
            CycleReviewEntity(
                cycleStart = review.cycleStart.toString(),
                reviewedAccounts = review.reviewedAccountIds.sorted().joinToString(","),
                balancesChecked = review.balancesChecked,
                completedAtEpochMillis = review.completedAtEpochMillis,
            ),
        )
    }

    override fun observeFreshCycles(): Flow<Set<LocalDate>> =
        preferences.freshBalanceCycles.map { set -> set.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet() }

    override suspend fun setFresh(cycleStart: LocalDate, fresh: Boolean) {
        preferences.setFreshBalanceCycle(cycleStart, fresh)
        if (!fresh) {
            // Back to carrying on from the cycle before: the start corrections go (kept as deleted, like any removal).
            val now = clock.now().toEpochMilli()
            database.transactions().getAll()
                .filter { com.ledgerline.app.finance.cycle.CycleBalances.isStartCorrection(it.toDomain(), cycleStart) }
                .forEach { database.transactions().update(it.copy(deletedAtEpochMillis = now, updatedAtEpochMillis = now)) }
        }
    }

    override suspend fun setStartBalance(cycleStart: LocalDate, accountId: String, signedMinor: Long) {
        val account = database.accounts().get(accountId)?.toDomain() ?: return
        val now = clock.now().toEpochMilli()
        database.withTransaction {
            val all = database.transactions().getAll()
            val existing = all.filter { it.accountId == accountId && com.ledgerline.app.finance.cycle.CycleBalances.isStartCorrection(it.toDomain(), cycleStart) }
            val delta = com.ledgerline.app.finance.cycle.CycleBalances.correctionMinor(account, all.map { it.toDomain() }, cycleStart, signedMinor)
            existing.drop(1).forEach { database.transactions().update(it.copy(deletedAtEpochMillis = now, updatedAtEpochMillis = now)) }
            val keep = existing.firstOrNull()
            when {
                delta == 0L -> keep?.let { database.transactions().update(it.copy(deletedAtEpochMillis = now, updatedAtEpochMillis = now)) }
                keep != null -> database.transactions().update(keep.copy(amountMinor = kotlin.math.abs(delta), balanceEffectMinor = delta, updatedAtEpochMillis = now))
                else -> database.transactions().insert(
                    com.ledgerline.app.data.local.entity.TransactionEntity(
                        id = com.ledgerline.app.data.Ids.new(),
                        accountId = accountId,
                        counterAccountId = null,
                        linkId = null,
                        amountMinor = kotlin.math.abs(delta),
                        balanceEffectMinor = delta,
                        currency = account.currency,
                        type = com.ledgerline.app.domain.model.TransactionType.ADJUSTMENT.name,
                        merchantRaw = com.ledgerline.app.finance.cycle.CycleBalances.NAME,
                        merchantNormalized = com.ledgerline.app.finance.cycle.CycleBalances.NAME,
                        merchantId = null,
                        description = com.ledgerline.app.finance.cycle.CycleBalances.NAME,
                        categoryId = null,
                        occurredOn = cycleStart.toString(),
                        postedOn = cycleStart.toString(),
                        detectionSource = com.ledgerline.app.domain.model.DetectionSource.MANUAL.name,
                        confidencePercent = com.ledgerline.app.finance.policy.FinancePolicy.MANUAL_CONFIDENCE_PERCENT,
                        recurringPaymentId = null,
                        status = com.ledgerline.app.domain.model.TransactionStatus.CONFIRMED.name,
                        userConfirmed = true,
                        note = "This cycle started with fresh balances; earlier cycles are unchanged.",
                        fingerprint = "cycle-start|$cycleStart|$accountId",
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        deletedAtEpochMillis = null,
                    ),
                )
            }
        }
    }

    private fun CycleReviewEntity.toDomain() = CycleReview(
        cycleStart = LocalDate.parse(cycleStart),
        reviewedAccountIds = reviewedAccounts.split(',').filter { it.isNotBlank() }.toSet(),
        balancesChecked = balancesChecked,
        completedAtEpochMillis = completedAtEpochMillis,
    )
}
