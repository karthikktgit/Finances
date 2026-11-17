package com.ledgerline.app.finance.subscription

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind

object SubscriptionRollup {
    private val known = setOf(
        "netflix",
        "spotify",
        "amazon prime",
        "icloud",
        "google one",
        "hulu",
        "disney",
        "youtube",
        "adobe",
        "dropbox",
        "gym",
    )

    fun isKnownSubscription(normalizedMerchant: String): Boolean {
        val name = normalizedMerchant.trim().lowercase()
        return known.any { name == it || name.contains(it) }
    }

    fun active(items: List<RecurringPayment>, currency: String): List<RecurringPayment> =
        items.filter {
            it.active && it.currency == currency && it.kind == ScheduledKind.SUBSCRIPTION &&
                it.direction == CashFlowDirection.OUTFLOW
        }

    fun monthlyMinor(items: List<RecurringPayment>): Long = items.sumOf { toMonthly(it.amountMinor, it.frequency) }

    fun annualizedMinor(monthlyMinor: Long): Long = monthlyMinor * 12

    fun toMonthly(amountMinor: Long, frequency: RecurrenceFrequency): Long = when (frequency) {
        RecurrenceFrequency.DAILY -> amountMinor * 30
        RecurrenceFrequency.WEEKLY -> amountMinor * 52 / 12
        RecurrenceFrequency.BIWEEKLY -> amountMinor * 26 / 12
        RecurrenceFrequency.MONTHLY -> amountMinor
        RecurrenceFrequency.QUARTERLY -> amountMinor / 3
        RecurrenceFrequency.YEARLY -> amountMinor / 12
        RecurrenceFrequency.ONCE -> 0
        RecurrenceFrequency.CUSTOM -> amountMinor
    }
}
