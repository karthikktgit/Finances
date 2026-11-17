package com.ledgerline.app.domain.model

import java.time.LocalDate

data class ExpenseNote(
    val id: String,
    val name: String,
    val amountMinor: Long?,
    val dueOn: LocalDate?,
    val note: String,
    val completed: Boolean,
)

data class ExpenseNoteDraft(
    val existingId: String?,
    val name: String,
    val amountMinor: Long?,
    val dueOn: LocalDate?,
    val note: String,
    val completed: Boolean,
)

object ExpenseNoteOrder {
    fun validate(name: String, amountMinor: Long?): String? {
        if (name.trim().isEmpty()) return "Name the expense note."
        if (amountMinor != null && amountMinor < 0) return "The expected amount cannot be negative."
        return null
    }

    fun isOverdue(note: ExpenseNote, today: LocalDate): Boolean =
        !note.completed && note.dueOn != null && note.dueOn.isBefore(today)

    fun open(notes: List<ExpenseNote>, today: LocalDate): List<ExpenseNote> =
        notes.filter { !it.completed }.sortedWith(compareBy<ExpenseNote> { it.dueOn == null }.thenBy { it.dueOn ?: LocalDate.MAX }.thenBy { it.name })

    fun completed(notes: List<ExpenseNote>): List<ExpenseNote> =
        notes.filter { it.completed }.sortedBy { it.name }
}
