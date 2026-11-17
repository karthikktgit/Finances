package com.ledgerline.app.finance.balance

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.finance.schedule.ProjectedOccurrence

/**
 * Projected balance is the posted balance plus unpaid scheduled activity on that
 * account. Posted transactions are already inside [actualMinor]. Cancelled and
 * skipped dates are omitted by the caller. Transfers change only the scheduled
 * account stored on the series.
 */
object AccountProjection {
    fun projectedMinor(
        actualMinor: Long,
        accountId: String,
        scheduled: List<ProjectedOccurrence>,
    ): Long {
        val delta = scheduled
            .asSequence()
            .filter { it.accountId == accountId && it.state == OccurrenceState.SCHEDULED }
            .sumOf { event ->
                if (event.direction == CashFlowDirection.INFLOW) event.amountMinor else -event.amountMinor
            }
        return actualMinor + delta
    }
}
