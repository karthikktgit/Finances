package com.ledgerline.app.finance.recurring

import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class RecurringSuggestion(
    val merchantNormalized: String,
    val frequency: RecurrenceFrequency,
    val typicalAmountMinor: Long,
    val dayOfMonth: Int?,
    val sampleCount: Int,
    val accountId: String,
    val categoryId: String?,
    val currency: String,
)

object RecurringDetector {
    fun detect(transactions: List<LedgerTransaction>, today: LocalDate): List<RecurringSuggestion> {
        return transactions
            .filter { BalanceMath.isActive(it) && it.type == TransactionType.EXPENSE && it.merchantNormalized.isNotBlank() }
            .filter { !it.occurredOn.isAfter(today) }
            .groupBy { Triple(it.merchantNormalized, it.currency, it.accountId) }
            .mapNotNull { (key, rows) -> suggestion(key, rows) }
    }

    private fun suggestion(
        key: Triple<String, String, String>,
        rows: List<LedgerTransaction>,
    ): RecurringSuggestion? {
        if (rows.size < 3) return null
        val ordered = rows.sortedBy { it.occurredOn }
        val gaps = ordered.zipWithNext { left, right -> ChronoUnit.DAYS.between(left.occurredOn, right.occurredOn) }
        if (gaps.any { it <= 0 }) return null
        val medianGap = medianLong(gaps)
        val frequency = frequencyFor(medianGap) ?: return null
        val amounts = ordered.map { it.amountMinor }
        val typical = medianLong(amounts)
        if (typical <= 0) return null
        val day = if (frequency == RecurrenceFrequency.MONTHLY || frequency == RecurrenceFrequency.QUARTERLY || frequency == RecurrenceFrequency.YEARLY) {
            ordered.map { it.occurredOn.dayOfMonth }.sorted().let { it[it.size / 2] }
        } else {
            null
        }
        return RecurringSuggestion(
            merchantNormalized = key.first,
            frequency = frequency,
            typicalAmountMinor = typical,
            dayOfMonth = day,
            sampleCount = rows.size,
            accountId = key.third,
            categoryId = ordered.last().categoryId,
            currency = key.second,
        )
    }

    private fun frequencyFor(medianGap: Long): RecurrenceFrequency? = when (medianGap) {
        in 6L..8L -> RecurrenceFrequency.WEEKLY
        in 13L..16L -> RecurrenceFrequency.BIWEEKLY
        in 25L..35L -> RecurrenceFrequency.MONTHLY
        in 80L..100L -> RecurrenceFrequency.QUARTERLY
        in 350L..380L -> RecurrenceFrequency.YEARLY
        else -> null
    }

    private fun medianLong(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    fun gapSpread(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        return abs(values.max() - values.min())
    }
}
