package com.ledgerline.app.finance.period

import java.time.LocalDate
import java.time.YearMonth

data class DayHighlight(
    val inCycle: Boolean,
    val cycleStart: Boolean,
    val cycleEnd: Boolean,
    val selected: Boolean,
)

object CycleHighlight {
    fun of(date: LocalDate, period: PayPeriod?, selected: LocalDate, cycleMode: Boolean): DayHighlight {
        val inside = cycleMode && period != null && period.contains(date)
        return DayHighlight(
            inCycle = inside,
            cycleStart = inside && date == period!!.start,
            cycleEnd = inside && date == period!!.end,
            selected = date == selected,
        )
    }

    fun datesInMonth(month: YearMonth, period: PayPeriod): List<LocalDate> {
        val start = maxOf(period.start, month.atDay(1))
        val end = minOf(period.end, month.atEndOfMonth())
        if (start.isAfter(end)) return emptyList()
        val dates = mutableListOf<LocalDate>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            dates += cursor
            cursor = cursor.plusDays(1)
        }
        return dates
    }
}
