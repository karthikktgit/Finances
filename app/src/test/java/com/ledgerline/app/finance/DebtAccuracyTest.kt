package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtMonthPlan
import com.ledgerline.app.domain.model.DebtSettlement
import com.ledgerline.app.domain.model.DebtType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PayChoice
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.calendar.CalendarDot
import com.ledgerline.app.finance.calendar.CalendarMarks
import com.ledgerline.app.finance.calendar.DebtCalendar
import com.ledgerline.app.finance.dashboard.DashboardAssembler
import com.ledgerline.app.finance.debt.CardInterest
import com.ledgerline.app.finance.debt.DebtCashFlow
import com.ledgerline.app.finance.debt.DebtDates
import com.ledgerline.app.finance.debt.DebtForecast
import com.ledgerline.app.finance.debt.ForecastInput
import com.ledgerline.app.finance.debt.InterestMath
import com.ledgerline.app.finance.debt.RatePoint
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DebtAccuracyTest {
    @Test
    fun dailyRate_changesWithBillingLengthAndApr() {
        val longMonth = InterestMath.onBalance(100_000, 1_999, 31, 365)
        val shortMonth = InterestMath.onBalance(100_000, 1_999, 28, 365)
        val lowerApr = InterestMath.onBalance(100_000, 999, 31, 365)
        val twelfth = 100_000L * 1_999 / 120_000
        assertEquals(1_698L, longMonth)
        assertEquals(1_533L, shortMonth)
        assertTrue(longMonth > shortMonth)
        assertTrue(longMonth > lowerApr)
        assertNotEquals(twelfth, longMonth)
    }

    @Test
    fun averageDailyBalance_usesEachDaysBalance() {
        val apr = 1_999
        val bothDays = InterestMath.onDailyBalances(listOf(10_000, 10_000), apr)
        val oneDay = InterestMath.onDailyBalances(listOf(10_000, 0), apr)
        assertEquals(InterestMath.onBalance(20_000, apr, 1), bothDays)
        assertTrue(bothDays > oneDay)
    }

    @Test
    fun buckets_keepSeparateAprsAndGrace() {
        val purchase = List(30) { 10_000L }
        val cash = List(30) { 2_000L }
        val mixed = CardInterest.buckets(
            purchaseBalances = purchase,
            purchaseAprBasisPoints = 1_999,
            transferBalances = emptyList(),
            transferAprBasisPoints = null,
            cashBalances = cash,
            cashAprBasisPoints = 2_799,
        )
        val purchaseOnly = InterestMath.onDailyBalances(purchase, 1_999)
        val cashOnly = InterestMath.onDailyBalances(cash, 2_799)
        assertEquals(purchaseOnly + cashOnly, mixed.interestMinor)
        val grace = CardInterest.buckets(
            purchaseBalances = purchase,
            purchaseAprBasisPoints = 1_999,
            transferBalances = emptyList(),
            transferAprBasisPoints = null,
            cashBalances = emptyList(),
            cashAprBasisPoints = null,
            graceOnPurchases = true,
            previousStatementPaidInFull = true,
        )
        assertEquals(0L, grace.interestMinor)
        assertTrue(grace.graceApplied)
        val unknownGrace = CardInterest.buckets(
            purchaseBalances = purchase,
            purchaseAprBasisPoints = 1_999,
            transferBalances = emptyList(),
            transferAprBasisPoints = null,
            cashBalances = emptyList(),
            cashAprBasisPoints = null,
            graceOnPurchases = true,
            previousStatementPaidInFull = null,
        )
        assertNull(unknownGrace.interestMinor)
        assertTrue(unknownGrace.missing.any { it.contains("previous statement") })
    }

    @Test
    fun monthPlans_changeLaterBalancesWithoutRewritingEarlierMonths() {
        val smaller = forecast(mapOf(YearMonth.of(2026, 11) to 7_500, YearMonth.of(2026, 12) to 15_000))
        val larger = forecast(mapOf(YearMonth.of(2026, 11) to 7_500, YearMonth.of(2026, 12) to 25_000))
        assertEquals(7_500L, smaller.months[0].paymentMinor)
        assertEquals(15_000L, smaller.months[1].paymentMinor)
        assertEquals(smaller.months[0].closingMinor, smaller.months[1].openingMinor)
        assertEquals(smaller.months[0].closingMinor, larger.months[0].closingMinor)
        assertTrue(larger.months[1].closingMinor!! < smaller.months[1].closingMinor!!)
        assertTrue(smaller.months[0].principalPaidMinor != smaller.months[1].principalPaidMinor)
        assertTrue(smaller.estimate)
        assertTrue(smaller.note.contains("not a statement"))
    }

    @Test
    fun shortfall_stopsThePayoffDate() {
        val projection = forecast(emptyMap(), choice = PayChoice.FIXED, fixed = 100, months = 3, opening = 100_000)
        assertTrue(projection.stoppedForShortfall)
        assertNull(projection.payoffMonth)
        assertTrue(projection.months.single().unpaidInterestMinor!! > 0)
        assertEquals(projection.months.single().openingMinor, 100_000L)
    }

    @Test
    fun floatingRate_appliesOnlyFromItsEffectiveDate() {
        val input = baseInput(choice = PayChoice.FIXED, fixed = 10_000, months = 2, opening = 100_000).copy(
            start = YearMonth.of(2026, 1),
            aprKnown = false,
            rateChanges = listOf(RatePoint(LocalDate.of(2026, 1, 1), 500), RatePoint(LocalDate.of(2026, 2, 1), 700)),
        )
        val projection = DebtForecast.project(input)
        val january = projection.months[0]
        val february = projection.months[1]
        assertEquals(InterestMath.onBalance(100_000, 500, 31), january.interestMinor)
        assertEquals(InterestMath.onBalance(january.closingMinor!!, 700, 28), february.interestMinor)
        assertNotEquals(InterestMath.onBalance(january.closingMinor!!, 500, 28), february.interestMinor)
    }

    @Test
    fun leapYearAndMonthEndDueDates() {
        val ordinary = forecast(emptyMap(), choice = PayChoice.FIXED, fixed = 10_000, months = 1, opening = 100_000, start = YearMonth.of(2026, 2))
        val leap = forecast(emptyMap(), choice = PayChoice.FIXED, fixed = 10_000, months = 1, opening = 100_000, start = YearMonth.of(2028, 2))
        assertEquals(InterestMath.onBalance(100_000, 1_999, 28), ordinary.months.single().interestMinor)
        assertEquals(InterestMath.onBalance(100_000, 1_999, 29), leap.months.single().interestMinor)
        assertEquals(LocalDate.of(2026, 2, 28), DebtDates.dueOn(YearMonth.of(2026, 2), 31, null))
        assertEquals(LocalDate.of(2028, 2, 29), DebtDates.dueOn(YearMonth.of(2028, 2), 31, null))
        assertEquals(LocalDate.of(2026, 11, 3), DebtDates.dueOn(YearMonth.of(2026, 11), 20, LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun cashFlow_countsAnUnpaidDebtOnceAndSkipsASettledMonth() {
        val today = LocalDate.of(2026, 10, 12)
        val debt = debt(showInCalendar = false)
        val hidden = DebtCashFlow.unpaid(listOf(debt), emptyList(), emptyList(), today, today.plusDays(30), today)
        val shown = DebtCashFlow.unpaid(listOf(debt.copy(showInCalendar = true)), emptyList(), emptyList(), today, today.plusDays(30), today)
        assertEquals(shown, hidden)
        assertEquals(5_000L, hidden.single().amountMinor)
        assertEquals(LocalDate.of(2026, 10, 15), hidden.single().date)
        val settled = DebtCashFlow.unpaid(
            listOf(debt),
            emptyList(),
            listOf(settlement("2026-10")),
            today,
            today.plusDays(30),
            today,
        )
        assertTrue(settled.isEmpty())
    }

    @Test
    fun linkedSchedule_isNotCountedTwice() {
        val today = LocalDate.of(2026, 10, 12)
        val series = series()
        val debt = debt(recurringId = series.id).copy(showInCalendar = true)
        val plan = DebtMonthPlan("p", debt.id, YearMonth.of(2026, 10), 5_000, null)
        val debtEvents = DebtCashFlow.unpaid(listOf(debt), listOf(plan), emptyList(), today.minusMonths(1), today.plusDays(30), today)
        val recurring = listOf(
            ProjectedOccurrence(series.id, series.name, LocalDate.of(2026, 10, 15), 8_000, CashFlowDirection.OUTFLOW, ScheduledKind.DEBT, PaymentPriority.MANDATORY, "checking", IncomeConfidence.NONE, OccurrenceState.SCHEDULED, false),
        )
        val kept = DebtCashFlow.withoutCoveredSeries(recurring, listOf(debt), debtEvents, emptyList())
        assertTrue(kept.isEmpty())
        val october = debtEvents.single { it.date == LocalDate.of(2026, 10, 15) }
        assertEquals(5_000L, october.amountMinor)
    }

    @Test
    fun safeToSpend_includesTheUnpaidDebtPaymentAndNotTheCardPurchase() {
        val today = LocalDate.of(2026, 10, 12)
        val settings = UserSettings("USD", 0, 30, true)
        val checking = account("checking", AccountType.CHECKING, 500_000, liquid = true)
        val card = account("card", AccountType.CREDIT_CARD, -180_000, liquid = false)
        val purchase = transaction("buy", "card", 4_000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 2))
        val plain = DashboardAssembler.assemble(today, settings, listOf(checking, card), emptyList(), listOf(purchase), emptyList(), emptyList())
        val withDebt = DashboardAssembler.assemble(
            today, settings, listOf(checking, card), emptyList(), listOf(purchase), emptyList(), emptyList(),
            debts = listOf(debt()),
        )
        assertEquals(0L, plain.committedPaymentsMinor)
        assertEquals(10_000L, withDebt.committedPaymentsMinor)
        assertEquals(plain.monthSpendingMinor, withDebt.monthSpendingMinor)
    }

    @Test
    fun calendar_dotsFollowTheRecordAndVisibilityDoesNotChangeCashFlow() {
        val date = LocalDate.of(2026, 10, 15)
        val manual = transaction("m", "checking", 1_200, TransactionType.EXPENSE, date)
        val income = transaction("i", "checking", 3_000, TransactionType.INCOME, date)
        val transfer = transaction("t", "checking", 2_000, TransactionType.TRANSFER, date, linkId = "link-transfer")
        val marks = CalendarMarks.forDate(
            date = date,
            transactions = listOf(manual, income, transfer, transfer.copy(id = "t2", accountId = "card", balanceEffectMinor = 2_000)),
            occurrences = listOf(occurrence(date)),
            debtItems = DebtCalendar.items(YearMonth.of(2026, 10), listOf(debt()), emptyList(), emptyList(), date, respectVisibility = true),
            settlementLinkIds = emptySet(),
            linkedRecurringIds = emptySet(),
        )
        assertEquals(listOf(CalendarDot.SCHEDULED, CalendarDot.MANUAL, CalendarDot.INCOME), marks.dots)
        assertTrue(marks.extraCount > 0)
        val hidden = DebtCalendar.items(YearMonth.of(2026, 10), listOf(debt(showInCalendar = false)), emptyList(), emptyList(), date, respectVisibility = true)
        assertTrue(hidden.isEmpty())
        val paid = DebtCalendar.items(
            YearMonth.of(2026, 10),
            listOf(debt()),
            emptyList(),
            listOf(settlement("2026-10")),
            date,
            respectVisibility = true,
        )
        assertTrue(paid.single().paid)
        val paidMarks = CalendarMarks.forDate(date, emptyList(), emptyList(), paid, setOf("pay-link"), emptySet())
        assertTrue(CalendarDot.DEBT !in paidMarks.dots)
    }

    @Test
    fun paymentPair_movesBothAccountsWithoutAddingAnExpense() {
        val amount = 5_000L
        val source = BalanceMath.effectFor(TransactionType.PAYMENT, amount)
        val liability = -source
        assertEquals(-amount, source)
        assertEquals(amount, liability)
        assertEquals(0L, source + liability)
        assertNotEquals(TransactionType.EXPENSE, TransactionType.PAYMENT)
    }

    private fun forecast(
        plans: Map<YearMonth, Long>,
        choice: PayChoice = PayChoice.SAVED_PLAN,
        fixed: Long = 0,
        months: Int = 2,
        opening: Long = 180_000,
        start: YearMonth = YearMonth.of(2026, 11),
    ) = DebtForecast.project(baseInput(choice, fixed, months, opening, start, plans))

    private fun baseInput(
        choice: PayChoice,
        fixed: Long,
        months: Int,
        opening: Long,
        start: YearMonth = YearMonth.of(2026, 11),
        plans: Map<YearMonth, Long> = emptyMap(),
    ) = ForecastInput(
        openingMinor = opening,
        start = start,
        monthCount = months,
        dueDayOfMonth = 15,
        aprKnown = true,
        aprBasisPoints = 1_999,
        rateUpdatedOn = null,
        rateChanges = emptyList(),
        minimumKnown = true,
        minimumMinor = 4_000,
        feeMinor = 0,
        accruedInterestMinor = null,
        plans = plans,
        dueOverrides = emptyMap(),
        choice = choice,
        fixedMinor = fixed,
        statementCloseDay = 20,
    )

    private fun debt(showInCalendar: Boolean = true, recurringId: String? = null) = Debt(
        id = "card-debt",
        name = "Card",
        accountId = "card",
        principalMinor = 180_000,
        balanceMinor = 180_000,
        aprBasisPoints = 1_999,
        minimumPaymentMinor = 5_000,
        dueDayOfMonth = 15,
        frequency = RecurrenceFrequency.MONTHLY,
        priority = PaymentPriority.MANDATORY,
        currency = "USD",
        debtType = DebtType.CREDIT_CARD,
        paymentAccountId = "checking",
        showInCalendar = showInCalendar,
        aprKnown = true,
        minimumKnown = true,
        recurringPaymentId = recurringId,
    )

    private fun settlement(month: String) = DebtSettlement(
        id = "s",
        debtId = "card-debt",
        yearMonth = YearMonth.parse(month),
        paidOn = LocalDate.parse("$month-15"),
        amountMinor = 5_000,
        transactionId = "tx",
        linkId = "pay-link",
        interestMinor = 0,
        principalMinor = 0,
        feeMinor = 0,
        splitKnown = false,
    )

    private fun series() = RecurringPayment(
        id = "series",
        name = "Card payment",
        accountId = "checking",
        categoryId = null,
        direction = CashFlowDirection.OUTFLOW,
        kind = ScheduledKind.DEBT,
        amountMinor = 8_000,
        currency = "USD",
        frequency = RecurrenceFrequency.MONTHLY,
        intervalDays = null,
        dayOfMonth = 15,
        dayOfWeek = null,
        startOn = LocalDate.of(2026, 1, 15),
        endOn = null,
        priority = PaymentPriority.MANDATORY,
        incomeConfidence = IncomeConfidence.NONE,
        active = true,
        note = "",
    )

    private fun account(id: String, type: AccountType, opening: Long, liquid: Boolean) = Account(
        id, id, "", type, null, opening, null, "USD", true, true, liquid, SyncStatus.MANUAL, null, null, 0,
    )

    private fun transaction(
        id: String,
        accountId: String,
        amount: Long,
        type: TransactionType,
        date: LocalDate,
        linkId: String? = null,
    ) = LedgerTransaction(
        id = id,
        accountId = accountId,
        counterAccountId = null,
        linkId = linkId,
        amountMinor = amount,
        balanceEffectMinor = if (type == TransactionType.INCOME) amount else -amount,
        currency = "USD",
        type = type,
        merchantRaw = id,
        merchantNormalized = id,
        merchantId = null,
        description = id,
        categoryId = null,
        occurredOn = date,
        postedOn = date,
        detectionSource = DetectionSource.MANUAL,
        confidencePercent = 100,
        recurringPaymentId = null,
        status = TransactionStatus.CONFIRMED,
        userConfirmed = true,
        note = "",
        fingerprint = id,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
        deletedAtEpochMillis = null,
    )

    private fun occurrence(date: LocalDate) = ProjectedOccurrence(
        recurringId = "bill",
        name = "Rent",
        date = date,
        amountMinor = 1_000,
        direction = CashFlowDirection.OUTFLOW,
        kind = ScheduledKind.BILL,
        priority = PaymentPriority.MANDATORY,
        accountId = "checking",
        incomeConfidence = IncomeConfidence.NONE,
        state = OccurrenceState.SCHEDULED,
        overdue = false,
    )
}
