package com.ledgerline.app.finance.calendar

import com.ledgerline.app.finance.period.PayPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Every cell is a full calendar date. Leading and trailing days keep their own
 * month and year instead of being rewritten as the displayed month's day number.
 */
object CalendarGrid {
    fun dates(month: YearMonth): List<LocalDate> {
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value % 7
        // Always six weeks, so the calendar is the same height in every month and nothing moves when you swipe.
        val rows = 6
        val start = first.minusDays(lead.toLong())
        return List(rows * 7) { offset -> start.plusDays(offset.toLong()) }
    }

    fun rangeEdge(date: LocalDate, period: PayPeriod): RangeEdge? {
        if (!period.contains(date)) return null
        val roundStart = date == period.start || date.dayOfWeek == DayOfWeek.SUNDAY || !period.contains(date.minusDays(1))
        val roundEnd = date == period.end || date.dayOfWeek == DayOfWeek.SATURDAY || !period.contains(date.plusDays(1))
        return RangeEdge(roundStart, roundEnd)
    }
}

data class RangeEdge(val roundStart: Boolean, val roundEnd: Boolean)
