package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetPace
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.BudgetSnapshot
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.CycleType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.SafeToSpendLine
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.domain.model.WarningSeverity
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.budget.BudgetMath
import com.ledgerline.app.finance.dashboard.HomeMath
import com.ledgerline.app.finance.money.AmountInput
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.safetospend.SafeToSpendEngine
import com.ledgerline.app.finance.subscription.SubscriptionRollup
import com.ledgerline.app.finance.warnings.CashWarningEngine
import com.ledgerline.app.ui.onboarding.OnboardingCurrencies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Calculations that had no direct test, plus the figures the redesigned Home, Activity, and Plan show. */
class CalculationCoverageTest {
    private val today = LocalDate.of(2026, 11, 2)

    // ---- Home figures ----

    @Test
    fun daysLeftCountsTodayAndNeverDropsBelowOne() {
        assertEquals(18L, HomeMath.daysLeft(today, LocalDate.of(2026, 11, 19)))
        assertEquals(1L, HomeMath.daysLeft(today, today))
        assertEquals(1L, HomeMath.daysLeft(today, today.minusDays(5)))
    }

    @Test
    fun perDayIsAnEvenShareRoundedDown() {
        // $2,147.50 over 18 days is $119.305…, shown as $119.30, so spending it every day never overshoots.
        assertEquals(11_930L, HomeMath.perDay(214_750L, 18))
        assertEquals(0L, HomeMath.perDay(0L, 18))
        assertEquals(0L, HomeMath.perDay(-5_000L, 18))
        assertEquals(500L, HomeMath.perDay(500L, 1))
        assertTrue(HomeMath.perDay(214_750L, 18) * 18 <= 214_750L)
    }

    @Test
    fun cycleDayCountsFromOneAndCoversPastAndFutureCycles() {
        val period = PayPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19))
        assertEquals(14L to 31L, HomeMath.cycleDay(period, today))
        assertEquals(1L to 31L, HomeMath.cycleDay(period, period.start))
        assertEquals(31L to 31L, HomeMath.cycleDay(period, period.end))
        assertEquals(31L to 31L, HomeMath.cycleDay(period, period.end.plusDays(10)))
        assertEquals(0L to 31L, HomeMath.cycleDay(period, period.start.minusDays(1)))
    }

    @Test
    fun daySpendingCountsOnlyCountedExpenses() {
        val rows = listOf(
            tx(TransactionType.EXPENSE, 1_000),
            tx(TransactionType.EXPENSE, 2_500),
            tx(TransactionType.REFUND, 400),
            tx(TransactionType.TRANSFER, 9_000),
            tx(TransactionType.INCOME, 50_000),
            tx(TransactionType.EXPENSE, 7_000, status = TransactionStatus.IGNORED),
            tx(TransactionType.EXPENSE, 7_000, status = TransactionStatus.DUPLICATE),
            tx(TransactionType.EXPENSE, 7_000, status = TransactionStatus.DETECTED),
            tx(TransactionType.EXPENSE, 7_000, deleted = true),
        )
        assertEquals(3_500L, HomeMath.daySpending(rows))
    }

    @Test
    fun readableSafeToSpendLinesStillAddUpToTheTotal() {
        val result = SafeToSpendEngine.calculate(
            SafeToSpendEngine.Input(
                liquidBalanceMinor = 266_950,
                committedPaymentsMinor = 2_200,
                minimumBufferMinor = 50_000,
                plannedTransfersMinor = 0,
                expectedIncomeMinor = 380_000,
                plannedDiscretionaryMinor = 0,
            ),
        )
        val lines = HomeMath.readableLines(result.lines)
        assertEquals(result.amountMinor, lines.sumOf { it.minor })
        assertEquals(214_750L, result.amountMinor)
        // Zero lines are hidden, and the remaining labels are plain words.
        assertEquals(listOf("Money in your spending accounts", "Bills still due this cycle", "Safety buffer you keep"), lines.map { it.label })
        // The balance line stays even at zero, so the list always starts from what you have.
        assertEquals(1, HomeMath.readableLines(listOf(SafeToSpendLine("Current liquid balance", 0))).size)
    }

    @Test
    fun expectedIncomeNeverRaisesSafeToSpend() {
        fun safe(income: Long) = SafeToSpendEngine.calculate(SafeToSpendEngine.Input(100_000, 20_000, 10_000, 0, income, 0)).amountMinor
        assertEquals(safe(0), safe(500_000))
    }

    // ---- Money ----

    @Test
    fun signedLabelsUseATrueMinusSign() {
        val us = Locale.US
        assertEquals("+\$12.50", MoneyFormat.signedLabel(1_250, "USD", us))
        assertEquals("−\$12.50", MoneyFormat.signedLabel(-1_250, "USD", us))
        assertEquals("\$0.00", MoneyFormat.signedLabel(0, "USD", us))
        assertEquals("\$1,234,567.89", MoneyFormat.format(123_456_789, "USD", us))
    }

    @Test
    fun everyCurrencyOfferedUsesTwoDecimals() {
        // Captured amounts and typed amounts both assume cents; a whole-unit currency here would be off by 100.
        OnboardingCurrencies.list.forEach { code -> assertEquals(code, 2, MoneyFormat.fractionDigits(code)) }
        assertEquals(0, MoneyFormat.fractionDigits("JPY"))
        assertEquals(1_500L, AmountInput.toMinor("1500", MoneyFormat.fractionDigits("JPY")))
    }

    @Test
    fun balanceEffectsHaveTheRightSign() {
        assertEquals(-500L, BalanceMath.effectFor(TransactionType.EXPENSE, 500))
        assertEquals(-500L, BalanceMath.effectFor(TransactionType.EXPENSE, -500))
        assertEquals(500L, BalanceMath.effectFor(TransactionType.INCOME, 500))
        assertEquals(500L, BalanceMath.effectFor(TransactionType.REFUND, 500))
        assertEquals(-500L, BalanceMath.effectFor(TransactionType.TRANSFER, 500))
        assertEquals(-500L, BalanceMath.effectFor(TransactionType.PAYMENT, 500))
        assertEquals(-70L, BalanceMath.effectFor(TransactionType.ADJUSTMENT, 500, adjustmentSigned = -70))
        assertEquals(500L, BalanceMath.counterEffect(-500))
    }

    // ---- Plan figures ----

    @Test
    fun repeatingBillsConvertToAMonthlyFigure() {
        assertEquals(3_000L, SubscriptionRollup.toMonthly(100, RecurrenceFrequency.DAILY))
        assertEquals(4_333L, SubscriptionRollup.toMonthly(1_000, RecurrenceFrequency.WEEKLY))
        assertEquals(2_166L, SubscriptionRollup.toMonthly(1_000, RecurrenceFrequency.BIWEEKLY))
        assertEquals(1_000L, SubscriptionRollup.toMonthly(1_000, RecurrenceFrequency.MONTHLY))
        assertEquals(1_000L, SubscriptionRollup.toMonthly(3_000, RecurrenceFrequency.QUARTERLY))
        assertEquals(833L, SubscriptionRollup.toMonthly(9_999, RecurrenceFrequency.YEARLY))
        // A one-time bill is not a monthly cost; Plan hides the monthly line when this is all there is.
        assertEquals(0L, SubscriptionRollup.toMonthly(2_200, RecurrenceFrequency.ONCE))
        assertEquals(12_000L, SubscriptionRollup.annualizedMinor(1_000))
    }

    @Test
    fun budgetPaceProjectsTheCurrentRate() {
        val half = BudgetMath.evaluate(spentMinor = 20_000, budgetMinor = 50_000, elapsedDays = 15, periodDays = 30)
        assertEquals(30_000L, half.remainingMinor)
        assertEquals(40_000L, half.projectedMinor)
        assertEquals(BudgetPace.ON_TRACK, half.pace)

        val fast = BudgetMath.evaluate(30_000, 50_000, 10, 30)
        assertEquals(90_000L, fast.projectedMinor)
        assertEquals(BudgetPace.LIKELY_OVER, fast.pace)

        val over = BudgetMath.evaluate(60_000, 50_000, 20, 30)
        assertEquals(-10_000L, over.remainingMinor)
        assertEquals(BudgetPace.OVER, over.pace)

        // Exactly at the limit is not over.
        assertEquals(BudgetPace.ON_TRACK, BudgetMath.evaluate(50_000, 50_000, 30, 30).pace)
        // Day zero has no pace yet; the projection is what is already spent.
        assertEquals(5_000L, BudgetMath.evaluate(5_000, 50_000, 0, 30).projectedMinor)
    }

    @Test
    fun cyclesStepBackAndForthWithoutGapsOrOverlaps() {
        val settingsByType = listOf(
            UserSettings("USD", 0, 30, true, cycleType = CycleType.MONTHLY, cycleStartDay = 20),
            UserSettings("USD", 0, 30, true, cycleType = CycleType.MONTHLY, cycleStartDay = 31),
            UserSettings("USD", 0, 30, true, cycleType = CycleType.SEMIMONTHLY, cycleStartDay = 1, cycleSecondDay = 15),
            UserSettings("USD", 0, 30, true, cycleType = CycleType.WEEKLY, cycleAnchor = LocalDate.of(2026, 1, 5)),
            UserSettings("USD", 0, 30, true, cycleType = CycleType.BIWEEKLY, cycleAnchor = LocalDate.of(2026, 1, 9)),
        )
        settingsByType.forEach { settings ->
            var period = CycleSchedule.containing(LocalDate.of(2026, 1, 15), settings)
            repeat(30) {
                val next = CycleSchedule.next(period, settings)
                assertEquals("${settings.cycleType}: next starts the day after", period.end.plusDays(1), next.start)
                assertEquals("${settings.cycleType}: previous undoes next", period, CycleSchedule.previous(next, settings))
                assertFalse(next.end.isBefore(next.start))
                period = next
            }
        }
    }

    // ---- Warnings ----

    @Test
    fun warningsCoverEachSituationAtTheRightLevel() {
        val rent = due("Rent", today.plusDays(2), 120_000, PaymentPriority.MANDATORY)
        val gym = due("Gym", today.plusDays(1), 3_000, PaymentPriority.IMPORTANT)
        val later = due("Insurance", today.plusDays(10), 9_000, PaymentPriority.MANDATORY)
        val optional = due("Streaming", today, 1_500, PaymentPriority.OPTIONAL)
        val bonus = income("Bonus", today.plusDays(5), IncomeConfidence.LOW)
        val budget = BudgetSnapshot(
            Budget("b", "Dining", null, null, BudgetPeriod.MONTHLY, 20_000, "USD", true, true),
            "Dining", 25_000, -5_000, 40_000, BudgetPace.OVER,
        )
        val warnings = CashWarningEngine.build(
            today = today, currency = "USD", liquidMinor = 100_000, safeToSpendMinor = -30_000, bufferMinor = 10_000,
            upcoming = listOf(rent, gym, later, optional, bonus), budgets = listOf(budget),
            thisMonthSpentMinor = 0, lastMonthSpentMinor = 0, dayOfMonth = 2, daysInMonth = 30, excludedCurrencyCount = 1,
        )
        val byTitle = warnings.associate { it.title to it.severity }
        assertEquals(WarningSeverity.CRITICAL, byTitle["Safe-to-spend estimate is below zero"])
        assertEquals(WarningSeverity.HIGH, byTitle["Rent is due in 2 days"])
        assertEquals(WarningSeverity.MEDIUM, byTitle["Gym is due tomorrow"])
        assertEquals(WarningSeverity.HIGH, byTitle["Committed payments exceed liquid funds"])
        assertEquals(WarningSeverity.MEDIUM, byTitle["Dining is over budget"])
        assertEquals(WarningSeverity.INFO, byTitle["Bonus is not included in safe-to-spend"])
        assertEquals(WarningSeverity.INFO, byTitle["Some accounts are excluded from totals"])
        // Too far away, or optional: no warning.
        assertFalse(byTitle.keys.any { it.startsWith("Insurance") || it.startsWith("Streaming") })
    }

    @Test
    fun aHealthyMonthHasNoWarnings() {
        val warnings = CashWarningEngine.build(
            today = today, currency = "USD", liquidMinor = 500_000, safeToSpendMinor = 300_000, bufferMinor = 10_000,
            upcoming = listOf(due("Insurance", today.plusDays(10), 9_000, PaymentPriority.MANDATORY)), budgets = emptyList(),
            thisMonthSpentMinor = 10_000, lastMonthSpentMinor = 300_000, dayOfMonth = 15, daysInMonth = 30, excludedCurrencyCount = 0,
        )
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun aFasterSpendingPaceIsFlaggedOnlyPastTheThreshold() {
        fun pace(thisMonth: Long) = CashWarningEngine.build(
            today, "USD", 500_000, 300_000, 0, emptyList(), emptyList(),
            thisMonthSpentMinor = thisMonth, lastMonthSpentMinor = 100_000, dayOfMonth = 10, daysInMonth = 30, excludedCurrencyCount = 0,
        ).any { it.title == "Spending pace is higher than last month" }
        // 10 days in, 30,000 projects to 90,000: below last month. 40,000 projects to 120,000: 20% higher.
        assertFalse(pace(30_000))
        assertTrue(pace(40_000))
        // 38,000 projects to 114,000, 14% higher: under the 15% threshold.
        assertFalse(pace(38_000))
    }

    @Test
    fun safeToSpendInsideTheBufferIsAMildWarning() {
        val warnings = CashWarningEngine.build(today, "USD", 100_000, 5_000, 10_000, emptyList(), emptyList(), 0, 0, 1, 30, 0)
        assertEquals(WarningSeverity.MEDIUM, warnings.single().severity)
    }

    // ---- helpers ----

    private fun tx(type: TransactionType, amount: Long, status: TransactionStatus = TransactionStatus.CONFIRMED, deleted: Boolean = false) =
        LedgerTransaction(
            id = "t$amount$type$status$deleted", accountId = "a", counterAccountId = null, linkId = null, amountMinor = amount,
            balanceEffectMinor = BalanceMath.effectFor(type, amount), currency = "USD", type = type, merchantRaw = "", merchantNormalized = "",
            merchantId = null, description = "", categoryId = null, occurredOn = today, postedOn = null,
            detectionSource = DetectionSource.MANUAL, confidencePercent = 100, recurringPaymentId = null, status = status,
            userConfirmed = true, note = "", fingerprint = "", createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
            deletedAtEpochMillis = if (deleted) 1L else null,
        )

    private fun due(name: String, date: LocalDate, amount: Long, priority: PaymentPriority) = ScheduledOccurrence(
        recurringId = name, name = name, date = date, amountMinor = amount, direction = CashFlowDirection.OUTFLOW, priority = priority,
        kind = ScheduledKind.BILL, accountId = "a", accountName = "Checking", incomeConfidence = IncomeConfidence.HIGH,
        countedInSafeToSpend = priority == PaymentPriority.MANDATORY || priority == PaymentPriority.IMPORTANT, liquidDeltaMinor = -amount,
    )

    private fun income(name: String, date: LocalDate, confidence: IncomeConfidence) = ScheduledOccurrence(
        recurringId = name, name = name, date = date, amountMinor = 50_000, direction = CashFlowDirection.INFLOW,
        priority = PaymentPriority.FLEXIBLE, kind = ScheduledKind.INCOME, accountId = "a", accountName = "Checking",
        incomeConfidence = confidence, countedInSafeToSpend = false, liquidDeltaMinor = 50_000,
    )
}
