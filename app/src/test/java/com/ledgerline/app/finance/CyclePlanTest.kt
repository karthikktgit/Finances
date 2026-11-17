package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.CycleAmount
import com.ledgerline.app.domain.model.CycleReview
import com.ledgerline.app.domain.model.CycleType
import com.ledgerline.app.domain.model.GoalContribution
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.SavingsGoal
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.cycle.CycleAmounts
import com.ledgerline.app.finance.cycle.CycleBalances
import com.ledgerline.app.finance.cycle.CycleSetup
import com.ledgerline.app.finance.dashboard.DashboardAssembler
import com.ledgerline.app.finance.goals.GoalMath
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.worker.CycleReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A cycle set up with its own amounts: they apply to that cycle only, everywhere, and the usual amounts stay. */
class CyclePlanTest {
    private val today = LocalDate.of(2026, 11, 9)
    private val settings = UserSettings("USD", 0, 30, true, CycleType.MONTHLY, LocalDate.of(2026, 1, 20), 20, 5)
    private val cycle = CycleSchedule.containing(today, settings)
    private val next = CycleSchedule.containing(cycle.end.plusDays(1), settings)

    private val groceries = Budget("g", "Groceries", null, null, BudgetPeriod.MONTHLY, 30_000, "USD", true, true)
    private val rent = Budget("r", "Rent", null, null, BudgetPeriod.MONTHLY, 34_000, "USD", true, true)
    private val checking = Account("chk", "Checking", "", AccountType.CHECKING, null, 500_000, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0)

    private fun bill(id: String, amount: Long, day: Int, direction: CashFlowDirection = CashFlowDirection.OUTFLOW) = RecurringPayment(
        id = id, name = id, accountId = "chk", categoryId = null, direction = direction,
        kind = if (direction == CashFlowDirection.INFLOW) ScheduledKind.INCOME else ScheduledKind.BILL, amountMinor = amount,
        currency = "USD", frequency = RecurrenceFrequency.MONTHLY, intervalDays = null, dayOfMonth = day, dayOfWeek = null,
        startOn = LocalDate.of(2026, 1, day), endOn = null, priority = PaymentPriority.MANDATORY, incomeConfidence = IncomeConfidence.NONE,
        active = true, note = "",
    )

    @Test
    fun ownAmountAppliesToItsCycleOnly() {
        val amounts = listOf(CycleAmount(cycle.start, "budget:g", 25_000))
        assertEquals(25_000L, CycleAmounts.budgetsFor(listOf(groceries), amounts, cycle.start).single().amountMinor)
        // Next cycle goes back to the usual amount.
        assertEquals(30_000L, CycleAmounts.budgetsFor(listOf(groceries), amounts, next.start).single().amountMinor)
        // And the budget itself is never changed.
        assertEquals(30_000L, groceries.amountMinor)
    }

    @Test
    fun zeroTurnsAnItemOffForThatCycle() {
        val amounts = listOf(CycleAmount(cycle.start, "budget:r", 0))
        val shown = CycleAmounts.budgetsFor(listOf(groceries, rent), amounts, cycle.start)
        assertFalse(shown.first { it.id == "r" }.active)
        assertTrue(shown.first { it.id == "g" }.active)
    }

    @Test
    fun safeToSpendAndHomeUseTheCyclesOwnAmounts() {
        val usual = DashboardAssembler.assemble(today, settings, listOf(checking), emptyList(), emptyList(), emptyList(), listOf(groceries))
        val own = DashboardAssembler.assemble(
            today, settings, listOf(checking), emptyList(), emptyList(), emptyList(), listOf(groceries),
            cycleAmounts = listOf(CycleAmount(cycle.start, "budget:g", 10_000)),
        )
        assertEquals(30_000L, usual.budgets.single().budget.amountMinor)
        assertEquals(10_000L, own.budgets.single().budget.amountMinor)
        // Planning less for groceries this cycle leaves more safe to spend.
        assertTrue(own.safeToSpend.amountMinor > usual.safeToSpend.amountMinor)
    }

    @Test
    fun goalUsesTheCyclesOwnSaving() {
        val goal = SavingsGoal("room", "Room", 200_000, "USD", null, null, false, 20_000, LocalDate.of(2026, 9, 1))
        val period = PayPeriod(cycle.start, cycle.end)
        val usual = GoalMath.progress(goal, emptyList<GoalContribution>(), listOf(checking), emptyList(), today, period)
        val own = GoalMath.progress(goal, emptyList(), listOf(checking), emptyList(), today, period, cyclePlanMinor = 5_000)
        val off = GoalMath.progress(goal, emptyList(), listOf(checking), emptyList(), today, period, cyclePlanMinor = 0)
        assertEquals(20_000L, usual.setAsideNowMinor)
        assertEquals(5_000L, own.setAsideNowMinor)
        assertEquals(0L, off.setAsideNowMinor)
        assertNull(off.planMonthlyMinor)
    }

    @Test
    fun billsListSkippedDatesAndOwnAmounts() {
        val phone = bill("phone", 6_000, 25)
        val pay = bill("pay", 400_000, 1, CashFlowDirection.INFLOW)
        val gym = bill("gym", 4_000, 28)
        val adjustments = listOf(
            OccurrenceAdjustment("phone", LocalDate.of(2026, 10, 25), OccurrenceState.SCHEDULED, 7_500, null, null),
            OccurrenceAdjustment("gym", LocalDate.of(2026, 10, 28), OccurrenceState.SKIPPED, null, null, null),
        )
        val rows = CycleSetup.bills(listOf(phone, pay, gym), adjustments, emptySet(), PayPeriod(cycle.start, cycle.end), today)
        val phoneRow = rows.first { it.recurringId == "phone" }
        assertEquals(7_500L, phoneRow.amountMinor)
        assertEquals(6_000L, phoneRow.usualMinor)
        assertTrue(phoneRow.own)
        // A skipped date still shows, so it can be switched back on.
        assertTrue(rows.first { it.recurringId == "gym" }.skipped)
        val totals = CycleSetup.totals(rows, emptyList(), emptyList())
        assertEquals(400_000L, totals.inMinor)
        assertEquals(7_500L, totals.billsMinor)
    }

    @Test
    fun leftoversAreUnpaidBillsFromBeforeTheCycle() {
        val phone = bill("phone", 6_000, 25)
        val before = CycleSetup.leftovers(listOf(phone), emptyList(), setOf("phone" to LocalDate.of(2026, 9, 25)), PayPeriod(cycle.start, cycle.end), today)
        // Three months back from Oct 20: July 25 and August 25 are unpaid, September 25 was paid, October 25 is
        // inside this cycle, and June 25 is too old to carry.
        assertEquals(listOf(LocalDate.of(2026, 7, 25), LocalDate.of(2026, 8, 25)), before.map { it.date })
    }

    @Test
    fun copyingLastCycleMatchesItExactly() {
        val previous = CycleSchedule.containing(cycle.start.minusDays(1), settings).start
        val amounts = listOf(CycleAmount(previous, "budget:g", 20_000), CycleAmount(cycle.start, "budget:r", 0))
        val copy = CycleSetup.copyAmounts(amounts, previous, cycle.start, listOf("budget:g", "budget:r"))
        assertEquals(20_000L, copy["budget:g"])
        // Rent had no own amount last cycle, so it goes back to usual here too.
        assertNull(copy["budget:r"])
    }

    @Test
    fun spendingRowsShowUsualAndOwn() {
        val rows = CycleSetup.spending(listOf(groceries, rent), listOf(CycleAmount(cycle.start, "budget:r", 0)), PayPeriod(cycle.start, cycle.end), "USD")
        assertEquals(30_000L, rows.first { it.name == "Groceries" }.amountMinor)
        assertTrue(rows.first { it.name == "Rent" }.off)
        assertEquals(30_000L, CycleSetup.totals(emptyList(), rows, emptyList()).spendingMinor)
    }

    @Test
    fun stepsCountStatementsBalancesAndFinish() {
        val ids = setOf("a", "b")
        assertEquals(0, CycleSetup.stepsDone(CycleReview(cycle.start, setOf("a")), ids))
        assertEquals(1, CycleSetup.stepsDone(CycleReview(cycle.start, ids), ids))
        assertEquals(3, CycleSetup.stepsDone(CycleReview(cycle.start, ids, true, 1L), ids))
    }

    @Test
    fun theMigrationOnlyAddsTheTwoNewTables() {
        val migration = com.ledgerline.app.data.local.database.Migrations.MIGRATION_12_13
        assertEquals(12, migration.startVersion)
        assertEquals(13, migration.endVersion)
        val all = com.ledgerline.app.data.local.database.Migrations.ALL
        assertEquals(com.ledgerline.app.data.local.database.Migrations.MIGRATION_15_16, all.last())
        assertEquals(com.ledgerline.app.data.local.database.Migrations.MIGRATION_14_15, all[all.size - 2])
        assertEquals(com.ledgerline.app.data.local.database.Migrations.MIGRATION_13_14, all[all.size - 3])
        assertEquals(migration, all[all.size - 4])
    }

    @Test
    fun aSpendingItemRunsOnlyBetweenItsStartAndEnd() {
        val end = com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.AFTER_COUNT, 2)
        val last = CycleAmounts.lastCycleStart(cycle.start, end, settings)
        // Two cycles from Oct 20: Oct 20 and Nov 20.
        assertEquals(next.start, last)
        val item = groceries.copy(startOn = cycle.start, endOn = last, maxCycles = 2)
        val previous = CycleSchedule.containing(cycle.start.minusDays(1), settings).start
        val after = CycleSchedule.containing(next.end.plusDays(1), settings).start
        assertFalse(CycleAmounts.budgetsFor(listOf(item), emptyList(), previous).single().active)
        assertTrue(CycleAmounts.budgetsFor(listOf(item), emptyList(), cycle.start).single().active)
        assertTrue(CycleAmounts.budgetsFor(listOf(item), emptyList(), next.start).single().active)
        assertFalse(CycleAmounts.budgetsFor(listOf(item), emptyList(), after).single().active)
        // Ends on a date: the cycle holding that date is the last one.
        val onDate = com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.ON_DATE, on = LocalDate.of(2026, 12, 5))
        assertEquals(next.start, CycleAmounts.lastCycleStart(cycle.start, onDate, settings))
        assertNull(CycleAmounts.lastCycleStart(cycle.start, com.ledgerline.app.finance.schedule.ScheduleEnd(), settings))
    }

    @Test
    fun freshBalancesStartTheCycleFromWhatYouEnterAndLeaveEarlierCyclesAlone() {
        fun tx(id: String, on: LocalDate, effect: Long, type: com.ledgerline.app.domain.model.TransactionType, name: String = id) = com.ledgerline.app.domain.model.LedgerTransaction(
            id = id, accountId = "chk", counterAccountId = null, linkId = null, amountMinor = kotlin.math.abs(effect), balanceEffectMinor = effect,
            currency = "USD", type = type, merchantRaw = name, merchantNormalized = name, merchantId = null, description = "", categoryId = null,
            occurredOn = on, postedOn = null, detectionSource = com.ledgerline.app.domain.model.DetectionSource.MANUAL, confidencePercent = 100,
            recurringPaymentId = null, status = com.ledgerline.app.domain.model.TransactionStatus.CONFIRMED, userConfirmed = true, note = "",
            fingerprint = "", createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
        )
        val account = checking.copy(openingBalanceMinor = 100_000)
        val before = tx("rent", LocalDate.of(2026, 10, 1), -150_000, com.ledgerline.app.domain.model.TransactionType.EXPENSE)
        val during = tx("food", LocalDate.of(2026, 10, 25), -5_000, com.ledgerline.app.domain.model.TransactionType.EXPENSE)
        val txs = listOf(before, during)
        // Without a reset the cycle starts at 1,000 − 1,500 = −500.
        assertEquals(-50_000L, CycleBalances.carriedMinor(account, txs, cycle.start))
        // Start fresh at 2,000: a 2,500 correction on the cycle's first day.
        val delta = CycleBalances.correctionMinor(account, txs, cycle.start, 200_000)
        assertEquals(250_000L, delta)
        val correction = tx("c", cycle.start, delta, com.ledgerline.app.domain.model.TransactionType.ADJUSTMENT, CycleBalances.NAME)
        val withReset = txs + correction
        assertTrue(CycleBalances.isStartCorrection(correction, cycle.start))
        assertEquals(200_000L, CycleBalances.startMinor(account, withReset, cycle.start))
        // Today it is the fresh start less this cycle's spending; the cycle before still ends at −500.
        assertEquals(195_000L, com.ledgerline.app.finance.balance.BalanceMath.currentBalanceMinor(account, withReset))
        assertEquals(-50_000L, com.ledgerline.app.finance.balance.BalanceMath.currentBalanceMinor(account, withReset.filter { it.occurredOn.isBefore(cycle.start) }))
        // A card's owed amount is typed as a positive number.
        val card = checking.copy(type = AccountType.CREDIT_CARD)
        assertEquals(-20_000L, CycleBalances.signed(card, 20_000))
    }

    @Test
    fun goalsShowOnTheCalendar() {
        val goal = SavingsGoal("house", "Moving", 180_000, "USD", LocalDate.of(2027, 1, 5), null, false, 30_000, LocalDate.of(2026, 9, 1))
        val progress = GoalMath.progressAll(listOf(goal), listOf(GoalContribution("c1", "house", 10_000, LocalDate.of(2026, 11, 3), "")), listOf(checking), emptyList(), today, PayPeriod(cycle.start, cycle.end))
        val days = com.ledgerline.app.finance.calendar.GoalCalendar.days(
            progress,
            listOf(GoalContribution("c1", "house", 10_000, LocalDate.of(2026, 11, 3), "")),
            settings,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2027, 1, 31),
        ) { start, id -> if (start == LocalDate.of(2026, 12, 20) && id == "house") 5_000L else null }
        // Each cycle start before the goal date asks for the monthly saving; December has its own amount.
        assertEquals(30_000L, days.getValue(LocalDate.of(2026, 10, 20)).single().amountMinor)
        assertEquals(30_000L, days.getValue(LocalDate.of(2026, 11, 20)).single().amountMinor)
        assertEquals(5_000L, days.getValue(LocalDate.of(2026, 12, 20)).single().amountMinor)
        // Not after the goal date.
        assertFalse(days.containsKey(LocalDate.of(2027, 1, 20)))
        // Money saved on its day, and the goal date with what is still to go.
        assertEquals(com.ledgerline.app.finance.calendar.GoalDayKind.SAVED, days.getValue(LocalDate.of(2026, 11, 3)).single().kind)
        val target = days.getValue(LocalDate.of(2027, 1, 5)).single()
        assertEquals(com.ledgerline.app.finance.calendar.GoalDayKind.TARGET, target.kind)
        assertEquals(170_000L, target.amountMinor)
    }

    @Test
    fun goalTimelineShowsEachCycleAndTheRestOfThePlan() {
        // $1,800 by Jan 5, made in September; $200 saved in October's cycle (Sep 20 – Oct 19) and $100 this cycle.
        val goal = SavingsGoal("house", "Moving", 180_000, "USD", LocalDate.of(2027, 1, 5), null, false, null, LocalDate.of(2026, 9, 25))
        val saved = listOf(
            GoalContribution("a", "house", 20_000, LocalDate.of(2026, 10, 2), ""),
            GoalContribution("b", "house", 10_000, LocalDate.of(2026, 11, 1), ""),
        )
        val period = PayPeriod(cycle.start, cycle.end)
        val progress = GoalMath.progress(goal, saved, listOf(checking), emptyList(), today, period)
        val t = com.ledgerline.app.finance.goals.GoalTimeline.build(progress, saved, listOf(checking), emptyList(), today, settings)
        assertEquals(20_000L, t.lastCycleSavedMinor)
        assertEquals(10_000L, t.thisCycle!!.savedMinor)
        // 1,600 left at the start of this cycle over the 3 cycles to Jan 5 (Oct 20, Nov 20, Dec 20): about 533 each.
        assertEquals(53_334L, t.thisCycle!!.plannedMinor)
        assertEquals(43_334L, t.stillThisCycleMinor)
        val ahead = t.rows.filter { it.whenIs == com.ledgerline.app.finance.goals.GoalCycleWhen.AHEAD }
        // Nov 20 and Dec 20 cycles carry the other 1,000; nothing is planned after the goal date.
        assertEquals(listOf(next.start, LocalDate.of(2026, 12, 20)), ahead.map { it.cycle.start })
        assertEquals(150_000L - 43_334L, ahead.sumOf { it.plannedMinor ?: 0 })
        assertEquals(180_000L, ahead.last().totalMinor)
    }

    @Test
    fun aReachedGoalStaysWhileHeldAndLeavesAfterTheCycleItIsSpent() {
        val goal = SavingsGoal("trip", "Trip", 50_000, "USD", LocalDate.of(2026, 11, 1), null, false, null, LocalDate.of(2026, 9, 1))
        val saved = GoalContribution("a", "trip", 50_000, LocalDate.of(2026, 10, 25), "")
        val thisCycle = PayPeriod(cycle.start, cycle.end)
        val nextCycle = PayPeriod(next.start, next.end)
        val later = next.start.plusDays(2)
        fun shows(entries: List<GoalContribution>, g: SavingsGoal, on: LocalDate, period: PayPeriod) =
            com.ledgerline.app.finance.goals.GoalTimeline.showsIn(GoalMath.progress(g, entries, listOf(checking), emptyList(), on, period), period)
        // Reached and still holding the money: it shows next cycle too.
        assertTrue(shows(listOf(saved), goal, today, thisCycle))
        assertTrue(shows(listOf(saved), goal, later, nextCycle))
        // Spent on it this cycle: it shows this cycle, then leaves.
        val spent = listOf(saved, GoalContribution("b", "trip", -50_000, LocalDate.of(2026, 11, 5), "Flights", com.ledgerline.app.domain.model.GoalEntryKind.SPENT_ON_GOAL))
        assertTrue(shows(spent, goal, today, thisCycle))
        assertFalse(shows(spent, goal, later, nextCycle))
        // Finished as Done: only the cycle it was finished in.
        val done = goal.copy(outcome = com.ledgerline.app.domain.model.GoalOutcome.SPENT_ON_GOAL, outcomeOn = today)
        assertTrue(shows(listOf(saved), done, today, thisCycle))
        assertFalse(shows(listOf(saved), done, later, nextCycle))
        // Not reached by its date: it stays, still asking for the rest.
        assertTrue(shows(listOf(saved.copy(amountMinor = 10_000)), goal, later, nextCycle))
    }

    @Test
    fun endingsReadInPlainWords() {
        val start = LocalDate.of(2026, 10, 22)
        val us = java.util.Locale.US
        val monthly = com.ledgerline.app.domain.model.RecurrenceFrequency.MONTHLY
        assertEquals(
            "6 payments: Oct 22, 2026 to Mar 22, 2027, then it stops.",
            com.ledgerline.app.finance.schedule.EndSummary.describe(com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.AFTER_COUNT, 6), start, monthly, "payments", us),
        )
        assertEquals(
            "Stops after Dec 22, 2026: 3 payments, the last on Dec 22, 2026.",
            com.ledgerline.app.finance.schedule.EndSummary.describe(com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.ON_DATE, on = LocalDate.of(2026, 12, 22)), start, monthly, "payments", us),
        )
    }

    @Test
    fun remindersSkipOverdueBillsMovedOntoToday() {
        fun occ(name: String, date: LocalDate, overdue: Boolean) = com.ledgerline.app.domain.model.ScheduledOccurrence(
            recurringId = name, name = name, date = date, amountMinor = 2_500, direction = CashFlowDirection.OUTFLOW,
            priority = PaymentPriority.IMPORTANT, kind = ScheduledKind.SUBSCRIPTION, accountId = "chk", accountName = "Checking",
            incomeConfidence = IncomeConfidence.NONE, countedInSafeToSpend = true, liquidDeltaMinor = -2_500, overdue = overdue,
        )
        // Car wash was due Oct 25 and is unpaid: the forecast dates it today. Gym is really due today.
        val schedule = listOf(occ("Car wash", today, overdue = true), occ("Gym", today, overdue = false))
        assertEquals(listOf("Gym"), com.ledgerline.app.worker.ReminderLines.billsOn(schedule, today).map { it.first })
    }

    @Test
    fun goalMoneyStaysHeldInItsAccountUntilSpentOrUsedElsewhere() {
        val goal = SavingsGoal("house", "Moving", 180_000, "USD", null, "chk", false, 30_000, LocalDate.of(2026, 9, 1))
        val saved = listOf(GoalContribution("a", "house", 50_000, LocalDate.of(2026, 10, 2), ""))
        val period = PayPeriod(cycle.start, cycle.end)
        fun plan(g: SavingsGoal) = com.ledgerline.app.finance.envelopes.EnvelopePlan.build(
            period, today, listOf(checking), emptyList(), emptyList(), emptyList(), emptyList(),
            GoalMath.progressAll(listOf(g), saved, listOf(checking), emptyList(), today, period), null, "USD",
        ).accounts.single()
        val holding = plan(goal)
        assertEquals(listOf("Moving" to 50_000L), holding.savedGoals)
        // Spent on the goal: closed, so nothing is held or asked for any more.
        val spent = plan(goal.copy(outcome = com.ledgerline.app.domain.model.GoalOutcome.SPENT_ON_GOAL, outcomeOn = today))
        assertTrue(spent.savedGoals.isEmpty())
        assertEquals(0L, spent.savingsDueMinor)
        assertEquals(holding.freeMinor + 50_000 + holding.savingsDueMinor, spent.freeMinor)
    }

    @Test
    fun cycleReviewSaysWhatCarriesOnEndsAndStarts() {
        fun bill(id: String, day: Int, start: LocalDate, end: LocalDate? = null) = RecurringPayment(
            id = id, name = id, accountId = "chk", categoryId = null, direction = CashFlowDirection.OUTFLOW, kind = ScheduledKind.SUBSCRIPTION,
            amountMinor = 1_000, currency = "USD", frequency = RecurrenceFrequency.MONTHLY, intervalDays = null, dayOfMonth = day, dayOfWeek = null,
            startOn = start, endOn = end, priority = PaymentPriority.IMPORTANT, incomeConfidence = IncomeConfidence.NONE, active = true, note = "",
        )
        val period = PayPeriod(cycle.start, cycle.end)
        val gym = bill("Gym", 25, LocalDate.of(2026, 10, 25))
        val insurance = bill("Insurance", 22, LocalDate.of(2026, 10, 22), end = LocalDate.of(2026, 10, 22))
        val phone = bill("Phone", 5, LocalDate.of(2026, 12, 5))
        val plan = com.ledgerline.app.finance.envelopes.EnvelopePlan.build(period, today, listOf(checking), emptyList(), emptyList(), listOf(groceries), emptyList(), emptyList(), null, "USD")
        val r = com.ledgerline.app.finance.cycle.CycleReview.build(plan, null, PayPeriod(next.start, next.end), listOf(gym, insurance, phone), listOf(groceries, rent.copy(startOn = next.start)))
        assertEquals(listOf("Gym"), r.continuing.map { it.name })
        assertEquals(LocalDate.of(2026, 11, 25), r.continuing.single().date)
        assertEquals(listOf("Insurance"), r.ending.map { it.name })
        assertEquals(listOf("Phone"), r.starting.map { it.name })
        assertEquals(listOf("Rent"), r.itemsStarting)
        assertEquals(listOf("Groceries"), r.items.map { it.name })
    }

    @Test
    fun spendingFromAGoalKeepsTrackOfWhatItWasFor() {
        val goal = SavingsGoal("house", "Moving", 180_000, "USD", null, "chk", false, null, LocalDate.of(2026, 9, 1))
        val entries = listOf(
            GoalContribution("a", "house", 50_000, LocalDate.of(2026, 10, 2), ""),
            GoalContribution("b", "house", -20_000, LocalDate.of(2026, 11, 3), "Movers deposit", com.ledgerline.app.domain.model.GoalEntryKind.SPENT_ON_GOAL),
            GoalContribution("c", "house", -10_000, LocalDate.of(2026, 11, 4), "Car repair", com.ledgerline.app.domain.model.GoalEntryKind.USED_ELSEWHERE),
        )
        val p = GoalMath.progress(goal, entries, listOf(checking), emptyList(), today, PayPeriod(cycle.start, cycle.end))
        // Spent on the goal still counts as saved toward it; money used elsewhere does not.
        assertEquals(40_000L, p.savedMinor)
        assertEquals(20_000L, p.spentOnGoalMinor)
        assertEquals(10_000L, p.usedElsewhereMinor)
        // Held: 500 saved − 200 spent on it − 100 used elsewhere.
        assertEquals(20_000L, p.heldMinor)
        assertEquals(140_000L, p.remainingMinor)
    }

    @Test
    fun statementReminderOnlyOnTheCyclesFirstDay() {
        assertTrue(CycleReminder.isFirstDay(cycle.start, cycle.start))
        assertFalse(CycleReminder.isFirstDay(cycle.start.plusDays(1), cycle.start))
    }
}
