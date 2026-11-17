package com.ledgerline.app.domain.model

import java.time.LocalDate

/**
 * Something you are saving toward: a room deposit, stocks, a trip. Progress comes either from an account kept only
 * for it (its balance is the progress) or from amounts you add yourself.
 */
data class SavingsGoal(
    val id: String,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    /** When you want to have it by. Null means no deadline. */
    val targetDate: LocalDate?,
    /** Where the money is kept, if you said. */
    val accountId: String?,
    /** True when [accountId] holds only this goal's money, so its balance is the progress. */
    val useAccountBalance: Boolean,
    /** How much you plan to put aside each month. Null means work it out from the target date. */
    val monthlyMinor: Long?,
    val createdOn: LocalDate,
    val archived: Boolean = false,
    /** What became of the money: null while you are still holding it for the goal. */
    val outcome: GoalOutcome? = null,
    val outcomeOn: LocalDate? = null,
) {
    /** Finished: spent on the goal or used for something else. It no longer holds money or asks for any. */
    val closed: Boolean get() = outcome != null
}

/** What happened to a goal's money. */
enum class GoalOutcome {
    /** Spent on what it was saved for. */
    SPENT_ON_GOAL,

    /** Used for something else instead. */
    USED_ELSEWHERE,
}

/** Money you put toward a goal (positive) or took back out (negative), and what for. */
data class GoalContribution(
    val id: String,
    val goalId: String,
    val amountMinor: Long,
    val onDate: LocalDate,
    val note: String,
    val kind: GoalEntryKind = if (amountMinor >= 0) GoalEntryKind.SAVED else GoalEntryKind.TAKEN_OUT,
    /** The spend recorded with it, when it was paid from the goal's account. */
    val transactionId: String? = null,
)

/** What a goal entry was. */
enum class GoalEntryKind {
    /** Money put aside for the goal. */
    SAVED,

    /** Money taken back out, no reason given. */
    TAKEN_OUT,

    /** Spent on what the goal was for: the goal stays funded, the money is no longer held. */
    SPENT_ON_GOAL,

    /** Spent on something else: the goal is that much less funded. */
    USED_ELSEWHERE,
}

data class GoalDraft(
    val existingId: String?,
    val name: String,
    val targetMinor: Long,
    val targetDate: LocalDate?,
    val accountId: String?,
    val useAccountBalance: Boolean,
    val monthlyMinor: Long?,
)
