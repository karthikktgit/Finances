package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.forecast.CashFlowHistory
import com.ledgerline.app.finance.forecast.CashFlowPlan
import com.ledgerline.app.finance.forecast.DayBalance
import com.ledgerline.app.finance.forecast.WhatIf
import com.ledgerline.app.finance.forecast.WhatIfBuy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The cash-flow chart's real past and the "what if I buy this on that day" scenario. */
class CashFlowHistoryTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val checking = account("chk", 100_000)
    private val savings = account("sav", 500_000)

    @Test
    fun historyStartsAtTheFirstRecordAndNeverReachesToday() {
        val txs = listOf(tx("Coffee", today.minusDays(5), -500), tx("Lunch", today, -1_500))
        assertEquals(today.minusDays(6), CashFlowHistory.startOn(today, 30, listOf(checking), setOf("chk"), txs))
        assertEquals(today.minusDays(3), CashFlowHistory.startOn(today, 3, listOf(checking), setOf("chk"), txs))
        // Nothing recorded before today: no history to show.
        assertNull(CashFlowHistory.startOn(today, 30, listOf(checking), setOf("chk"), listOf(tx("Lunch", today, -1_500))))
        assertNull(CashFlowHistory.startOn(today, 0, listOf(checking), setOf("chk"), txs))
    }

    @Test
    fun eachPastDayEndsWithTheBalanceHomeWouldShowForThatDay() {
        val txs = listOf(
            tx("Pay", today.minusDays(4), 200_000, TransactionType.INCOME),
            tx("Rent", today.minusDays(3), -120_000, recurring = "rent"),
            tx("Groceries", today.minusDays(3), -8_000),
            tx("Shoes", today.minusDays(1), -9_000),
            tx("Moved", today.minusDays(2), 50_000, TransactionType.INCOME, account = "sav"),
            tx("Deleted", today.minusDays(2), -99_000, deleted = true),
            tx("Today", today, -700),
        )
        val days = CashFlowHistory.days(today.minusDays(5), today, listOf(checking, savings), setOf("chk"), txs)
        assertEquals(5, days.size)
        assertEquals(today.minusDays(1), days.last().date)
        days.forEach { day -> assertEquals(BalanceMath.balanceAsOf(checking, txs, day.date), day.endMinor) }
        val rentDay = days.single { it.date == today.minusDays(3) }
        assertEquals(120_000L, rentDay.billsMinor)
        assertEquals(8_000L, rentDay.spentMinor)
        assertEquals(200_000L, days.single { it.date == today.minusDays(4) }.inMinor)
        // The savings deposit is not in the checking line.
        assertEquals(0L, days.single { it.date == today.minusDays(2) }.inMinor)
        // Both accounts together add up.
        val both = CashFlowHistory.days(today.minusDays(5), today, listOf(checking, savings), setOf("chk", "sav"), txs)
        assertEquals(days.last().endMinor + BalanceMath.balanceAsOf(savings, txs, today.minusDays(1)), both.last().endMinor)
    }

    @Test
    fun onlySpendingWellAboveYourUsualDayIsCalledOut() {
        val txs = listOf(
            tx("a", today.minusDays(6), -1_000), tx("b", today.minusDays(5), -1_200), tx("c", today.minusDays(4), -900),
            tx("Shoes", today.minusDays(2), -12_000),
        )
        val days = CashFlowHistory.days(today.minusDays(7), today, listOf(checking), setOf("chk"), txs)
        assertEquals(setOf(today.minusDays(2)), CashFlowHistory.bigSpendDays(days))
        // Small days are never called out, even when they are double the usual.
        val small = CashFlowHistory.days(today.minusDays(7), today, listOf(checking), setOf("chk"), listOf(tx("a", today.minusDays(3), -300), tx("b", today.minusDays(2), -900)))
        assertTrue(CashFlowHistory.bigSpendDays(small).isEmpty())
    }

    @Test
    fun aPurchaseLowersEveryDayFromItsDateOn() {
        val base = days(60_000, 50_000, 40_000, 140_000)
        val scenario = WhatIf.apply(base, listOf(WhatIfBuy(45_000, today.plusDays(1))))
        assertEquals(listOf(60_000L, 5_000L, -5_000L, 95_000L), scenario.map { it.endMinor })
        assertTrue(scenario[2].negative)
        assertEquals(base, WhatIf.apply(base, emptyList()))
    }

    @Test
    fun theVerdictSaysWhatChangesBeforeYouBuy() {
        val paydays = listOf(today.plusDays(3))
        val base = CashFlowPlan.summarize(days(60_000, 50_000, 40_000, 140_000), paydays, bufferMinor = 10_000)!!
        val buys = listOf(WhatIfBuy(45_000, today.plusDays(1)))
        val scenario = CashFlowPlan.summarize(WhatIf.apply(days(60_000, 50_000, 40_000, 140_000), buys), paydays, 10_000)!!
        val result = WhatIf.result(base, scenario, buys, paceMinor = 3_000, safeMinor = 20_000)
        assertTrue(result.newlyShort)
        assertEquals(today.plusDays(2), result.runsOutOn)
        assertFalse(result.newlyIntoBuffer)
        assertEquals(45_000L, result.lowestDropMinor)
        assertEquals(-5_000L, result.beforePaydayMinor)
        assertEquals(15L, result.daysOfSpending)
        assertTrue(result.overSafe)
        // A small buy only dips into the buffer.
        val small = listOf(WhatIfBuy(35_000, today.plusDays(1)))
        val dip = WhatIf.result(base, CashFlowPlan.summarize(WhatIf.apply(days(60_000, 50_000, 40_000, 140_000), small), paydays, 10_000)!!, small, 0, 50_000)
        assertFalse(dip.newlyShort)
        assertTrue(dip.newlyIntoBuffer)
        assertNull(dip.daysOfSpending)
        assertFalse(dip.overSafe)
    }

    private fun days(vararg ends: Long) = ends.mapIndexed { i, end -> DayBalance(today.plusDays(i.toLong()), end, 0, 0, end < 0, false) }

    private fun account(id: String, opening: Long) = Account(
        id, id, "", AccountType.CHECKING, null, opening, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0,
    )

    private fun tx(
        name: String,
        date: LocalDate,
        effect: Long,
        type: TransactionType = if (effect < 0) TransactionType.EXPENSE else TransactionType.INCOME,
        recurring: String? = null,
        account: String = "chk",
        deleted: Boolean = false,
    ) = LedgerTransaction(
        id = "$name-$date", accountId = account, counterAccountId = null, linkId = null, amountMinor = kotlin.math.abs(effect),
        balanceEffectMinor = effect, currency = "USD", type = type, merchantRaw = name, merchantNormalized = name.lowercase(),
        merchantId = null, description = "", categoryId = null, occurredOn = date, postedOn = null, detectionSource = DetectionSource.MANUAL,
        confidencePercent = 100, recurringPaymentId = recurring, status = TransactionStatus.CONFIRMED, userConfirmed = true, note = "",
        fingerprint = "", createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = if (deleted) 1 else null,
    )
}
