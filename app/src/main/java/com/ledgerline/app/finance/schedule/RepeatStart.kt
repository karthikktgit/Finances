package com.ledgerline.app.finance.schedule

import java.time.LocalDate

/** Where a repeating transaction starts. */
object RepeatStart {
    /**
     * Same day each month: that day in [date]'s month, or next month if it has already passed by [date] (a short
     * month uses its last day). Otherwise the schedule starts on [date] itself.
     */
    fun first(date: LocalDate, day: Int, monthlyOnDay: Boolean): LocalDate {
        if (!monthlyOnDay) return date
        val d = day.coerceIn(1, 31)
        val inMonth = date.withDayOfMonth(minOf(d, date.lengthOfMonth()))
        if (!inMonth.isBefore(date)) return inMonth
        val next = date.plusMonths(1)
        return next.withDayOfMonth(minOf(d, next.lengthOfMonth()))
    }
}
