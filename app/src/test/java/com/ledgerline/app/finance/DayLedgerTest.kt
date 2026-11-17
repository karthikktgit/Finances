package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.balance.DayLedger
import com.ledgerline.app.finance.period.CycleHighlight
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.domain.model.CycleType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.schedule.RecurringSchedule
import com.ledgerline.app.finance.time.FinancialDate
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayLedgerTest {
    private val cash = account("checking", 100_000, liquid = true)
    private val day1 = LocalDate.of(2026, 10, 10)
    private val day2 = LocalDate.of(2026, 10, 11)
    private val day3 = LocalDate.of(2026, 10, 12)

    @Test
    fun threeDays_keepStartSpentAndEndIndependent() {
        val rows = listOf(
            tx("e1", day1, 10_000, -10_000, TransactionType.EXPENSE),
            tx("e2", day2, 5_000, -5_000, TransactionType.EXPENSE),
            tx("in", day3, 20_000, 20_000, TransactionType.INCOME),
        )
        val first = DayLedger.figures(day1, listOf(cash), rows)
        val second = DayLedger.figures(day2, listOf(cash), rows)
        val third = DayLedger.figures(day3, listOf(cash), rows)
        assertEquals(100_000L, first.startMinor)
        assertEquals(10_000L, first.spentMinor)
        assertEquals(90_000L, first.endMinor)
        assertEquals(90_000L, second.startMinor)
        assertEquals(5_000L, second.spentMinor)
        assertEquals(85_000L, second.endMinor)
        assertEquals(85_000L, third.startMinor)
        assertEquals(0L, third.spentMinor)
        assertEquals(105_000L, third.endMinor)
    }

    @Test
    fun unplanned_tracksOnlyMarkedExpensesAndEditsReplaceTheOldAmount() {
        val planned = tx("plan", day1, 10_000, -10_000, TransactionType.EXPENSE)
        assertEquals(0L, DayLedger.figures(day1, listOf(cash), listOf(planned)).unplannedMinor)
        val thirty = tx("u1", day1, 3_000, -3_000, TransactionType.EXPENSE, unplanned = true)
        val twenty = tx("u2", day1, 2_000, -2_000, TransactionType.EXPENSE, unplanned = true)
        assertEquals(5_000L, DayLedger.figures(day1, listOf(cash), listOf(planned, thirty, twenty)).unplannedMinor)
        val forty = thirty.copy(amountMinor = 4_000, balanceEffectMinor = -4_000)
        assertEquals(6_000L, DayLedger.figures(day1, listOf(cash), listOf(planned, forty, twenty)).unplannedMinor)
        assertEquals(4_000L, DayLedger.figures(day1, listOf(cash), listOf(planned, forty, twenty.copy(unplanned = false))).unplannedMinor)
        assertEquals(0L, DayLedger.figures(day1, listOf(cash), listOf(planned, forty.copy(deletedAtEpochMillis = 1L))).unplannedMinor)
        assertEquals(90_000L, DayLedger.figures(day1, listOf(cash), listOf(planned)).endMinor)
    }

    @Test
    fun lateHistoricalIncome_rebuildsLaterDaysAndLeavesTheEarlierDay() {
        val oct10 = LocalDate.of(2026, 10, 10)
        val oct11 = LocalDate.of(2026, 10, 11)
        val oct12 = LocalDate.of(2026, 10, 12)
        val oct13 = LocalDate.of(2026, 10, 13)
        val before = listOf(
            tx("e10", oct10, 10_000, -10_000, TransactionType.EXPENSE),
            tx("e12", oct12, 5_000, -5_000, TransactionType.EXPENSE),
        )
        assertEquals(90_000L, DayLedger.figures(oct10, listOf(cash), before).endMinor)
        val income = tx("in11", oct11, 20_000, 20_000, TransactionType.INCOME)
        val after = before + income
        assertEquals(90_000L, DayLedger.figures(oct10, listOf(cash), after).endMinor)
        assertEquals(90_000L, DayLedger.figures(oct11, listOf(cash), after).startMinor)
        assertEquals(110_000L, DayLedger.figures(oct11, listOf(cash), after).endMinor)
        assertEquals(110_000L, DayLedger.figures(oct12, listOf(cash), after).startMinor)
        assertEquals(105_000L, DayLedger.figures(oct12, listOf(cash), after).endMinor)
        assertEquals(105_000L, DayLedger.figures(oct13, listOf(cash), after).startMinor)
    }

    @Test
    fun expenseToday_doesNotChangeYesterday() {
        val yesterday = LocalDate.of(2026, 10, 12)
        val today = LocalDate.of(2026, 10, 13)
        val rows = listOf(tx("today", today, 2_000, -2_000, TransactionType.EXPENSE))
        val before = DayLedger.figures(yesterday, listOf(cash), rows)
        val current = DayLedger.figures(today, listOf(cash), rows)
        assertEquals(100_000L, before.startMinor)
        assertEquals(100_000L, before.endMinor)
        assertEquals(0L, before.spentMinor)
        assertEquals(100_000L, current.startMinor)
        assertEquals(2_000L, current.spentMinor)
        assertEquals(98_000L, current.endMinor)
        assertEquals(LocalDate.of(2026, 10, 13), FinancialDate.fromPickerMillis(FinancialDate.toPickerMillis(today)))
    }

    @Test
    fun transferAndCardPayment_doNotIncreaseSpending() {
        val savings = account("savings", 40_000, liquid = true)
        val card = account("card", -20_000, liquid = false).copy(type = AccountType.CREDIT_CARD)
        val purchase = tx("buy", day1, 5_000, -5_000, TransactionType.EXPENSE).copy(accountId = "card")
        val payment = tx("pay", day2, 5_000, -5_000, TransactionType.PAYMENT)
        val out = tx("out", day1, 20_000, -20_000, TransactionType.TRANSFER)
        val into = tx("into", day1, 20_000, 20_000, TransactionType.TRANSFER).copy(accountId = "savings")
        val rows = listOf(purchase, payment, out, into)
        val accounts = listOf(cash, savings, card)
        assertEquals(5_000L, DayLedger.figures(day1, accounts, rows).spentMinor)
        assertEquals(0L, DayLedger.figures(day2, accounts, rows).spentMinor)
        val consolidatedBefore = BalanceMath.balanceAsOf(cash, rows, day1.minusDays(1)) + BalanceMath.balanceAsOf(savings, rows, day1.minusDays(1))
        val consolidatedAfter = BalanceMath.balanceAsOf(cash, rows, day1) + BalanceMath.balanceAsOf(savings, rows, day1)
        assertEquals(consolidatedBefore, consolidatedAfter)
        assertEquals(80_000L, BalanceMath.balanceAsOf(cash, rows, day1))
        assertEquals(60_000L, BalanceMath.balanceAsOf(savings, rows, day1))
    }

    @Test
    fun refundMarkedUnplanned_reducesUnplannedSpending() {
        val expense = tx("snack", day1, 3_000, -3_000, TransactionType.EXPENSE, unplanned = true)
        val refund = tx("back", day1, 1_000, 1_000, TransactionType.REFUND, unplanned = true)
        val figures = DayLedger.figures(day1, listOf(cash), listOf(expense, refund))
        assertEquals(2_000L, figures.unplannedMinor)
        assertEquals(2_000L, figures.spentMinor)
        assertEquals(98_000L, figures.endMinor)
    }

    @Test
    fun newSalarySchedule_doesNotCreateACompletedTransaction() {
        val salary = RecurringPayment(
            id = "pay", name = "Salary", accountId = "checking", categoryId = null,
            direction = CashFlowDirection.INFLOW, kind = ScheduledKind.INCOME, amountMinor = 200_000, currency = "USD",
            frequency = RecurrenceFrequency.MONTHLY, intervalDays = null, dayOfMonth = 31, dayOfWeek = null,
            startOn = LocalDate.of(2026, 11, 30), endOn = null, priority = PaymentPriority.MANDATORY,
            incomeConfidence = IncomeConfidence.HIGH, active = true, note = "",
        )
        val dates = RecurringSchedule.occurrencesBetween(salary, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 3, 31))
        assertTrue(dates.all { !it.isBefore(salary.startOn) })
        assertFalse(dates.contains(LocalDate.of(2026, 10, 31)))
        assertEquals(0L, DayLedger.figures(LocalDate.of(2026, 10, 13), listOf(cash), emptyList()).spentMinor)
        assertEquals(100_000L, DayLedger.figures(LocalDate.of(2026, 10, 13), listOf(cash), emptyList()).endMinor)
    }

    @Test
    fun cycleUnplanned_matchesTheSumOfTheSameDays() {
        val period = PayPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19))
        val rows = listOf(
            tx("in", LocalDate.of(2026, 10, 20), 3_000, -3_000, TransactionType.EXPENSE, unplanned = true),
            tx("edge", LocalDate.of(2026, 11, 19), 2_000, -2_000, TransactionType.EXPENSE, unplanned = true),
            tx("before", LocalDate.of(2026, 10, 19), 9_000, -9_000, TransactionType.EXPENSE, unplanned = true),
            tx("after", LocalDate.of(2026, 11, 20), 8_000, -8_000, TransactionType.EXPENSE, unplanned = true),
            tx("planned", LocalDate.of(2026, 10, 21), 4_000, -4_000, TransactionType.EXPENSE),
            tx("move", LocalDate.of(2026, 10, 22), 1_000, -1_000, TransactionType.TRANSFER, unplanned = true),
        )
        val cycle = DayLedger.cycleFigures(period, listOf(cash), rows)
        var summed = 0L
        var cursor = period.start
        while (!cursor.isAfter(period.end)) {
            summed += DayLedger.figures(cursor, listOf(cash), rows).unplannedMinor
            cursor = cursor.plusDays(1)
        }
        assertEquals(5_000L, cycle.unplannedMinor)
        assertEquals(summed, cycle.unplannedMinor)
        assertEquals(9_000L, cycle.spentMinor)
        val settings = UserSettings("USD", 0, 30, true, CycleType.MONTHLY, LocalDate.of(2026, 1, 20), 20, 5)
        val found = CycleSchedule.containing(LocalDate.of(2026, 10, 25), settings)
        assertEquals(period, found)
        assertTrue(CycleHighlight.of(LocalDate.of(2026, 10, 20), found, LocalDate.of(2026, 10, 25), true).inCycle)
        assertTrue(CycleHighlight.of(LocalDate.of(2026, 11, 19), found, LocalDate.of(2026, 10, 25), true).cycleEnd)
        val dayOnly = CycleHighlight.of(LocalDate.of(2026, 10, 25), found, LocalDate.of(2026, 10, 25), false)
        assertFalse(dayOnly.inCycle)
        assertTrue(dayOnly.selected)
    }

    private fun account(id: String, opening: Long, liquid: Boolean) = Account(
        id, id, "", AccountType.CHECKING, null, opening, null, "USD", true, true, liquid,
        SyncStatus.MANUAL, null, null, 0,
    )

    private fun tx(
        id: String,
        date: LocalDate,
        amount: Long,
        effect: Long,
        type: TransactionType,
        unplanned: Boolean = false,
    ) = LedgerTransaction(
        id = id,
        accountId = "checking",
        counterAccountId = null,
        linkId = null,
        amountMinor = amount,
        balanceEffectMinor = effect,
        currency = "USD",
        type = type,
        merchantRaw = id,
        merchantNormalized = id,
        merchantId = null,
        description = id,
        categoryId = null,
        occurredOn = date,
        postedOn = date,
        detectionSource = DetectionSource.MANUAL,
        confidencePercent = 100,
        recurringPaymentId = null,
        status = TransactionStatus.CONFIRMED,
        userConfirmed = true,
        note = "",
        fingerprint = id,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
        deletedAtEpochMillis = null,
        unplanned = unplanned,
    )
}
