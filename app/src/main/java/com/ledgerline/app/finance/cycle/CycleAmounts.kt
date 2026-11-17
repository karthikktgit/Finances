package com.ledgerline.app.finance.cycle

import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.CycleAmount
import java.time.LocalDate

/**
 * Applies one cycle's own amounts over the usual ones. Every place that works out a cycle (Home, cash flow, Safe to
 * spend) goes through here, so a change made when setting up a month shows the same everywhere.
 */
object CycleAmounts {
    fun budgetKey(id: String) = "budget:$id"

    fun goalKey(id: String) = "goal:$id"

    /**
     * Budgets as they are for the cycle starting [cycleStart]: an item with its own amount uses it for exactly this
     * cycle; one set to zero is off this cycle, so its spending falls to your catch-all like any other.
     */
    fun budgetsFor(budgets: List<Budget>, amounts: List<CycleAmount>, cycleStart: LocalDate): List<Budget> {
        val mine = amounts.filter { it.cycleStart == cycleStart }.associate { it.itemKey to it.amountMinor }
        return budgets.map { budget ->
            // Outside its start and end, an item is simply not planned that cycle.
            val running = if (budget.runsIn(cycleStart)) budget else budget.copy(active = false)
            val own = mine[budgetKey(budget.id)] ?: return@map running
            running.copy(amountMinor = own, period = BudgetPeriod.PAY_CYCLE, active = running.active && own > 0)
        }
    }

    /**
     * The first day of the last cycle for a schedule that starts in the cycle beginning [start]: the [count]th cycle
     * from there, or the cycle holding [on].
     */
    fun lastCycleStart(start: LocalDate, end: com.ledgerline.app.finance.schedule.ScheduleEnd, settings: com.ledgerline.app.domain.model.UserSettings): LocalDate? =
        when (end.mode) {
            com.ledgerline.app.finance.schedule.EndMode.NEVER -> null
            com.ledgerline.app.finance.schedule.EndMode.ON_DATE ->
                com.ledgerline.app.finance.period.CycleSchedule.containing(maxOf(end.on ?: start, start), settings).start
            com.ledgerline.app.finance.schedule.EndMode.AFTER_COUNT -> {
                var cycle = com.ledgerline.app.finance.period.CycleSchedule.containing(start, settings)
                repeat((end.count.coerceAtLeast(1)) - 1) { cycle = com.ledgerline.app.finance.period.CycleSchedule.next(cycle, settings) }
                cycle.start
            }
        }

    /** Each goal's own saving for the cycle starting [cycleStart], by goal id. */
    fun goalPlans(amounts: List<CycleAmount>, cycleStart: LocalDate): Map<String, Long> =
        amounts.filter { it.cycleStart == cycleStart && it.itemKey.startsWith("goal:") }
            .associate { it.itemKey.removePrefix("goal:") to it.amountMinor }
}
