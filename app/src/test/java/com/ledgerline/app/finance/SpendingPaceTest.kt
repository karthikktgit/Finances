package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.forecast.ImpulseCheck
import com.ledgerline.app.finance.forecast.PaceVerdict
import com.ledgerline.app.finance.forecast.SpendingPace
import com.ledgerline.app.finance.period.PayPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Today's limit, the spending forecast, and the "thinking of buying something?" check. */
class SpendingPaceTest {
    private val cycle = PayPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19))
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun todaysLimitIsFixedAtTheStartOfTheDay() {
        // $2,147.50 left now after spending $50 today, 18 days to go including today.
        val limit = SpendingPace.dailyLimit(safeNowMinor = 214_750, spentTodayMinor = 5_000, today = today, cycleEnd = cycle.end)
        assertEquals(18L, limit.daysLeft)
        assertEquals(12_208L, limit.limitMinor)
        assertEquals(7_208L, limit.leftTodayMinor)
        assertFalse(limit.over)
        // Spending more today does not move today's limit; it only shrinks what is left of it.
        val more = SpendingPace.dailyLimit(safeNowMinor = 194_750, spentTodayMinor = 25_000, today = today, cycleEnd = cycle.end)
        assertEquals(limit.limitMinor, more.limitMinor)
        assertTrue(more.over)
        assertEquals(-12_792L, more.leftTodayMinor)
        // Going over makes tomorrow's share smaller than today's.
        assertTrue(more.tomorrowLimitMinor!! < more.limitMinor)
        assertEquals(11_455L, more.tomorrowLimitMinor)
    }

    @Test
    fun theLastDayHasNoTomorrowAndNothingLeftMeansNoLimit() {
        assertNull(SpendingPace.dailyLimit(10_000, 0, cycle.end, cycle.end).tomorrowLimitMinor)
        assertEquals(10_000L, SpendingPace.dailyLimit(10_000, 0, cycle.end, cycle.end).limitMinor)
        val broke = SpendingPace.dailyLimit(-5_000, 0, today, cycle.end)
        assertEquals(0L, broke.limitMinor)
        assertEquals(0L, broke.tomorrowLimitMinor)
    }

    @Test
    fun aFastPaceShowsTheDayTheMoneyRunsOut() {
        // 14 days in, $1,400 spent: $100 a day. $1,000 left lasts 10 more days, to Nov 12, a week before payday.
        val pace = SpendingPace.forecast(spentMinor = 140_000, safeNowMinor = 100_000, today = today, period = cycle)
        assertEquals(14L, pace.daysElapsed)
        assertEquals(17L, pace.daysLeft)
        assertEquals(10_000L, pace.dailyAverageMinor)
        assertEquals(-70_000L, pace.endMinor)
        assertEquals(LocalDate.of(2026, 11, 12), pace.runsOutOn)
        assertEquals(PaceVerdict.RUNS_OUT, pace.verdict)
    }

    @Test
    fun aSlowPaceFinishesWithMoneyLeft() {
        val pace = SpendingPace.forecast(spentMinor = 28_000, safeNowMinor = 100_000, today = today, period = cycle)
        assertEquals(2_000L, pace.dailyAverageMinor)
        assertEquals(66_000L, pace.endMinor)
        assertNull(pace.runsOutOn)
        assertEquals(PaceVerdict.ON_TRACK, pace.verdict)
    }

    @Test
    fun justEnoughIsFlaggedAsTight() {
        // $50 a day for 17 more days is $850 of $900: $50 left, one day's spending.
        val pace = SpendingPace.forecast(spentMinor = 70_000, safeNowMinor = 90_000, today = today, period = cycle)
        assertEquals(5_000L, pace.endMinor)
        assertEquals(PaceVerdict.TIGHT, pace.verdict)
    }

    @Test
    fun noSpendingAndAlreadyEmptyAreHandled() {
        assertEquals(PaceVerdict.NO_SPENDING_YET, SpendingPace.forecast(0, 50_000, today, cycle).verdict)
        val empty = SpendingPace.forecast(10_000, -2_000, today, cycle)
        assertEquals(today, empty.runsOutOn)
        assertEquals(PaceVerdict.RUNS_OUT, empty.verdict)
        // On the first day of the cycle the average is today's spending.
        assertEquals(1L, SpendingPace.forecast(3_000, 50_000, cycle.start, cycle).daysElapsed)
        // Past the end of the cycle nothing is left to forecast.
        assertEquals(0L, SpendingPace.forecast(3_000, 50_000, cycle.end, cycle).daysLeft)
    }

    @Test
    fun everydaySpendingLeavesOutBillsAndSubtractsRefunds() {
        val rows = listOf(
            tx(TransactionType.EXPENSE, 4_000),
            tx(TransactionType.EXPENSE, 1_000),
            tx(TransactionType.EXPENSE, 120_000, bill = "rent"),
            tx(TransactionType.REFUND, 500),
            tx(TransactionType.INCOME, 300_000),
            tx(TransactionType.EXPENSE, 9_000, status = TransactionStatus.DETECTED),
        )
        assertEquals(4_500L, SpendingPace.everyday(rows))
        assertEquals(0L, SpendingPace.everyday(listOf(tx(TransactionType.REFUND, 500))))
    }

    @Test
    fun leftOnADayMatchesTheRunOutDate() {
        // Same numbers as the run-out test: $1,000 at $100 a day runs out on Nov 12.
        val pace = SpendingPace.forecast(spentMinor = 140_000, safeNowMinor = 100_000, today = today, period = cycle)
        assertEquals(0L, SpendingPace.leftOn(100_000, pace.dailyAverageMinor, today, pace.runsOutOn!!))
        assertTrue(SpendingPace.leftOn(100_000, pace.dailyAverageMinor, today, pace.runsOutOn!!.plusDays(1)) < 0)
        assertEquals(100_000L, SpendingPace.leftOn(100_000, pace.dailyAverageMinor, today, today))
        assertEquals(pace.endMinor, SpendingPace.leftOn(100_000, pace.dailyAverageMinor, today, cycle.end))
    }

    @Test
    fun theCurveDropsToZeroAtTheLimitAndFasterAtYourPace() {
        val (atPace, atLimit) = SpendingPace.curve(safeNowMinor = 100_000, averageMinor = 10_000, daysLeft = 17)
        assertEquals(18, atPace.size)
        assertEquals(100_000L, atPace.first())
        assertEquals(-70_000L, atPace.last())
        assertEquals(0L, atLimit.last())
        assertTrue(atLimit.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun aPurchaseIsShownInDaysOfYourLimit() {
        // $1,000 for 18 days is $55.55 a day. A $120 jacket is just over two days of that.
        val check = ImpulseCheck.check(priceMinor = 12_000, safeNowMinor = 100_000, today = today, cycleEnd = cycle.end)
        assertEquals(5_555L, check.limitBeforeMinor)
        assertEquals(4_888L, check.limitAfterMinor)
        assertEquals(2.16, check.daysOfLimit, 0.01)
        assertFalse(check.unaffordable)
        val tooMuch = ImpulseCheck.check(priceMinor = 150_000, safeNowMinor = 100_000, today = today, cycleEnd = cycle.end)
        assertTrue(tooMuch.unaffordable)
        assertEquals(0L, tooMuch.limitAfterMinor)
        assertEquals(-50_000L, tooMuch.safeAfterMinor)
    }

    private var next = 0

    private fun tx(type: TransactionType, amount: Long, bill: String? = null, status: TransactionStatus = TransactionStatus.CONFIRMED) =
        LedgerTransaction(
            id = "t${next++}", accountId = "chk", counterAccountId = null, linkId = null, amountMinor = amount,
            balanceEffectMinor = BalanceMath.effectFor(type, amount), currency = "USD", type = type, merchantRaw = "", merchantNormalized = "",
            merchantId = null, description = "", categoryId = null, occurredOn = today, postedOn = null,
            detectionSource = DetectionSource.MANUAL, confidencePercent = 100, recurringPaymentId = bill, status = status,
            userConfirmed = true, note = "", fingerprint = "", createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
        )
}
