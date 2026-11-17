package com.ledgerline.app.finance.schedule

import com.ledgerline.app.domain.model.RecurrenceFrequency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A schedule's ending in plain words, such as "6 payments: Nov 22, 2026 to Apr 22, 2027". */
object EndSummary {
    /** The [index]th date (0 is [start]) of a schedule repeating at [frequency]. */
    fun nth(start: LocalDate, frequency: RecurrenceFrequency, index: Int): LocalDate = when (frequency) {
        RecurrenceFrequency.DAILY -> start.plusDays(index.toLong())
        RecurrenceFrequency.WEEKLY -> start.plusWeeks(index.toLong())
        RecurrenceFrequency.BIWEEKLY -> start.plusWeeks(2L * index)
        RecurrenceFrequency.QUARTERLY -> start.plusMonths(3L * index)
        RecurrenceFrequency.YEARLY -> start.plusYears(index.toLong())
        else -> start.plusMonths(index.toLong())
    }

    /** How many dates fall from [start] to [last], both included. */
    fun countUntil(start: LocalDate, frequency: RecurrenceFrequency, last: LocalDate): Int {
        if (last.isBefore(start)) return 1
        var n = 0
        while (n < 600 && !nth(start, frequency, n).isAfter(last)) n++
        return n.coerceAtLeast(1)
    }

    fun describe(end: ScheduleEnd, start: LocalDate, frequency: RecurrenceFrequency, unit: String, locale: Locale = Locale.getDefault()): String {
        val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
        val one = unit.removeSuffix("s")
        return when (end.mode) {
            EndMode.NEVER -> "Keeps going every time until you change it."
            EndMode.AFTER_COUNT -> {
                val count = end.count.coerceAtLeast(1)
                val last = nth(start, frequency, count - 1)
                if (count == 1) "Just once: ${start.format(fmt)}."
                else "$count $unit: ${start.format(fmt)} to ${last.format(fmt)}, then it stops."
            }
            EndMode.ON_DATE -> {
                val until = maxOf(end.on ?: start, start)
                val count = countUntil(start, frequency, until)
                val last = nth(start, frequency, count - 1)
                "Stops after ${until.format(fmt)}: $count ${if (count == 1) one else unit}, the last on ${last.format(fmt)}."
            }
        }
    }
}
