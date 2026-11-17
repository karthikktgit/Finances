package com.ledgerline.app.finance.schedule

import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.time.AppClock
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bills on autopay complete themselves: every due date from the day autopay was turned on through today is
 * recorded as paid, exactly as if you had tapped Paid. Dates you skipped or already paid are left alone, and
 * nothing before the autopay start date is touched, so turning it on never back-fills old history.
 */
object AutoPayRule {
    fun due(item: RecurringPayment, adjustments: List<OccurrenceAdjustment>, today: java.time.LocalDate): List<java.time.LocalDate> {
        val since = item.autopaySince ?: return emptyList()
        if (!item.active) return emptyList()
        val from = maxOf(since, item.startOn)
        if (from.isAfter(today)) return emptyList()
        val handled = adjustments
            .filter { it.recurringId == item.id && (it.state == OccurrenceState.PAID || it.state == OccurrenceState.SKIPPED) }
            .map { it.occurrenceOn }
            .toSet()
        return RecurringSchedule.occurrencesBetween(item, from, today).filter { it !in handled }
    }
}

@Singleton
class AutoPay @Inject constructor(
    private val recurring: RecurringRepository,
    private val clock: AppClock,
) {
    /** Marks every autopay date that has come due. Returns how many were recorded. */
    suspend fun settleDue(): Int {
        val today = clock.today()
        val adjustments = recurring.observeAdjustments().first()
        var count = 0
        recurring.observe().first().forEach { item ->
            AutoPayRule.due(item, adjustments, today).forEach { date ->
                if (recurring.markPaid(item.id, date, automatic = true) is SaveResult.Saved) count++
            }
        }
        return count
    }
}
