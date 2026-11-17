package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.forecast.CashFlowPlan
import com.ledgerline.app.finance.forecast.DayBalance
import com.ledgerline.app.finance.forecast.FlowRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The cash-flow screen's headline, payday figures, and the timeline of days when money moves. */
class CashFlowPlanTest {
    private val today = LocalDate.of(2026, 11, 3)

    private fun days(vararg ends: Long) = ends.mapIndexed { i, end ->
        DayBalance(today.plusDays(i.toLong()), end, 0, 0, end < 0, false)
    }

    private fun event(name: String, offset: Long, amount: Long, direction: CashFlowDirection, kind: ScheduledKind, account: String = "chk") =
        ScheduledOccurrence(
            recurringId = name, name = name, date = today.plusDays(offset), amountMinor = amount, direction = direction,
            priority = PaymentPriority.MANDATORY, kind = kind, accountId = account, accountName = "Checking",
            incomeConfidence = IncomeConfidence.HIGH, countedInSafeToSpend = true,
            liquidDeltaMinor = if (direction == CashFlowDirection.INFLOW) amount else -amount,
        )

    @Test
    fun theHeadlineFindsTheLowestPointAndTheDayCashRunsOut() {
        val summary = CashFlowPlan.summarize(days(50_000, 30_000, -5_000, -12_000, 90_000), paydays = listOf(today.plusDays(4)), bufferMinor = 20_000)!!
        assertEquals(50_000L, summary.todayMinor)
        assertEquals(-12_000L, summary.lowestMinor)
        assertEquals(today.plusDays(3), summary.lowestOn)
        assertEquals(today.plusDays(2), summary.runsOutOn)
        assertEquals(today.plusDays(2), summary.belowBufferOn)
        assertEquals(today.plusDays(4), summary.nextPayday)
        // What has to last until payday: the day before it.
        assertEquals(-12_000L, summary.beforePaydayMinor)
        assertEquals(90_000L, summary.endMinor)
        assertEquals(FlowRisk.SHORT, CashFlowPlan.risk(summary))
    }

    @Test
    fun dippingIntoTheBufferIsTightAndStayingAboveIsFine() {
        val tight = CashFlowPlan.summarize(days(50_000, 15_000, 40_000), emptyList(), bufferMinor = 20_000)!!
        assertEquals(FlowRisk.TIGHT, CashFlowPlan.risk(tight))
        assertNull(tight.runsOutOn)
        assertNull(tight.nextPayday)
        assertNull(tight.beforePaydayMinor)
        val fine = CashFlowPlan.summarize(days(50_000, 45_000, 40_000), emptyList(), bufferMinor = 20_000)!!
        assertEquals(FlowRisk.FINE, CashFlowPlan.risk(fine))
        // With no buffer set, only going below zero counts.
        assertEquals(FlowRisk.FINE, CashFlowPlan.risk(CashFlowPlan.summarize(days(500, 10), emptyList(), bufferMinor = 0)!!))
        assertNull(CashFlowPlan.summarize(emptyList(), emptyList(), 0))
    }

    @Test
    fun theEarliestOfTwoEqualLowsIsReported() {
        val summary = CashFlowPlan.summarize(days(10_000, 2_000, 8_000, 2_000), emptyList(), 0)!!
        assertEquals(today.plusDays(1), summary.lowestOn)
    }

    @Test
    fun paydaysAreFutureIncomeOnTheChosenAccounts() {
        val events = listOf(
            event("Salary", 0, 380_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
            event("Salary", 17, 380_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
            event("Side job", 5, 20_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME, account = "other"),
            event("Rent", 10, 120_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL),
        )
        assertEquals(listOf(today.plusDays(17)), CashFlowPlan.paydays(events, setOf("chk"), today))
    }

    @Test
    fun theTimelineListsOnlyDaysMoneyMovesWithTheBalanceAfter() {
        val window = days(50_000, 49_000, 48_000, -73_000, -74_000, 305_000)
        val events = listOf(
            event("Rent", 3, 120_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL),
            event("Salary", 5, 380_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
            event("Gym", 3, 3_000, CashFlowDirection.OUTFLOW, ScheduledKind.SUBSCRIPTION),
            event("Elsewhere", 1, 1_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL, account = "other"),
            // Already paid: its transaction is counted, so the calendar item is not listed again.
            event("Netflix", 2, 1_549, CashFlowDirection.OUTFLOW, ScheduledKind.SUBSCRIPTION),
        )
        val paidNetflix = tx("Netflix", today.plusDays(2), -1_549, recurring = "Netflix")
        val futureDated = tx("Car repair", today.plusDays(4), -30_000)
        val todaysCoffee = tx("Coffee", today, -500)
        val timeline = CashFlowPlan.timeline(window, events, listOf(paidNetflix, futureDated, todaysCoffee), setOf("chk"), mapOf("chk" to "Checking"))
        assertEquals(listOf(today.plusDays(2), today.plusDays(3), today.plusDays(4), today.plusDays(5)), timeline.map { it.date })
        val rentDay = timeline[1]
        assertEquals(-73_000L, rentDay.endMinor)
        // Biggest outgoing last, income first.
        assertEquals(listOf("Gym", "Rent"), rentDay.items.map { it.name })
        assertEquals(-120_000L, rentDay.items.last().signedMinor)
        assertTrue(rentDay.items.all { it.scheduled && it.accountName == "Checking" })
        assertEquals("Car repair", timeline[2].items.single().name)
        assertTrue(!timeline[2].items.single().scheduled)
        assertEquals(380_000L, timeline[3].items.single().signedMinor)
        // The paid Netflix shows once, as the recorded transaction.
        assertEquals(1, timeline[0].items.size)
        assertTrue(!timeline[0].items.single().scheduled)
    }

    private fun tx(name: String, date: LocalDate, effect: Long, recurring: String? = null) = LedgerTransaction(
        id = "$name-$date", accountId = "chk", counterAccountId = null, linkId = null, amountMinor = kotlin.math.abs(effect),
        balanceEffectMinor = effect, currency = "USD", type = if (effect < 0) TransactionType.EXPENSE else TransactionType.INCOME,
        merchantRaw = name, merchantNormalized = name.lowercase(), merchantId = null, description = "", categoryId = null,
        occurredOn = date, postedOn = null, detectionSource = DetectionSource.MANUAL, confidencePercent = 100,
        recurringPaymentId = recurring, status = TransactionStatus.CONFIRMED, userConfirmed = true, note = "", fingerprint = "",
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
    )
}
