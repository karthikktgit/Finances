package com.ledgerline.app.domain.usecase

import com.ledgerline.app.domain.model.AccountDraft
import com.ledgerline.app.domain.model.CycleType
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Starts a new pay cycle today with a clean slate. Nothing in your history is deleted: past transactions stay where
 * they are and keep their dates, so they simply fall in an earlier cycle and no longer count toward this one.
 *
 * - The cycle now starts today (for a monthly cycle, on today's day of the month from now on).
 * - Each account's balance is set to what you entered, with a balance correction dated today, so the history above
 *   it still adds up.
 * - Scheduled payments are either all removed (with any planned, unpaid spends), or kept; when kept, any still
 *   unpaid from before today are skipped so nothing old is carried into the new cycle. Nothing is duplicated: the
 *   schedules themselves are not copied.
 */
class FreshStart @Inject constructor(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val recurring: RecurringRepository,
    private val transactions: TransactionRepository,
    private val clock: AppClock,
) {
    suspend fun start(balances: Map<String, Long>, keepSchedules: Boolean) {
        val today = clock.today()
        val current = settings.observe().first()
        settings.update(
            current.copy(
                cycleAnchor = today,
                cycleStartDay = if (current.cycleType == CycleType.MONTHLY) today.dayOfMonth.coerceAtMost(28) else current.cycleStartDay,
            ),
        )
        accounts.observe().first().filter { it.id in balances }.forEach { account ->
            accounts.save(
                AccountDraft(
                    existingId = account.id,
                    name = account.name,
                    institution = account.institution,
                    type = account.type,
                    lastFour = account.lastFour.orEmpty(),
                    enteredBalanceMinor = balances.getValue(account.id),
                    applyBalance = true,
                    creditLimitMinor = account.creditLimitMinor,
                    active = account.active,
                    includeInNetWorth = account.includeInNetWorth,
                    includeInSafeToSpend = account.includeInSafeToSpend,
                ),
            )
        }
        val txs = transactions.observeAll().first()
        if (!keepSchedules) {
            recurring.observe().first().forEach { recurring.delete(it.id) }
            txs.filter { BalanceMath.isPlannedUnpaid(it) }.forEach { transactions.softDelete(it.id) }
        } else {
            val settled = txs.mapNotNull { tx -> tx.recurringPaymentId?.takeIf { BalanceMath.isActive(tx) }?.let { it to tx.occurredOn } }.toSet()
            OccurrenceMaterializer.project(recurring.observe().first(), recurring.observeAdjustments().first(), settled, today.minusYears(1), today.minusDays(1), today)
                .filter { it.state == OccurrenceState.SCHEDULED }
                .forEach { recurring.skip(it.recurringId, it.date) }
        }
    }
}
