package com.ledgerline.app.finance

import com.ledgerline.app.data.local.database.Migrations
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.finance.schedule.AutoPayRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Which due dates autopay records, and which it leaves for you. */
class AutoPayTest {
    private val today = LocalDate.of(2026, 11, 3)

    private fun bill(since: LocalDate?, start: LocalDate = LocalDate.of(2026, 1, 5), active: Boolean = true) = RecurringPayment(
        id = "netflix", name = "Netflix", accountId = "chk", categoryId = null, direction = CashFlowDirection.OUTFLOW,
        kind = ScheduledKind.SUBSCRIPTION, amountMinor = 1_549, currency = "USD", frequency = RecurrenceFrequency.MONTHLY,
        intervalDays = null, dayOfMonth = start.dayOfMonth, dayOfWeek = start.dayOfWeek.value, startOn = start, endOn = null,
        priority = PaymentPriority.MANDATORY, incomeConfidence = IncomeConfidence.NONE, active = active, note = "",
        autopaySince = since,
    )

    @Test
    fun billsWithoutAutopayAreLeftForYou() {
        assertTrue(AutoPayRule.due(bill(since = null), emptyList(), today).isEmpty())
    }

    @Test
    fun turningAutopayOnNeverBackfillsOldDates() {
        // On since Nov 1: Netflix is due on the 5th, so nothing is due yet on Nov 3.
        assertTrue(AutoPayRule.due(bill(since = LocalDate.of(2026, 11, 1)), emptyList(), today).isEmpty())
        // Since Oct 1: the Oct 5 date is recorded, January to September are not.
        assertEquals(listOf(LocalDate.of(2026, 10, 5)), AutoPayRule.due(bill(since = LocalDate.of(2026, 10, 1)), emptyList(), today))
    }

    @Test
    fun aDueDateIsRecordedOnTheDayItself() {
        val dueToday = bill(since = LocalDate.of(2026, 11, 1), start = LocalDate.of(2026, 1, 3))
        assertEquals(listOf(today), AutoPayRule.due(dueToday, emptyList(), today))
    }

    @Test
    fun paidAndSkippedDatesAreNotTouched() {
        val item = bill(since = LocalDate.of(2026, 8, 1))
        val adjustments = listOf(
            OccurrenceAdjustment("netflix", LocalDate.of(2026, 8, 5), OccurrenceState.PAID, null, null, "t1"),
            OccurrenceAdjustment("netflix", LocalDate.of(2026, 9, 5), OccurrenceState.SKIPPED, null, null, null),
            OccurrenceAdjustment("other", LocalDate.of(2026, 10, 5), OccurrenceState.PAID, null, null, "t2"),
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 5)), AutoPayRule.due(item, adjustments, today))
    }

    @Test
    fun pausedBillsAndFutureStartsWaitTheirTurn() {
        assertTrue(AutoPayRule.due(bill(since = LocalDate.of(2026, 10, 1), active = false), emptyList(), today).isEmpty())
        assertTrue(AutoPayRule.due(bill(since = LocalDate.of(2026, 10, 1), start = LocalDate.of(2026, 12, 5)), emptyList(), today).isEmpty())
    }

    @Test
    fun theMigrationOnlyAddsTheAutopayColumn() {
        assertEquals(8, Migrations.MIGRATION_8_9.startVersion)
        assertEquals(9, Migrations.MIGRATION_8_9.endVersion)
        assertEquals(Migrations.MIGRATION_8_9, Migrations.ALL[7])
    }
}
