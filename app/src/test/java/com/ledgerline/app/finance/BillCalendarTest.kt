package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.calendar.BillDay
import com.ledgerline.app.finance.calendar.BillMonth
import com.ledgerline.app.finance.calendar.BillPresets
import com.ledgerline.app.finance.calendar.BillPreview
import com.ledgerline.app.finance.calendar.CalendarGrid
import com.ledgerline.app.finance.calendar.DayMark
import com.ledgerline.app.finance.calendar.DebtCalendarItem
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** The bill calendar: month totals, what each day shows, compact amounts, presets, and the next-dates preview. */
class BillCalendarTest {
    private val november = YearMonth.of(2026, 11)
    private val today = LocalDate.of(2026, 11, 2)

    private fun event(
        name: String,
        day: Int,
        amount: Long,
        direction: CashFlowDirection = CashFlowDirection.OUTFLOW,
        kind: ScheduledKind = ScheduledKind.BILL,
        state: OccurrenceState = OccurrenceState.SCHEDULED,
        overdue: Boolean = false,
        month: YearMonth = november,
    ) = ProjectedOccurrence(
        recurringId = name, name = name, date = month.atDay(day), amountMinor = amount, direction = direction, kind = kind,
        priority = PaymentPriority.MANDATORY, accountId = "chk", incomeConfidence = IncomeConfidence.HIGH, state = state, overdue = overdue,
    )

    private val events = listOf(
        event("Rent", 1, 120_000, state = OccurrenceState.PAID),
        event("Phone", 1, 4_500, overdue = true),
        event("Netflix", 5, 1_549, kind = ScheduledKind.SUBSCRIPTION),
        event("Salary", 20, 380_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
        event("Savings", 20, 50_000, kind = ScheduledKind.TRANSFER),
        // Shown in the grid's spill-over days but not part of November.
        event("Rent", 1, 120_000, month = YearMonth.of(2026, 12)),
    )
    private val carLoan = DebtCalendarItem("car", "Car loan", november.atDay(15), november, 32_000, paid = false, overdue = false, accountId = null, paymentAccountId = "chk")

    @Test
    fun monthTotalsCountBillsAndLeaveOutTransfersAndOtherMonths() {
        val entries = BillMonth.entries(events, listOf(carLoan), linkedRecurringIds = emptySet())
        val summary = BillMonth.summary(entries, november)
        // Rent 1,200 + Phone 45 + Netflix 15.49 + Car loan 320; the transfer and December's rent are not bills here.
        assertEquals(158_049L, summary.billsMinor)
        assertEquals(120_000L, summary.paidMinor)
        assertEquals(38_049L, summary.leftMinor)
        assertEquals(4, summary.billCount)
        assertEquals(1, summary.paidCount)
        assertEquals(1, summary.overdueCount)
        assertEquals(380_000L, summary.incomeMinor)
    }

    @Test
    fun aDebtShownFromItsScheduleIsNotCountedTwice() {
        val linked = BillMonth.entries(events + event("Car loan", 15, 32_000, kind = ScheduledKind.DEBT), listOf(carLoan), linkedRecurringIds = setOf("Car loan"))
        assertEquals(1, linked.count { it.name == "Car loan" })
        assertEquals("car", linked.single { it.name == "Car loan" }.debtId)
    }

    @Test
    fun eachDayShowsWhatIsStillDue() {
        val entries = BillMonth.entries(events, listOf(carLoan), emptySet())
        val spent = LedgerTransaction(
            id = "t", accountId = "chk", counterAccountId = null, linkId = null, amountMinor = 900, balanceEffectMinor = -900,
            currency = "USD", type = TransactionType.EXPENSE, merchantRaw = "Coffee", merchantNormalized = "coffee", merchantId = null,
            description = "", categoryId = null, occurredOn = november.atDay(5), postedOn = null, detectionSource = DetectionSource.MANUAL,
            confidencePercent = 100, recurringPaymentId = null, status = TransactionStatus.CONFIRMED, userConfirmed = true, note = "",
            fingerprint = "", createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
        )
        val days = BillMonth.days(entries, listOf(spent), CalendarGrid.dates(november))
        val first = days.getValue(november.atDay(1))
        // Rent is paid, so only the overdue phone bill is still due on the 1st.
        assertEquals(4_500L, first.dueMinor)
        assertEquals(2, first.billCount)
        assertFalse(first.allPaid)
        assertTrue(first.overdue)
        assertEquals(1_549L, days.getValue(november.atDay(5)).dueMinor)
        assertTrue(days.getValue(november.atDay(5)).hasSpending)
        assertEquals(32_000L, days.getValue(november.atDay(15)).dueMinor)
        // The transfer on the 20th is not shown as a bill; the salary is income.
        assertEquals(0L, days.getValue(november.atDay(20)).dueMinor)
        assertEquals(380_000L, days.getValue(november.atDay(20)).incomeMinor)
        assertEquals(0, days.getValue(november.atDay(10)).billCount)
        // One dot per kind of thing on the day: the 1st has a paid rent and an overdue phone bill; the 5th a bill and coffee.
        assertEquals(listOf(DayMark.OVERDUE, DayMark.PAID), first.marks)
        assertEquals(listOf(DayMark.DUE, DayMark.SPENT), days.getValue(november.atDay(5)).marks)
        assertEquals(900L, days.getValue(november.atDay(5)).spentMinor)
        assertEquals(listOf(DayMark.INCOME), days.getValue(november.atDay(20)).marks)
        assertTrue(days.getValue(november.atDay(10)).marks.isEmpty())
    }

    @Test
    fun theHeatMapIsRedForMoneyOutAndGreenForMoneyInScaledToTheBusiestDay() {
        fun day(d: Int, spent: Long = 0, paid: Long = 0, received: Long = 0) =
            BillDay(november.atDay(d), 0, 0, 0, false, false, spent > 0, spent, 0, received > 0, paid, received)
        val days = listOf(day(1, spent = 1_000), day(2, spent = 4_000, paid = 12_000), day(3, received = 380_000), day(4, spent = 500, received = 300), day(5)).associateBy { it.date }
        val heat = com.ledgerline.app.finance.calendar.HeatMap.of(days, days.keys)
        // The 2nd (16,000 out) is the hottest red; the 1st is cooler but still visible.
        assertEquals(1f, heat.getValue(november.atDay(2)).level, 0.001f)
        assertTrue(heat.getValue(november.atDay(2)).out)
        assertTrue(heat.getValue(november.atDay(1)).level in 0.2f..0.4f)
        // Pay day is green at full strength; a day with more out than in is red; a quiet day has no heat.
        assertFalse(heat.getValue(november.atDay(3)).out)
        assertEquals(1f, heat.getValue(november.atDay(3)).level, 0.001f)
        assertTrue(heat.getValue(november.atDay(4)).out)
        assertNull(heat[november.atDay(5)])
    }

    @Test
    fun aDayWhoseBillsAreAllPaidSaysSo() {
        val entries = BillMonth.entries(listOf(event("Rent", 1, 120_000, state = OccurrenceState.PAID)), emptyList(), emptySet())
        val day = BillMonth.days(entries, emptyList(), listOf(november.atDay(1))).getValue(november.atDay(1))
        assertTrue(day.allPaid)
        assertEquals(0L, day.dueMinor)
    }

    @Test
    fun entriesAreInDateOrderWithBillsBeforeIncome() {
        val entries = BillMonth.entries(events, listOf(carLoan), emptySet()).filter { YearMonth.from(it.date) == november }
        assertEquals(listOf("Phone", "Rent", "Netflix", "Car loan", "Savings", "Salary"), entries.map { it.name })
    }

    @Test
    fun compactAmountsFitACalendarCell() {
        val us = Locale.US
        assertEquals("\$22", BillMonth.compact(2_200, "USD", us))
        assertEquals("\$15", BillMonth.compact(1_549, "USD", us))
        assertEquals("\$480", BillMonth.compact(48_000, "USD", us))
        assertEquals("-\$355", BillMonth.compact(-35_500, "USD", us))
        assertEquals("\$1.2k", BillMonth.compact(120_000, "USD", us))
        assertEquals("\$12k", BillMonth.compact(1_200_000, "USD", us))
        assertEquals("\$3.8k", BillMonth.compact(380_000, "USD", us))
        assertEquals("\$2.5M", BillMonth.compact(250_000_000, "USD", us))
        assertEquals("\$1k", BillMonth.compact(100_000, "USD", us))
    }

    @Test
    fun presetsFindTheMatchingCategory() {
        val categories = listOf(
            Category("cat_monthly", null, "Monthly Expenditures", Necessity.ESSENTIAL, false, 50),
            Category("cat_rent", "cat_monthly", "Rent", Necessity.ESSENTIAL, false, 51),
            Category("cat_subscriptions", "cat_monthly", "Subscriptions", Necessity.DISCRETIONARY, false, 55),
            Category("cat_paycheck", "cat_income", "Salary", Necessity.ESSENTIAL, false, 31),
            Category("cat_education_loan", "cat_debt", "Education Loan", Necessity.ESSENTIAL, false, 22),
            Category("cat_old", null, "Internet", Necessity.ESSENTIAL, true, 99),
        )
        assertEquals("cat_rent", BillPresets.category("Rent", categories)?.id)
        assertEquals("cat_subscriptions", BillPresets.category("Subscriptions", categories)?.id)
        assertEquals("cat_paycheck", BillPresets.category("Salary", categories)?.id)
        assertEquals("cat_education_loan", BillPresets.category("Loan", categories)?.id)
        // Archived categories are never chosen.
        assertNull(BillPresets.category("Internet", categories))
        // Every preset is listed under the kind it creates.
        ScheduledKind.entries.forEach { kind -> BillPresets.forKind(kind).forEach { assertEquals(kind == ScheduledKind.INCOME, it.incoming) } }
    }

    @Test
    fun previewShowsTheNextDatesIncludingShortMonths() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 31)),
            BillPreview.nextDates(RecurrenceFrequency.MONTHLY, jan31, from = today),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 27), LocalDate.of(2026, 12, 11)),
            BillPreview.nextDates(RecurrenceFrequency.BIWEEKLY, LocalDate.of(2026, 10, 30), from = today),
        )
        // A start date in the future is where the preview begins.
        assertEquals(LocalDate.of(2026, 12, 5), BillPreview.nextDates(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 12, 5), from = today).first())
        assertEquals(listOf(LocalDate.of(2026, 11, 9)), BillPreview.nextDates(RecurrenceFrequency.ONCE, LocalDate.of(2026, 11, 9), from = today))
    }

    @Test
    fun repeatsAreDescribedInPlainWords() {
        val us = Locale.US
        assertEquals("Every month on the 5th", BillPreview.describe(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 11, 5), locale = us))
        assertEquals("Every month on the 1st", BillPreview.describe(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 11, 1), locale = us))
        assertEquals("Every month on the 22nd", BillPreview.describe(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 11, 22), locale = us))
        assertEquals("Every month on the 13th", BillPreview.describe(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 11, 13), locale = us))
        assertEquals("Every 2 weeks on Friday", BillPreview.describe(RecurrenceFrequency.BIWEEKLY, LocalDate.of(2026, 10, 30), locale = us))
        assertEquals("Every year on Nov 5", BillPreview.describe(RecurrenceFrequency.YEARLY, LocalDate.of(2026, 11, 5), locale = us))
        assertEquals("Once, on Nov 5", BillPreview.describe(RecurrenceFrequency.ONCE, LocalDate.of(2026, 11, 5), locale = us))
        assertEquals("Every 10 days", BillPreview.describe(RecurrenceFrequency.CUSTOM, LocalDate.of(2026, 11, 5), intervalDays = 10, locale = us))
    }
}
