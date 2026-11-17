package com.ledgerline.app.domain.model

import java.time.LocalDate

/**
 * One cycle's own amount for a spending item ("budget:<id>") or savings goal ("goal:<id>"), used instead of its usual
 * amount for the cycle that starts on [cycleStart] only. Zero means it is off for that cycle.
 */
data class CycleAmount(
    val cycleStart: LocalDate,
    val itemKey: String,
    val amountMinor: Long,
)

/** How far the start-of-cycle checklist has got for the cycle starting on [cycleStart]. */
data class CycleReview(
    val cycleStart: LocalDate,
    /** Accounts whose statement you have downloaded and checked. */
    val reviewedAccountIds: Set<String> = emptySet(),
    /** You confirmed every account's balance matches the bank. */
    val balancesChecked: Boolean = false,
    /** Set when you finished setting the cycle up. */
    val completedAtEpochMillis: Long? = null,
) {
    val completed: Boolean get() = completedAtEpochMillis != null
}
