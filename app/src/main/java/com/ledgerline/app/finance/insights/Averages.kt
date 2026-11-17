package com.ledgerline.app.finance.insights

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.category.SpendingRules
import java.time.LocalDate
import java.time.YearMonth

/** A category's usual month next to this month. */
data class CategoryAverage(val name: String, val averageMinor: Long, val thisMonthMinor: Long)

data class Averages(
    /** The average end-of-day balance across spending accounts over the window. */
    val balanceMinor: Long,
    val perDayMinor: Long,
    val perMonthMinor: Long,
    val categories: List<CategoryAverage>,
    /** How many days the averages cover (shorter when your records are newer than the window). */
    val days: Int,
)

/**
 * Your usual spending and balance, from the last [windowDays] days (or since your first record, if later). Spending is
 * expenses less refunds; categories roll up to their top-level group so the list stays short.
 */
object InsightAverages {
    fun compute(
        today: LocalDate,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        categories: List<Category>,
        windowDays: Int = 90,
    ): Averages {
        val liquid = accounts.filter { it.active && !it.deleted && it.includeInSafeToSpend }
        val ids = liquid.map { it.id }.toSet()
        val active = transactions.filter { BalanceMath.isActive(it) && it.accountId in ids }
        val first = active.minOfOrNull { it.occurredOn } ?: today
        val start = maxOf(today.minusDays(windowDays.toLong() - 1), first)
        val days = (java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceAtLeast(1)
        // Average end-of-day balance: start from the balance the day before, then walk each day's changes.
        var balance = liquid.sumOf { BalanceMath.balanceAsOf(it, active, start.minusDays(1)) }
        val byDay = active.filter { !it.occurredOn.isBefore(start) && !it.occurredOn.isAfter(today) }.groupBy { it.occurredOn }
        var total = 0L
        var date = start
        while (!date.isAfter(today)) {
            balance += byDay[date].orEmpty().sumOf { it.balanceEffectMinor }
            total += balance
            date = date.plusDays(1)
        }
        val window = active.filter { !it.occurredOn.isBefore(start) && !it.occurredOn.isAfter(today) }
        fun net(rows: List<LedgerTransaction>) =
            (rows.filter { SpendingRules.countsAsSpending(it) }.sumOf { it.amountMinor } - rows.filter { it.type == TransactionType.REFUND }.sumOf { it.amountMinor }).coerceAtLeast(0)
        val spent = net(window)
        val months = days / 30.4375
        val byId = categories.associateBy { it.id }
        fun root(id: String?): String {
            var c = id?.let { byId[it] } ?: return "No category"
            while (c.parentId != null) c = byId[c.parentId] ?: break
            return c.name
        }
        val month = YearMonth.from(today)
        val thisMonth = active.filter { YearMonth.from(it.occurredOn) == month }
        val categoryAverages = window.groupBy { root(it.categoryId) }
            .map { (name, rows) ->
                CategoryAverage(
                    name = name,
                    averageMinor = (net(rows) / months.coerceAtLeast(1.0)).toLong(),
                    thisMonthMinor = net(thisMonth.filter { root(it.categoryId) == name }),
                )
            }
            .filter { it.averageMinor > 0 || it.thisMonthMinor > 0 }
            .sortedByDescending { it.averageMinor }
        return Averages(
            balanceMinor = total / days,
            perDayMinor = spent / days,
            perMonthMinor = (spent / months.coerceAtLeast(1.0)).toLong(),
            categories = categoryAverages,
            days = days,
        )
    }
}
