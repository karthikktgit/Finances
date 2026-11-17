package com.ledgerline.app.data.repository

import com.ledgerline.app.data.Ids
import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.entity.GoalContributionEntity
import com.ledgerline.app.data.local.entity.SavingsGoalEntity
import com.ledgerline.app.domain.model.GoalContribution
import com.ledgerline.app.domain.model.GoalDraft
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.SavingsGoal
import com.ledgerline.app.domain.repository.GoalRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.time.AppClock
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) : GoalRepository {
    override fun observe(): Flow<List<SavingsGoal>> = database.goals().observe().map { rows -> rows.map { it.toDomain() } }

    override fun observeContributions(): Flow<List<GoalContribution>> =
        database.goals().observeContributions().map { rows ->
            rows.map { row ->
                val kind = row.kind?.let { runCatching { enumValueOf<com.ledgerline.app.domain.model.GoalEntryKind>(it) }.getOrNull() }
                    ?: if (row.amountMinor >= 0) com.ledgerline.app.domain.model.GoalEntryKind.SAVED else com.ledgerline.app.domain.model.GoalEntryKind.TAKEN_OUT
                GoalContribution(row.id, row.goalId, row.amountMinor, LocalDate.parse(row.onDate), row.note, kind, row.transactionId)
            }
        }

    override suspend fun get(id: String): SavingsGoal? = database.goals().get(id)?.toDomain()

    override suspend fun save(draft: GoalDraft): SaveResult {
        val name = draft.name.trim()
        if (name.isEmpty()) return SaveResult.Invalid("Name the goal, like Room deposit.")
        if (draft.targetMinor <= 0) return SaveResult.Invalid("Enter how much you want to save.")
        if (draft.monthlyMinor != null && draft.monthlyMinor < 0) return SaveResult.Invalid("The monthly amount cannot be negative.")
        val existing = draft.existingId?.let { database.goals().get(it) }
        val accountCurrency = draft.accountId?.let { database.accounts().get(it)?.currency }
        database.goals().upsert(
            SavingsGoalEntity(
                id = existing?.id ?: Ids.new(),
                name = name,
                targetMinor = draft.targetMinor,
                currency = accountCurrency ?: existing?.currency ?: settings.observe().first().primaryCurrency,
                targetDate = draft.targetDate?.toString(),
                accountId = draft.accountId,
                useAccountBalance = draft.accountId != null && draft.useAccountBalance,
                monthlyMinor = draft.monthlyMinor?.takeIf { it > 0 },
                createdOn = existing?.createdOn ?: clock.today().toString(),
                archived = existing?.archived ?: false,
                updatedAtEpochMillis = clock.now().toEpochMilli(),
                outcome = existing?.outcome,
                outcomeOn = existing?.outcomeOn,
            ),
        )
        return SaveResult.Saved
    }

    override suspend fun delete(id: String) = database.goals().delete(id)

    override suspend fun addContribution(goalId: String, amountMinor: Long, onDate: LocalDate, note: String): SaveResult {
        if (amountMinor == 0L) return SaveResult.Invalid("Enter an amount.")
        if (database.goals().get(goalId) == null) return SaveResult.Invalid("That goal is no longer on this device.")
        database.goals().insertContribution(
            GoalContributionEntity(Ids.new(), goalId, amountMinor, onDate.toString(), note.trim(), clock.now().toEpochMilli()),
        )
        return SaveResult.Saved
    }

    override suspend fun deleteContribution(id: String) {
        // A spend recorded with the entry goes too, so the account matches.
        val linked = database.goals().observeContributions().first().firstOrNull { it.id == id }?.transactionId
        database.goals().deleteContribution(id)
        linked?.let { txId ->
            database.transactions().getAll().firstOrNull { it.id == txId }?.let { tx ->
                val now = clock.now().toEpochMilli()
                database.transactions().update(tx.copy(deletedAtEpochMillis = now, updatedAtEpochMillis = now))
            }
        }
    }

    override suspend fun recordSpend(
        goalId: String,
        amountMinor: Long,
        onDate: LocalDate,
        note: String,
        onGoal: Boolean,
        recordTransaction: Boolean,
    ): SaveResult {
        val goal = database.goals().get(goalId) ?: return SaveResult.Invalid("That goal is no longer on this device.")
        if (amountMinor <= 0) return SaveResult.Invalid("Enter an amount.")
        val now = clock.now().toEpochMilli()
        val account = goal.accountId?.let { database.accounts().get(it) }
        database.withTransaction {
            // The purchase itself, from the account the goal's money is kept in, so the balance matches.
            val txId = if (recordTransaction && account != null) {
                val id = Ids.new()
                val name = note.trim().ifBlank { goal.name }
                val normalized = com.ledgerline.app.finance.merchant.MerchantNormalizer.normalize(name).ifBlank { name }
                database.transactions().insert(
                    com.ledgerline.app.data.local.entity.TransactionEntity(
                        id = id, accountId = account.id, counterAccountId = null, linkId = null,
                        amountMinor = amountMinor, balanceEffectMinor = -amountMinor, currency = account.currency,
                        type = com.ledgerline.app.domain.model.TransactionType.EXPENSE.name,
                        merchantRaw = name, merchantNormalized = normalized, merchantId = null,
                        description = name, categoryId = null, occurredOn = onDate.toString(), postedOn = onDate.toString(),
                        detectionSource = com.ledgerline.app.domain.model.DetectionSource.MANUAL.name,
                        confidencePercent = com.ledgerline.app.finance.policy.FinancePolicy.MANUAL_CONFIDENCE_PERCENT,
                        recurringPaymentId = null, status = com.ledgerline.app.domain.model.TransactionStatus.CONFIRMED.name,
                        userConfirmed = true,
                        note = if (onGoal) "Spent from savings for ${goal.name}." else "Paid with money saved for ${goal.name}.",
                        fingerprint = com.ledgerline.app.finance.fingerprint.TransactionFingerprint.of(
                            account.currency, amountMinor, account.id, onDate, com.ledgerline.app.domain.model.TransactionType.EXPENSE, normalized,
                        ),
                        createdAtEpochMillis = now, updatedAtEpochMillis = now, deletedAtEpochMillis = null,
                    ),
                )
                id
            } else {
                null
            }
            database.goals().insertContribution(
                GoalContributionEntity(
                    Ids.new(), goalId, -amountMinor, onDate.toString(), note.trim(), now,
                    kind = (if (onGoal) com.ledgerline.app.domain.model.GoalEntryKind.SPENT_ON_GOAL else com.ledgerline.app.domain.model.GoalEntryKind.USED_ELSEWHERE).name,
                    transactionId = txId,
                ),
            )
        }
        return SaveResult.Saved
    }

    override suspend fun setOutcome(id: String, outcome: com.ledgerline.app.domain.model.GoalOutcome?, on: LocalDate) {
        val existing = database.goals().get(id) ?: return
        database.goals().upsert(
            existing.copy(
                outcome = outcome?.name,
                outcomeOn = outcome?.let { on.toString() },
                updatedAtEpochMillis = clock.now().toEpochMilli(),
            ),
        )
    }
}

private fun SavingsGoalEntity.toDomain() = SavingsGoal(
    id = id,
    name = name,
    targetMinor = targetMinor,
    currency = currency,
    targetDate = targetDate?.let(LocalDate::parse),
    accountId = accountId,
    useAccountBalance = useAccountBalance,
    monthlyMinor = monthlyMinor,
    createdOn = LocalDate.parse(createdOn),
    archived = archived,
    outcome = outcome?.let { runCatching { enumValueOf<com.ledgerline.app.domain.model.GoalOutcome>(it) }.getOrNull() },
    outcomeOn = outcomeOn?.let(LocalDate::parse),
)
