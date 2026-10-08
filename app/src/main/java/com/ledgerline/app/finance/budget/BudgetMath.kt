package com.ledgerline.app.finance.budget

import com.ledgerline.app.domain.model.BudgetPace
import java.math.BigDecimal
import java.math.RoundingMode

data class BudgetMathResult(
    val remainingMinor: Long,
    val projectedMinor: Long,
    val pace: BudgetPace,
)

object BudgetMath {
    fun evaluate(spentMinor: Long, budgetMinor: Long, elapsedDays: Int, periodDays: Int): BudgetMathResult {
        val remaining = budgetMinor - spentMinor
        val projected = if (elapsedDays <= 0 || periodDays <= 0) {
            spentMinor
        } else {
            BigDecimal.valueOf(spentMinor)
                .multiply(BigDecimal.valueOf(periodDays.toLong()))
                .divide(BigDecimal.valueOf(elapsedDays.toLong()), 0, RoundingMode.HALF_UP)
                .longValueExact()
        }
        val pace = when {
            spentMinor > budgetMinor -> BudgetPace.OVER
            projected > budgetMinor -> BudgetPace.LIKELY_OVER
            else -> BudgetPace.ON_TRACK
        }
        return BudgetMathResult(remaining, projected, pace)
    }
}
