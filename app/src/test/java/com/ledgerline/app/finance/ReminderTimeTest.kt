package com.ledgerline.app.finance

import com.ledgerline.app.worker.ReminderTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** The daily payments reminder comes at the chosen time: later today if still ahead, otherwise tomorrow. */
class ReminderTimeTest {
    @Test
    fun theNextReminderIsTodayIfTheTimeIsStillAheadOtherwiseTomorrow() {
        val morning = LocalDateTime.of(2026, 11, 3, 7, 30)
        assertEquals(LocalDateTime.of(2026, 11, 3, 9, 0), ReminderTime.next(morning, 540))
        val late = LocalDateTime.of(2026, 11, 3, 9, 0)
        assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), ReminderTime.next(late, 540))
        assertEquals(LocalDateTime.of(2026, 11, 4, 0, 0), ReminderTime.next(LocalDateTime.of(2026, 11, 3, 23, 59), 0))
    }
}
