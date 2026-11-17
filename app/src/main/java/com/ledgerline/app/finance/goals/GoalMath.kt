package com.ledgerline.app.finance.goals

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.GoalContribution
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SavingsGoal
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.period.PayPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where a goal stands and what it takes to finish it. */
data class GoalProgress(
    val goal: SavingsGoal,
    val savedMinor: Long,
    val remainingMinor: Long,
    val fraction: Float,
    val reached: Boolean,
    /** The monthly amount the plan uses: yours if you set one, otherwise what the target date needs. */
    val planMonthlyMinor: Long?,
    /** What the target date needs each month from now, if there is a date. */
    val neededPerMonthMinor: Long?,
    /** When the goal is reached at the planned monthly amount. */
    val finishOn: LocalDate?,
    /** True or false when there is both a date and a plan; null when there is nothing to compare. */
    val onTrack: Boolean?,
    /** Put toward the goal so far in this pay cycle. */
    val savedThisCycleMinor: Long,
    /** What is still to put aside this cycle to keep to the plan. Held back from Safe to spend. */
    val setAsideNowMinor: Long,
    /** The account it is kept in was deleted or is not active, so its balance cannot be used. */
    val accountMissing: Boolean,
    /** Spent on what the goal was for: still counts as saved toward it, but no longer held. */
    val spentOnGoalMinor: Long = 0,
    /** Taken for something else: the goal is that much less funded. */
    val usedElsewhereMinor: Long = 0,
    /** Saved and not spent yet: what is still held for the goal. */
    val heldMinor: Long = savedMinor,
    /** The last day money was spent from it (on the goal or something else). */
    val lastSpendOn: LocalDate? = null,
)

object GoalMath {
    /** Whole months from [today] until [date], counting a part month as a month. At least one while the date is ahead. */
    fun monthsUntil(today: LocalDate, date: LocalDate): Long {
        if (!date.isAfter(today)) return 0
        val whole = ChronoUnit.MONTHS.between(today, date)
        return if (today.plusMonths(whole).isBefore(date)) whole + 1 else whole.coerceAtLeast(1)
    }

    private fun ceilDiv(a: Long, b: Long): Long = if (b <= 0) 0 else (a + b - 1) / b

    fun progress(
        goal: SavingsGoal,
        contributions: List<GoalContribution>,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        today: LocalDate,
        cycle: PayPeriod,
        /** This cycle's own saving, set when the cycle was set up; zero means none this cycle. */
        cyclePlanMinor: Long? = null,
    ): GoalProgress {
        val account = goal.accountId?.let { id -> accounts.firstOrNull { it.id == id && it.active && !it.deleted } }
        val byBalance = goal.useAccountBalance && account != null
        val mine = contributions.filter { it.goalId == goal.id }
        val spentOnGoal = -mine.filter { it.kind == com.ledgerline.app.domain.model.GoalEntryKind.SPENT_ON_GOAL }.sumOf { it.amountMinor }
        val usedElsewhere = -mine.filter { it.kind == com.ledgerline.app.domain.model.GoalEntryKind.USED_ELSEWHERE }.sumOf { it.amountMinor }
        // Saving toward the goal: everything except money spent on the goal itself, which still counts as done.
        val funding = mine.filter { it.kind != com.ledgerline.app.domain.model.GoalEntryKind.SPENT_ON_GOAL }
        val active = transactions.filter { BalanceMath.isActive(it) }
        val balance = if (byBalance) BalanceMath.currentBalanceMinor(account!!, active) else 0L
        val saved = if (byBalance) {
            balance + spentOnGoal
        } else {
            funding.sumOf { it.amountMinor }
        }.coerceAtLeast(0)
        // A finished goal holds nothing and asks for nothing more.
        val held = if (goal.closed) 0L else if (byBalance) balance.coerceAtLeast(0) else (saved - spentOnGoal).coerceAtLeast(0)
        val lastSpend = mine.filter {
            it.kind == com.ledgerline.app.domain.model.GoalEntryKind.SPENT_ON_GOAL || it.kind == com.ledgerline.app.domain.model.GoalEntryKind.USED_ELSEWHERE
        }.maxOfOrNull { it.onDate }
        val savedThisCycle = if (byBalance) {
            // Money that moved into the account this cycle, net of money taken out.
            active.filter { it.accountId == account!!.id && cycle.contains(it.occurredOn) && !it.occurredOn.isAfter(today) }.sumOf { it.balanceEffectMinor }
        } else {
            funding.filter { cycle.contains(it.onDate) && !it.onDate.isAfter(today) }.sumOf { it.amountMinor }
        }.coerceAtLeast(0)
        val remaining = (goal.targetMinor - saved).coerceAtLeast(0)
        val reached = goal.targetMinor > 0 && remaining == 0L
        // Worked out from what was left at the start of this cycle, so saving this month does not shrink this
        // month's own target; it just counts toward it.
        val remainingAtCycleStart = remaining + savedThisCycle
        // Counted in pay cycles from this one's first day, so the rest is split evenly across the cycles left
        // (this one included) rather than front-loaded into this cycle.
        val needed = goal.targetDate?.let { date ->
            val months = if (date.isBefore(cycle.start)) 0L else monthsUntil(cycle.start, date)
            if (months == 0L) remaining else ceilDiv(remainingAtCycleStart, months)
        }?.takeIf { !reached }
        val usual = goal.monthlyMinor?.takeIf { it > 0 } ?: needed
        val plan = (if (cyclePlanMinor != null) cyclePlanMinor else usual)?.takeIf { !reached && !goal.closed && it > 0 }
        val finish = when {
            reached -> today
            // Saving [plan] each payday from this cycle's: the payday of the last one needed.
            plan != null && plan > 0 -> maxOf(today, cycle.start.plusMonths((ceilDiv(remainingAtCycleStart, plan) - 1).coerceAtLeast(0)))
            else -> null
        }
        val onTrack = when {
            reached -> true
            goal.targetDate == null || finish == null -> null
            else -> !finish.isAfter(goal.targetDate)
        }
        val setAside = plan?.let { (minOf(it, remaining) - savedThisCycle).coerceAtLeast(0) } ?: 0
        return GoalProgress(
            goal = goal,
            savedMinor = saved,
            remainingMinor = remaining,
            fraction = if (goal.targetMinor > 0) (saved.toFloat() / goal.targetMinor.toFloat()).coerceIn(0f, 1f) else 0f,
            reached = reached,
            planMonthlyMinor = plan,
            neededPerMonthMinor = needed,
            finishOn = finish,
            onTrack = onTrack,
            savedThisCycleMinor = savedThisCycle,
            setAsideNowMinor = setAside,
            accountMissing = goal.useAccountBalance && account == null,
            spentOnGoalMinor = spentOnGoal,
            usedElsewhereMinor = usedElsewhere,
            heldMinor = held,
            lastSpendOn = lastSpend,
        )
    }

    fun progressAll(
        goals: List<SavingsGoal>,
        contributions: List<GoalContribution>,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        today: LocalDate,
        cycle: PayPeriod,
        cyclePlans: Map<String, Long> = emptyMap(),
    // A finished goal (spent, or used for something else) no longer holds money or asks for any.
    // Finished goals are included (holding and asking for nothing) so Home can show them in the cycle they finished.
    ): List<GoalProgress> = goals.filter { !it.archived }.map { progress(it, contributions, accounts, transactions, today, cycle, cyclePlans[it.id]) }

    /**
     * What Safe to spend holds back for goals:
     * - this cycle's plan that is not saved yet, wherever it will be kept, so you pay yourself first; and
     * - money already saved that still sits in a spending account (checking, cash), because it is spoken for.
     * Money saved in a separate savings account has already left the spending pool, so nothing more is held for it.
     */
    fun heldBack(progress: List<GoalProgress>, accounts: List<Account>): Long = progress.sumOf { row ->
        // A reached goal's money is still spoken for until you spend it on the goal or remove the goal.
        val inSpendingAccount = row.goal.accountId?.let { id -> accounts.firstOrNull { it.id == id && !it.deleted }?.includeInSafeToSpend } == true
        row.setAsideNowMinor + if (inSpendingAccount) row.heldMinor else 0L
    }
}

/** The plan as you edit a goal: what a date needs per month, and when a monthly amount gets you there. */
object GoalPreview {
    data class Plan(val neededPerMonthMinor: Long?, val finishOn: LocalDate?, val late: Boolean)

    fun plan(targetMinor: Long, savedMinor: Long, date: LocalDate?, monthlyMinor: Long?, today: LocalDate): Plan {
        val remaining = (targetMinor - savedMinor).coerceAtLeast(0)
        val needed = date?.let { d ->
            val months = GoalMath.monthsUntil(today, d)
            if (months == 0L) remaining else (remaining + months - 1) / months
        }
        val finish = monthlyMinor?.takeIf { it > 0 }?.let { m -> today.plusMonths((remaining + m - 1) / m) }
        return Plan(needed, finish, late = date != null && finish != null && finish.isAfter(date))
    }
}

/** One-tap starting points for common goals. */
object GoalPresets {
    val names = listOf("Room deposit", "Emergency fund", "Stocks", "Trip", "New phone", "Car")
}
