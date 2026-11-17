package com.ledgerline.app.finance.cycle

import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.finance.envelopes.EnvelopeKind
import com.ledgerline.app.finance.envelopes.PayPlan
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.schedule.RecurringSchedule
import java.time.LocalDate

/** One cycle's money in a few numbers, for putting two cycles side by side. */
data class CycleNumbers(
    val incomeMinor: Long,
    val billsPaidMinor: Long,
    val spentMinor: Long,
    val unmarkedMinor: Long,
    val savedMinor: Long,
    val stillToPayMinor: Long,
) {
    /** Everything that went out: bills paid, spending, and money put aside. */
    val outMinor: Long get() = billsPaidMinor + spentMinor + savedMinor
}

/** A spending item over one cycle: planned against spent. */
data class ItemResult(val name: String, val plannedMinor: Long, val spentMinor: Long) {
    val overMinor: Long get() = (spentMinor - plannedMinor).coerceAtLeast(0)
}

/** A bill or pay as it changes between this cycle and the next. */
data class SeriesLine(val name: String, val amountMinor: Long, val date: LocalDate, val incoming: Boolean)

data class CycleReviewResult(
    val cycle: PayPeriod,
    val next: PayPeriod,
    val now: CycleNumbers,
    val before: CycleNumbers?,
    val items: List<ItemResult>,
    /** Bills and pay that also come next cycle, on their next date. */
    val continuing: List<SeriesLine>,
    /** Bills and pay whose last date is in this cycle (or earlier): they stop here. */
    val ending: List<SeriesLine>,
    /** Bills and pay that start next cycle. */
    val starting: List<SeriesLine>,
    /** Spending items planned this cycle and not next. */
    val itemsEnding: List<String>,
    /** Spending items planned next cycle and not this one. */
    val itemsStarting: List<String>,
)

/**
 * The end-of-cycle review: this cycle next to the one before, how each spending item went, and what carries on,
 * stops, or starts next cycle.
 */
object CycleReview {
    fun numbers(plan: PayPlan): CycleNumbers = CycleNumbers(
        incomeMinor = plan.incomeReceivedMinor,
        billsPaidMinor = plan.bills?.bills.orEmpty().filter { it.paid }.sumOf { it.spentMinor },
        spentMinor = plan.spentMinor,
        unmarkedMinor = plan.outsideMinor,
        savedMinor = plan.savings.sumOf { it.spentMinor },
        stillToPayMinor = plan.stillToPayMinor,
    )

    fun build(
        plan: PayPlan,
        previous: PayPlan?,
        next: PayPeriod,
        series: List<RecurringPayment>,
        budgets: List<Budget>,
    ): CycleReviewResult {
        val cycle = plan.cycle
        val live = series.filter { it.active }
        fun firstIn(item: RecurringPayment, period: PayPeriod) = RecurringSchedule.occurrencesBetween(item, period.start, period.end).firstOrNull()
        fun line(item: RecurringPayment, date: LocalDate) = SeriesLine(item.name, item.amountMinor, date, item.direction == CashFlowDirection.INFLOW)
        val continuing = live.mapNotNull { item ->
            val now = firstIn(item, cycle) ?: return@mapNotNull null
            val then = firstIn(item, next) ?: return@mapNotNull null
            now.let { line(item, then) }
        }
        val ending = live.mapNotNull { item ->
            val last = RecurringSchedule.occurrencesBetween(item, cycle.start, cycle.end).lastOrNull() ?: return@mapNotNull null
            val later = RecurringSchedule.occurrencesBetween(item, cycle.end.plusDays(1), cycle.end.plusYears(5)).firstOrNull()
            if (later == null) line(item, last) else null
        }
        val starting = live.mapNotNull { item ->
            val then = firstIn(item, next) ?: return@mapNotNull null
            val before = RecurringSchedule.occurrencesBetween(item, item.startOn.minusDays(1).coerceAtMost(cycle.end), cycle.end).firstOrNull()
            if (before == null) line(item, then) else null
        }
        val active = budgets.filter { it.active }
        return CycleReviewResult(
            cycle = cycle,
            next = next,
            now = numbers(plan),
            before = previous?.let(::numbers),
            items = plan.envelopes.filter { it.kind == EnvelopeKind.SPENDING }.map { ItemResult(it.name, it.plannedMinor, it.spentMinor) },
            continuing = continuing.sortedBy { it.date },
            ending = ending.sortedBy { it.date },
            starting = starting.sortedBy { it.date },
            itemsEnding = active.filter { it.runsIn(cycle.start) && !it.runsIn(next.start) }.map { it.name },
            itemsStarting = active.filter { !it.runsIn(cycle.start) && it.runsIn(next.start) }.map { it.name },
        )
    }
}

private fun LocalDate.coerceAtMost(other: LocalDate): LocalDate = if (isAfter(other)) other else this
