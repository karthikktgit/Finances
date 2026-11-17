package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.dashboard.DashboardAssembler
import com.ledgerline.app.finance.dashboard.PlannedActual
import com.ledgerline.app.finance.forecast.ForecastSession
import com.ledgerline.app.finance.period.PayPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannedActualTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val period = PayPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19))
    private val liquid = setOf("a")

    private fun due(id: String, date: LocalDate, amount: Long, direction: CashFlowDirection, kind: ScheduledKind = ScheduledKind.BILL, account: String = "a", overdue: Boolean = false) =
        ScheduledOccurrence(id, id, date, amount, direction, PaymentPriority.MANDATORY, kind, account, account, IncomeConfidence.HIGH, true, 0, overdue)

    private fun summary(items: List<ScheduledOccurrence>, actual: Long = 100_000, received: Long = 40_000, spent: Long = 15_000, p: PayPeriod = period) =
        PlannedActual.summarize(p, today, items, liquid, actual, received, spent)

    @Test
    fun actualFiguresAreCarriedThroughUnchanged() {
        val s = summary(listOf(due("rent", today.plusDays(3), 50_000, CashFlowDirection.OUTFLOW)))
        assertEquals(100_000L, s.actualBalanceMinor)
        assertEquals(40_000L, s.incomeReceivedMinor)
        assertEquals(15_000L, s.spentMinor)
    }

    @Test
    fun unpaidItemsOnlyAffectThePlannedSide() {
        val s = summary(
            listOf(
                due("rent", today.plusDays(3), 50_000, CashFlowDirection.OUTFLOW),
                due("pay", today.plusDays(8), 200_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
                due("card", today.plusDays(9), 12_000, CashFlowDirection.OUTFLOW, ScheduledKind.DEBT),
            ),
        )
        assertEquals(62_000L, s.unpaidOutMinor)
        assertEquals(200_000L, s.incomeExpectedMinor)
        assertEquals(15_000L + 62_000L, s.spendingPlannedMinor)
        assertEquals(40_000L + 200_000L, s.incomePlannedMinor)
        assertEquals(100_000L - 50_000L + 200_000L - 12_000L, s.projectedEndMinor)
        assertEquals(40_000L - 15_000L, s.netActualMinor)
    }

    @Test
    fun expectedIncomeIsNeverPartOfTheActualBalance() {
        val s = summary(listOf(due("pay", today.plusDays(2), 300_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME)))
        assertEquals(100_000L, s.actualBalanceMinor)
        assertEquals(40_000L, s.incomeReceivedMinor)
        assertEquals(300_000L, s.incomeExpectedMinor)
    }

    @Test
    fun transfersAreNeitherIncomeNorSpendingNorProjected() {
        val s = summary(listOf(due("move", today.plusDays(1), 70_000, CashFlowDirection.OUTFLOW, ScheduledKind.TRANSFER)))
        assertEquals(0L, s.unpaidOutMinor)
        assertEquals(0L, s.incomeExpectedMinor)
        assertEquals(100_000L, s.projectedEndMinor)
        assertTrue(s.unpaid.isEmpty())
    }

    @Test
    fun theSameItemIsCountedOnce() {
        val rent = due("rent", today.plusDays(3), 50_000, CashFlowDirection.OUTFLOW)
        val s = summary(listOf(rent, rent.copy()))
        assertEquals(50_000L, s.unpaidOutMinor)
        assertEquals(50_000L, 100_000L - s.projectedEndMinor)
    }

    @Test
    fun overdueItemsBelongToTheCurrentCycle() {
        val s = summary(listOf(due("old", today, 9_000, CashFlowDirection.OUTFLOW, overdue = true)))
        assertEquals(9_000L, s.unpaidOutMinor)
    }

    @Test
    fun itemsOutsideTheCycleAreNotInItsSummaryButStillMoveALaterProjection() {
        val nextCycleBill = due("later", LocalDate.of(2026, 11, 25), 30_000, CashFlowDirection.OUTFLOW)
        val current = summary(listOf(nextCycleBill))
        assertEquals(0L, current.unpaidOutMinor)
        assertEquals(100_000L, current.projectedEndMinor)
        val next = summary(listOf(nextCycleBill), p = PayPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 19)))
        assertEquals(30_000L, next.unpaidOutMinor)
        assertEquals(70_000L, next.projectedEndMinor)
    }

    @Test
    fun accountsOutsideSafeToSpendDoNotMoveTheProjectedBalance() {
        val s = summary(listOf(due("loan", today.plusDays(2), 40_000, CashFlowDirection.OUTFLOW, account = "other")))
        assertEquals(100_000L, s.projectedEndMinor)
        assertEquals(40_000L, s.unpaidOutMinor)
    }

    @Test
    fun aFinishedCycleHasNothingLeftToPlan() {
        val past = PayPeriod(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 19))
        val s = summary(listOf(due("x", LocalDate.of(2026, 10, 1), 5_000, CashFlowDirection.OUTFLOW)), p = past)
        assertEquals(s.actualBalanceMinor, s.projectedEndMinor)
    }

    @Test
    fun switchingViewsDoesNotChangeTheInputs() {
        val items = listOf(due("rent", today.plusDays(3), 50_000, CashFlowDirection.OUTFLOW))
        val before = items.toList()
        summary(items)
        summary(items)
        assertEquals(before, items)
    }

    @Test
    fun plannedSalaryDoesNotRaiseTheActualBalanceOrSafeToSpend() {
        val checking = Account("a", "Checking", "", AccountType.CHECKING, null, 300_000, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0)
        val settings = UserSettings("USD", 50_000, 30, true)
        fun assemble(items: List<RecurringPayment>) = DashboardAssembler.assemble(
            today = today, settings = settings, accounts = listOf(checking), categories = emptyList(),
            transactions = emptyList(), recurring = items, budgets = emptyList(),
        )
        val salary = RecurringPayment(
            "pay", "Salary", "a", null, CashFlowDirection.INFLOW, ScheduledKind.INCOME, 400_000, "USD",
            RecurrenceFrequency.ONCE, null, null, null, today.plusDays(4), null, PaymentPriority.IMPORTANT, IncomeConfidence.HIGH, true, "", 1,
        )
        val without = assemble(emptyList())
        val with = assemble(listOf(salary))
        assertEquals(without.liquidMinor, with.liquidMinor)
        assertEquals(without.safeToSpend.amountMinor, with.safeToSpend.amountMinor)
        val s = PlannedActual.summarize(PayPeriod(with.cycleStart, with.cycleEnd), today, with.forecastSchedule, setOf("a"), with.liquidMinor, 0, 0)
        assertEquals(400_000L, s.incomeExpectedMinor)
        assertEquals(700_000L, s.projectedEndMinor)
        assertEquals(300_000L, s.actualBalanceMinor)
    }

    @Test
    fun theScheduleReachesTheLongestForecastPeriod() {
        val checking = Account("a", "Checking", "", AccountType.CHECKING, null, 0, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0)
        val yearly = RecurringPayment(
            "ins", "Insurance", "a", null, CashFlowDirection.OUTFLOW, ScheduledKind.BILL, 90_000, "USD",
            RecurrenceFrequency.ONCE, null, null, null, today.plusDays(ForecastSession.MAX_DAYS - 1L), null,
            PaymentPriority.MANDATORY, IncomeConfidence.NONE, true, "", 1,
        )
        val dashboard = DashboardAssembler.assemble(
            today = today, settings = UserSettings("USD", 0, 30, true), accounts = listOf(checking), categories = emptyList(),
            transactions = emptyList(), recurring = listOf(yearly), budgets = emptyList(),
        )
        assertTrue(dashboard.forecastSchedule.any { it.recurringId == "ins" && it.date == today.plusDays(359) })
    }
}
