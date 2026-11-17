package com.ledgerline.app.finance.forecast

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import java.time.LocalDate

data class DailyForecast(
    val date: LocalDate,
    val actualMinor: Long,
    val actualLabel: String,
    val expectedIncomingMinor: Long,
    val scheduledOutgoingMinor: Long,
    val completedIncomingMinor: Long,
    val completedOutgoingMinor: Long,
    val netChangeMinor: Long,
    val projectedEndMinor: Long,
    val historical: Boolean,
    val incoming: List<ProjectedOccurrence>,
    val outgoing: List<ProjectedOccurrence>,
)

object DailyBalanceForecast {
    fun forDate(
        date: LocalDate,
        today: LocalDate,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        scheduled: List<ProjectedOccurrence>,
    ): DailyForecast {
        val liquid = accounts.filter { it.includeInSafeToSpend }
        val liquidIds = liquid.map { it.id }.toSet()
        val posted = liquid.sumOf { BalanceMath.balanceAsOf(it, transactions, date) }
        val dayTransactions = transactions.filter { BalanceMath.isActive(it) && it.accountId in liquidIds && it.occurredOn == date }
        val completedIn = dayTransactions.filter { it.type == TransactionType.INCOME || it.type == TransactionType.REFUND }.sumOf { it.amountMinor }
        val completedOut = dayTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
        val dayEvents = scheduled.filter { it.date == date && it.accountId in liquidIds }
        val incoming = dayEvents.filter { it.direction == CashFlowDirection.INFLOW && it.kind != ScheduledKind.TRANSFER }
        val outgoing = dayEvents.filter { it.direction == CashFlowDirection.OUTFLOW && it.kind != ScheduledKind.TRANSFER }
        val expectedIn = incoming.sumOf { it.amountMinor }
        val scheduledOut = outgoing.sumOf { it.amountMinor }
        val past = date.isBefore(today)
        val scheduledDelta = if (past) {
            0L
        } else {
            val through = scheduled.filter { event ->
                event.accountId in liquidIds &&
                    event.kind != ScheduledKind.TRANSFER &&
                    !event.date.isBefore(today) &&
                    !event.date.isAfter(date)
            }
            through.filter { it.direction == CashFlowDirection.INFLOW }.sumOf { it.amountMinor } -
                through.filter { it.direction == CashFlowDirection.OUTFLOW }.sumOf { it.amountMinor }
        }
        val label = if (past) {
            "End of this date, from the opening balance and completed transactions through this date."
        } else {
            "Posted balance through this date. Unpaid scheduled items are in the projection only."
        }
        val dayNet = if (past) completedIn - completedOut else completedIn - completedOut + expectedIn - scheduledOut
        return DailyForecast(
            date = date,
            actualMinor = posted,
            actualLabel = label,
            expectedIncomingMinor = expectedIn,
            scheduledOutgoingMinor = scheduledOut,
            completedIncomingMinor = completedIn,
            completedOutgoingMinor = completedOut,
            netChangeMinor = dayNet,
            projectedEndMinor = posted + scheduledDelta,
            historical = past,
            incoming = incoming,
            outgoing = outgoing,
        )
    }

    fun consolidatedActual(accounts: List<Account>, balances: Map<String, Long>): Long =
        accounts.filter { it.includeInNetWorth && !BalanceMath.isDebtType(it.type) }.sumOf { balances[it.id] ?: 0L }
}

object ProjectionColor {
    const val USD_LIMIT_MINOR = 50_000L

    /** True when the USD amount is strictly above $500. Null means the rule does not apply. */
    fun aboveUsdLimit(amountMinor: Long, currency: String): Boolean? {
        if (!currency.equals("USD", ignoreCase = true)) return null
        return amountMinor > USD_LIMIT_MINOR
    }
}
