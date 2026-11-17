package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.ExpenseNote
import com.ledgerline.app.domain.model.ExpenseNoteOrder
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.DayLedger
import com.ledgerline.app.finance.forecast.MonthlyForecast
import com.ledgerline.app.finance.safetospend.SafeToSpendEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpenseNoteTest {
    private val today = LocalDate.of(2026, 10, 12)

    @Test
    fun notes_canOmitAmountAndDateAndSortOpenItemsFirst() {
        assertNull(ExpenseNoteOrder.validate("Groceries", null))
        assertEquals("Name the expense note.", ExpenseNoteOrder.validate("  ", 100))
        val notes = listOf(
            note("later", LocalDate.of(2026, 12, 1), completed = false),
            note("overdue", LocalDate.of(2026, 10, 1), completed = false),
            note("none", null, completed = false),
            note("done", LocalDate.of(2026, 10, 2), completed = true),
            note("year", LocalDate.of(2027, 1, 3), completed = false),
        )
        assertEquals(listOf("overdue", "later", "year", "none"), ExpenseNoteOrder.open(notes, today).map { it.id })
        assertEquals(listOf("done"), ExpenseNoteOrder.completed(notes).map { it.id })
        assertTrue(ExpenseNoteOrder.isOverdue(notes.first { it.id == "overdue" }, today))
        assertTrue(!ExpenseNoteOrder.isOverdue(notes.first { it.id == "later" }, today))
    }

    @Test
    fun notes_doNotChangeBalancesSpendingOrForecasts() {
        val account = Account(
            "checking", "Checking", "", AccountType.CHECKING, null, 100_000, null, "USD", true, true, true,
            SyncStatus.MANUAL, null, null, 0,
        )
        val expense = LedgerTransaction(
            id = "rent", accountId = "checking", counterAccountId = null, linkId = null, amountMinor = 10_000,
            balanceEffectMinor = -10_000, currency = "USD", type = TransactionType.EXPENSE, merchantRaw = "Rent",
            merchantNormalized = "rent", merchantId = null, description = "Rent", categoryId = null,
            occurredOn = today, postedOn = today, detectionSource = DetectionSource.MANUAL, confidencePercent = 100,
            recurringPaymentId = null, status = TransactionStatus.CONFIRMED, userConfirmed = true, note = "",
            fingerprint = "rent", createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
        )
        val before = DayLedger.figures(today, listOf(account), listOf(expense))
        val after = DayLedger.figures(today, listOf(account), listOf(expense))
        assertEquals(before, after)
        assertEquals(10_000L, before.spentMinor)
        assertEquals(0L, before.unplannedMinor)
        val forecast = MonthlyForecast.project(today, 1, 90_000, emptyList(), 0)
        assertEquals(90_000L, forecast.endingMinor)
        val safe = SafeToSpendEngine.calculate(
            SafeToSpendEngine.Input(90_000, 0, 0, 0, 0, 0),
        )
        assertEquals(90_000L, safe.amountMinor)
    }

    private fun note(id: String, due: LocalDate?, completed: Boolean) = ExpenseNote(
        id = id,
        name = id,
        amountMinor = null,
        dueOn = due,
        note = "",
        completed = completed,
    )
}
