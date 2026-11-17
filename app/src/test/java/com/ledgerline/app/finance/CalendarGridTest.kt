package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.CycleType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.calendar.CalendarGrid
import com.ledgerline.app.finance.period.CycleHighlight
import com.ledgerline.app.finance.period.CycleSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class CalendarGridTest {
    private val monthly = UserSettings("USD", 0, 30, true, CycleType.MONTHLY, LocalDate.of(2026, 1, 20), 20, 5)

    @Test
    fun overflowCell_keepsThePreviousMonthInsteadOfTheDisplayedDayNumber() {
        val january = CalendarGrid.dates(YearMonth.of(2026, 1))
        assertEquals(LocalDate.of(2025, 12, 28), january.first())
        assertNotEquals(LocalDate.of(2026, 1, 28), january.first())
        val november = CalendarGrid.dates(YearMonth.of(2025, 11))
        val octoberOverflow = november.first { it.monthValue == 10 }
        assertEquals(LocalDate.of(2025, 10, 26), octoberOverflow)
        assertNotEquals(YearMonth.of(2025, 11).atDay(octoberOverflow.dayOfMonth), octoberOverflow)
    }

    @Test
    fun everyMonthIsSixWeeksSoTheCalendarNeverChangesHeight() {
        (1..12).forEach { m -> assertEquals(42, CalendarGrid.dates(YearMonth.of(2026, m)).size) }
        // February 2026 starts on a Sunday and has exactly four weeks; it is still padded to six.
        val feb = CalendarGrid.dates(YearMonth.of(2026, 2))
        assertEquals(LocalDate.of(2026, 2, 1), feb.first())
        assertEquals(LocalDate.of(2026, 3, 14), feb.last())
    }

    @Test
    fun cycleHighlight_continuesAcrossOctoberAndNovemberWithoutGaps() {
        val selected = LocalDate.of(2026, 11, 10)
        val cycle = CycleSchedule.containing(selected, monthly)
        assertEquals(LocalDate.of(2026, 10, 20), cycle.start)
        assertEquals(LocalDate.of(2026, 11, 19), cycle.end)
        val covered = (CalendarGrid.dates(YearMonth.of(2026, 10)) + CalendarGrid.dates(YearMonth.of(2026, 11)))
            .filter { cycle.contains(it) }
            .toSet()
        var cursor = cycle.start
        while (!cursor.isAfter(cycle.end)) {
            assertTrue(covered.contains(cursor))
            cursor = cursor.plusDays(1)
        }
        assertFalse(covered.contains(LocalDate.of(2026, 10, 19)))
        assertFalse(covered.contains(LocalDate.of(2026, 11, 20)))
        val next = CycleSchedule.containing(LocalDate.of(2026, 11, 20), monthly)
        assertEquals(LocalDate.of(2026, 11, 20), next.start)
        assertEquals(LocalDate.of(2026, 12, 19), next.end)
        val day = CycleHighlight.of(selected, cycle, selected, false)
        assertFalse(day.inCycle)
        assertTrue(day.selected)
        val restored = CycleHighlight.of(selected, cycle, selected, true)
        assertTrue(restored.inCycle && restored.selected)
        assertTrue(CalendarGrid.rangeEdge(cycle.start, cycle)!!.roundStart)
        assertTrue(CalendarGrid.rangeEdge(cycle.end, cycle)!!.roundEnd)
    }

    @Test
    fun bundledInter_isPresentAsAFontFile() {
        val candidates = listOf(
            File("src/main/res/font/inter_regular.ttf"),
            File("app/src/main/res/font/inter_regular.ttf"),
        )
        val font = candidates.firstOrNull { it.exists() }
        assertTrue(font != null && font.length() > 10_000)
        val header = font!!.inputStream().use { it.readNBytes(4) }
        assertEquals(0, header[0].toInt() and 0xFF)
        assertEquals(1, header[1].toInt() and 0xFF)
        assertEquals(0, header[2].toInt() and 0xFF)
        assertEquals(0, header[3].toInt() and 0xFF)
    }
}
