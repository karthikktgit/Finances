package com.ledgerline.app.finance.forecast

import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.category.SpendingRules
import com.ledgerline.app.finance.envelopes.EnvelopeBill
import com.ledgerline.app.finance.period.PayPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One day of the pay cycle: what you planned to have spent by its end, and what you had actually paid (none after today). */
data class TrackPoint(val date: LocalDate, val plannedMinor: Long, val paidMinor: Long?)

data class SpendingTrackResult(
    val points: List<TrackPoint>,
    /** Everything planned for the whole cycle. */
    val plannedTotalMinor: Long,
    val plannedToDateMinor: Long,
    val paidToDateMinor: Long,
    /** Planned spends whose day has come but are not paid yet. */
    val unpaidPlannedMinor: Long,
    /** What makes up the planned line by today: bills due, spending items spread over the days, and spends you planned. */
    val plannedBillsToDateMinor: Long = 0,
    val plannedBudgetsToDateMinor: Long = 0,
    val plannedSpendsToDateMinor: Long = 0,
) {
    /** Above zero: you have paid more than planned by now. Below zero: less. */
    val aheadMinor: Long get() = paidToDateMinor - plannedToDateMinor
}

/**
 * Your spending behaviour over the pay cycle as two running totals. Planned: bills on their due dates, spends you
 * planned on their planned dates, and your spending budgets spread evenly across the days. Paid: everything actually
 * paid, by the day it was paid.
 */
object SpendingTrack {
    /**
     * The bills that belong on the planned line for dates from [from]: each money-out bill due in the range (paid or
     * not), plus any still unpaid from before it, which count on the first day. Skipped dates and transfers between
     * your own accounts are left out.
     */
    fun billsFor(events: List<com.ledgerline.app.finance.schedule.ProjectedOccurrence>, from: LocalDate): List<EnvelopeBill> =
        events
            .filter { it.direction == com.ledgerline.app.domain.model.CashFlowDirection.OUTFLOW && it.kind != com.ledgerline.app.domain.model.ScheduledKind.TRANSFER }
            .filter { it.state != com.ledgerline.app.domain.model.OccurrenceState.SKIPPED }
            .filter { !it.date.isBefore(from) || (it.overdue && it.state != com.ledgerline.app.domain.model.OccurrenceState.PAID) }
            .map { EnvelopeBill("${it.recurringId}-${it.date}", it.name, it.date, it.amountMinor, it.state == com.ledgerline.app.domain.model.OccurrenceState.PAID, it.overdue, it.accountId, it.recurringId) }

    /**
     * What a transaction adds to the paid line: everyday spending, and any payment of a bill (a card or loan payment
     * counts here because its bill is on the planned line). Refunds take away. Moving money between your accounts is
     * not spending.
     */
    fun paidAmount(tx: LedgerTransaction): Long = when {
        !BalanceMath.isActive(tx) -> 0L
        tx.recurringPaymentId != null && tx.balanceEffectMinor < 0 && tx.type != TransactionType.TRANSFER -> tx.amountMinor
        SpendingRules.countsAsSpending(tx) -> tx.amountMinor
        tx.type == TransactionType.REFUND -> -tx.amountMinor
        else -> 0L
    }

    fun build(
        cycle: PayPeriod,
        today: LocalDate,
        bills: List<EnvelopeBill>,
        budgetsMinor: Long,
        transactions: List<LedgerTransaction>,
        /** True for a planned spend that one of your spending items already covers, so it is not counted twice. */
        coveredByBudget: (LedgerTransaction) -> Boolean = { false },
    ): SpendingTrackResult {
        val days = (ChronoUnit.DAYS.between(cycle.start, cycle.end) + 1).toInt().coerceAtLeast(1)
        // Bills left over from before the cycle count on its first day.
        val billOn = bills.groupBy { maxOf(it.date, cycle.start) }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }
        val planned = transactions.filter { tx ->
            tx.deletedAtEpochMillis == null && tx.type == TransactionType.EXPENSE &&
                (BalanceMath.isPlannedUnpaid(tx) || (tx.plannedOn != null && BalanceMath.isActive(tx))) &&
                !coveredByBudget(tx)
        }
        val plannedOn = planned
            .map { (it.plannedOn ?: it.occurredOn) to it.amountMinor }
            .filter { (date, _) -> cycle.contains(date) || (date.isBefore(cycle.start)) }
            .groupBy({ maxOf(it.first, cycle.start) }, { it.second }).mapValues { (_, l) -> l.sum() }
        val paidOn = transactions
            .filter { cycle.contains(it.occurredOn) }
            .groupBy { it.occurredOn }
            .mapValues { (_, l) -> l.sumOf(::paidAmount) }
        var plannedRun = 0L
        var paidRun = 0L
        var plannedToDate = 0L
        var billsRun = 0L
        var budgetsRun = 0L
        var spendsRun = 0L
        var billsToDate = 0L
        var budgetsToDate = 0L
        var spendsToDate = 0L
        val points = (0 until days).map { i ->
            val date = cycle.start.plusDays(i.toLong())
            val spread = budgetsMinor * (i + 1) / days - budgetsMinor * i / days
            plannedRun += spread + (billOn[date] ?: 0L) + (plannedOn[date] ?: 0L)
            billsRun += billOn[date] ?: 0L
            budgetsRun += spread
            spendsRun += plannedOn[date] ?: 0L
            val paid = if (date.isAfter(today)) {
                null
            } else {
                paidRun += paidOn[date] ?: 0L
                plannedToDate = plannedRun
                billsToDate = billsRun
                budgetsToDate = budgetsRun
                spendsToDate = spendsRun
                paidRun
            }
            TrackPoint(date, plannedRun, paid)
        }
        val unpaid = transactions.filter { BalanceMath.isPlannedUnpaid(it) && it.type == TransactionType.EXPENSE && !(it.plannedOn ?: it.occurredOn).isAfter(today) }.sumOf { it.amountMinor }
        return SpendingTrackResult(points, plannedRun, plannedToDate, paidRun.coerceAtLeast(0), unpaid, billsToDate, budgetsToDate, spendsToDate)
    }
}
