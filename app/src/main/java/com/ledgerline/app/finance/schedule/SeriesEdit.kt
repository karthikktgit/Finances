package com.ledgerline.app.finance.schedule

import com.ledgerline.app.domain.model.RecurringPayment
import java.time.LocalDate

object SeriesEdit {
    fun endBefore(item: RecurringPayment, occurrenceOn: LocalDate): RecurringPayment {
        val end = occurrenceOn.minusDays(1)
        return if (end.isBefore(item.startOn)) {
            item.copy(active = false, endOn = item.startOn)
        } else {
            item.copy(endOn = end)
        }
    }

    fun occurrencesUsedBefore(item: RecurringPayment, occurrenceOn: LocalDate): Int {
        if (!occurrenceOn.isAfter(item.startOn)) return 0
        return RecurringSchedule.occurrencesBetween(item, item.startOn, occurrenceOn.minusDays(1)).size
    }

    fun remainingCount(item: RecurringPayment, occurrenceOn: LocalDate): Int? {
        val max = item.maxOccurrences ?: return null
        return (max - occurrencesUsedBefore(item, occurrenceOn)).coerceAtLeast(0)
    }
}
