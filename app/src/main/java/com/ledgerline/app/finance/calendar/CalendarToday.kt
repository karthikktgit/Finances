package com.ledgerline.app.finance.calendar

import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.period.PayPeriod
import java.time.LocalDate
import java.time.YearMonth

data class CalendarCursor(
    val month: YearMonth,
    val selected: LocalDate,
)

object CalendarToday {
    fun go(today: LocalDate): CalendarCursor = CalendarCursor(YearMonth.from(today), today)

    fun highlightedCycle(highlight: Boolean, selected: LocalDate, settings: UserSettings): PayPeriod? =
        if (highlight) CycleSchedule.containing(selected, settings) else null
}
