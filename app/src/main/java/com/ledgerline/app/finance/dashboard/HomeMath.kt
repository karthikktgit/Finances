package com.ledgerline.app.finance.dashboard

import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SafeToSpendLine
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.period.PayPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Small figures shown on Home and Activity, kept here so they are tested rather than computed inside screens. */
object HomeMath {
    /** Days safe-to-spend has to last, counting today. Never below one. */
    fun daysLeft(today: LocalDate, cycleEnd: LocalDate): Long =
        (ChronoUnit.DAYS.between(today, cycleEnd) + 1).coerceAtLeast(1)

    /** An even daily share of what is safe to spend. Zero when nothing is safe to spend. Rounds down. */
    fun perDay(safeMinor: Long, daysLeft: Long): Long = if (safeMinor <= 0 || daysLeft <= 0) 0 else safeMinor / daysLeft

    /** Which day of the cycle today is, and how many days the cycle has. Past and future cycles report full or zero. */
    fun cycleDay(period: PayPeriod, today: LocalDate): Pair<Long, Long> {
        val total = ChronoUnit.DAYS.between(period.start, period.end) + 1
        val day = when {
            today.isBefore(period.start) -> 0
            today.isAfter(period.end) -> total
            else -> ChronoUnit.DAYS.between(period.start, today) + 1
        }
        return day to total
    }

    /** Spending on one day: counted expenses only. Refunds, transfers, and ignored or duplicate rows are left out. */
    fun daySpending(rows: List<LedgerTransaction>): Long =
        rows.filter { BalanceMath.isActive(it) && it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }

    /**
     * The safe-to-spend lines in plain words. Zero lines other than the balance are hidden, because they only add
     * noise. The amounts are unchanged, so the shown lines still add up to the total.
     */
    fun readableLines(lines: List<SafeToSpendLine>): List<SafeToSpendLine> =
        lines.mapIndexedNotNull { index, line ->
            if (index > 0 && line.minor == 0L) return@mapIndexedNotNull null
            line.copy(label = plainLabel(line.label))
        }

    private fun plainLabel(label: String): String = when (label) {
        "Current liquid balance" -> "Money in your spending accounts"
        "Committed payments" -> "Bills still due this cycle"
        "Minimum buffer" -> "Safety buffer you keep"
        "Planned transfers" -> "Transfers planned out"
        "Planned discretionary spending" -> "Set aside for your budgets"
        "Savings goals" -> "Saving for your goals"
        else -> label
    }
}
