package com.ledgerline.app.finance.goals

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.GoalContribution
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SavingsGoal
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.period.PayPeriod
import java.time.LocalDate

enum class GoalCycleWhen { PAST, NOW, AHEAD }

/** One pay cycle of a goal: what was (or is) planned for it, and what was saved in it. */
data class GoalCycleRow(
    val cycle: PayPeriod,
    val whenIs: GoalCycleWhen,
    /** Planned for this cycle; null for a past cycle with no plan on record. */
    val plannedMinor: Long?,
    val savedMinor: Long,
    /** Saved in all by the end of this cycle (for a coming cycle, if the plan is kept). */
    val totalMinor: Long,
)

/** The goal over time: how much was saved each cycle, this cycle's plan, and the plan for each cycle until it is done. */
data class GoalTimelineResult(
    val rows: List<GoalCycleRow>,
    val lastCycleSavedMinor: Long?,
    val thisCycle: GoalCycleRow?,
    /** Still to put aside this cycle to keep to the plan. */
    val stillThisCycleMinor: Long,
)

object GoalTimeline {
    private const val MAX_AHEAD = 36

    fun build(
        progress: GoalProgress,
        contributions: List<GoalContribution>,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        today: LocalDate,
        settings: UserSettings,
        /** A cycle's own saving for this goal, by cycle start, if one was set. */
        cyclePlan: (LocalDate) -> Long? = { null },
    ): GoalTimelineResult {
        val goal = progress.goal
        val current = CycleSchedule.containing(today, settings)
        val account = goal.accountId?.let { id -> accounts.firstOrNull { it.id == id && it.active && !it.deleted } }
        val byBalance = goal.useAccountBalance && account != null
        val active = transactions.filter { BalanceMath.isActive(it) }
        fun savedIn(cycle: PayPeriod): Long = if (byBalance) {
            active.filter { it.accountId == account!!.id && cycle.contains(it.occurredOn) && !it.occurredOn.isAfter(today) }.sumOf { it.balanceEffectMinor }
        } else {
            contributions.filter {
                it.goalId == goal.id && it.kind != com.ledgerline.app.domain.model.GoalEntryKind.SPENT_ON_GOAL && cycle.contains(it.onDate) && !it.onDate.isAfter(today)
            }.sumOf { it.amountMinor }
        }

        val rows = mutableListOf<GoalCycleRow>()
        // From the cycle the goal was made in up to this one.
        var cycle = CycleSchedule.containing(minOf(goal.createdOn, current.start), settings)
        var total = progress.savedMinor - (generateSequence(cycle) { CycleSchedule.next(it, settings) }
            .takeWhile { !it.start.isAfter(current.start) }.sumOf { savedIn(it) })
        while (!cycle.start.isAfter(current.start)) {
            val saved = savedIn(cycle)
            total += saved
            val now = cycle.start == current.start
            val planned = if (now) progress.planMonthlyMinor?.let { minOf(it, progress.remainingMinor + progress.savedThisCycleMinor) }
            else cyclePlan(cycle.start) ?: goal.monthlyMinor
            rows += GoalCycleRow(cycle, if (now) GoalCycleWhen.NOW else GoalCycleWhen.PAST, planned, saved, total)
            cycle = CycleSchedule.next(cycle, settings)
        }

        // Ahead: what is left after this cycle's plan, by your monthly amount or spread evenly up to the goal date.
        var left = (progress.remainingMinor - progress.setAsideNowMinor).coerceAtLeast(0)
        var projected = progress.savedMinor + progress.setAsideNowMinor
        var ahead = 0
        while (left > 0 && ahead < MAX_AHEAD && !progress.reached) {
            val target = goal.targetDate
            // Paydays before the goal date count, the same as the goal's own plan.
            if (target != null && !cycle.start.isBefore(target)) break
            val cyclesLeft = target?.let { t ->
                generateSequence(cycle) { CycleSchedule.next(it, settings) }.takeWhile { it.start.isBefore(t) }.count().coerceAtLeast(1)
            }
            val plan = cyclePlan(cycle.start) ?: goal.monthlyMinor?.takeIf { it > 0 }
                ?: cyclesLeft?.let { (left + it - 1) / it }
                ?: break
            val step = minOf(plan, left)
            left -= step
            projected += step
            rows += GoalCycleRow(cycle, GoalCycleWhen.AHEAD, step, 0, projected)
            cycle = CycleSchedule.next(cycle, settings)
            ahead++
        }
        val past = rows.filter { it.whenIs == GoalCycleWhen.PAST }
        return GoalTimelineResult(
            rows = rows,
            lastCycleSavedMinor = past.lastOrNull()?.savedMinor,
            thisCycle = rows.firstOrNull { it.whenIs == GoalCycleWhen.NOW },
            stillThisCycleMinor = progress.setAsideNowMinor,
        )
    }

    /**
     * Whether a goal belongs on Home for [cycle]: not before it was made, and once reached only in the cycle it was
     * reached in (money went in that cycle, or its date falls in it). A goal past its date and not reached stays.
     */
    fun showsIn(progress: GoalProgress, cycle: PayPeriod): Boolean {
        val goal = progress.goal
        if (goal.createdOn.isAfter(cycle.end)) return false
        // Finished (Done or Dropped): only in the cycle it was finished in.
        if (goal.closed) return goal.outcomeOn?.let { cycle.contains(it) } == true
        if (!progress.reached) return true
        // Reached and still holding the money: it stays, every cycle, until it is spent.
        if (progress.heldMinor > 0) return true
        // Reached and all spent: only in the cycle the money went (or the cycle it was reached).
        return progress.lastSpendOn?.let { cycle.contains(it) } == true || progress.savedThisCycleMinor > 0 ||
            goal.targetDate?.let { cycle.contains(it) } == true
    }
}
