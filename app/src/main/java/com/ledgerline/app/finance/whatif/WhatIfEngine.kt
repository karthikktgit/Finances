package com.ledgerline.app.finance.whatif

import com.ledgerline.app.domain.model.ForecastDay
import com.ledgerline.app.finance.forecast.CashFlowForecastEngine
import java.time.LocalDate

data class WhatIfRequest(
    val oneTimeExpenseMinor: Long = 0,
    val oneTimeDate: LocalDate? = null,
    val cancelKey: String? = null,
    val monthlySaveMinor: Long = 0,
)

data class WhatIfResult(
    val baselineEndMinor: Long,
    val scenarioEndMinor: Long,
    val crossesBuffer: Boolean,
    val summary: String,
)

object WhatIfEngine {
    fun evaluate(
        start: LocalDate,
        days: Int,
        openingMinor: Long,
        events: List<CashFlowForecastEngine.DatedAmount>,
        dailyDiscretionaryMinor: Long,
        bufferMinor: Long,
        request: WhatIfRequest,
    ): WhatIfResult {
        val baseline = CashFlowForecastEngine.project(start, days, openingMinor, events, dailyDiscretionaryMinor)
        val adjusted = events.filter { request.cancelKey == null || it.key != request.cancelKey }.toMutableList()
        if (request.oneTimeExpenseMinor > 0) {
            adjusted += CashFlowForecastEngine.DatedAmount(
                date = request.oneTimeDate ?: start,
                key = "what-if",
                label = "Scenario expense",
                deltaMinor = -request.oneTimeExpenseMinor,
            )
        }
        if (request.monthlySaveMinor > 0) {
            var cursor = start
            val end = start.plusDays(days.toLong())
            while (!cursor.isAfter(end)) {
                adjusted += CashFlowForecastEngine.DatedAmount(cursor, "save", "Scenario saving", request.monthlySaveMinor)
                cursor = cursor.plusMonths(1)
            }
        }
        val scenario = CashFlowForecastEngine.project(start, days, openingMinor, adjusted, dailyDiscretionaryMinor)
        val baselineEnd = baseline.lastOrNull()?.balanceAfterMinor ?: openingMinor
        val scenarioEnd = scenario.lastOrNull()?.balanceAfterMinor ?: openingMinor
        val lowest = scenario.minOfOrNull { it.balanceAfterMinor } ?: scenarioEnd
        val crosses = lowest < bufferMinor
        val summary = if (crosses) {
            "This scenario would push the projected liquid balance below the minimum buffer. The projection is an estimate."
        } else {
            "The projected liquid balance stays at or above the minimum buffer in this scenario. The projection is an estimate."
        }
        return WhatIfResult(baselineEnd, scenarioEnd, crosses, summary)
    }

    fun endBalance(days: List<ForecastDay>, opening: Long): Long = days.lastOrNull()?.balanceAfterMinor ?: opening
}
