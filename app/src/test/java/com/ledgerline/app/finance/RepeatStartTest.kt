package com.ledgerline.app.finance

import com.ledgerline.app.finance.schedule.RepeatStart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Where a repeating transaction's schedule starts. */
class RepeatStartTest {
    @Test
    fun sameDayEachMonthStartsThisMonthOrNext() {
        val date = LocalDate.of(2026, 11, 15)
        assertEquals(LocalDate.of(2026, 11, 20), RepeatStart.first(date, 20, monthlyOnDay = true))
        assertEquals(LocalDate.of(2026, 11, 15), RepeatStart.first(date, 15, monthlyOnDay = true))
        assertEquals(LocalDate.of(2026, 12, 3), RepeatStart.first(date, 3, monthlyOnDay = true))
        // Day 31 in a 30-day month is its last day; in February, the 28th.
        assertEquals(LocalDate.of(2026, 11, 30), RepeatStart.first(date, 31, monthlyOnDay = true))
        assertEquals(LocalDate.of(2027, 2, 28), RepeatStart.first(LocalDate.of(2027, 2, 1), 31, monthlyOnDay = true))
        // Custom patterns start on the chosen date.
        assertEquals(date, RepeatStart.first(date, 3, monthlyOnDay = false))
    }

    @Test
    fun aScheduleEndsNeverAfterACountOrOnADate() {
        val start = LocalDate.of(2026, 11, 15)
        val never = com.ledgerline.app.finance.schedule.ScheduleEnd()
        assertEquals(null, never.endOn(start))
        assertEquals(null, never.maxOccurrences())
        val six = com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.AFTER_COUNT, count = 6)
        assertEquals(6, six.maxOccurrences())
        assertEquals(1, com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.AFTER_COUNT, count = 0).maxOccurrences())
        val onDate = com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.ON_DATE, on = LocalDate.of(2027, 3, 1))
        assertEquals(LocalDate.of(2027, 3, 1), onDate.endOn(start))
        // An end before the start is moved to the start, so the schedule still happens once.
        assertEquals(start, onDate.copy(on = LocalDate.of(2026, 1, 1)).endOn(start))
    }
}
