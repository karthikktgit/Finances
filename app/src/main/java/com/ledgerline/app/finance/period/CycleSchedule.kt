package com.ledgerline.app.finance.period

import com.ledgerline.app.domain.model.CycleType
import com.ledgerline.app.domain.model.UserSettings
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

/**
 * Builds the salary cycle from saved settings. Days do not overlap or leave a gap:
 * the next cycle starts the day after this one ends.
 */
object CycleSchedule {
    fun containing(date: LocalDate, settings: UserSettings): PayPeriod = when (settings.cycleType) {
        CycleType.WEEKLY -> step(date, settings.cycleAnchor, 7)
        CycleType.BIWEEKLY -> step(date, settings.cycleAnchor, 14)
        CycleType.SEMIMONTHLY -> semimonthly(date, settings.cycleStartDay, settings.cycleSecondDay)
        CycleType.MONTHLY -> monthly(date, settings.cycleStartDay)
    }

    fun previous(period: PayPeriod, settings: UserSettings): PayPeriod = containing(period.start.minusDays(1), settings)

    fun next(period: PayPeriod, settings: UserSettings): PayPeriod = containing(period.end.plusDays(1), settings)

    private fun step(date: LocalDate, anchor: LocalDate, length: Int): PayPeriod {
        val days = ChronoUnit.DAYS.between(anchor, date)
        val offset = Math.floorMod(days, length.toLong())
        val start = date.minusDays(offset)
        return PayPeriod(start, start.plusDays(length.toLong() - 1))
    }

    private fun monthly(date: LocalDate, startDay: Int): PayPeriod {
        val day = startDay.coerceIn(1, 31)
        val thisStart = clampedDay(YearMonth.from(date), day)
        val start = if (!date.isBefore(thisStart)) thisStart else clampedDay(YearMonth.from(date).minusMonths(1), day)
        val next = clampedDay(YearMonth.from(start).plusMonths(1), day)
        return PayPeriod(start, next.minusDays(1))
    }

    private fun semimonthly(date: LocalDate, firstDay: Int, secondDay: Int): PayPeriod {
        val first = min(firstDay, secondDay).coerceIn(1, 28)
        val second = maxOf(firstDay, secondDay).coerceIn(first + 1, 31)
        val month = YearMonth.from(date)
        val firstDate = clampedDay(month, first)
        val secondDate = clampedDay(month, second)
        return when {
            !date.isBefore(secondDate) -> PayPeriod(secondDate, clampedDay(month.plusMonths(1), first).minusDays(1))
            !date.isBefore(firstDate) -> PayPeriod(firstDate, secondDate.minusDays(1))
            else -> PayPeriod(clampedDay(month.minusMonths(1), second), firstDate.minusDays(1))
        }
    }

    private fun clampedDay(month: YearMonth, day: Int): LocalDate = month.atDay(min(day, month.lengthOfMonth()))
}
