package com.ledgerline.app.finance.schedule

import com.ledgerline.app.domain.model.BalanceCheckpoint
import java.time.LocalDate

data class PreviewEvent(
    val date: LocalDate,
    val label: String,
    val deltaMinor: Long,
)

object ScheduledBalancePreview {
    const val DISCLAIMER =
        "Estimated liquid balance if only these scheduled items post. " +
            "Everyday spending after today is not included unless it is already on the calendar."

    fun project(startingLiquidMinor: Long, events: List<PreviewEvent>): List<BalanceCheckpoint> {
        var running = startingLiquidMinor
        return events
            .sortedWith(compareBy<PreviewEvent> { it.date }.thenBy { it.label })
            .map { event ->
                running += event.deltaMinor
                BalanceCheckpoint(
                    date = event.date,
                    label = event.label,
                    deltaMinor = event.deltaMinor,
                    balanceAfterMinor = running,
                )
            }
    }
}
