package com.ledgerline.app.finance.cycle

import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.CycleAmount
import com.ledgerline.app.domain.model.CycleReview
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.finance.envelopes.EnvelopePlan
import com.ledgerline.app.finance.goals.GoalProgress
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import java.time.LocalDate

/** One scheduled date in a cycle: a bill going out or pay coming in, as it stands for that cycle. */
data class CycleBillRow(
    val recurringId: String,
    val name: String,
    val date: LocalDate,
    val amountMinor: Long,
    /** The schedule's usual amount, to show when this date has its own. */
    val usualMinor: Long,
    val incoming: Boolean,
    val paid: Boolean,
    val skipped: Boolean,
) {
    val own: Boolean get() = amountMinor != usualMinor
    val key: String get() = "$recurringId|$date"
}

/** A spending item or goal as it stands for a cycle: its usual amount and, if set, the cycle's own. */
data class CycleItemRow(
    val key: String,
    val name: String,
    val usualMinor: Long,
    val ownMinor: Long?,
    val accountId: String? = null,
) {
    val amountMinor: Long get() = ownMinor ?: usualMinor
    val off: Boolean get() = ownMinor == 0L
}

data class CycleTotals(val inMinor: Long, val billsMinor: Long, val spendingMinor: Long, val goalsMinor: Long) {
    /** What is left of the money coming in once everything planned is paid. Negative means short. */
    val leftMinor: Long get() = inMinor - billsMinor - spendingMinor - goalsMinor
}

/**
 * What a cycle starts with, worked out for the start-of-cycle checklist: every scheduled date in it (skipped ones
 * too, so they can be brought back), bills left unpaid from before it, and each spending item and goal with the
 * cycle's own amount where one was set.
 */
object CycleSetup {
    fun bills(
        series: List<RecurringPayment>,
        adjustments: List<OccurrenceAdjustment>,
        settled: Set<Pair<String, LocalDate>>,
        cycle: PayPeriod,
        today: LocalDate,
    ): List<CycleBillRow> {
        val skipped = adjustments.filter { it.state == OccurrenceState.SKIPPED }.map { it.recurringId to it.occurrenceOn }.toSet()
        // Skipped dates are projected as if scheduled, so they are listed and can be switched back on.
        val shown = adjustments.map { if (it.state == OccurrenceState.SKIPPED) it.copy(state = OccurrenceState.SCHEDULED) else it }
        val usual = series.associate { it.id to it.amountMinor }
        return OccurrenceMaterializer.project(series, shown, settled, cycle.start, cycle.end, today)
            .map { row ->
                CycleBillRow(
                    recurringId = row.recurringId,
                    name = row.name,
                    date = row.date,
                    amountMinor = row.amountMinor,
                    usualMinor = usual[row.recurringId] ?: row.amountMinor,
                    incoming = row.direction == CashFlowDirection.INFLOW,
                    paid = row.state == OccurrenceState.PAID,
                    skipped = (row.recurringId to row.date) in skipped,
                )
            }
            .sortedWith(compareBy({ it.date }, { it.name }))
    }

    /** Bills from the three months before [cycle] still not paid or skipped: carried in unless you skip them. */
    fun leftovers(
        series: List<RecurringPayment>,
        adjustments: List<OccurrenceAdjustment>,
        settled: Set<Pair<String, LocalDate>>,
        cycle: PayPeriod,
        today: LocalDate,
    ): List<CycleBillRow> {
        val usual = series.associate { it.id to it.amountMinor }
        return OccurrenceMaterializer.project(series, adjustments, settled, cycle.start.minusMonths(3), cycle.start.minusDays(1), today)
            .filter { it.state == OccurrenceState.SCHEDULED && it.direction == CashFlowDirection.OUTFLOW }
            .map { CycleBillRow(it.recurringId, it.name, it.date, it.amountMinor, usual[it.recurringId] ?: it.amountMinor, false, false, false) }
            .sortedBy { it.date }
    }

    fun spending(budgets: List<Budget>, amounts: List<CycleAmount>, cycle: PayPeriod, currency: String): List<CycleItemRow> {
        val own = amounts.filter { it.cycleStart == cycle.start }.associate { it.itemKey to it.amountMinor }
        return budgets.filter { it.active && it.currency == currency && it.runsIn(cycle.start) }.map { budget ->
            val key = CycleAmounts.budgetKey(budget.id)
            CycleItemRow(key, budget.name, EnvelopePlan.perCycle(budget, cycle), own[key], budget.accountId)
        }.sortedBy { it.name.lowercase() }
    }

    /** [progress] is each goal's usual plan, worked out without any cycle's own amount. */
    fun goals(progress: List<GoalProgress>, amounts: List<CycleAmount>, cycleStart: LocalDate): List<CycleItemRow> {
        val own = amounts.filter { it.cycleStart == cycleStart }.associate { it.itemKey to it.amountMinor }
        return progress.filter { !it.reached && !it.goal.closed }.map { row ->
            val key = CycleAmounts.goalKey(row.goal.id)
            CycleItemRow(key, row.goal.name, row.planMonthlyMinor ?: 0L, own[key], row.goal.accountId)
        }
    }

    fun totals(bills: List<CycleBillRow>, spending: List<CycleItemRow>, goals: List<CycleItemRow>): CycleTotals = CycleTotals(
        inMinor = bills.filter { it.incoming && !it.skipped }.sumOf { it.amountMinor },
        billsMinor = bills.filter { !it.incoming && !it.skipped }.sumOf { it.amountMinor },
        spendingMinor = spending.sumOf { it.amountMinor },
        goalsMinor = goals.sumOf { it.amountMinor },
    )

    /**
     * The amounts to give [to] so it starts like [from]: every own amount set for [from], keyed for [to]. Items with
     * no own amount in [from] are cleared in [to], so the two match exactly.
     */
    fun copyAmounts(amounts: List<CycleAmount>, from: LocalDate, to: LocalDate, keys: Collection<String>): Map<String, Long?> {
        val source = amounts.filter { it.cycleStart == from }.associate { it.itemKey to it.amountMinor }
        return keys.associateWith { source[it] }
    }

    /** Steps done of the four: statements, balances, plan reviewed, finished. */
    fun stepsDone(review: CycleReview, accountIds: Set<String>): Int = listOf(
        accountIds.isNotEmpty() && review.reviewedAccountIds.containsAll(accountIds),
        review.balancesChecked,
        review.completed,
    ).count { it }
}
