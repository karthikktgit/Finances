package com.ledgerline.app.domain.repository

import com.ledgerline.app.domain.model.ExpenseNote
import com.ledgerline.app.domain.model.ExpenseNoteDraft
import com.ledgerline.app.domain.model.SaveResult
import kotlinx.coroutines.flow.Flow

interface ExpenseNoteRepository {
    fun observe(): Flow<List<ExpenseNote>>

    suspend fun save(draft: ExpenseNoteDraft): SaveResult

    suspend fun setCompleted(id: String, completed: Boolean)

    suspend fun delete(id: String)
}
