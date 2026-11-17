package com.ledgerline.app.domain.repository

import com.ledgerline.app.domain.model.CycleAmount
import com.ledgerline.app.domain.model.CycleReview
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** What each cycle starts with: its own amounts for spending items and goals, and the start-of-cycle checklist. */
interface CyclePlanRepository {
    fun observeAmounts(): Flow<List<CycleAmount>>

    fun observeReviews(): Flow<List<CycleReview>>

    /** Sets [itemKey]'s own amount for the cycle starting [cycleStart]; null goes back to the usual amount. */
    suspend fun setAmount(cycleStart: LocalDate, itemKey: String, amountMinor: Long?)

    suspend fun review(cycleStart: LocalDate): CycleReview

    suspend fun saveReview(review: CycleReview)

    /** Cycles (by first day) that start with fresh balances instead of carrying on from the cycle before. */
    fun observeFreshCycles(): Flow<Set<LocalDate>>

    /**
     * Turns fresh balances on or off for the cycle starting [cycleStart]. Off removes its start corrections, so
     * balances carry on from the cycle before again.
     */
    suspend fun setFresh(cycleStart: LocalDate, fresh: Boolean)

    /** Sets [accountId]'s starting balance for the cycle ([signedMinor]: owed below zero). */
    suspend fun setStartBalance(cycleStart: LocalDate, accountId: String, signedMinor: Long)
}
