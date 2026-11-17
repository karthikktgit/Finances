package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.finance.forecast.MonthlyForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlyForecastTest {
    private val today = LocalDate.of(2026, 10, 12)

    @Test
    fun oneMonth_usesRemainingDaysAndDoesNotRepeatAPostedExpense() {
        val forecast = MonthlyForecast.project(
            today = today,
            monthCount = 1,
            openingMinor = 40_000,
            events = listOf(
                event("pay", LocalDate.of(2026, 10, 20), 10_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME, 10_000),
                event("rent", LocalDate.of(2026, 10, 28), 4_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL, -4_000),
            ),
            dailyDiscretionaryMinor = 0,
        )
        assertEquals(1, forecast.months.size)
        assertEquals(YearMonth.of(2026, 10), forecast.months.single().month)
        assertEquals(40_000L, forecast.openingMinor)
        assertEquals(10_000L, forecast.incomingMinor)
        assertEquals(4_000L, forecast.outgoingMinor)
        assertEquals(46_000L, forecast.endingMinor)
        assertFalse(forecast.months.single().negative)
    }

    @Test
    fun threeAndSixMonths_carryEachEndingBalanceForward() {
        val bill = event("nov", LocalDate.of(2026, 11, 5), 20_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL, -20_000)
        val three = MonthlyForecast.project(today, 3, 50_000, listOf(bill), 0)
        val six = MonthlyForecast.project(today, 6, 50_000, listOf(bill), 0)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12)), three.months.map { it.month })
        assertEquals(three.months[0].endingMinor, three.months[1].openingMinor)
        assertEquals(30_000L, three.months[1].endingMinor)
        assertEquals(30_000L, three.endingMinor)
        assertEquals(6, six.months.size)
        assertEquals(YearMonth.of(2027, 3), six.months.last().month)
        assertEquals(three.endingMinor, six.endingMinor)
    }

    @Test
    fun laterMonth_canTurnNegativeWhileTheCurrentMonthStaysPositive() {
        val forecast = MonthlyForecast.project(
            today,
            3,
            10_000,
            listOf(event("big", LocalDate.of(2026, 11, 2), 25_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL, -25_000)),
            0,
        )
        assertFalse(forecast.months[0].negative)
        assertEquals(10_000L, forecast.months[0].endingMinor)
        assertTrue(forecast.months[1].negative)
        assertEquals(-15_000L, forecast.months[1].endingMinor)
        assertEquals(listOf("big"), forecast.shortfalls.map { it.name })
    }

    @Test
    fun transfer_movesCashWithoutCountingAsIncomeOrSpending() {
        val forecast = MonthlyForecast.project(
            today,
            1,
            20_000,
            listOf(event("move", LocalDate.of(2026, 10, 15), 5_000, CashFlowDirection.OUTFLOW, ScheduledKind.TRANSFER, -5_000)),
            0,
        )
        assertEquals(0L, forecast.incomingMinor)
        assertEquals(0L, forecast.outgoingMinor)
        assertEquals(15_000L, forecast.endingMinor)
    }

    @Test
    fun cardPayment_isOneOutgoingAndAPurchaseIsNotAddedAgain() {
        val forecast = MonthlyForecast.project(
            today,
            1,
            50_000,
            listOf(event("card", LocalDate.of(2026, 10, 18), 5_000, CashFlowDirection.OUTFLOW, ScheduledKind.DEBT, -5_000)),
            0,
        )
        assertEquals(5_000L, forecast.outgoingMinor)
        assertEquals(45_000L, forecast.endingMinor)
    }

    @Test
    fun yearBoundary_includesJanuaryAfterDecember() {
        val forecast = MonthlyForecast.project(
            LocalDate.of(2026, 12, 20),
            3,
            8_000,
            listOf(event("jan", LocalDate.of(2027, 1, 4), 1_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME, 1_000)),
            0,
        )
        assertEquals(
            listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2)),
            forecast.months.map { it.month },
        )
        assertEquals(8_000L, forecast.months[1].openingMinor)
        assertEquals(9_000L, forecast.endingMinor)
    }

    @Test
    fun missingAccount_isReportedAndDoesNotChangeTheBalance() {
        val forecast = MonthlyForecast.project(
            today,
            1,
            12_000,
            listOf(event("loose", LocalDate.of(2026, 10, 21), 3_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL, 0).copy(accountId = "", accountName = "Account not assigned")),
            0,
        )
        assertEquals(1, forecast.unassignedCount)
        assertEquals(12_000L, forecast.endingMinor)
        assertTrue(forecast.note.contains("not a guarantee"))
    }

    private fun event(
        name: String,
        date: LocalDate,
        amount: Long,
        direction: CashFlowDirection,
        kind: ScheduledKind,
        delta: Long,
    ) = ScheduledOccurrence(
        recurringId = name,
        name = name,
        date = date,
        amountMinor = amount,
        direction = direction,
        priority = PaymentPriority.MANDATORY,
        kind = kind,
        accountId = "checking",
        accountName = "Checking",
        incomeConfidence = IncomeConfidence.HIGH,
        countedInSafeToSpend = delta != 0L,
        liquidDeltaMinor = delta,
        overdue = false,
    )
}
