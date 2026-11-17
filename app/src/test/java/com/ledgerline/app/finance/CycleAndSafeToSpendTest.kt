package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.CycleType
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.forecast.DailyBalanceForecast
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.safetospend.SafeToSpendEngine
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleAndSafeToSpendTest {
    @Test
    fun safeToSpend_doesNotAddExpectedIncome() {
        val result = SafeToSpendEngine.calculate(
            SafeToSpendEngine.Input(300_000, 100_000, 50_000, 0, 80_000, 0),
        )
        assertEquals(150_000, result.amountMinor)
        assertTrue(result.lines.none { it.label == "Expected income" })
    }

    @Test
    fun monthlyCycle_runsFromThe20thThroughThe19th() {
        val settings = UserSettings("USD", 0, 30, true, CycleType.MONTHLY, LocalDate.of(2026, 1, 20), 20, 5)
        val cycle = CycleSchedule.containing(LocalDate.of(2026, 10, 25), settings)
        assertEquals(LocalDate.of(2026, 10, 20), cycle.start)
        assertEquals(LocalDate.of(2026, 11, 19), cycle.end)
        val next = CycleSchedule.next(cycle, settings)
        assertEquals(cycle.end.plusDays(1), next.start)
    }

    @Test
    fun weeklyAndBiweekly_followTheAnchorWithoutGaps() {
        val anchor = LocalDate.of(2026, 10, 1)
        val weekly = UserSettings("USD", 0, 30, true, CycleType.WEEKLY, anchor, 20, 5)
        val biweekly = weekly.copy(cycleType = CycleType.BIWEEKLY)
        val week = CycleSchedule.containing(LocalDate.of(2026, 10, 8), weekly)
        assertEquals(LocalDate.of(2026, 10, 8), week.start)
        assertEquals(LocalDate.of(2026, 10, 14), week.end)
        val twoWeeks = CycleSchedule.containing(LocalDate.of(2026, 10, 20), biweekly)
        assertEquals(LocalDate.of(2026, 10, 15), twoWeeks.start)
        assertEquals(LocalDate.of(2026, 10, 28), twoWeeks.end)
        assertEquals(twoWeeks.end.plusDays(1), CycleSchedule.next(twoWeeks, biweekly).start)
    }

    @Test
    fun semimonthly_splitsTheMonthOnTheChosenDays() {
        val settings = UserSettings("USD", 0, 30, true, CycleType.SEMIMONTHLY, LocalDate.of(2026, 1, 1), 1, 16)
        val early = CycleSchedule.containing(LocalDate.of(2026, 10, 10), settings)
        val late = CycleSchedule.containing(LocalDate.of(2026, 10, 20), settings)
        assertEquals(LocalDate.of(2026, 10, 1), early.start)
        assertEquals(LocalDate.of(2026, 10, 15), early.end)
        assertEquals(LocalDate.of(2026, 10, 16), late.start)
        assertEquals(LocalDate.of(2026, 10, 31), late.end)
    }

    @Test
    fun leapYearMonthlyStart_usesTheLastDayOfFebruary() {
        val settings = UserSettings("USD", 0, 30, true, CycleType.MONTHLY, LocalDate.of(2024, 1, 31), 31, 5)
        val cycle = CycleSchedule.containing(LocalDate.of(2024, 2, 20), settings)
        assertEquals(LocalDate.of(2024, 1, 31), cycle.start)
        assertEquals(LocalDate.of(2024, 2, 28), cycle.end)
    }

    @Test
    fun dailyForecast_putsIncomeOnlyOnItsDateAndDoesNotDeductPostedMoneyAgain() {
        val checking = Account(
            "a", "Bank A", "", AccountType.CHECKING, null, 100_000, null, "USD", true, true, true,
            SyncStatus.MANUAL, null, null, 0,
        )
        val today = LocalDate.of(2026, 10, 20)
        val salary = event("pay", today, 50_000, CashFlowDirection.INFLOW)
        val bill = event("rent", today.plusDays(1), 20_000, CashFlowDirection.OUTFLOW)
        val todayForecast = DailyBalanceForecast.forDate(today, today, listOf(checking), emptyList(), listOf(salary, bill))
        val tomorrow = DailyBalanceForecast.forDate(today.plusDays(1), today, listOf(checking), emptyList(), listOf(salary, bill))
        assertEquals(50_000, todayForecast.expectedIncomingMinor)
        assertEquals(0, todayForecast.scheduledOutgoingMinor)
        assertEquals(150_000, todayForecast.projectedEndMinor)
        assertEquals(0, tomorrow.expectedIncomingMinor)
        assertEquals(20_000, tomorrow.scheduledOutgoingMinor)
        assertEquals(130_000, tomorrow.projectedEndMinor)
        assertFalse(todayForecast.actualLabel.contains("received") && todayForecast.expectedIncomingMinor == 0L)
    }

    private fun event(id: String, date: LocalDate, amount: Long, direction: CashFlowDirection) = ProjectedOccurrence(
        id, id, date, amount, direction,
        if (direction == CashFlowDirection.INFLOW) ScheduledKind.INCOME else ScheduledKind.BILL,
        PaymentPriority.MANDATORY, "a", IncomeConfidence.HIGH, OccurrenceState.SCHEDULED, false,
    )
}
