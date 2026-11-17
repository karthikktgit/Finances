package com.ledgerline.app.finance.calendar

import java.time.LocalDate
import kotlin.math.sqrt

/** How hot a day is: red for money out, green for money in. [level] runs from 0 (none) to 1 (the busiest day shown). */
data class Heat(val out: Boolean, val level: Float)

object HeatMap {
    /**
     * Each day's heat, scaled to the busiest days of the dates shown so the colour always uses its full range. A day
     * with both money in and money out shows whichever is larger. Square-rooted, so a few big days do not wash out
     * all the small ones.
     */
    fun of(days: Map<LocalDate, BillDay>, shown: Collection<LocalDate>): Map<LocalDate, Heat> {
        val rows = shown.mapNotNull { days[it] }
        val maxOut = rows.maxOfOrNull { it.outMinor } ?: 0L
        val maxIn = rows.maxOfOrNull { it.receivedMinor } ?: 0L
        return rows.mapNotNull { day ->
            val out = day.outMinor
            val into = day.receivedMinor
            when {
                out <= 0 && into <= 0 -> null
                out >= into -> day.date to Heat(true, scale(out, maxOut))
                else -> day.date to Heat(false, scale(into, maxIn))
            }
        }.toMap()
    }

    private fun scale(value: Long, max: Long): Float = if (max <= 0) 0f else sqrt(value.toFloat() / max).coerceIn(0.12f, 1f)
}
