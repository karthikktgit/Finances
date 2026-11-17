package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.calendar.BillMonth
import com.ledgerline.app.finance.calendar.CalendarGrid
import com.ledgerline.app.finance.calendar.HeatMap
import com.ledgerline.app.finance.envelopes.EnvelopePlan
import com.ledgerline.app.finance.envelopes.HomeItemKind
import com.ledgerline.app.finance.forecast.DailyAccountForecast
import com.ledgerline.app.finance.forecast.SpendingTrack
import com.ledgerline.app.finance.insights.InsightAverages
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * One month, entered the way you would enter it (bills on the calendar or Home, spends from Add, a card payment, a
 * skipped subscription, planned spends), checked through every screen's numbers: Home rings and "un-marked", the
 * calendar, the planned-versus-paid line, the cash projection, the heat map, and Insights. They must all agree.
 */
class MonthScenarioTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val cycle = PayPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
    private val chk = account("chk", 300_000)
    private val card = account("card", 50_000)
    private val categories = listOf(
        Category("cat_groceries_gas", null, "Groceries & Gas", Necessity.LIKELY_ESSENTIAL, false, 10),
        Category("cat_groceries", "cat_groceries_gas", "Groceries", Necessity.LIKELY_ESSENTIAL, false, 11),
        Category("cat_rent", null, "Rent", Necessity.ESSENTIAL, false, 51),
        Category("cat_card_payment", null, "Credit Card Payment", Necessity.ESSENTIAL, false, 21),
    )

    // Bills: rent (paid), a card payment (paid, recorded as a payment), phone (due later), a gym subscription whose
    // November date was skipped, and water left unpaid from October.
    private val series = listOf(
        bill("rent", "Rent", 150_000, 1, ScheduledKind.BILL, "cat_rent"),
        bill("cardpay", "Card payment", 20_000, 10, ScheduledKind.DEBT, "cat_card_payment"),
        bill("phone", "Phone", 5_000, 20, ScheduledKind.BILL, null),
        bill("gym", "Gym", 3_000, 12, ScheduledKind.SUBSCRIPTION, null, start = LocalDate.of(2026, 11, 12)),
        bill("water", "Water", 2_000, 25, ScheduledKind.BILL, null, start = LocalDate.of(2026, 10, 25), end = LocalDate.of(2026, 10, 31)),
    )
    private val adjustments = listOf(OccurrenceAdjustment("gym", LocalDate.of(2026, 11, 12), OccurrenceState.SKIPPED, null, null, null))
    private val budgets = listOf(Budget("g", "Groceries", "cat_groceries", "chk", BudgetPeriod.PAY_CYCLE, 40_000, "USD", true, true))

    private val txs = listOf(
        tx("pay", LocalDate.of(2026, 11, 1), 400_000, TransactionType.INCOME, null),
        tx("rent", LocalDate.of(2026, 11, 1), 150_000, TransactionType.EXPENSE, "cat_rent", recurring = "rent"),
        tx("cardpay", LocalDate.of(2026, 11, 10), 20_000, TransactionType.PAYMENT, "cat_card_payment", recurring = "cardpay"),
        tx("groc1", LocalDate.of(2026, 11, 5), 12_000, TransactionType.EXPENSE, "cat_groceries"),
        tx("groc2", LocalDate.of(2026, 11, 7), 3_000, TransactionType.EXPENSE, "cat_groceries", account = "card"),
        tx("coffee", LocalDate.of(2026, 11, 8), 500, TransactionType.EXPENSE, null),
        // Planned, not paid: a concert (no item) and groceries (covered by the Groceries item).
        tx("concert", LocalDate.of(2026, 11, 18), 8_000, TransactionType.EXPENSE, null, paid = false),
        tx("grocplan", LocalDate.of(2026, 11, 16), 5_000, TransactionType.EXPENSE, "cat_groceries", paid = false),
        // Planned for the 9th, paid on the 14th.
        tx("fix", LocalDate.of(2026, 11, 14), 6_000, TransactionType.EXPENSE, null, plannedOn = LocalDate.of(2026, 11, 9)),
    )
    private val settled = txs.mapNotNull { tx -> tx.recurringPaymentId?.takeIf { BalanceMath.isActive(tx) }?.let { it to tx.occurredOn } }.toSet()

    private fun events(from: LocalDate, to: LocalDate) = OccurrenceMaterializer.project(series, adjustments, settled, from, to, today)

    @Test
    fun homeRingsAndUnmarkedAgreeWithWhatWasEntered() {
        val entries = BillMonth.entries(events(cycle.start.minusMonths(3), cycle.end), emptyList(), emptySet())
        val plan = EnvelopePlan.build(cycle, today, listOf(chk, card), txs, categories, budgets, entries, emptyList(), null, "USD")
        val items = plan.sections.flatMap { it.items }.associateBy { it.name }
        // Paid bills are full; the card payment counts as paid even though it is recorded as a payment.
        assertTrue(items.getValue("Rent").paid)
        assertEquals(150_000L, items.getValue("Rent").spentMinor)
        assertTrue(items.getValue("Card payment").paid)
        assertFalse(items.getValue("Phone").paid)
        // Water left unpaid in October is carried into this pay; the skipped gym date is not a bill at all.
        assertTrue(items.getValue("Water").overdue)
        assertNull(items["Gym"])
        assertEquals(5_000L + 2_000, plan.stillToPayMinor)
        // Groceries: both shops count, the card one marked as another account; planned groceries do not count yet.
        val groceries = items.getValue("Groceries")
        assertEquals(15_000L, groceries.spentMinor)
        assertEquals(mapOf("chk" to 12_000L, "card" to 3_000L), groceries.spentByAccount)
        // Only the coffee is un-marked; the paid phone fix (no item) is too.
        assertEquals(500L + 6_000, plan.outsideMinor)
        assertEquals(setOf("coffee", "fix"), plan.outsideIds.toSet())
    }

    @Test
    fun theCalendarShowsTheSameBillsAndTheHeatOfEachDay() {
        val month = YearMonth.of(2026, 11)
        val entries = BillMonth.entries(events(CalendarGrid.dates(month).first(), CalendarGrid.dates(month).last()), emptyList(), emptySet())
        val summary = BillMonth.summary(entries, month)
        // Rent, card payment, phone: the skipped gym is gone.
        assertEquals(3, summary.billCount)
        assertEquals(2, summary.paidCount)
        assertEquals(175_000L, summary.billsMinor)
        val days = BillMonth.days(entries, txs, CalendarGrid.dates(month))
        // The 1st: 4,000 came in and 1,500 went out on rent, so the day is green.
        val heat = HeatMap.of(days, CalendarGrid.dates(month).filter { YearMonth.from(it) == month })
        assertFalse(heat.getValue(LocalDate.of(2026, 11, 1)).out)
        assertTrue(heat.getValue(LocalDate.of(2026, 11, 10)).out)
        // A planned, unpaid spend leaves no heat on its day, but shows as still to pay on it.
        assertNull(heat[LocalDate.of(2026, 11, 18)])
        val concertDay = days.getValue(LocalDate.of(2026, 11, 18))
        assertEquals(8_000L, concertDay.plannedMinor)
        assertEquals(8_000L, concertDay.toPayMinor)
        assertTrue(com.ledgerline.app.finance.calendar.DayMark.DUE in concertDay.marks)
    }

    @Test
    fun plannedAndPaidLinesCountEachThingOnce() {
        val bills = SpendingTrack.billsFor(events(cycle.start.minusMonths(3), cycle.end), cycle.start)
        // Rent, card payment, phone, plus the water still owed from October; not the skipped gym.
        assertEquals(setOf("Rent", "Card payment", "Phone", "Water"), bills.map { it.name }.toSet())
        val planned = txs.filter { it.plannedOn != null || BalanceMath.isPlannedUnpaid(it) }
        val covered = EnvelopePlan.owners(budgets, categories, planned.map { it.copy(paid = true) }).filterKeys { it != null }.values.flatten().map { it.id }.toSet()
        assertEquals(setOf("grocplan"), covered)
        val track = SpendingTrack.build(cycle, today, bills, 40_000, txs) { it.id in covered }
        // Planned by the 15th: water 20 (carried to the 1st) + rent 1,500 + card 200 + fix 60 (planned the 9th)
        // + groceries budget 400 × 15/30 = 200. The concert (18th) and phone (20th) are later.
        assertEquals(2_000L + 150_000 + 20_000 + 6_000 + 20_000, track.plannedToDateMinor)
        // Paid by the 15th: rent, the card payment, both grocery shops, coffee, and the fix.
        assertEquals(150_000L + 20_000 + 12_000 + 3_000 + 500 + 6_000, track.paidToDateMinor)
        // The whole cycle adds the phone, the concert, and the rest of the groceries budget; planned groceries are not added twice.
        assertEquals(2_000L + 150_000 + 20_000 + 5_000 + 6_000 + 8_000 + 40_000, track.plannedTotalMinor)
        // Nothing planned is overdue and unpaid yet (the concert is the 18th, the groceries the 16th).
        assertEquals(0L, track.unpaidPlannedMinor)
        assertEquals(track.paidToDateMinor - track.plannedToDateMinor, track.aheadMinor)
        // The planned line, explained: bills, the spending items' budget so far, and spends you planned.
        assertEquals(2_000L + 150_000 + 20_000, track.plannedBillsToDateMinor)
        assertEquals(20_000L, track.plannedBudgetsToDateMinor)
        assertEquals(6_000L, track.plannedSpendsToDateMinor)
        assertEquals(track.plannedToDateMinor, track.plannedBillsToDateMinor + track.plannedBudgetsToDateMinor + track.plannedSpendsToDateMinor)
    }

    @Test
    fun theCashProjectionTakesPlannedSpendsOnTheirDays() {
        // As the cash-flow screen does: planned spends count on their day (or today if it has passed).
        val ahead = txs.map { if (BalanceMath.isPlannedUnpaid(it)) it.copy(paid = true, occurredOn = maxOf(it.occurredOn, today)) else it }
        val phone = ScheduledOccurrence(
            recurringId = "phone", name = "Phone", date = LocalDate.of(2026, 11, 20), amountMinor = 5_000, direction = CashFlowDirection.OUTFLOW,
            priority = PaymentPriority.MANDATORY, kind = ScheduledKind.BILL, accountId = "chk", accountName = "Checking",
            incomeConfidence = IncomeConfidence.NONE, countedInSafeToSpend = true, liquidDeltaMinor = -5_000,
        )
        val result = DailyAccountForecast.project(today, 10, listOf(chk), setOf("chk"), ahead, listOf(phone), 0L).combined!!
        val start = BalanceMath.balanceAsOf(chk, txs, today)
        fun on(d: Int) = result.single { it.date == LocalDate.of(2026, 11, d) }.endMinor
        assertEquals(start, on(15))
        assertEquals(start - 5_000, on(16))
        assertEquals(start - 5_000 - 8_000, on(18))
        assertEquals(start - 5_000 - 8_000 - 5_000, on(20))
        // Nothing else moves the line: no daily spending is guessed.
        assertEquals(on(20), on(24))
        // Today's real balance does not include anything still only planned.
        assertEquals(300_000L + 400_000 - 150_000 - 20_000 - 12_000 - 500 - 6_000, start)
    }

    @Test
    fun insightsAveragesUseOnlyWhatWasPaid() {
        val avg = InsightAverages.compute(today, listOf(chk, card), txs, categories)
        // Spending: rent 1,500 + groceries 150 + coffee 5 + fix 60 (the card payment is not spending; plans are not paid).
        assertEquals((150_000L + 15_000 + 500 + 6_000) / 15, avg.perDayMinor)
        assertEquals(setOf("Rent", "Groceries & Gas", "No category"), avg.categories.map { it.name }.toSet())
    }

    private fun account(id: String, opening: Long) = Account(id, id, "", AccountType.CHECKING, null, opening, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0)

    private fun bill(id: String, name: String, amount: Long, day: Int, kind: ScheduledKind, category: String?, start: LocalDate = LocalDate.of(2026, 11, day), end: LocalDate? = null) =
        RecurringPayment(
            id = id, name = name, accountId = "chk", categoryId = category, direction = CashFlowDirection.OUTFLOW, kind = kind, amountMinor = amount,
            currency = "USD", frequency = RecurrenceFrequency.MONTHLY, intervalDays = null, dayOfMonth = day, dayOfWeek = null, startOn = start,
            endOn = end, priority = PaymentPriority.MANDATORY, incomeConfidence = IncomeConfidence.NONE, active = true, note = "",
        )

    private fun tx(
        id: String,
        on: LocalDate,
        amount: Long,
        type: TransactionType,
        category: String?,
        account: String = "chk",
        recurring: String? = null,
        paid: Boolean = true,
        plannedOn: LocalDate? = null,
    ) = LedgerTransaction(
        id = id, accountId = account, counterAccountId = null, linkId = null, amountMinor = amount,
        balanceEffectMinor = if (type == TransactionType.INCOME) amount else -amount, currency = "USD", type = type, merchantRaw = id,
        merchantNormalized = id, merchantId = null, description = "", categoryId = category, occurredOn = on, postedOn = null,
        detectionSource = DetectionSource.MANUAL, confidencePercent = 100, recurringPaymentId = recurring, status = TransactionStatus.CONFIRMED,
        userConfirmed = true, note = "", fingerprint = "", createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
        paid = paid, plannedOn = plannedOn ?: if (!paid) on else null,
    )
}

class BillPaidAmountTest {
    @Test
    fun aBillPaidAtADifferentAmountShowsWhatWasPaidAndStillCountsAsPaid() {
        val today = LocalDate.of(2026, 11, 20)
        val cycle = PayPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        val chk = Account("chk", "chk", "", AccountType.CHECKING, null, 100_000, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0)
        val phone = RecurringPayment(
            id = "phone", name = "Phone", accountId = "chk", categoryId = null, direction = CashFlowDirection.OUTFLOW, kind = ScheduledKind.BILL,
            amountMinor = 4_500, currency = "USD", frequency = RecurrenceFrequency.MONTHLY, intervalDays = null, dayOfMonth = 18, dayOfWeek = null,
            startOn = LocalDate.of(2026, 11, 18), endOn = null, priority = PaymentPriority.MANDATORY, incomeConfidence = IncomeConfidence.NONE, active = true, note = "",
        )
        fun paidWith(amount: Long) = LedgerTransaction(
            id = "p$amount", accountId = "chk", counterAccountId = null, linkId = null, amountMinor = amount, balanceEffectMinor = -amount, currency = "USD",
            type = TransactionType.EXPENSE, merchantRaw = "Phone", merchantNormalized = "phone", merchantId = null, description = "", categoryId = null,
            occurredOn = LocalDate.of(2026, 11, 18), postedOn = null, detectionSource = DetectionSource.MANUAL, confidencePercent = 100,
            recurringPaymentId = "phone", status = TransactionStatus.CONFIRMED, userConfirmed = true, note = "", fingerprint = "",
            createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
        )
        listOf(5_000L, 4_000L).forEach { amount ->
            val txs = listOf(paidWith(amount))
            val settled = setOf("phone" to LocalDate.of(2026, 11, 18))
            val entries = BillMonth.entries(OccurrenceMaterializer.project(listOf(phone), emptyList(), settled, cycle.start, cycle.end, today), emptyList(), emptySet())
            val item = EnvelopePlan.build(cycle, today, listOf(chk), txs, emptyList(), emptyList(), entries, emptyList(), null, "USD").sections.flatMap { it.items }.single()
            assertTrue(item.paid)
            assertEquals(amount, item.spentMinor)
            // What is left of the pay uses what the bill really cost.
            val plan = EnvelopePlan.build(cycle, today, listOf(chk), txs, emptyList(), emptyList(), entries, emptyList(), 100_000, "USD")
            assertEquals(100_000L - amount, plan.leftOfPayMinor)
            assertEquals(4_500L, item.plannedMinor)
        }
    }
}
