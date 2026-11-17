package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.HomeGroup
import com.ledgerline.app.domain.model.HomeGroupItem
import com.ledgerline.app.finance.envelopes.HomeItemKind
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.SavingsGoal
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.calendar.BillEntry
import com.ledgerline.app.finance.envelopes.EnvelopeHealth
import com.ledgerline.app.finance.envelopes.EnvelopeKind
import com.ledgerline.app.finance.envelopes.EnvelopePlan
import com.ledgerline.app.finance.envelopes.EnvelopePresets
import com.ledgerline.app.finance.goals.GoalMath
import com.ledgerline.app.finance.period.PayPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Home's envelopes: $4,000 of pay split into bills, spending envelopes kept in accounts, and savings. */
class EnvelopePlanTest {
    private val cycle = PayPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19))
    private val today = LocalDate.of(2026, 11, 4) // day 16 of 31
    private val chk = account("chk", "Checking", 300_000)
    private val card = account("card", "Gas card", 40_000)
    private val categories = listOf(
        Category("cat_groceries_gas", null, "Groceries & Gas", Necessity.LIKELY_ESSENTIAL, false, 10),
        Category("cat_groceries", "cat_groceries_gas", "Groceries", Necessity.LIKELY_ESSENTIAL, false, 11),
        Category("cat_fuel", "cat_groceries_gas", "Gas / Fuel", Necessity.LIKELY_ESSENTIAL, false, 12),
        Category("cat_unplanned", null, "Unplanned", Necessity.UNKNOWN, false, 40),
        Category("cat_monthly", null, "Monthly Expenditures", Necessity.ESSENTIAL, false, 50),
        Category("cat_rent", "cat_monthly", "Rent", Necessity.ESSENTIAL, false, 51),
        Category("cat_income", null, "Income", Necessity.ESSENTIAL, false, 30),
    )
    private val groceries = budget("g", "Groceries", "cat_groceries", "chk", 40_000)
    private val gas = budget("f", "Gas", "cat_fuel", "card", 15_000)
    private val everyday = budget("e", "Everyday spending", null, null, 30_000)
    private val bills = listOf(
        bill("Salary", LocalDate.of(2026, 10, 20), 400_000, incoming = true, paid = true),
        bill("Rent", LocalDate.of(2026, 11, 1), 150_000, paid = true),
        bill("Phone", LocalDate.of(2026, 11, 12), 5_000),
        bill("Rent", LocalDate.of(2026, 12, 1), 150_000), // next cycle
    )
    private val spends = listOf(
        tx("Market", LocalDate.of(2026, 10, 22), 12_000, "cat_groceries", "chk"),
        tx("Market on the card", LocalDate.of(2026, 10, 30), 3_000, "cat_groceries", "card"),
        tx("Shell", LocalDate.of(2026, 10, 25), 6_000, "cat_fuel", "card"),
        tx("Coffee", LocalDate.of(2026, 11, 2), 2_000, "cat_unplanned", "chk"),
        tx("Rent paid", LocalDate.of(2026, 11, 1), 150_000, "cat_rent", "chk", recurring = "Rent"),
        tx("Last cycle", LocalDate.of(2026, 10, 10), 9_000, "cat_groceries", "chk"),
        tx("Pay", LocalDate.of(2026, 10, 20), 400_000, null, "chk", type = TransactionType.INCOME),
    )
    private val goal = SavingsGoal("room", "Room deposit", 200_000, "USD", null, "chk", false, 20_000, LocalDate.of(2026, 9, 1))

    private fun plan(budgets: List<Budget> = listOf(groceries, gas, everyday), override: Long? = null) = EnvelopePlan.build(
        cycle, today, listOf(chk, card), spends, categories, budgets, bills,
        GoalMath.progressAll(listOf(goal), emptyList(), listOf(chk, card), spends, today, cycle), override, "USD",
    )

    @Test
    fun payIsSplitAndWhatIsLeftHasNoJobYet() {
        val plan = plan()
        assertEquals(400_000L, plan.incomeMinor)
        assertTrue(plan.incomeFromCalendar)
        // Bills 1,550 (this cycle only) + groceries 400 + gas 150 + everyday 300 + saving 200.
        assertEquals(260_000L, plan.plannedMinor)
        assertEquals(140_000L, plan.unassignedMinor)
        assertEquals(5_000L, plan.stillToPayMinor)
        // Everyday spending is 230 (in envelopes or not); left of the pay is 4,000 − 1,550 bills − 200 saving − 230 spent.
        assertEquals(23_000L, plan.spentMinor)
        assertEquals(400_000L - 155_000 - 20_000 - 23_000, plan.leftOfPayMinor)
        // With no envelopes at all, what is left of the pay is the same: envelopes only divide it.
        assertEquals(plan.leftOfPayMinor, plan(emptyList()).leftOfPayMinor)
        val bills = plan.bills!!
        assertEquals(155_000L, bills.plannedMinor)
        assertEquals(150_000L, bills.spentMinor)
        // Unpaid bills first.
        assertEquals("Phone", bills.bills.first().name)
        // A bill left unpaid from last cycle is carried into this one; a paid one from last cycle is not.
        val carried = EnvelopePlan.build(
            cycle, today, listOf(chk, card), spends, categories, emptyList(),
            this.bills + bill("Water", LocalDate.of(2026, 10, 5), 3_000).copy(overdue = true) + bill("Gym", LocalDate.of(2026, 10, 6), 4_000, paid = true),
            emptyList(), null, "USD",
        )
        assertEquals(8_000L, carried.stillToPayMinor)
        assertEquals(158_000L, carried.bills!!.plannedMinor)
        // Your own pay figure replaces the calendar's.
        assertEquals(-10_000L, plan(override = 250_000).unassignedMinor)
    }

    @Test
    fun eachSpendIsCountedInExactlyOneEnvelope() {
        val plan = plan()
        fun spent(name: String) = plan.envelopes.single { it.name == name }.spentMinor
        // Groceries bought on the gas card still count as groceries, marked as paid from another account.
        assertEquals(15_000L, spent("Groceries"))
        assertEquals(3_000L, plan.envelopes.single { it.name == "Groceries" }.spentOtherMinor)
        assertEquals(6_000L, spent("Gas"))
        assertEquals(0L, plan.envelopes.single { it.name == "Gas" }.spentOtherMinor)
        assertEquals(2_000L, spent("Everyday spending"))
        // Each ring knows its transactions, so tapping it can list them; spends in no envelope are listed too.
        assertEquals(setOf("Market-2026-10-22", "Market on the card-2026-10-30"), plan.envelopes.single { it.name == "Groceries" }.transactionIds.toSet())
        assertEquals(listOf("Coffee-2026-11-02"), plan(listOf(groceries, gas)).outsideIds)
        assertEquals(0L, plan.outsideMinor)
        // Every envelope's spending plus spending outside them is all the everyday spending this cycle.
        assertEquals(23_000L, plan.envelopes.filter { it.kind == EnvelopeKind.SPENDING }.sumOf { it.spentMinor } + plan.outsideMinor)
        // Without a catch-all, only the coffee is outside any envelope.
        val noCatchAll = plan(listOf(groceries, gas))
        assertEquals(2_000L, noCatchAll.outsideMinor)
        assertEquals(1, noCatchAll.outsideCount)
        // Safe to spend counts the same.
        assertEquals(mapOf("g" to 15_000L, "f" to 6_000L, "e" to 2_000L), EnvelopePlan.spentByBudget(listOf(groceries, gas, everyday), categories, spends, cycle))
    }

    @Test
    fun anEnvelopeSaysWhatIsLeftAndIfItIsGoingTooFast() {
        val plan = plan(listOf(groceries.copy(amountMinor = 18_000), gas))
        val grocery = plan.envelopes.single { it.name == "Groceries" }
        assertEquals(3_000L, grocery.leftMinor)
        // Day 16 of 31: about $92.90 should be gone by now; $150 is more than a tenth of the envelope ahead.
        assertEquals(EnvelopeHealth.FAST, grocery.health)
        assertEquals(187L, grocery.perDayMinor) // $30 over the 16 days left
        assertEquals(EnvelopeHealth.ON_TRACK, plan.envelopes.single { it.name == "Gas" }.health)
        val over = plan(listOf(groceries.copy(amountMinor = 10_000))).envelopes.single { it.name == "Groceries" }
        assertEquals(EnvelopeHealth.OVER, over.health)
        assertEquals(5_000L, over.overMinor)
        assertEquals(0L, over.perDayMinor)
    }

    @Test
    fun eachAccountShowsWhatIsSpokenForAndWhatIsFree() {
        val plan = plan()
        val checking = plan.accounts.single { it.account.id == "chk" }
        // Balance: 3,000 opening + 4,000 pay − 120 − 20 − 1,500 rent − 90 last cycle = 5,270.
        assertEquals(527_000L, checking.balanceMinor)
        assertEquals(5_000L, checking.billsDueMinor)
        assertEquals(25_000L, checking.envelopesLeftMinor)
        assertEquals(20_000L, checking.savingsDueMinor)
        assertEquals(527_000L - 5_000 - 25_000 - 20_000, checking.freeMinor)
        assertEquals("Phone", checking.nextDue!!.name)
        val gasCard = plan.accounts.single { it.account.id == "card" }
        assertEquals(9_000L, gasCard.envelopesLeftMinor)
        // 400 − 30 − 60 = 310 in the card, 90 of it still in the gas envelope.
        assertEquals(31_000L - 9_000, gasCard.freeMinor)
    }

    @Test
    fun aCreditCardShowsButIsNotCountedAsFreeMoney() {
        // A credit card kept out of Safe to spend, with 200 spent on it.
        val travelCard = Account("card2", "Travel card", "", AccountType.CREDIT_CARD, null, 0, null, "USD", true, true, false, SyncStatus.MANUAL, null, null, 0)
        val charge = tx("Phone", LocalDate.of(2026, 11, 4), 20_000, null, "card2")
        fun build(accounts: List<Account>) = EnvelopePlan.build(
            cycle, today, accounts, spends + charge, categories, listOf(groceries, gas, everyday), bills,
            GoalMath.progressAll(listOf(goal), emptyList(), listOf(chk, card), spends, today, cycle), null, "USD",
        )
        // The same spending, with and without the card in the account list.
        val without = build(listOf(chk, card))
        val with = build(listOf(chk, card, travelCard))
        val row = with.accounts.single { it.account.id == "card2" }
        assertFalse(row.counted)
        assertEquals(-20_000L, row.balanceMinor)
        // It shows, but what is free until payday is the same as without it.
        assertEquals(without.freeOverallMinor, with.freeOverallMinor)
        assertEquals(without.countedAccounts.map { it.account.id }, with.countedAccounts.map { it.account.id })
    }

    @Test
    fun billsAndEnvelopesSitInTheirHomeGroups() {
        val groups = listOf(HomeGroup("grp_monthly", "Monthly", 0), HomeGroup("grp_subscriptions", "Subscriptions", 1), HomeGroup("fun", "Fun", 2))
        val withNetflix = this.bills + bill("Netflix", LocalDate.of(2026, 11, 8), 1_549).copy(kind = ScheduledKind.SUBSCRIPTION)
        fun sections(placements: List<HomeGroupItem>) = EnvelopePlan.build(
            cycle, today, listOf(chk, card), spends, categories, listOf(groceries, gas), withNetflix, emptyList(), null, "USD", groups, placements,
        ).sections
        val auto = sections(emptyList())
        // Rent (one item even with two dates in view), phone, and envelopes go to the first group; Netflix to Subscriptions.
        assertEquals(listOf("Rent", "Groceries", "Gas", "Phone"), auto[0].items.map { it.name })
        assertEquals(listOf("Netflix"), auto[1].items.map { it.name })
        assertTrue(auto[2].items.isEmpty())
        val rent = auto[0].items.first()
        assertTrue(rent.paid)
        assertEquals(HomeItemKind.BILL, rent.kind)
        val phone = auto[0].items.single { it.name == "Phone" }
        assertEquals(LocalDate.of(2026, 11, 12), phone.nextDue)
        // The ring: $120 of $400 from checking, $30 from the card.
        val grocery = auto[0].items.single { it.name == "Groceries" }
        assertEquals(0.30f, grocery.ownFraction, 0.001f)
        assertEquals(0.075f, grocery.otherFraction, 0.001f)
        assertEquals(25_000L, grocery.leftMinor)
        // Moving items: gas to Fun, and a placement in a deleted group falls back.
        val moved = sections(listOf(HomeGroupItem("budget:f", "fun", 0), HomeGroupItem("bill:Phone", "gone", 0)))
        assertEquals(listOf("Gas"), moved[2].items.map { it.name })
        assertTrue(moved[0].items.any { it.name == "Phone" })
        assertEquals(150_000L + 40_000 + 15_000 + 5_000, auto[0].plannedMinor)
        // No groups at all: one Monthly group with everything.
        val none = EnvelopePlan.build(cycle, today, listOf(chk, card), spends, categories, listOf(groceries), this.bills, emptyList(), null, "USD").sections
        assertEquals(listOf("Monthly expenses"), none.map { it.name })
        assertEquals(3, none.single().items.size)
    }

    @Test
    fun anItemCountsTheCategoryItsNameMeans() {
        val rent = budget("rent", "Rent", null, null, 34_000)
        val loan = budget("loan", "Loan", null, null, 100_000)
        val other = budget("x", "Dog walker", null, null, 5_000)
        val rows = listOf(tx("Landlord", LocalDate.of(2026, 11, 2), 34_000, "cat_rent", "chk"), tx("Bank", LocalDate.of(2026, 11, 3), 20_000, "cat_other_debt", "chk"))
        val cats = categories + Category("cat_other_debt", "cat_debt", "Other Debt", Necessity.ESSENTIAL, false, 23) + Category("cat_debt", null, "Debt", Necessity.ESSENTIAL, false, 20)
        val spent = EnvelopePlan.spentByBudget(listOf(rent, loan, other), cats, rows, cycle)
        assertEquals(34_000L, spent["rent"])
        assertEquals(20_000L, spent["loan"])
        // A name that matches nothing takes nothing by itself, and is not the catch-all.
        assertEquals(0L, spent["x"])
        assertEquals("cat_rent", EnvelopePlan.effectiveCategory(rent, cats))
        assertEquals(null, EnvelopePlan.effectiveCategory(other, cats))
        // Only a catch-all name takes what nothing else claims.
        val catchAll = EnvelopePlan.spentByBudget(listOf(rent, other, budget("e", "Everyday spending", null, null, 1)), cats, rows + tx("Odd", LocalDate.of(2026, 11, 3), 700, null, "chk"), cycle)
        // The catch-all takes the $200 debt payment and the $7 odd spend: neither belongs to Rent or "Dog walker".
        assertEquals(20_700L, catchAll["e"] ?: 0L)
        assertEquals(0L, catchAll["x"])
    }

    @Test
    fun ringsSplitByTheAccountThatPaid() {
        val plan = plan(listOf(groceries))
        val item = plan.sections.flatMap { it.items }.single { it.name == "Groceries" }
        assertEquals(mapOf("chk" to 12_000L, "card" to 3_000L), item.spentByAccount)
        // Segments are shares of the planned $400, biggest first, and never more than a full ring.
        assertEquals(listOf("chk" to 0.30f, "card" to 0.075f), item.segments)
        val capped = item.copy(plannedMinor = 10_000)
        assertEquals(1f, capped.segments.sumOf { it.second.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun aGoalIsARingOfThisMonthsSavingOrTheWholeGoal() {
        val withPlan = GoalMath.progress(goal, listOf(com.ledgerline.app.domain.model.GoalContribution("c", "room", 5_000, LocalDate.of(2026, 11, 1), "")), listOf(chk, card), emptyList(), today, cycle)
        val monthly = EnvelopePlan.build(cycle, today, listOf(chk, card), spends, categories, emptyList(), bills, listOf(withPlan), null, "USD").goalItems.single()
        assertEquals(HomeItemKind.GOAL, monthly.kind)
        assertEquals(20_000L, monthly.plannedMinor)
        assertEquals(5_000L, monthly.spentMinor)
        assertEquals(mapOf("chk" to 5_000L), monthly.spentByAccount)
        val noPlan = GoalMath.progress(goal.copy(monthlyMinor = null), listOf(com.ledgerline.app.domain.model.GoalContribution("c", "room", 5_000, LocalDate.of(2026, 11, 1), "")), listOf(chk, card), emptyList(), today, cycle)
        val whole = EnvelopePlan.build(cycle, today, listOf(chk, card), spends, categories, emptyList(), bills, listOf(noPlan), null, "USD").goalItems.single()
        assertEquals(200_000L, whole.plannedMinor)
        assertEquals(5_000L, whole.spentMinor)
    }

    @Test
    fun weeklyAndMonthlyBudgetsAreScaledToTheCycle() {
        val twoWeeks = PayPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14))
        assertEquals(20_000L, EnvelopePlan.perCycle(budget("w", "W", null, null, 10_000, BudgetPeriod.WEEKLY), twoWeeks))
        assertEquals(46_027L, EnvelopePlan.perCycle(budget("m", "M", null, null, 100_000, BudgetPeriod.MONTHLY), twoWeeks))
        assertEquals(100_000L, EnvelopePlan.perCycle(budget("m", "M", null, null, 100_000, BudgetPeriod.MONTHLY), cycle))
        assertEquals(12_345L, EnvelopePlan.perCycle(budget("p", "P", null, null, 12_345, BudgetPeriod.PAY_CYCLE), twoWeeks))
    }

    @Test
    fun presetsComeFromYourSpendingCategoriesMostUsedFirst() {
        val offer = EnvelopePresets.offer(categories, spends, taken = emptySet())
        assertEquals(listOf("Groceries", "Gas & Fuel", "Unplanned", "Everyday spending"), offer.map { it.name })
        // Bills, income, and parent groups are not offered; taken ones drop out, including the catch-all.
        val rest = EnvelopePresets.offer(categories, spends, taken = setOf("cat_groceries", null))
        assertFalse(rest.any { it.name == "Groceries" || it.name == "Everyday spending" || it.name == "Rent" })
    }

    private fun account(id: String, name: String, opening: Long) = Account(
        id, name, "", AccountType.CHECKING, null, opening, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0,
    )

    private fun budget(id: String, name: String, category: String?, account: String?, amount: Long, period: BudgetPeriod = BudgetPeriod.PAY_CYCLE) =
        Budget(id, name, category, account, period, amount, "USD", true, true)

    private fun bill(name: String, date: LocalDate, amount: Long, incoming: Boolean = false, paid: Boolean = false) = BillEntry(
        "r-$name-$date", name, date, amount, incoming, if (incoming) ScheduledKind.INCOME else ScheduledKind.BILL, paid, false, "chk", name, null,
    )

    private fun tx(
        name: String,
        date: LocalDate,
        amount: Long,
        category: String?,
        account: String,
        recurring: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
    ) = LedgerTransaction(
        id = "$name-$date", accountId = account, counterAccountId = null, linkId = null, amountMinor = amount,
        balanceEffectMinor = if (type == TransactionType.INCOME) amount else -amount, currency = "USD", type = type,
        merchantRaw = name, merchantNormalized = name.lowercase(), merchantId = null, description = "", categoryId = category,
        occurredOn = date, postedOn = null, detectionSource = DetectionSource.MANUAL, confidencePercent = 100,
        recurringPaymentId = recurring, status = TransactionStatus.CONFIRMED, userConfirmed = true, note = "", fingerprint = "",
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
    )
}
