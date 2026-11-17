package com.ledgerline.app.finance.schedule

import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/**
 * Expands a stored recurrence rule into dates. Occurrences are calculated for a
 * window. They are not inserted as rows.
 *
 * Month-end rule: monthly, quarterly, and yearly dates keep the scheduled day of
 * month. When a month is shorter, that occurrence lands on the last day of the
 * month, and the next one uses the original day again. A payment on the 31st is
 * January 31, February 28 or 29, then March 31.
 */
object RecurringSchedule {
    fun occurrencesBetween(
        item: RecurringPayment,
        startInclusive: LocalDate,
        endInclusive: LocalDate,
    ): List<LocalDate> {
        if (!item.active || item.amountMinor <= 0 || startInclusive > endInclusive) return emptyList()
        val limit = item.maxOccurrences
        if (limit != null && limit < 1) return emptyList()
        val seriesEnd = item.endOn
        val generateFromSeriesStart = limit != null
        val generateEnd = when {
            seriesEnd != null && !generateFromSeriesStart -> minOf(seriesEnd, endInclusive)
            seriesEnd != null -> seriesEnd
            generateFromSeriesStart -> maxOf(endInclusive, item.startOn).plusYears(40)
            else -> endInclusive
        }
        if (item.startOn > generateEnd) return emptyList()
        val generated = generate(item, generateEnd, limit)
        return generated.filter { date ->
            !date.isBefore(startInclusive) &&
                !date.isAfter(endInclusive) &&
                (seriesEnd == null || !date.isAfter(seriesEnd))
        }
    }

    fun nextOnOrAfter(item: RecurringPayment, today: LocalDate): LocalDate? =
        occurrencesBetween(item, today, today.plusYears(3)).firstOrNull()

    private fun generate(item: RecurringPayment, end: LocalDate, limit: Int?): List<LocalDate> {
        val dates = when (item.frequency) {
            RecurrenceFrequency.ONCE -> listOf(item.startOn).filter { !it.isAfter(end) }
            RecurrenceFrequency.DAILY -> datesByStep(item.startOn, 1, end, limit)
            RecurrenceFrequency.WEEKLY -> datesByStep(item.startOn, 7, end, limit)
            RecurrenceFrequency.BIWEEKLY -> datesByStep(item.startOn, 14, end, limit)
            RecurrenceFrequency.MONTHLY -> datesByMonth(item, end, 1, limit)
            RecurrenceFrequency.QUARTERLY -> datesByMonth(item, end, 3, limit)
            RecurrenceFrequency.YEARLY -> datesByMonth(item, end, 12, limit)
            RecurrenceFrequency.CUSTOM -> {
                val step = item.intervalDays ?: return emptyList()
                if (step < 1) emptyList() else datesByStep(item.startOn, step.toLong(), end, limit)
            }
        }
        return if (limit != null) dates.take(limit) else dates
    }

    private fun datesByStep(
        anchor: LocalDate,
        stepDays: Long,
        end: LocalDate,
        limit: Int?,
    ): List<LocalDate> {
        if (stepDays <= 0 || anchor > end) return emptyList()
        val cap = limit ?: Int.MAX_VALUE
        val dates = ArrayList<LocalDate>(min(cap, 512).coerceAtLeast(1))
        var cursor = anchor
        while (!cursor.isAfter(end) && dates.size < cap) {
            dates += cursor
            val next = cursor.plusDays(stepDays)
            if (!next.isAfter(cursor)) break
            cursor = next
        }
        return dates
    }

    private fun datesByMonth(
        item: RecurringPayment,
        end: LocalDate,
        monthStep: Int,
        limit: Int?,
    ): List<LocalDate> {
        if (monthStep < 1) return emptyList()
        val cap = limit ?: Int.MAX_VALUE
        val day = (item.dayOfMonth ?: item.startOn.dayOfMonth).coerceIn(1, 31)
        var month = YearMonth.from(item.startOn)
        val dates = ArrayList<LocalDate>()
        var guard = 0
        while (guard < 600 && dates.size < cap) {
            val date = month.atDay(min(day, month.lengthOfMonth()))
            if (date > end && month.isAfter(YearMonth.from(end))) break
            if (!date.isBefore(item.startOn) && !date.isAfter(end)) {
                if (item.endOn == null || !date.isAfter(item.endOn)) dates += date
            }
            if (date > end) break
            month = month.plusMonths(monthStep.toLong())
            guard += 1
        }
        return dates.distinct()
    }
}
