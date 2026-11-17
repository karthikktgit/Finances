package com.ledgerline.app.domain.repository

import com.ledgerline.app.domain.model.GoalContribution
import com.ledgerline.app.domain.model.GoalDraft
import com.ledgerline.app.domain.model.SavingsGoal
import com.ledgerline.app.domain.model.SaveResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface GoalRepository {
    fun observe(): Flow<List<SavingsGoal>>

    fun observeContributions(): Flow<List<GoalContribution>>

    suspend fun get(id: String): SavingsGoal?

    suspend fun save(draft: GoalDraft): SaveResult

    suspend fun delete(id: String)

    /** Records money put toward a goal (positive) or taken back out (negative). */
    suspend fun addContribution(goalId: String, amountMinor: Long, onDate: LocalDate, note: String): SaveResult

    suspend fun deleteContribution(id: String)

    /**
     * Money spent from what was saved: on the goal itself ([onGoal]) or on something else. With [recordTransaction],
     * the purchase is also added as a spend from the goal's account.
     */
    suspend fun recordSpend(goalId: String, amountMinor: Long, onDate: LocalDate, note: String, onGoal: Boolean, recordTransaction: Boolean): SaveResult

    /** Records what became of the goal's money ([outcome] null reopens it, still holding the money). */
    suspend fun setOutcome(id: String, outcome: com.ledgerline.app.domain.model.GoalOutcome?, on: LocalDate)
}
