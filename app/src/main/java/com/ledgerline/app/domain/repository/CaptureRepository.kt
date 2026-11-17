package com.ledgerline.app.domain.repository

import com.ledgerline.app.domain.model.CaptureStatus
import com.ledgerline.app.domain.model.CapturedSpend
import kotlinx.coroutines.flow.Flow

interface CaptureRepository {
    fun observePending(): Flow<List<CapturedSpend>>

    fun observeRecent(): Flow<List<CapturedSpend>>

    suspend fun get(id: String): CapturedSpend?

    /** Stores a new capture. Returns false when the same payment was already captured. */
    suspend fun record(capture: CapturedSpend, fingerprint: String): Boolean

    suspend fun setStatus(id: String, status: CaptureStatus, transactionId: String? = null)
}
