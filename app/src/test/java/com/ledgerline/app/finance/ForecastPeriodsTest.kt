package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.finance.forecast.DailyAccountForecast
import com.ledgerline.app.finance.forecast.ForecastSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ForecastPeriodsTest {
    private val today = LocalDate.of(2027, 12, 20)
    private val checking = account("a", "Checking", AccountType.CHECKING, 100_000)
    private val savings = account("b", "Savings", AccountType.SAVINGS, 50_000)
    private val card = account("c", "Card", AccountType.CREDIT_CARD, -20_000)

    private fun project(
        ids: Set<String>,
        days: Int,
        events: List<ScheduledOccurrence> = emptyList(),
        accounts: List<Account> = listOf(checking, savings, card),
        discretionary: Long = 0,
    ) = DailyAccountForecast.project(today, days, accounts, ids, emptyList(), events, discretionary)

    @Test
    fun theSupportedPeriodsAreExactlyTheRequestedOnes() {
        assertEquals(listOf(30, 60, 90, 120, 180, 360), ForecastSession.PERIODS)
        assertEquals(360, ForecastSession.MAX_DAYS)
        ForecastSession.PERIODS.forEach { assertEquals(it, ForecastSession().chooseDays(it).days) }
    }

    @Test
    fun anUnsupportedPeriodKeepsTheCurrentOne() {
        val session = ForecastSession().chooseDays(90)
        assertEquals(90, session.chooseDays(45).days)
        assertEquals(90, session.chooseDays(0).days)
        assertEquals(30, ForecastSession().days)
    }

    @Test
    fun everyPeriodHasOneRowPerDateStartingToday() {
        ForecastSession.PERIODS.forEach { n ->
            val dates = DailyAccountForecast.dates(today, n)
            assertEquals(n, dates.size)
            assertEquals(today, dates.first())
            assertEquals(today.plusDays(n - 1L), dates.last())
            assertEquals(n, dates.toSet().size)
            dates.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
            val combined = project(setOf("a"), n).combined!!
            assertEquals(dates, combined.map { it.date })
        }
    }

    @Test
    fun periodBoundariesIncludeTheLastDayAndNothingBeyond() {
        // 2027-12-20 + 179 days is a boundary for the 180-day view; the next day only appears in 360.
        val lastOf180 = today.plusDays(179)
        val firstBeyond = today.plusDays(180)
        val bill = event("late", firstBeyond, 25_000, CashFlowDirection.OUTFLOW, "a")
        val inside = event("edge", lastOf180, 10_000, CashFlowDirection.OUTFLOW, "a")
        val short = project(setOf("a"), 180, listOf(bill, inside)).combined!!
        val long = project(setOf("a"), 360, listOf(bill, inside)).combined!!
        assertEquals(90_000L, short.last().endMinor)
        assertEquals(90_000L, long.first { it.date == lastOf180 }.endMinor)
        assertEquals(90_000L, long.first { it.date == lastOf180.plusDays(0) }.endMinor)
        assertEquals(65_000L, long.last().endMinor)
        assertTrue(short.none { it.date == firstBeyond })
        assertEquals(65_000L, long.first { it.date == firstBeyond }.endMinor)
    }

    @Test
    fun the360DayViewSpansALeapDayAndAYearEnd() {
        val leapStart = LocalDate.of(2027, 12, 20)
        val dates = DailyAccountForecast.dates(leapStart, 360)
        assertTrue(dates.contains(LocalDate.of(2028, 2, 29)))
        assertTrue(dates.contains(LocalDate.of(2028, 1, 1)))
        assertFalse(dates.contains(LocalDate.of(2029, 1, 1)))
    }

    @Test
    fun anyCombinationOfAccountsIsSupported() {
        val ids = listOf("a", "b", "c")
        val subsets = (1 until 8).map { mask -> ids.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet() }
        subsets.forEach { subset ->
            val result = project(subset, 60)
            assertEquals(subset, result.selectedIds.toSet())
            val expected = listOf(checking, savings, card).filter { it.id in subset }.sumOf { it.openingBalanceMinor }
            assertEquals(expected, result.combined!!.first().endMinor)
        }
    }

    @Test
    fun anAccountListedTwiceIsCountedOnce() {
        val result = project(setOf("a", "b"), 30, accounts = listOf(checking, checking, savings, savings))
        assertEquals(listOf("a", "b"), result.accounts.map { it.accountId })
        assertEquals(150_000L, result.combined!!.first().endMinor)
    }

    @Test
    fun selectAllAndDeselectAllAreSetOperations() {
        val active = setOf("a", "b", "c")
        val all = ForecastSession().selectAll(active)
        assertEquals(active, all.selection(active))
        assertEquals(emptySet<String>(), all.clear().selection(active))
        assertEquals(setOf("a"), all.clear().toggle("a", active).selection(active))
        // An account that is no longer active disappears from the selection.
        assertEquals(setOf("a", "b"), all.selection(setOf("a", "b")))
    }

    @Test
    fun combinedEqualsTheSumOfTheAccounts() {
        val events = listOf(
            event("rent", today.plusDays(3), 40_000, CashFlowDirection.OUTFLOW, "a"),
            event("pay", today.plusDays(10), 200_000, CashFlowDirection.INFLOW, "a"),
            event("gym", today.plusDays(12), 3_000, CashFlowDirection.OUTFLOW, "b"),
            event("card", today.plusDays(20), 15_000, CashFlowDirection.OUTFLOW, "c"),
        )
        listOf(30, 120, 360).forEach { n ->
            val result = project(setOf("a", "b", "c"), n, events)
            result.combined!!.forEachIndexed { i, day ->
                assertEquals(result.accounts.sumOf { it.days[i].endMinor }, day.endMinor)
            }
        }
        val single = project(setOf("b"), 30, events)
        assertEquals(47_000L, single.combined!!.last().endMinor)
        assertEquals(single.accounts.single().days.map { it.endMinor }, single.combined!!.map { it.endMinor })
    }

    @Test
    fun aTransferIsNotConsolidatedIncomeOrSpending() {
        val transfer = event("move", today.plusDays(2), 30_000, CashFlowDirection.OUTFLOW, "a", ScheduledKind.TRANSFER)
        val result = project(setOf("a", "b"), 30, listOf(transfer))
        val day = result.combined!!.first { it.date == today.plusDays(2) }
        assertEquals(0L, day.incomingMinor)
        assertEquals(0L, day.outgoingMinor)
    }

    @Test
    fun deletedAccountsAreIgnoredEvenIfStillSelected() {
        val live = listOf(checking, savings)
        val result = DailyAccountForecast.project(today, 30, live, setOf("a", "gone"), emptyList(), emptyList(), 0)
        assertEquals(listOf("a"), result.selectedIds)
        assertEquals(100_000L, result.combined!!.last().endMinor)
    }

    @Test
    fun actualBalancesAreNotChangedByUnpaidItems() {
        val unpaid = event("salary", today.plusDays(5), 500_000, CashFlowDirection.INFLOW, "a", ScheduledKind.INCOME)
        val result = project(setOf("a"), 30, listOf(unpaid))
        assertEquals(100_000L, result.combined!!.first().endMinor)
        assertEquals(600_000L, result.combined!!.last().endMinor)
        assertNotEquals(result.combined!!.first().endMinor, result.combined!!.last().endMinor)
        assertEquals(100_000L, checking.openingBalanceMinor)
    }

    private fun account(id: String, name: String, type: AccountType, opening: Long) = Account(
        id, name, "", type, null, opening, null, "USD", true, true, type != AccountType.CREDIT_CARD,
        SyncStatus.MANUAL, null, null, 0,
    )

    private fun event(
        id: String,
        date: LocalDate,
        amount: Long,
        direction: CashFlowDirection,
        accountId: String,
        kind: ScheduledKind = ScheduledKind.BILL,
    ) = ScheduledOccurrence(
        id, id, date, amount, direction, PaymentPriority.MANDATORY, kind, accountId, accountId,
        IncomeConfidence.HIGH, true, 0, false,
    )
}
