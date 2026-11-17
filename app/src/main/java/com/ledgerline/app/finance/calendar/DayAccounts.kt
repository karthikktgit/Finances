package com.ledgerline.app.finance.calendar

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import java.time.LocalDate

/** One account on one day: what it started with, what came in and went out, and what it ended with. */
data class DayAccountRow(
    val accountId: String,
    val name: String,
    val startMinor: Long,
    val inMinor: Long,
    val outMinor: Long,
) {
    val endMinor: Long get() = startMinor + inMinor - outMinor
    val moved: Boolean get() = inMinor != 0L || outMinor != 0L
}

/**
 * Each account's money on [date]. Recorded transactions count on their day. For today and later days, bills and income
 * still to come (and spends you planned) count on their due day too, so a future day shows where each account is
 * heading. Moves between your own accounts are in on one and out on the other.
 */
object DayAccounts {
    fun of(
        date: LocalDate,
        today: LocalDate,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        /** Scheduled bills and income from today up to [date]; the ones still unpaid count as coming. */
        scheduled: List<ProjectedOccurrence>,
    ): List<DayAccountRow> {
        val live = accounts.filter { it.active && !it.deleted }
        // What is still to come, by account and day: unpaid, not skipped, and not a move between your own accounts.
        val coming = scheduled.filter { it.state == OccurrenceState.SCHEDULED && it.kind != ScheduledKind.TRANSFER && !it.date.isBefore(today) && !it.date.isAfter(date) }
        val planned = transactions.filter { BalanceMath.isPlannedUnpaid(it) && it.occurredOn.isAfter(today.minusDays(1)) && !it.occurredOn.isAfter(date) }
        fun signed(o: ProjectedOccurrence) = if (o.direction == CashFlowDirection.INFLOW) o.amountMinor else -o.amountMinor
        return live.map { account ->
            val recordedBefore = BalanceMath.balanceAsOf(account, transactions, date.minusDays(1))
            val comingBefore = coming.filter { it.accountId == account.id && it.date.isBefore(date) }.sumOf(::signed) -
                planned.filter { it.accountId == account.id && it.occurredOn.isBefore(date) }.sumOf { it.amountMinor }
            val recordedToday = transactions.filter { BalanceMath.isActive(it) && it.accountId == account.id && it.occurredOn == date }.map { it.balanceEffectMinor }
            val comingToday = coming.filter { it.accountId == account.id && it.date == date }.map(::signed) +
                planned.filter { it.accountId == account.id && it.occurredOn == date }.map { -it.amountMinor }
            val moves = recordedToday + comingToday
            DayAccountRow(
                accountId = account.id,
                name = account.name,
                startMinor = recordedBefore + comingBefore,
                inMinor = moves.filter { it > 0 }.sum(),
                outMinor = -moves.filter { it < 0 }.sum(),
            )
        }.sortedWith(compareByDescending<DayAccountRow> { it.moved }.thenBy { it.name })
    }
}
