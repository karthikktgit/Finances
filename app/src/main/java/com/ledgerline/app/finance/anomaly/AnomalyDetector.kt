package com.ledgerline.app.finance.anomaly

import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.InsightNote
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.money.MoneyFormat
import java.time.LocalDate
import kotlin.math.sqrt

object AnomalyDetector {
    fun analyze(
        today: LocalDate,
        currency: String,
        categories: List<Category>,
        transactions: List<LedgerTransaction>,
    ): List<InsightNote> {
        val historyStart = today.minusDays(180)
        val recentStart = today.minusDays(14)
        val active = transactions.filter {
            BalanceMath.isActive(it) && it.currency == currency && it.type == TransactionType.EXPENSE &&
                !it.occurredOn.isBefore(historyStart) && !it.occurredOn.isAfter(today)
        }
        return active
            .filter { !it.occurredOn.isBefore(recentStart) }
            .mapNotNull { candidate ->
                val peers = active.filter {
                    it.id != candidate.id && it.categoryId == candidate.categoryId && it.occurredOn.isBefore(candidate.occurredOn)
                }
                if (peers.size < 4) return@mapNotNull null
                val amounts = peers.map { it.amountMinor.toDouble() }
                val median = peers.map { it.amountMinor }.sorted().let { it[it.size / 2] }
                val mean = amounts.average()
                val variance = amounts.sumOf { (it - mean) * (it - mean) } / amounts.size
                val threshold = maxOf(median * 2, (mean + 2 * sqrt(variance)).toLong())
                if (candidate.amountMinor <= threshold || median <= 0) return@mapNotNull null
                val label = CategoryTree.label(candidate.categoryId, categories).ifBlank { "Uncategorized" }
                InsightNote(
                    "Unusual amount",
                    "A $label transaction of ${MoneyFormat.format(candidate.amountMinor, currency)} at ${candidate.merchantRaw.ifBlank { "an unnamed merchant" }} is higher than the usual range around ${MoneyFormat.format(median, currency)}. This is a pattern comparison, not a fraud finding.",
                )
            }
            .distinctBy { it.detail }
            .take(5)
    }
}
