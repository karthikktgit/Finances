package com.ledgerline.app.finance.leak

import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.InsightNote
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.money.MoneyFormat
import java.time.LocalDate
import java.time.YearMonth

object MoneyLeakEngine {
    private const val SMALL_PURCHASE_MINOR = 2_000L
    private const val SMALL_PURCHASE_COUNT = 8

    fun analyze(
        today: LocalDate,
        currency: String,
        categories: List<Category>,
        transactions: List<LedgerTransaction>,
        recurring: List<RecurringPayment>,
    ): List<InsightNote> {
        val notes = mutableListOf<InsightNote>()
        val active = transactions.filter { BalanceMath.isActive(it) && it.currency == currency }
        val thisMonth = monthRows(active, YearMonth.from(today))
        val lastMonth = monthRows(active, YearMonth.from(today).minusMonths(1))
        val small = thisMonth.filter { it.type == TransactionType.EXPENSE && it.amountMinor in 1..SMALL_PURCHASE_MINOR }
        if (small.size >= SMALL_PURCHASE_COUNT) {
            val total = small.sumOf { it.amountMinor }
            notes += InsightNote(
                "Small purchases",
                "You made ${small.size} purchases under ${MoneyFormat.format(SMALL_PURCHASE_MINOR, currency)} this month, totaling ${MoneyFormat.format(total, currency)}.",
            )
        }
        watchCategory(notes, categories, thisMonth, lastMonth, currency, "coffee", "Coffee")
        watchCategory(notes, categories, thisMonth, lastMonth, currency, "restaurant", "Restaurant")
        watchCategory(notes, categories, thisMonth, lastMonth, currency, "delivery", "Food delivery")
        watchCategory(notes, categories, thisMonth, lastMonth, currency, "dining", "Dining")
        val fees = thisMonth.filter { it.type == TransactionType.EXPENSE && it.merchantNormalized.contains("fee") }
        if (fees.isNotEmpty()) {
            notes += InsightNote(
                "Fees",
                "${fees.size} fee ${if (fees.size == 1) "charge" else "charges"} this month, totaling ${MoneyFormat.format(fees.sumOf { it.amountMinor }, currency)}.",
            )
        }
        val atm = thisMonth.filter { it.type == TransactionType.EXPENSE && it.merchantNormalized.contains("atm") }
        if (atm.size >= 3) {
            notes += InsightNote(
                "ATM withdrawals",
                "ATM withdrawals this month: ${atm.size}, totaling ${MoneyFormat.format(atm.sumOf { it.amountMinor }, currency)}.",
            )
        }
        val subscriptions = recurring.filter { it.active && it.kind == ScheduledKind.SUBSCRIPTION && it.currency == currency }
        subscriptions.groupBy { it.name.trim().lowercase() }.filter { it.value.size > 1 }.forEach { (name, rows) ->
            notes += InsightNote(
                "Repeated subscription",
                "${rows.size} active schedules use the name ${name.replaceFirstChar { it.titlecase() }}. Compare them before treating both as required.",
            )
        }
        priceIncreases(active, today, currency).forEach { notes += it }
        return notes
    }

    private fun watchCategory(
        notes: MutableList<InsightNote>,
        categories: List<Category>,
        current: List<LedgerTransaction>,
        previous: List<LedgerTransaction>,
        currency: String,
        token: String,
        label: String,
    ) {
        val ids = categories.filter { it.name.contains(token, ignoreCase = true) }.map { it.id }.toSet()
        if (ids.isEmpty()) return
        val now = spend(current, ids)
        val then = spend(previous, ids)
        if (then >= 1_000 && now > then && (now - then) * 100 / then >= 25) {
            notes += InsightNote(
                label,
                "$label spending moved from ${MoneyFormat.format(then, currency)} to ${MoneyFormat.format(now, currency)}.",
            )
        }
    }

    private fun priceIncreases(transactions: List<LedgerTransaction>, today: LocalDate, currency: String): List<InsightNote> {
        val start = today.minusDays(120)
        return transactions
            .filter { it.type == TransactionType.EXPENSE && !it.occurredOn.isBefore(start) && it.merchantNormalized.isNotBlank() }
            .groupBy { it.merchantNormalized }
            .mapNotNull { (merchant, rows) ->
                if (rows.size < 4) return@mapNotNull null
                val ordered = rows.sortedBy { it.occurredOn }
                val latest = ordered.last()
                val prior = ordered.dropLast(1).map { it.amountMinor }.sorted()
                val median = prior[prior.size / 2]
                if (median <= 0 || latest.amountMinor * 100 <= median * 115) return@mapNotNull null
                InsightNote(
                    "Price change",
                    "$merchant is ${MoneyFormat.format(latest.amountMinor, currency)}. The earlier median was ${MoneyFormat.format(median, currency)}.",
                )
            }
    }

    private fun monthRows(transactions: List<LedgerTransaction>, month: YearMonth): List<LedgerTransaction> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        return transactions.filter { !it.occurredOn.isBefore(start) && !it.occurredOn.isAfter(end) }
    }

    private fun spend(transactions: List<LedgerTransaction>, categoryIds: Set<String>): Long {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE && it.categoryId in categoryIds }.sumOf { it.amountMinor }
        val refunds = transactions.filter { it.type == TransactionType.REFUND && it.categoryId in categoryIds }.sumOf { it.amountMinor }
        return (expenses - refunds).coerceAtLeast(0)
    }
}
