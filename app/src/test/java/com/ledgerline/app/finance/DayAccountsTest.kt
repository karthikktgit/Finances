package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.calendar.DayAccounts
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/** Each account's start, ins, outs, and end for one calendar day. */
class DayAccountsTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val chk = account("chk", "Checking", 100_000)
    private val sav = account("sav", "Savings", 50_000)

    @Test
    fun aPastDayShowsWhatWasRecorded() {
        val txs = listOf(
            tx("chk", LocalDate.of(2026, 11, 5), 40_000, TransactionType.INCOME),
            tx("chk", LocalDate.of(2026, 11, 5), -2_500, TransactionType.EXPENSE),
            // A move between accounts: out of checking, into savings.
            tx("chk", LocalDate.of(2026, 11, 5), -10_000, TransactionType.TRANSFER),
            tx("sav", LocalDate.of(2026, 11, 5), 10_000, TransactionType.TRANSFER),
            tx("chk", LocalDate.of(2026, 11, 3), -1_000, TransactionType.EXPENSE),
        )
        val rows = DayAccounts.of(LocalDate.of(2026, 11, 5), today, listOf(chk, sav), txs, emptyList()).associateBy { it.accountId }
        val c = rows.getValue("chk")
        assertEquals(99_000L, c.startMinor)
        assertEquals(40_000L, c.inMinor)
        assertEquals(12_500L, c.outMinor)
        assertEquals(126_500L, c.endMinor)
        assertEquals(60_000L, rows.getValue("sav").endMinor)
    }

    @Test
    fun aFutureDayAddsWhatIsScheduledAndPlanned() {
        val scheduled = listOf(
            bill("rent", LocalDate.of(2026, 11, 12), 30_000, CashFlowDirection.OUTFLOW),
            bill("pay", LocalDate.of(2026, 11, 15), 80_000, CashFlowDirection.INFLOW),
            bill("phone", LocalDate.of(2026, 11, 15), 4_000, CashFlowDirection.OUTFLOW),
            bill("gym", LocalDate.of(2026, 11, 15), 3_000, CashFlowDirection.OUTFLOW, OccurrenceState.SKIPPED),
        )
        val planned = tx("chk", LocalDate.of(2026, 11, 13), -5_000, TransactionType.EXPENSE, paid = false)
        val rows = DayAccounts.of(LocalDate.of(2026, 11, 15), today, listOf(chk, sav), listOf(planned), scheduled).associateBy { it.accountId }
        val c = rows.getValue("chk")
        // Before the 15th: rent on the 12th and the planned spend on the 13th.
        assertEquals(100_000L - 30_000 - 5_000, c.startMinor)
        assertEquals(80_000L, c.inMinor)
        // The skipped gym is not counted.
        assertEquals(4_000L, c.outMinor)
        assertEquals(65_000L + 80_000 - 4_000, c.endMinor)
        assertFalse(rows.getValue("sav").moved)
        // Today: income due today and not yet received counts as coming in.
        val todayRows = DayAccounts.of(today, today, listOf(chk), emptyList(), listOf(bill("pay", today, 80_000, CashFlowDirection.INFLOW)))
        assertEquals(80_000L, todayRows.single().inMinor)
        assertEquals(180_000L, todayRows.single().endMinor)
    }

    private fun account(id: String, name: String, opening: Long) = Account(id, name, "", AccountType.CHECKING, null, opening, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0)

    private fun bill(id: String, on: LocalDate, amount: Long, direction: CashFlowDirection, state: OccurrenceState = OccurrenceState.SCHEDULED) = ProjectedOccurrence(
        recurringId = id, name = id, date = on, amountMinor = amount, direction = direction, kind = if (direction == CashFlowDirection.INFLOW) ScheduledKind.INCOME else ScheduledKind.BILL,
        priority = PaymentPriority.MANDATORY, accountId = "chk", incomeConfidence = IncomeConfidence.NONE, state = state, overdue = false,
    )

    private fun tx(account: String, on: LocalDate, effect: Long, type: TransactionType, paid: Boolean = true) = LedgerTransaction(
        id = "$account-$on-$effect", accountId = account, counterAccountId = null, linkId = null, amountMinor = kotlin.math.abs(effect),
        balanceEffectMinor = effect, currency = "USD", type = type, merchantRaw = "x", merchantNormalized = "x", merchantId = null, description = "",
        categoryId = null, occurredOn = on, postedOn = null, detectionSource = DetectionSource.MANUAL, confidencePercent = 100, recurringPaymentId = null,
        status = TransactionStatus.CONFIRMED, userConfirmed = true, note = "", fingerprint = "", createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
        deletedAtEpochMillis = null, paid = paid, plannedOn = if (!paid) on else null,
    )
}
