package com.ledgerline.app.finance.schedule

import java.time.LocalDate

/** How a repeating schedule stops: never, after a number of times, or on a date. */
enum class EndMode { NEVER, AFTER_COUNT, ON_DATE }

data class ScheduleEnd(val mode: EndMode = EndMode.NEVER, val count: Int = 12, val on: LocalDate? = null) {
    /** The end date to store, if any. A date before the start is moved to the start. */
    fun endOn(start: LocalDate): LocalDate? = if (mode == EndMode.ON_DATE) (on ?: start).let { maxOf(it, start) } else null

    /** How many times it happens in all, if limited. At least once. */
    fun maxOccurrences(): Int? = if (mode == EndMode.AFTER_COUNT) count.coerceAtLeast(1) else null
}
