package com.ledgerline.app.finance.dashboard

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.finance.forecast.AccountForecast
import com.ledgerline.app.finance.period.PayPeriod
import java.time.LocalDate

/** The two views on Home. Choosing one changes what is shown. It never edits a transaction or a schedule. */
enum class HomeView { ACTUAL, PLANNED }

/**
 * Planned figures sit next to the actual ones and never replace them.
 * Unpaid scheduled items appear here only. They are not income received, not spending, and not part of
 * the actual balance or Safe to Spend until a real transaction is recorded for them.
 */
data class PlannedActualSummary(
    val actualBalanceMinor: Long,
    val projectedEndMinor: Long,
    val incomeReceivedMinor: Long,
    val incomeExpectedMinor: Long,
    val spentMinor: Long,
    val unpaidOutMinor: Long,
    val unpaid: List<ScheduledOccurrence>,
) {
    val incomePlannedMinor: Long get() = incomeReceivedMinor + incomeExpectedMinor
    val spendingPlannedMinor: Long get() = spentMinor + unpaidOutMinor
    val netActualMinor: Long get() = incomeReceivedMinor - spentMinor
    val netPlannedMinor: Long get() = incomePlannedMinor - spendingPlannedMinor
}

object PlannedActual {
    /**
     * Unpaid scheduled items in [period], oldest first. Transfers between your accounts are left out because they
     * are neither income nor spending. Overdue items are dated today by the schedule, so they belong to the
     * current cycle. The same item on the same date and account is counted once.
     */
    fun unpaidInPeriod(period: PayPeriod, items: List<ScheduledOccurrence>): List<ScheduledOccurrence> =
        distinct(items).filter { it.kind != ScheduledKind.TRANSFER && period.contains(it.date) }
            .sortedWith(compareBy({ it.date }, { it.name }))

    fun distinct(items: List<ScheduledOccurrence>): List<ScheduledOccurrence> =
        items.distinctBy { Triple(it.recurringId, it.date, it.accountId) }

    /**
     * @param actualLiquidMinor the actual balance of the accounts counted in Safe to Spend
     * @param incomeReceivedMinor completed income in the period
     * @param spentMinor completed spending in the period, net of refunds
     * @param liquidAccountIds accounts counted in Safe to Spend
     */
    fun summarize(
        period: PayPeriod,
        today: LocalDate,
        items: List<ScheduledOccurrence>,
        liquidAccountIds: Set<String>,
        actualLiquidMinor: Long,
        incomeReceivedMinor: Long,
        spentMinor: Long,
    ): PlannedActualSummary {
        val unpaid = unpaidInPeriod(period, items)
        // A period that has already ended has nothing left to plan; its balance is the actual one.
        val projected = if (period.end.isBefore(today)) {
            actualLiquidMinor
        } else {
            actualLiquidMinor + distinct(items)
                .filter { it.kind != ScheduledKind.TRANSFER && it.accountId in liquidAccountIds && !it.date.isAfter(period.end) }
                .sumOf { AccountForecast.signedDelta(it) }
        }
        return PlannedActualSummary(
            actualBalanceMinor = actualLiquidMinor,
            projectedEndMinor = projected,
            incomeReceivedMinor = incomeReceivedMinor,
            incomeExpectedMinor = unpaid.filter { it.direction == CashFlowDirection.INFLOW }.sumOf { it.amountMinor },
            spentMinor = spentMinor,
            unpaidOutMinor = unpaid.filter { it.direction == CashFlowDirection.OUTFLOW }.sumOf { it.amountMinor },
            unpaid = unpaid,
        )
    }
}
