package com.ledgerline.app.finance.forecast

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import java.time.LocalDate

/** A day that has already happened: the cash it actually ended with, and what moved. */
data class PastDay(
    val date: LocalDate,
    val endMinor: Long,
    /** Income and refunds that came in. */
    val inMinor: Long,
    /** Everyday spending: expenses not tied to a bill. */
    val spentMinor: Long,
    /** Bills paid: expenses and payments recorded against a calendar bill. */
    val billsMinor: Long,
)

/** The real cash in the chosen accounts on the days before today, from what was recorded. */
object CashFlowHistory {
    /**
     * Where the history starts: up to [lookbackDays] before today, but never before the first recorded transaction on
     * the chosen accounts (the day before it, so the line starts at the opening balance). Null when there is no history.
     */
    fun startOn(today: LocalDate, lookbackDays: Int, accounts: List<Account>, selectedIds: Set<String>, transactions: List<LedgerTransaction>): LocalDate? {
        if (lookbackDays <= 0) return null
        val chosen = accounts.filter { it.id in selectedIds }.map { it.id }.toSet()
        val first = transactions
            .filter { BalanceMath.isActive(it) && it.accountId in chosen && it.occurredOn.isBefore(today) }
            .minOfOrNull { it.occurredOn } ?: return null
        val start = maxOf(today.minusDays(lookbackDays.toLong()), first.minusDays(1))
        return start.takeIf { it.isBefore(today) }
    }

    /** Each day from [from] up to the day before [today], with the cash at its end. Uses the same balance as Home. */
    fun days(from: LocalDate, today: LocalDate, accounts: List<Account>, selectedIds: Set<String>, transactions: List<LedgerTransaction>): List<PastDay> {
        if (!from.isBefore(today)) return emptyList()
        val chosen = accounts.distinctBy { it.id }.filter { it.id in selectedIds }
        val ids = chosen.map { it.id }.toSet()
        var balance = chosen.sumOf { BalanceMath.balanceAsOf(it, transactions, from.minusDays(1)) }
        val byDate = transactions
            .filter { BalanceMath.isActive(it) && it.accountId in ids && !it.occurredOn.isBefore(from) && it.occurredOn.isBefore(today) }
            .groupBy { it.occurredOn }
        val out = ArrayList<PastDay>()
        var date = from
        while (date.isBefore(today)) {
            val rows = byDate[date].orEmpty()
            balance += rows.sumOf { it.balanceEffectMinor }
            out += PastDay(
                date = date,
                endMinor = balance,
                inMinor = rows.filter { it.type == TransactionType.INCOME || it.type == TransactionType.REFUND }.sumOf { it.balanceEffectMinor },
                spentMinor = rows.filter { it.type == TransactionType.EXPENSE && it.recurringPaymentId == null }.sumOf { it.amountMinor },
                billsMinor = rows.filter { it.recurringPaymentId != null && it.balanceEffectMinor < 0 }.sumOf { -it.balanceEffectMinor },
            )
            date = date.plusDays(1)
        }
        return out
    }

    /**
     * Days worth calling out on the chart: spending well above your usual day. At least twice the average spending
     * day, and never for small amounts.
     */
    fun bigSpendDays(days: List<PastDay>, floorMinor: Long = 2_000): Set<LocalDate> {
        val spending = days.filter { it.spentMinor > 0 }
        if (spending.isEmpty()) return emptySet()
        val average = spending.sumOf { it.spentMinor } / spending.size
        val threshold = maxOf(average * 2, floorMinor)
        return spending.filter { it.spentMinor >= threshold }.map { it.date }.toSet()
    }
}

/** One purchase you are thinking about, on the day you would make it. */
data class WhatIfBuy(val amountMinor: Long, val date: LocalDate)

/** What a planned purchase would do to your cash, in the words that matter before you buy. */
data class WhatIfResult(
    val totalMinor: Long,
    val lowestMinor: Long,
    val lowestOn: LocalDate,
    /** How much lower the lowest point gets. */
    val lowestDropMinor: Long,
    /** True when the cash would run out and does not without the purchase. */
    val newlyShort: Boolean,
    val runsOutOn: LocalDate?,
    /** True when it pulls you into your safety buffer and you stay out of it without it. */
    val newlyIntoBuffer: Boolean,
    val beforePaydayMinor: Long?,
    /** Roughly how many days of your usual everyday spending this is. Null when there is no pace yet. */
    val daysOfSpending: Long?,
    /** More than Safe to spend has free right now. */
    val overSafe: Boolean,
)

object WhatIf {
    /** The projection with the purchases taken out on their days. Each lowers every day from its date on. */
    fun apply(days: List<DayBalance>, buys: List<WhatIfBuy>): List<DayBalance> {
        if (buys.isEmpty()) return days
        return days.map { day ->
            val spent = buys.filter { it.amountMinor > 0 && !it.date.isAfter(day.date) }.sumOf { it.amountMinor }
            if (spent == 0L) day else day.copy(endMinor = day.endMinor - spent, outgoingMinor = day.outgoingMinor, negative = day.endMinor - spent < 0)
        }
    }

    fun result(base: FlowSummary, scenario: FlowSummary, buys: List<WhatIfBuy>, paceMinor: Long, safeMinor: Long): WhatIfResult {
        val total = buys.sumOf { it.amountMinor.coerceAtLeast(0) }
        return WhatIfResult(
            totalMinor = total,
            lowestMinor = scenario.lowestMinor,
            lowestOn = scenario.lowestOn,
            lowestDropMinor = (base.lowestMinor - scenario.lowestMinor).coerceAtLeast(0),
            newlyShort = scenario.runsOutOn != null && base.runsOutOn == null,
            runsOutOn = scenario.runsOutOn,
            newlyIntoBuffer = scenario.belowBufferOn != null && base.belowBufferOn == null && scenario.runsOutOn == null,
            beforePaydayMinor = scenario.beforePaydayMinor,
            daysOfSpending = if (paceMinor > 0) (total + paceMinor / 2) / paceMinor else null,
            overSafe = total > safeMinor.coerceAtLeast(0),
        )
    }
}
