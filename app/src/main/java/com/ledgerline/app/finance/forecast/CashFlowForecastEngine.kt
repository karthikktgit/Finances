package com.ledgerline.app.finance.forecast

import com.ledgerline.app.domain.model.ForecastDay
import java.time.LocalDate

/**
 * Projects a liquid balance across a horizon.
 *
 * Each day applies scheduled inflows and outflows dated that day, then subtracts
 * [dailyDiscretionaryMinor]. Discretionary spending is an estimate. Scheduled
 * bills are not also included in that daily figure. The result is a projection,
 * not a guarantee.
 */
object CashFlowForecastEngine {
    data class DatedAmount(
        val date: LocalDate,
        val key: String,
        val label: String,
        val deltaMinor: Long,
    )

    fun dailyDiscretionaryMinor(
        reservedRemainingMinor: Long,
        horizonDays: Int,
        recentDiscretionaryMinor: Long,
        recentDays: Int,
    ): Long {
        if (reservedRemainingMinor > 0 && horizonDays > 0) {
            return reservedRemainingMinor / horizonDays
        }
        if (recentDiscretionaryMinor > 0 && recentDays > 0) {
            return recentDiscretionaryMinor / recentDays
        }
        return 0L
    }

    fun project(
        start: LocalDate,
        days: Int,
        openingMinor: Long,
        events: List<DatedAmount>,
        dailyDiscretionaryMinor: Long,
    ): List<ForecastDay> {
        if (days <= 0) return emptyList()
        val discretionary = dailyDiscretionaryMinor.coerceAtLeast(0)
        var balance = openingMinor
        return (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong())
            val scheduled = events.filter { it.date == date }.sumOf { it.deltaMinor }
            balance += scheduled - discretionary
            ForecastDay(
                date = date,
                scheduledMinor = scheduled,
                discretionaryMinor = -discretionary,
                balanceAfterMinor = balance,
            )
        }
    }

    const val DISCLAIMER =
        "Projected from the current liquid balance, scheduled items, and an estimated daily discretionary amount. " +
            "Based on current information. This is not a guarantee."
}
