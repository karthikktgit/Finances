package com.ledgerline.app.finance

import com.ledgerline.app.data.local.database.Migrations
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.GoalContribution
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SavingsGoal
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.goals.GoalMath
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.safetospend.SafeToSpendEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Savings goals: progress, the monthly plan, when it is reached, and what Safe to spend holds back for it. */
class SavingsGoalTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val cycle = PayPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19))
    private val checking = account("chk", AccountType.CHECKING, opening = 300_000, spending = true)
    private val savings = account("sav", AccountType.SAVINGS, opening = 50_000, spending = false)

    private fun goal(
        target: Long = 200_000,
        date: LocalDate? = null,
        account: String? = null,
        byBalance: Boolean = false,
        monthly: Long? = null,
    ) = SavingsGoal("room", "Room deposit", target, "USD", date, account, byBalance, monthly, LocalDate.of(2026, 9, 1))

    private fun added(amount: Long, on: LocalDate, goal: String = "room") = GoalContribution("c$amount$on", goal, amount, on, "")

    @Test
    fun monthsCountAPartMonthAsAMonth() {
        assertEquals(1L, GoalMath.monthsUntil(today, LocalDate.of(2026, 11, 20)))
        assertEquals(1L, GoalMath.monthsUntil(today, LocalDate.of(2026, 12, 3)))
        assertEquals(2L, GoalMath.monthsUntil(today, LocalDate.of(2026, 12, 4)))
        assertEquals(5L, GoalMath.monthsUntil(today, LocalDate.of(2027, 3, 31)))
        assertEquals(0L, GoalMath.monthsUntil(today, today))
        assertEquals(0L, GoalMath.monthsUntil(today, today.minusDays(3)))
    }

    @Test
    fun aDateWorksOutTheMonthlyAmount() {
        // $2,000 by Mar 31: $500 saved, $200 of it this cycle. At the start of the cycle $1,700 was left over the 6
        // paydays before the date (Oct 20 to Mar 20): $283.34 a cycle, so $83.34 is still to put aside this cycle.
        val progress = GoalMath.progress(
            goal(date = LocalDate.of(2027, 3, 31)),
            listOf(added(30_000, LocalDate.of(2026, 10, 1)), added(20_000, LocalDate.of(2026, 10, 25))),
            listOf(checking), emptyList(), today, cycle,
        )
        assertEquals(50_000L, progress.savedMinor)
        assertEquals(150_000L, progress.remainingMinor)
        assertEquals(0.25f, progress.fraction, 0.0001f)
        assertEquals(28_334L, progress.neededPerMonthMinor)
        assertEquals(28_334L, progress.planMonthlyMinor)
        // The sixth payday from Oct 20: Mar 20, before the Mar 31 date.
        assertEquals(LocalDate.of(2027, 3, 20), progress.finishOn)
        assertEquals(20_000L, progress.savedThisCycleMinor)
        assertEquals(8_334L, progress.setAsideNowMinor)
        assertFalse(progress.reached)
    }

    @Test
    fun aMonthlyAmountWorksOutTheFinishDateAndWhetherItIsOnTime() {
        val slow = GoalMath.progress(goal(date = LocalDate.of(2027, 3, 31), monthly = 20_000), emptyList(), emptyList(), emptyList(), today, cycle)
        // $2,000 at $200 a payday takes 10 paydays from Oct 20: Jul 20, well after March.
        assertEquals(LocalDate.of(2027, 7, 20), slow.finishOn)
        assertEquals(false, slow.onTrack)
        // $2,000 over the 6 paydays before Mar 31.
        assertEquals(33_334L, slow.neededPerMonthMinor)
        val fast = GoalMath.progress(goal(date = LocalDate.of(2027, 3, 31), monthly = 50_000), emptyList(), emptyList(), emptyList(), today, cycle)
        assertEquals(true, fast.onTrack)
        // No date: just a finish date, nothing to be on time for.
        val open = GoalMath.progress(goal(monthly = 25_000), emptyList(), emptyList(), emptyList(), today, cycle)
        // $2,000 at $250: the eighth payday from Oct 20.
        assertEquals(LocalDate.of(2027, 5, 20), open.finishOn)
        assertNull(open.onTrack)
        // No date and no plan: progress only.
        val bare = GoalMath.progress(goal(), emptyList(), emptyList(), emptyList(), today, cycle)
        assertNull(bare.planMonthlyMinor)
        assertNull(bare.finishOn)
        assertEquals(0L, bare.setAsideNowMinor)
    }

    @Test
    fun anAccountKeptForTheGoalIsItsProgress() {
        val moved = transfer("sav", 30_000, LocalDate.of(2026, 10, 28))
        val progress = GoalMath.progress(goal(account = "sav", byBalance = true, monthly = 40_000), emptyList(), listOf(checking, savings), listOf(moved), today, cycle)
        assertEquals(80_000L, progress.savedMinor)
        assertEquals(30_000L, progress.savedThisCycleMinor)
        assertEquals(10_000L, progress.setAsideNowMinor)
        // If that account is deleted, the goal says so instead of showing zero as if nothing was saved.
        val gone = GoalMath.progress(goal(account = "sav", byBalance = true), emptyList(), listOf(checking, savings.copy(deletedAtEpochMillis = 1)), emptyList(), today, cycle)
        assertTrue(gone.accountMissing)
    }

    @Test
    fun reachingTheTargetStopsThePlan() {
        val done = GoalMath.progress(goal(target = 50_000, monthly = 20_000), listOf(added(60_000, LocalDate.of(2026, 10, 1))), emptyList(), emptyList(), today, cycle)
        assertTrue(done.reached)
        assertEquals(1f, done.fraction)
        assertEquals(0L, done.remainingMinor)
        assertEquals(0L, done.setAsideNowMinor)
        assertNull(done.planMonthlyMinor)
        // The last month's plan is only what is left, not the whole monthly amount.
        val almost = GoalMath.progress(goal(target = 50_000, monthly = 20_000), listOf(added(45_000, LocalDate.of(2026, 10, 1))), emptyList(), emptyList(), today, cycle)
        assertEquals(5_000L, almost.setAsideNowMinor)
        // Taking money back out lowers the progress, never below zero.
        val withdrawn = GoalMath.progress(goal(), listOf(added(10_000, LocalDate.of(2026, 10, 1)), added(-30_000, LocalDate.of(2026, 10, 2))), emptyList(), emptyList(), today, cycle)
        assertEquals(0L, withdrawn.savedMinor)
    }

    @Test
    fun safeToSpendHoldsBackThePlanAndMoneyKeptInChecking() {
        // In checking: the $500 already saved is spoken for, plus $300 still to save this cycle.
        val inChecking = GoalMath.progress(goal(account = "chk", monthly = 30_000), listOf(added(50_000, LocalDate.of(2026, 10, 1))), listOf(checking), emptyList(), today, cycle)
        assertEquals(80_000L, GoalMath.heldBack(listOf(inChecking), listOf(checking)))
        // In a separate savings account: the saved money has already left the spending pool; only the plan is held.
        val inSavings = GoalMath.progress(goal(account = "sav", byBalance = true, monthly = 30_000), emptyList(), listOf(checking, savings), emptyList(), today, cycle)
        assertEquals(30_000L, GoalMath.heldBack(listOf(inSavings), listOf(checking, savings)))
        // Both together, and the line shows up in Safe to spend, which still adds up.
        val held = GoalMath.heldBack(listOf(inChecking, inSavings), listOf(checking, savings))
        val result = SafeToSpendEngine.calculate(SafeToSpendEngine.Input(300_000, 20_000, 10_000, 0, 0, 0, goalSetAsideMinor = held))
        assertEquals(300_000L - 20_000 - 10_000 - 110_000, result.amountMinor)
        assertEquals(-110_000L, result.lines.single { it.label == "Savings goals" }.minor)
        assertEquals(result.amountMinor, result.lines.sumOf { it.minor })
    }

    @Test
    fun savingDuringTheMonthCountsTowardItInsteadOfShrinkingIt() {
        // $1,000 over the 4 paydays before Feb 3 (Oct 20 to Jan 20) is $250 a cycle. After putting $100 aside this
        // cycle, $150 is still to go.
        val before = GoalMath.progress(goal(target = 100_000, date = LocalDate.of(2027, 2, 3)), emptyList(), emptyList(), emptyList(), today, cycle)
        assertEquals(25_000L, before.setAsideNowMinor)
        val after = GoalMath.progress(goal(target = 100_000, date = LocalDate.of(2027, 2, 3)), listOf(added(10_000, today)), emptyList(), emptyList(), today, cycle)
        assertEquals(25_000L, after.planMonthlyMinor)
        assertEquals(15_000L, after.setAsideNowMinor)
    }

    @Test
    fun theMigrationOnlyAddsTheGoalTables() {
        assertEquals(9, Migrations.MIGRATION_9_10.startVersion)
        assertEquals(10, Migrations.MIGRATION_9_10.endVersion)
        assertEquals(Migrations.MIGRATION_9_10, Migrations.ALL[8])
        assertEquals(10, Migrations.MIGRATION_10_11.startVersion)
        assertEquals(11, Migrations.MIGRATION_10_11.endVersion)
        assertEquals(Migrations.MIGRATION_10_11, Migrations.ALL[9])
    }

    private fun account(id: String, type: AccountType, opening: Long, spending: Boolean) = Account(
        id, id, "", type, null, opening, null, "USD", true, true, spending, SyncStatus.MANUAL, null, null, 0,
    )

    private fun transfer(to: String, amount: Long, on: LocalDate) = LedgerTransaction(
        id = "t$to$on", accountId = to, counterAccountId = "chk", linkId = "l", amountMinor = amount, balanceEffectMinor = amount,
        currency = "USD", type = TransactionType.TRANSFER, merchantRaw = "", merchantNormalized = "", merchantId = null, description = "",
        categoryId = null, occurredOn = on, postedOn = null, detectionSource = DetectionSource.MANUAL, confidencePercent = 100,
        recurringPaymentId = null, status = TransactionStatus.CONFIRMED, userConfirmed = true, note = "", fingerprint = "",
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
    )
}
