package com.ledgerline.app.finance.forecast

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class MonthFlow(
    val month: YearMonth,
    val openingMinor: Long,
    val incomingMinor: Long,
    val outgoingMinor: Long,
    val endingMinor: Long,
    val negative: Boolean,
)

data class HorizonForecast(
    val monthCount: Int,
    val openingMinor: Long,
    val incomingMinor: Long,
    val outgoingMinor: Long,
    val endingMinor: Long,
    val months: List<MonthFlow>,
    val shortfalls: List<ScheduledOccurrence>,
    val unassignedCount: Int,
    val note: String,
)

object MonthlyForecast {
    const val NOTE =
        "Projected from the actual liquid balance and unpaid scheduled items. " +
            "Transfers are not counted as income or spending. " +
            "Completed transactions are already in the starting balance. This is not a guarantee."

    fun project(
        today: LocalDate,
        monthCount: Int,
        openingMinor: Long,
        events: List<ScheduledOccurrence>,
        dailyDiscretionaryMinor: Long,
    ): HorizonForecast {
        val span = monthCount.coerceIn(1, 6)
        val last = YearMonth.from(today).plusMonths((span - 1).toLong())
        val windowEnd = last.atEndOfMonth()
        val relevant = events.map { event ->
            if (event.date.isBefore(today)) event.copy(date = today, overdue = true) else event
        }.filter { !it.date.isBefore(today) && !it.date.isAfter(windowEnd) }
        val unassigned = relevant.count { it.accountName == "Account not assigned" || it.accountId.isBlank() }
        var balance = openingMinor
        val months = mutableListOf<MonthFlow>()
        var month = YearMonth.from(today)
        while (!month.isAfter(last)) {
            val start = if (month == YearMonth.from(today)) today else month.atDay(1)
            val end = month.atEndOfMonth()
            val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
            val inMonth = relevant.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            val deltas = inMonth.filter { it.liquidDeltaMinor != 0L }.map {
                CashFlowForecastEngine.DatedAmount(it.date, it.recurringId, it.name, it.liquidDeltaMinor)
            }
            val walked = CashFlowForecastEngine.project(start, days, balance, deltas, dailyDiscretionaryMinor)
            val ending = walked.lastOrNull()?.balanceAfterMinor ?: balance
            val dipped = walked.any { it.balanceAfterMinor < 0 }
            months += MonthFlow(
                month = month,
                openingMinor = balance,
                incomingMinor = cash(inMonth, CashFlowDirection.INFLOW),
                outgoingMinor = cash(inMonth, CashFlowDirection.OUTFLOW),
                endingMinor = ending,
                negative = dipped || ending < 0,
            )
            balance = ending
            month = month.plusMonths(1)
        }
        val negativeMonths = months.filter { it.negative }.map { it.month }.toSet()
        val shortfalls = relevant.filter { event ->
            YearMonth.from(event.date) in negativeMonths &&
                event.direction == CashFlowDirection.OUTFLOW &&
                event.kind != ScheduledKind.TRANSFER &&
                event.liquidDeltaMinor < 0
        }
        return HorizonForecast(
            monthCount = span,
            openingMinor = openingMinor,
            incomingMinor = months.sumOf { it.incomingMinor },
            outgoingMinor = months.sumOf { it.outgoingMinor },
            endingMinor = months.lastOrNull()?.endingMinor ?: openingMinor,
            months = months,
            shortfalls = shortfalls,
            unassignedCount = unassigned,
            note = NOTE,
        )
    }

    private fun cash(events: List<ScheduledOccurrence>, direction: CashFlowDirection): Long =
        events.filter { it.direction == direction && it.kind != ScheduledKind.TRANSFER }.sumOf { it.amountMinor }
}
