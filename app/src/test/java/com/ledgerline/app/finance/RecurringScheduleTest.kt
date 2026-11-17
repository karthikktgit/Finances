package com.ledgerline.app.finance

import com.ledgerline.app.data.local.database.Migrations
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.balance.AccountProjection
import com.ledgerline.app.finance.dashboard.DashboardAssembler
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import com.ledgerline.app.ui.calendar.transactionTone
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import com.ledgerline.app.finance.schedule.RecurringSchedule
import com.ledgerline.app.finance.schedule.SeriesEdit
import com.ledgerline.app.ui.calendar.CalendarTone
import com.ledgerline.app.ui.calendar.calendarAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringScheduleTest {
    @Test
    fun oneTimePayment_occursOnlyOnTheStartDate() {
        val item = payment(frequency = RecurrenceFrequency.ONCE, start = LocalDate.of(2026, 10, 15))
        val dates = RecurringSchedule.occurrencesBetween(item, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        assertEquals(listOf(LocalDate.of(2026, 10, 15)), dates)
    }

    @Test
    fun monthlyPayment_repeatsOnTheSameDay() {
        val item = payment(start = LocalDate.of(2026, 1, 15), day = 15)
        val dates = RecurringSchedule.occurrencesBetween(item, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30))
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 4, 15),
            ),
            dates,
        )
    }

    @Test
    fun weeklyAndBiweekly_stepFromTheStartDate() {
        val start = LocalDate.of(2026, 10, 1)
        val weekly = payment(frequency = RecurrenceFrequency.WEEKLY, start = start, day = 1)
        val biweekly = payment(frequency = RecurrenceFrequency.BIWEEKLY, start = start, day = 1)
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 8),
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 22),
                LocalDate.of(2026, 10, 29),
            ),
            RecurringSchedule.occurrencesBetween(weekly, start, LocalDate.of(2026, 10, 31)),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 29)),
            RecurringSchedule.occurrencesBetween(biweekly, start, LocalDate.of(2026, 10, 31)),
        )
    }

    @Test
    fun yearlyPayment_keepsTheMonthAndClampsAShortFebruary() {
        val item = payment(frequency = RecurrenceFrequency.YEARLY, start = LocalDate.of(2024, 2, 29), day = 29)
        val dates = RecurringSchedule.occurrencesBetween(item, LocalDate.of(2024, 1, 1), LocalDate.of(2028, 12, 31))
        assertEquals(
            listOf(
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2025, 2, 28),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2027, 2, 28),
                LocalDate.of(2028, 2, 29),
            ),
            dates,
        )
    }

    @Test
    fun shortMonths_useTheLastDayThenReturnToTheOriginalDay() {
        val day31 = payment(start = LocalDate.of(2026, 1, 31), day = 31)
        val day30 = payment(start = LocalDate.of(2026, 1, 30), day = 30)
        val day29 = payment(start = LocalDate.of(2026, 1, 29), day = 29)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
            RecurringSchedule.occurrencesBetween(day31, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30)),
        )
        assertEquals(LocalDate.of(2026, 2, 28), RecurringSchedule.occurrencesBetween(day30, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).single())
        assertEquals(LocalDate.of(2026, 3, 30), RecurringSchedule.occurrencesBetween(day30, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).single())
        assertEquals(LocalDate.of(2026, 2, 28), RecurringSchedule.occurrencesBetween(day29, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).single())
        assertEquals(LocalDate.of(2026, 3, 29), RecurringSchedule.occurrencesBetween(day29, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).single())
    }

    @Test
    fun endDateAndOccurrenceCount_stopTheSeries() {
        val ended = payment(start = LocalDate.of(2026, 1, 15), day = 15).copy(endOn = LocalDate.of(2026, 3, 1))
        val counted = payment(start = LocalDate.of(2026, 1, 15), day = 15).copy(maxOccurrences = 2)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15)),
            RecurringSchedule.occurrencesBetween(ended, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15)),
            RecurringSchedule.occurrencesBetween(counted, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
        )
        assertTrue(RecurringSchedule.occurrencesBetween(counted, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 30)).isEmpty())
    }

    @Test
    fun repeatedExpansion_doesNotCreateDuplicateDates() {
        val item = payment(start = LocalDate.of(2026, 1, 10), day = 10)
        val first = RecurringSchedule.occurrencesBetween(item, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))
        val second = RecurringSchedule.occurrencesBetween(item, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))
        assertEquals(first, second)
        assertEquals(first.size, first.distinct().size)
    }

    @Test
    fun editingThisAndFuture_doesNotOverlapTheOriginalSeries() {
        val original = payment(id = "rent", start = LocalDate.of(2026, 1, 15), day = 15)
        val splitOn = LocalDate.of(2026, 4, 15)
        val ended = SeriesEdit.endBefore(original, splitOn)
        val successor = original.copy(id = "rent-next", startOn = splitOn, amountMinor = 150_000, dayOfMonth = 15)
        val before = RecurringSchedule.occurrencesBetween(ended, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 31))
        val after = RecurringSchedule.occurrencesBetween(successor, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 31))
        assertEquals(listOf(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 15)), before)
        assertTrue(after.first() == splitOn)
        assertTrue(before.intersect(after.toSet()).isEmpty())
        assertEquals(3, SeriesEdit.occurrencesUsedBefore(original, splitOn))
    }

    @Test
    fun markingOneDatePaid_leavesFutureDatesScheduled() {
        val item = payment(id = "insurance", start = LocalDate.of(2026, 10, 15), day = 15)
        val paid = OccurrenceAdjustment("insurance", LocalDate.of(2026, 10, 15), OccurrenceState.PAID, null, null, "txn")
        val events = OccurrenceMaterializer.project(
            items = listOf(item),
            adjustments = listOf(paid),
            settled = emptySet(),
            start = LocalDate.of(2026, 10, 1),
            end = LocalDate.of(2026, 12, 31),
            today = LocalDate.of(2026, 10, 20),
        )
        assertEquals(OccurrenceState.PAID, events.first { it.date == LocalDate.of(2026, 10, 15) }.state)
        assertEquals(OccurrenceState.SCHEDULED, events.first { it.date == LocalDate.of(2026, 11, 15) }.state)
        assertEquals(OccurrenceState.SCHEDULED, events.first { it.date == LocalDate.of(2026, 12, 15) }.state)
    }

    @Test
    fun skippingOneDate_doesNotRemoveTheSeries() {
        val item = payment(id = "phone", start = LocalDate.of(2026, 10, 5), day = 5)
        val skipped = OccurrenceAdjustment("phone", LocalDate.of(2026, 11, 5), OccurrenceState.SKIPPED, null, null, null)
        val events = OccurrenceMaterializer.project(
            items = listOf(item),
            adjustments = listOf(skipped),
            settled = emptySet(),
            start = LocalDate.of(2026, 10, 1),
            end = LocalDate.of(2026, 12, 31),
            today = LocalDate.of(2026, 10, 1),
        )
        assertFalse(events.any { it.date == LocalDate.of(2026, 11, 5) })
        assertTrue(events.any { it.date == LocalDate.of(2026, 10, 5) })
        assertTrue(events.any { it.date == LocalDate.of(2026, 12, 5) })
    }

    @Test
    fun paidOccurrence_isLeftOutOfTheForecastAndIsNotDeductedTwice() {
        val today = LocalDate.of(2026, 10, 1)
        val checking = Account(
            "checking", "Checking", "", AccountType.CHECKING, null, 300_000, null, "USD",
            true, true, true, SyncStatus.MANUAL, null, null, 0,
        )
        val rent = payment(id = "rent", name = "Rent", amount = 100_000, start = today, day = 1)
        val paid = OccurrenceAdjustment("rent", today, OccurrenceState.PAID, null, null, "txn_rent")
        val posted = LedgerTransaction(
            id = "txn_rent",
            accountId = "checking",
            counterAccountId = null,
            linkId = null,
            amountMinor = 100_000,
            balanceEffectMinor = -100_000,
            currency = "USD",
            type = TransactionType.EXPENSE,
            merchantRaw = "Rent",
            merchantNormalized = "Rent",
            merchantId = null,
            description = "Rent",
            categoryId = null,
            occurredOn = today,
            postedOn = today,
            detectionSource = DetectionSource.MANUAL,
            confidencePercent = 100,
            recurringPaymentId = "rent",
            status = TransactionStatus.CONFIRMED,
            userConfirmed = true,
            note = "",
            fingerprint = "fp",
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
            deletedAtEpochMillis = null,
        )
        val open = DashboardAssembler.assemble(
            today = today,
            settings = UserSettings("USD", 0, 20, true),
            accounts = listOf(checking),
            categories = emptyList(),
            transactions = emptyList(),
            recurring = listOf(rent),
            budgets = emptyList(),
        )
        val settled = DashboardAssembler.assemble(
            today = today,
            settings = UserSettings("USD", 0, 20, true),
            accounts = listOf(checking),
            categories = emptyList(),
            transactions = listOf(posted),
            recurring = listOf(rent),
            budgets = emptyList(),
            adjustments = listOf(paid),
        )
        assertEquals(100_000L, open.committedPaymentsMinor)
        assertTrue(open.forecast.any { it.scheduledMinor == -100_000L })
        assertEquals(0L, settled.committedPaymentsMinor)
        assertTrue(settled.upcoming.none { it.recurringId == "rent" && it.date == today })
        assertTrue(settled.forecast.none { it.scheduledMinor == -100_000L })
        assertEquals(200_000L, settled.liquidMinor)
    }

    @Test
    fun emptyLedger_hasNoFabricatedPayments() {
        val today = LocalDate.of(2026, 10, 11)
        val dashboard = DashboardAssembler.assemble(
            today = today,
            settings = UserSettings("USD", 0, 30, false),
            accounts = emptyList(),
            categories = emptyList(),
            transactions = emptyList(),
            recurring = emptyList(),
            budgets = emptyList(),
        )
        assertTrue(dashboard.upcoming.isEmpty())
        assertTrue(dashboard.recent.isEmpty())
        assertEquals(0, dashboard.monthSpendingMinor)
        assertEquals(0, dashboard.committedPaymentsMinor)
        assertTrue(dashboard.forecast.all { it.scheduledMinor == 0L })
        assertTrue(
            OccurrenceMaterializer.project(emptyList(), emptyList(), emptySet(), today, today.plusDays(30), today).isEmpty(),
        )
    }

    @Test
    fun calendarTone_usesOutgoingIncomeTransferOverdueAndPaidLabels() {
        val outgoing = calendarAppearance(CashFlowDirection.OUTFLOW, ScheduledKind.BILL, OccurrenceState.SCHEDULED, overdue = false)
        val income = calendarAppearance(CashFlowDirection.INFLOW, ScheduledKind.INCOME, OccurrenceState.SCHEDULED, overdue = false)
        val transfer = calendarAppearance(CashFlowDirection.OUTFLOW, ScheduledKind.TRANSFER, OccurrenceState.SCHEDULED, overdue = true)
        val overdue = calendarAppearance(CashFlowDirection.OUTFLOW, ScheduledKind.BILL, OccurrenceState.SCHEDULED, overdue = true)
        val paid = calendarAppearance(CashFlowDirection.OUTFLOW, ScheduledKind.BILL, OccurrenceState.PAID, overdue = true)
        assertEquals(CalendarTone.OUTGOING, outgoing.tone)
        assertEquals("Outgoing", outgoing.label())
        assertEquals(CalendarTone.INCOME, income.tone)
        assertEquals("Income", income.label())
        assertEquals(CalendarTone.TRANSFER, transfer.tone)
        assertEquals("Transfer", transfer.label())
        assertEquals("Outgoing · Overdue", overdue.label())
        assertTrue(overdue.overdue)
        assertEquals(CalendarTone.OUTGOING, paid.tone)
        assertEquals("Outgoing · Paid", paid.label())
        assertFalse(paid.overdue)
    }

    @Test
    fun projectedBalance_usesUnpaidScheduleOnceAndLeavesOtherAccountsAlone() {
        val bill = projected("checking", 40_000, CashFlowDirection.OUTFLOW, OccurrenceState.SCHEDULED)
        val paid = projected("checking", 10_000, CashFlowDirection.OUTFLOW, OccurrenceState.PAID)
        val income = projected("checking", 25_000, CashFlowDirection.INFLOW, OccurrenceState.SCHEDULED)
        val other = projected("savings", 99_000, CashFlowDirection.OUTFLOW, OccurrenceState.SCHEDULED)
        assertEquals(985_000L, AccountProjection.projectedMinor(1_000_000, "checking", listOf(bill, paid, income, other)))
    }

    @Test
    fun transactionTone_marksExpensesOutgoingAndTransfersNeutral() {
        assertEquals(com.ledgerline.app.ui.calendar.CalendarTone.OUTGOING, transactionTone(TransactionType.EXPENSE))
        assertEquals(com.ledgerline.app.ui.calendar.CalendarTone.INCOME, transactionTone(TransactionType.INCOME))
        assertEquals(com.ledgerline.app.ui.calendar.CalendarTone.INCOME, transactionTone(TransactionType.REFUND))
        assertEquals(com.ledgerline.app.ui.calendar.CalendarTone.TRANSFER, transactionTone(TransactionType.TRANSFER))
        assertEquals(com.ledgerline.app.ui.calendar.CalendarTone.TRANSFER, transactionTone(TransactionType.PAYMENT))
    }

    @Test
    fun migration_preservesThePathFromVersion1ToVersion2() {
        assertEquals(1, Migrations.MIGRATION_1_2.startVersion)
        assertEquals(2, Migrations.MIGRATION_1_2.endVersion)
        assertEquals(15, Migrations.ALL.size)
        assertEquals(6, Migrations.MIGRATION_6_7.startVersion)
        assertEquals(7, Migrations.MIGRATION_6_7.endVersion)
        assertEquals(4, Migrations.MIGRATION_4_5.startVersion)
        assertEquals(5, Migrations.MIGRATION_4_5.endVersion)
        assertEquals(5, Migrations.MIGRATION_5_6.startVersion)
        assertEquals(6, Migrations.MIGRATION_5_6.endVersion)
        assertEquals(2, Migrations.MIGRATION_2_3.startVersion)
        assertEquals(3, Migrations.MIGRATION_2_3.endVersion)
        assertEquals(3, Migrations.MIGRATION_3_4.startVersion)
        assertEquals(4, Migrations.MIGRATION_3_4.endVersion)
    }

    private fun projected(
        accountId: String,
        amount: Long,
        direction: CashFlowDirection,
        state: OccurrenceState,
    ) = ProjectedOccurrence(
        recurringId = accountId,
        name = accountId,
        date = LocalDate.of(2026, 10, 20),
        amountMinor = amount,
        direction = direction,
        kind = if (direction == CashFlowDirection.INFLOW) ScheduledKind.INCOME else ScheduledKind.BILL,
        priority = PaymentPriority.MANDATORY,
        accountId = accountId,
        incomeConfidence = IncomeConfidence.HIGH,
        state = state,
        overdue = false,
    )

    private fun payment(
        id: String = "pay",
        name: String = "Car Insurance",
        amount: Long = 27_000,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        start: LocalDate,
        day: Int = start.dayOfMonth,
    ) = RecurringPayment(
        id = id,
        name = name,
        accountId = "checking",
        categoryId = null,
        direction = CashFlowDirection.OUTFLOW,
        kind = ScheduledKind.BILL,
        amountMinor = amount,
        currency = "USD",
        frequency = frequency,
        intervalDays = null,
        dayOfMonth = day,
        dayOfWeek = null,
        startOn = start,
        endOn = null,
        priority = PaymentPriority.MANDATORY,
        incomeConfidence = IncomeConfidence.NONE,
        active = true,
        note = "",
    )
}
