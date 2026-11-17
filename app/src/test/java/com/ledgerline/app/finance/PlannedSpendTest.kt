package com.ledgerline.app.finance

import com.ledgerline.app.data.local.database.Migrations
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.envelopes.EnvelopeBill
import com.ledgerline.app.finance.forecast.SpendingTrack
import com.ledgerline.app.finance.period.PayPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Spends planned before they are paid, and the planned-versus-paid line. */
class PlannedSpendTest {
    private val cycle = PayPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10))
    private val today = LocalDate.of(2026, 11, 5)
    private val chk = Account("chk", "Checking", "", AccountType.CHECKING, null, 100_000, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0)

    @Test
    fun aPlannedSpendCountsNowhereUntilItIsPaid() {
        val planned = tx("Concert", today.plusDays(2), 8_000, paid = false)
        assertFalse(BalanceMath.isActive(planned))
        assertTrue(BalanceMath.isPlannedUnpaid(planned))
        assertEquals(100_000L, BalanceMath.currentBalanceMinor(chk, listOf(planned)))
        val paid = planned.copy(paid = true, plannedOn = planned.occurredOn, occurredOn = today)
        assertTrue(BalanceMath.isActive(paid))
        assertEquals(92_000L, BalanceMath.currentBalanceMinor(chk, listOf(paid)))
        // A deleted plan is neither.
        assertFalse(BalanceMath.isPlannedUnpaid(planned.copy(deletedAtEpochMillis = 1)))
    }

    @Test
    fun plannedLineAddsBillsPlansAndBudgetsAndPaidLineTracksWhatWasPaid() {
        val bills = listOf(EnvelopeBill("r", "Rent", LocalDate.of(2026, 11, 3), 50_000, true, false, "chk", "rent"))
        val txs = listOf(
            tx("Rent", LocalDate.of(2026, 11, 3), 50_000, recurring = "rent"),
            tx("Groceries", LocalDate.of(2026, 11, 2), 4_000),
            tx("Shoes", LocalDate.of(2026, 11, 4), 9_000),
            // Planned for the 4th, still unpaid; and one planned for the 8th.
            tx("Gift", LocalDate.of(2026, 11, 4), 3_000, paid = false),
            tx("Trip", LocalDate.of(2026, 11, 8), 20_000, paid = false),
            // Planned for the 2nd, paid on the 5th.
            tx("Phone fix", today, 6_000, plannedOn = LocalDate.of(2026, 11, 2)),
        )
        val track = SpendingTrack.build(cycle, today, bills, budgetsMinor = 10_000, transactions = txs)
        assertEquals(10, track.points.size)
        // Budgets of 100 spread over 10 days add 10 a day.
        assertEquals(1_000L, track.points[0].plannedMinor)
        // By the 5th: 50 budget + 500 rent + 30 gift + 60 phone fix.
        assertEquals(5_000L + 50_000 + 3_000 + 6_000, track.plannedToDateMinor)
        // Paid by the 5th: groceries 40, rent 500, shoes 90, phone fix 60.
        assertEquals(4_000L + 50_000 + 9_000 + 6_000, track.paidToDateMinor)
        assertEquals(track.paidToDateMinor - track.plannedToDateMinor, track.aheadMinor)
        // After today there is only the planned line; the whole cycle includes the trip.
        assertNull(track.points.last().paidMinor)
        assertEquals(10_000L + 50_000 + 3_000 + 6_000 + 20_000, track.plannedTotalMinor)
        // The gift is due and not paid; the trip is not due yet.
        assertEquals(3_000L, track.unpaidPlannedMinor)
    }

    @Test
    fun theMigrationAddsTwoColumnsOnly() {
        assertEquals(11, Migrations.MIGRATION_11_12.startVersion)
        assertEquals(12, Migrations.MIGRATION_11_12.endVersion)
        assertEquals(Migrations.MIGRATION_11_12, Migrations.ALL[Migrations.ALL.size - 5])
    }

    private fun tx(name: String, on: LocalDate, amount: Long, paid: Boolean = true, recurring: String? = null, plannedOn: LocalDate? = null) = LedgerTransaction(
        id = "$name-$on", accountId = "chk", counterAccountId = null, linkId = null, amountMinor = amount, balanceEffectMinor = -amount,
        currency = "USD", type = TransactionType.EXPENSE, merchantRaw = name, merchantNormalized = name.lowercase(), merchantId = null,
        description = "", categoryId = null, occurredOn = on, postedOn = null, detectionSource = DetectionSource.MANUAL, confidencePercent = 100,
        recurringPaymentId = recurring, status = TransactionStatus.CONFIRMED, userConfirmed = true, note = "", fingerprint = "",
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null, paid = paid,
        plannedOn = plannedOn ?: if (!paid) on else null,
    )
}
