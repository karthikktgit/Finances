package com.ledgerline.app.finance.balance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.category.SpendingRules
import com.ledgerline.app.finance.period.PayPeriod
import java.time.LocalDate

data class DayFigures(
    val startMinor: Long,
    val spentMinor: Long,
    val unplannedMinor: Long,
    val endMinor: Long,
    val complete: Boolean,
)

object DayLedger {
    fun figures(
        date: LocalDate,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
    ): DayFigures {
        val active = transactions.filter { BalanceMath.isActive(it) }
        val known = accounts.map { it.id }.toSet()
        val complete = active.all { it.accountId in known }
        val liquid = accounts.filter { it.includeInSafeToSpend }
        val start = liquid.sumOf { BalanceMath.balanceAsOf(it, active, date.minusDays(1)) }
        val end = liquid.sumOf { BalanceMath.balanceAsOf(it, active, date) }
        val day = active.filter { it.occurredOn == date }
        return DayFigures(
            startMinor = start,
            spentMinor = netSpending(day),
            unplannedMinor = netSpending(day.filter { it.unplanned }),
            endMinor = end,
            complete = complete,
        )
    }

    fun cycleFigures(
        period: PayPeriod,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
    ): DayFigures {
        val active = transactions.filter { BalanceMath.isActive(it) }
        val known = accounts.map { it.id }.toSet()
        val liquid = accounts.filter { it.includeInSafeToSpend }
        val during = active.filter { period.contains(it.occurredOn) }
        return DayFigures(
            startMinor = liquid.sumOf { BalanceMath.balanceAsOf(it, active, period.start.minusDays(1)) },
            spentMinor = netSpending(during),
            unplannedMinor = netSpending(during.filter { it.unplanned }),
            endMinor = liquid.sumOf { BalanceMath.balanceAsOf(it, active, period.end) },
            complete = active.all { it.accountId in known },
        )
    }

    /** Expenses minus refunds. Transfers and card payments are not spending. */
    fun netSpending(rows: List<LedgerTransaction>): Long {
        val expenses = rows.filter { SpendingRules.countsAsSpending(it) }.sumOf { it.amountMinor }
        val refunds = rows.filter { it.type == TransactionType.REFUND }.sumOf { it.amountMinor }
        return expenses - refunds
    }
}
