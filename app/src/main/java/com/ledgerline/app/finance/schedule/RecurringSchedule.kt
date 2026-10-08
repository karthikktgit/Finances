package com.ledgerline.app.finance.schedule

import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

object RecurringSchedule {
    fun occurrencesBetween(
        item: RecurringPayment,
        startInclusive: LocalDate,
        endInclusive: LocalDate,
    ): List<LocalDate> {
        if (!item.active || item.amountMinor <= 0) return emptyList()
        val end = item.endOn?.let { minOf(it, endInclusive) } ?: endInclusive
        val start = maxOf(startInclusive, item.startOn)
        if (start > end) return emptyList()
        return when (item.frequency) {
            RecurrenceFrequency.DAILY -> datesByStep(item.startOn, 1, start, end)
            RecurrenceFrequency.WEEKLY -> datesByStep(item.startOn, 7, start, end)
            RecurrenceFrequency.BIWEEKLY -> datesByStep(item.startOn, 14, start, end)
            RecurrenceFrequency.MONTHLY -> datesByMonth(item, start, end, 1)
            RecurrenceFrequency.QUARTERLY -> datesByMonth(item, start, end, 3)
            RecurrenceFrequency.YEARLY -> datesByMonth(item, start, end, 12)
            RecurrenceFrequency.CUSTOM -> {
                val step = item.intervalDays ?: return emptyList()
                if (step < 1) emptyList() else datesByStep(item.startOn, step.toLong(), start, end)
            }
        }
    }

    fun nextOnOrAfter(item: RecurringPayment, today: LocalDate): LocalDate? =
        occurrencesBetween(item, today, today.plusYears(3)).firstOrNull()

    private fun datesByStep(
        anchor: LocalDate,
        stepDays: Long,
        start: LocalDate,
        end: LocalDate,
    ): List<LocalDate> {
        if (stepDays <= 0) return emptyList()
        var cursor = anchor
        if (cursor < start) {
            val days = ChronoUnit.DAYS.between(cursor, start)
            val steps = (days + stepDays - 1) / stepDays
            cursor = cursor.plusDays(steps * stepDays)
        }
        val dates = mutableListOf<LocalDate>()
        while (cursor <= end) {
            if (cursor >= start) dates += cursor
            cursor = cursor.plusDays(stepDays)
        }
        return dates
    }

    private fun datesByMonth(
        item: RecurringPayment,
        start: LocalDate,
        end: LocalDate,
        monthStep: Int,
    ): List<LocalDate> {
        if (monthStep < 1) return emptyList()
        val day = (item.dayOfMonth ?: item.startOn.dayOfMonth).coerceIn(1, 31)
        var month = YearMonth.from(item.startOn)
        val dates = mutableListOf<LocalDate>()
        var guard = 0
        while (guard < 240) {
            val date = month.atDay(min(day, month.lengthOfMonth()))
            if (date > end && month.isAfter(YearMonth.from(end))) break
            if (!date.isBefore(start) && !date.isAfter(end) && !date.isBefore(item.startOn)) {
                if (item.endOn == null || !date.isAfter(item.endOn)) dates += date
            }
            if (date > end) break
            month = month.plusMonths(monthStep.toLong())
            guard += 1
        }
        return dates.distinct()
    }
}
