package com.ledgerline.app.data.repository

import com.ledgerline.app.data.Ids
import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.entity.ExpenseNoteEntity
import com.ledgerline.app.domain.model.ExpenseNote
import com.ledgerline.app.domain.model.ExpenseNoteDraft
import com.ledgerline.app.domain.model.ExpenseNoteOrder
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.repository.ExpenseNoteRepository
import com.ledgerline.app.domain.time.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseNoteRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
) : ExpenseNoteRepository {
    override fun observe(): Flow<List<ExpenseNote>> =
        database.expenseNotes().observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(draft: ExpenseNoteDraft): SaveResult {
        ExpenseNoteOrder.validate(draft.name, draft.amountMinor)?.let { return SaveResult.Invalid(it) }
        val now = clock.now().toEpochMilli()
        val existing = draft.existingId?.let { database.expenseNotes().get(it) }
        database.expenseNotes().upsert(
            ExpenseNoteEntity(
                id = existing?.id ?: Ids.new(),
                name = draft.name.trim(),
                amountMinor = draft.amountMinor?.takeIf { it > 0 },
                dueOn = draft.dueOn?.toString(),
                note = draft.note.trim(),
                completed = draft.completed,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
        return SaveResult.Saved
    }

    override suspend fun setCompleted(id: String, completed: Boolean) {
        val existing = database.expenseNotes().get(id) ?: return
        database.expenseNotes().upsert(
            existing.copy(completed = completed, updatedAtEpochMillis = clock.now().toEpochMilli()),
        )
    }

    override suspend fun delete(id: String) {
        database.expenseNotes().delete(id)
    }
}

private fun ExpenseNoteEntity.toDomain() = ExpenseNote(
    id = id,
    name = name,
    amountMinor = amountMinor,
    dueOn = dueOn?.let(LocalDate::parse),
    note = note,
    completed = completed,
)
