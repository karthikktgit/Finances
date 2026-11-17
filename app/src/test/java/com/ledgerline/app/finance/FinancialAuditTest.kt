package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.CycleType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.forecast.DailyBalanceForecast
import com.ledgerline.app.finance.forecast.ProjectionColor
import com.ledgerline.app.finance.period.CycleHighlight
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.period.PeriodLedger
import com.ledgerline.app.finance.safetospend.SafeToSpendEngine
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import com.ledgerline.app.finance.schedule.RecurringSchedule
import com.ledgerline.app.finance.time.FinancialDate
import com.ledgerline.app.ui.undo.UndoTiming
import com.ledgerline.app.finance.validation.TransactionRules
import com.ledgerline.app.domain.model.TransactionDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class FinancialAuditTest {
    private val opening = 100_000L
    private val account = Account(
        "checking", "Checking", "", AccountType.CHECKING, null, opening, null, "USD", true, true, true,
        SyncStatus.MANUAL, null, null, 0,
    )

    @Test
    fun historicalInsertion_recalculatesLaterDatesAndLeavesEarlierDatesAlone() {
        val oct10 = tx("e1", 10_000, -10_000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 10))
        val oct12 = tx("e2", 5_000, -5_000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 12))
        val beforeIncome = listOf(oct10, oct12)
        assertEquals(100_000L, asOf(beforeIncome, LocalDate.of(2026, 10, 9)))
        assertEquals(90_000L, asOf(beforeIncome, LocalDate.of(2026, 10, 10)))
        assertEquals(90_000L, asOf(beforeIncome, LocalDate.of(2026, 10, 11)))
        assertEquals(85_000L, asOf(beforeIncome, LocalDate.of(2026, 10, 12)))

        val income = tx("in", 20_000, 20_000, TransactionType.INCOME, LocalDate.of(2026, 10, 11))
        val inserted = listOf(oct10, oct12, income)
        assertEquals(100_000L, asOf(inserted, LocalDate.of(2026, 10, 9)))
        assertEquals(90_000L, asOf(inserted, LocalDate.of(2026, 10, 10)))
        assertEquals(110_000L, asOf(inserted, LocalDate.of(2026, 10, 11)))
        assertEquals(105_000L, asOf(inserted, LocalDate.of(2026, 10, 12)))

        val edited = listOf(oct10, oct12, income.copy(amountMinor = 15_000, balanceEffectMinor = 15_000))
        assertEquals(90_000L, asOf(edited, LocalDate.of(2026, 10, 10)))
        assertEquals(105_000L, asOf(edited, LocalDate.of(2026, 10, 11)))
        assertEquals(100_000L, asOf(edited, LocalDate.of(2026, 10, 12)))

        val deleted = listOf(oct10, oct12, income.copy(deletedAtEpochMillis = 1L))
        assertEquals(90_000L, asOf(deleted, LocalDate.of(2026, 10, 10)))
        assertEquals(90_000L, asOf(deleted, LocalDate.of(2026, 10, 11)))
        assertEquals(85_000L, asOf(deleted, LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun pastDayForecast_usesThatDaysBalanceRatherThanTheLatestBalance() {
        val rows = listOf(
            tx("e1", 10_000, -10_000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 10)),
            tx("in", 20_000, 20_000, TransactionType.INCOME, LocalDate.of(2026, 10, 11)),
            tx("e2", 5_000, -5_000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 12)),
        )
        val today = LocalDate.of(2026, 10, 12)
        val laterBill = ProjectedOccurrence(
            "rent", "Rent", LocalDate.of(2026, 10, 13), 2_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL,
            PaymentPriority.MANDATORY, account.id, IncomeConfidence.NONE, OccurrenceState.SCHEDULED, false,
        )
        val oct10 = DailyBalanceForecast.forDate(LocalDate.of(2026, 10, 10), today, listOf(account), rows, listOf(laterBill))
        val oct13 = DailyBalanceForecast.forDate(LocalDate.of(2026, 10, 13), today, listOf(account), rows, listOf(laterBill))
        assertEquals(90_000L, oct10.actualMinor)
        assertEquals(90_000L, oct10.projectedEndMinor)
        assertTrue(oct10.historical)
        assertEquals(10_000L, oct10.completedOutgoingMinor)
        assertEquals(0L, oct10.scheduledOutgoingMinor)
        assertEquals(105_000L, oct13.actualMinor)
        assertEquals(103_000L, oct13.projectedEndMinor)
        assertEquals(2_000L, oct13.scheduledOutgoingMinor)
    }

    @Test
    fun futureDatedTransaction_doesNotChangeAnEarlierEndOfDay() {
        val future = tx("later", 8_000, -8_000, TransactionType.EXPENSE, LocalDate.of(2026, 11, 2))
        assertEquals(100_000L, asOf(listOf(future), LocalDate.of(2026, 10, 12)))
        assertEquals(92_000L, asOf(listOf(future), LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun refundAndTransfer_followTheAccountEffects() {
        val refund = tx("refund", 4_000, 4_000, TransactionType.REFUND, LocalDate.of(2026, 10, 3))
        assertEquals(104_000L, asOf(listOf(refund), LocalDate.of(2026, 10, 3)))
        val savings = account.copy(id = "savings", name = "Savings", openingBalanceMinor = 40_000, includeInSafeToSpend = false)
        val out = tx("out", 7_000, -7_000, TransactionType.TRANSFER, LocalDate.of(2026, 10, 4))
        val into = tx("into", 7_000, 7_000, TransactionType.TRANSFER, LocalDate.of(2026, 10, 4)).copy(accountId = "savings")
        assertEquals(93_000L, BalanceMath.balanceAsOf(account, listOf(out, into), LocalDate.of(2026, 10, 4)))
        assertEquals(47_000L, BalanceMath.balanceAsOf(savings, listOf(out, into), LocalDate.of(2026, 10, 4)))
        val consolidated = BalanceMath.balanceAsOf(account, listOf(out, into), LocalDate.of(2026, 10, 4)) +
            BalanceMath.balanceAsOf(savings, listOf(out, into), LocalDate.of(2026, 10, 4))
        assertEquals(140_000L, consolidated)
    }

    @Test
    fun cycleBoundaries_includeTheEndDateOnce() {
        val settings = UserSettings("USD", 0, 30, true, CycleType.MONTHLY, LocalDate.of(2026, 1, 20), 20, 5)
        val cycle = CycleSchedule.containing(LocalDate.of(2026, 10, 25), settings)
        assertEquals(LocalDate.of(2026, 10, 20), cycle.start)
        assertEquals(LocalDate.of(2026, 11, 19), cycle.end)
        val onEnd = tx("end", 3_000, 3_000, TransactionType.INCOME, cycle.end)
        val after = tx("after", 9_000, 9_000, TransactionType.INCOME, cycle.end.plusDays(1))
        val before = tx("before", 8_000, 8_000, TransactionType.INCOME, cycle.start.minusDays(1))
        val report = PeriodLedger.report(LocalDate.of(2026, 11, 19), cycle, listOf(account), listOf(onEnd, after, before), emptyList(), emptyList())
        assertEquals(3_000L, report.incomeMinor)
        assertTrue(cycle.contains(cycle.start))
        assertTrue(cycle.contains(cycle.end))
        assertFalse(cycle.contains(cycle.end.plusDays(1)))
    }

    @Test
    fun cycleHighlight_followsEachConfiguredCycleAndClearsInDayMode() {
        val monthly = UserSettings("USD", 0, 30, true, CycleType.MONTHLY, LocalDate.of(2026, 1, 20), 20, 5)
        val november = CycleSchedule.containing(LocalDate.of(2026, 11, 2), monthly)
        val novemberDays = CycleHighlight.datesInMonth(YearMonth.of(2026, 11), november)
        assertEquals(LocalDate.of(2026, 11, 1), novemberDays.first())
        assertEquals(LocalDate.of(2026, 11, 19), novemberDays.last())
        assertFalse(novemberDays.contains(LocalDate.of(2026, 11, 20)))
        val selected = CycleHighlight.of(LocalDate.of(2026, 11, 2), november, LocalDate.of(2026, 11, 2), true)
        assertTrue(selected.inCycle && selected.selected)
        val start = CycleHighlight.of(november.start, november, LocalDate.of(2026, 11, 2), true)
        assertTrue(start.cycleStart && !start.selected)
        val outside = CycleHighlight.of(LocalDate.of(2026, 11, 20), november, LocalDate.of(2026, 11, 2), true)
        assertFalse(outside.inCycle)
        val dayMode = CycleHighlight.of(LocalDate.of(2026, 11, 2), november, LocalDate.of(2026, 11, 2), false)
        assertFalse(dayMode.inCycle)
        assertTrue(dayMode.selected)

        val weekly = UserSettings("USD", 0, 30, true, CycleType.WEEKLY, LocalDate.of(2026, 10, 5), 1, 5)
        val week = CycleSchedule.containing(LocalDate.of(2026, 10, 14), weekly)
        assertEquals(LocalDate.of(2026, 10, 12), week.start)
        assertEquals(LocalDate.of(2026, 10, 18), week.end)
        val biweekly = UserSettings("USD", 0, 30, true, CycleType.BIWEEKLY, LocalDate.of(2026, 10, 5), 1, 5)
        val fortnight = CycleSchedule.containing(LocalDate.of(2026, 10, 20), biweekly)
        assertEquals(LocalDate.of(2026, 10, 19), fortnight.start)
        assertEquals(LocalDate.of(2026, 11, 1), fortnight.end)
        val semi = UserSettings("USD", 0, 30, true, CycleType.SEMIMONTHLY, LocalDate.of(2026, 1, 1), 1, 16)
        val secondHalf = CycleSchedule.containing(LocalDate.of(2026, 10, 20), semi)
        assertEquals(LocalDate.of(2026, 10, 16), secondHalf.start)
        assertEquals(LocalDate.of(2026, 10, 31), secondHalf.end)
        val year = CycleSchedule.containing(LocalDate.of(2027, 1, 5), monthly)
        assertEquals(PayPeriod(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 19)), year)
        val leapSettings = UserSettings("USD", 0, 30, true, CycleType.MONTHLY, LocalDate.of(2024, 1, 31), 31, 5)
        val leap = CycleSchedule.containing(LocalDate.of(2024, 2, 20), leapSettings)
        val february = CycleHighlight.datesInMonth(YearMonth.of(2024, 2), leap)
        assertEquals(LocalDate.of(2024, 2, 1), february.first())
        assertEquals(LocalDate.of(2024, 2, 28), february.last())
        assertTrue(CycleHighlight.of(leap.end, leap, LocalDate.of(2024, 2, 20), true).cycleEnd)
    }

    @Test
    fun projectedEndColor_usesTheUsdThresholdInMinorUnits() {
        assertEquals(true, ProjectionColor.aboveUsdLimit(50_100, "USD"))
        assertEquals(true, ProjectionColor.aboveUsdLimit(50_001, "USD"))
        assertEquals(false, ProjectionColor.aboveUsdLimit(50_000, "USD"))
        assertEquals(false, ProjectionColor.aboveUsdLimit(49_999, "USD"))
        assertEquals(false, ProjectionColor.aboveUsdLimit(1, "USD"))
        assertEquals(false, ProjectionColor.aboveUsdLimit(0, "USD"))
        assertEquals(false, ProjectionColor.aboveUsdLimit(-1, "USD"))
        assertNull(ProjectionColor.aboveUsdLimit(50_100, "EUR"))
    }

    @Test
    fun financialDate_staysOnTheCalendarDateAcrossDaylightSaving() {
        listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1), LocalDate.of(2024, 2, 29), LocalDate.of(2026, 12, 31)).forEach { date ->
            assertEquals(date, FinancialDate.fromPickerMillis(FinancialDate.toPickerMillis(date)))
        }
        val zone = ZoneId.of("America/New_York")
        val springGap = LocalDate.of(2026, 3, 8).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 3, 8), FinancialDate.fromPickerMillis(FinancialDate.toPickerMillis(LocalDate.of(2026, 3, 8))))
        assertTrue(springGap != FinancialDate.toPickerMillis(LocalDate.of(2026, 3, 8)))
    }

    @Test
    fun validation_rejectsAmountsAndAccountsThatWouldCorruptABalance() {
        val draft = TransactionDraft(null, null, TransactionType.EXPENSE, 0, "Store", account.id, null, null, LocalDate.of(2026, 10, 1), "", false, false)
        assertEquals("Enter an amount greater than zero.", TransactionRules.validate(draft, account, null))
        val transfer = draft.copy(type = TransactionType.TRANSFER, amountMinor = 100, counterAccountId = account.id)
        assertEquals("Choose two different accounts.", TransactionRules.validate(transfer, account, account))
        assertEquals("Choose an account.", TransactionRules.validate(draft.copy(amountMinor = 100), null, null))
    }

    @Test
    fun safeToSpend_doesNotAddUnreceivedIncomeOrASecondCopyOfAPostedExpense() {
        val result = SafeToSpendEngine.calculate(
            SafeToSpendEngine.Input(
                liquidBalanceMinor = 90_000,
                committedPaymentsMinor = 5_000,
                minimumBufferMinor = 0,
                plannedTransfersMinor = 0,
                expectedIncomeMinor = 20_000,
                plannedDiscretionaryMinor = 0,
            ),
        )
        assertEquals(85_000L, result.amountMinor)
        assertFalse(result.lines.any { it.label.contains("Expected income") })
    }

    @Test
    fun monthEndRecurrence_landsOnTheRealDueDate() {
        val series = RecurringPayment(
            id = "rent", name = "Rent", accountId = account.id, categoryId = null,
            direction = CashFlowDirection.OUTFLOW, kind = ScheduledKind.BILL, amountMinor = 1, currency = "USD",
            frequency = RecurrenceFrequency.MONTHLY, intervalDays = null, dayOfMonth = 31, dayOfWeek = null,
            startOn = LocalDate.of(2024, 1, 31), endOn = null, priority = PaymentPriority.MANDATORY,
            incomeConfidence = IncomeConfidence.NONE, active = true, note = "",
        )
        val dates = RecurringSchedule.occurrencesBetween(series, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31))
        assertEquals(listOf(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31)), dates)
    }

    @Test
    fun octoberCycle_highlightsTheFullRangeAndSwitchesOnNovember20() {
        val settings = UserSettings("USD", 0, 30, true, CycleType.MONTHLY, LocalDate.of(2026, 1, 20), 20, 5)
        val october = CycleSchedule.containing(LocalDate.of(2026, 10, 25), settings)
        val novemberInside = CycleSchedule.containing(LocalDate.of(2026, 11, 10), settings)
        assertEquals(october, novemberInside)
        assertEquals(LocalDate.of(2026, 10, 20), october.start)
        assertEquals(LocalDate.of(2026, 11, 19), october.end)
        val octoberDays = CycleHighlight.datesInMonth(YearMonth.of(2026, 10), october)
        assertEquals(LocalDate.of(2026, 10, 20), octoberDays.first())
        assertEquals(LocalDate.of(2026, 10, 31), octoberDays.last())
        assertFalse(octoberDays.contains(LocalDate.of(2026, 10, 19)))
        val novemberDays = CycleHighlight.datesInMonth(YearMonth.of(2026, 11), october)
        assertEquals(LocalDate.of(2026, 11, 1), novemberDays.first())
        assertEquals(LocalDate.of(2026, 11, 19), novemberDays.last())
        assertFalse(novemberDays.contains(LocalDate.of(2026, 11, 20)))
        val next = CycleSchedule.containing(LocalDate.of(2026, 11, 20), settings)
        assertEquals(LocalDate.of(2026, 11, 20), next.start)
        assertEquals(LocalDate.of(2026, 12, 19), next.end)
        val selected = LocalDate.of(2026, 10, 25)
        val inCycle = CycleHighlight.of(selected, october, selected, true)
        assertTrue(inCycle.inCycle && inCycle.selected)
        val dayMode = CycleHighlight.of(selected, october, selected, false)
        assertFalse(dayMode.inCycle)
        assertTrue(dayMode.selected)
        val restored = CycleHighlight.of(selected, october, selected, true)
        assertTrue(restored.inCycle && restored.selected)
    }

    @Test
    fun periodTotals_countBoundaryDatesOnceAndCarryTheClosingBalanceForward() {
        val first = PayPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19))
        val second = PayPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 19))
        val income = tx("pay", 20_000, 20_000, TransactionType.INCOME, LocalDate.of(2026, 10, 20))
        val expense = tx("rent", 5_000, -5_000, TransactionType.EXPENSE, LocalDate.of(2026, 11, 19))
        val outsideBefore = tx("early", 9_000, 9_000, TransactionType.INCOME, LocalDate.of(2026, 10, 19))
        val outsideAfter = tx("late", 8_000, 8_000, TransactionType.INCOME, LocalDate.of(2026, 11, 20))
        val transferOut = tx("out", 3_000, -3_000, TransactionType.TRANSFER, LocalDate.of(2026, 10, 21))
        val cardPurchase = tx("card", 4_000, -4_000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 22)).copy(accountId = "card")
        val cardPayment = tx("paycard", 4_000, -4_000, TransactionType.PAYMENT, LocalDate.of(2026, 10, 28))
        val rows = listOf(income, expense, outsideBefore, outsideAfter, transferOut, cardPurchase, cardPayment)
        val card = account.copy(id = "card", type = AccountType.CREDIT_CARD, openingBalanceMinor = -10_000, includeInSafeToSpend = false)
        val open = PeriodLedger.report(LocalDate.of(2026, 11, 19), first, listOf(account, card), rows, emptyList(), emptyList())
        val checking = open.accounts.first { it.accountId == "checking" }
        assertEquals(20_000L, open.incomeMinor)
        assertEquals(5_000L, checking.expenseMinor)
        assertEquals(4_000L, open.accounts.first { it.accountId == "card" }.expenseMinor)
        assertEquals(109_000L, checking.startingMinor)
        assertEquals(117_000L, checking.actualMinor)
        val next = PeriodLedger.report(LocalDate.of(2026, 11, 20), second, listOf(account, card), rows, emptyList(), emptyList())
        val nextChecking = next.accounts.first { it.accountId == "checking" }
        assertEquals(117_000L, nextChecking.startingMinor)
        assertEquals(8_000L, next.incomeMinor)
        assertEquals(0L, nextChecking.expenseMinor)
        val loss = tx("loss", 150_000, -150_000, TransactionType.EXPENSE, LocalDate.of(2026, 10, 21))
        val drained = PeriodLedger.report(LocalDate.of(2026, 11, 19), first, listOf(account), listOf(loss), emptyList(), emptyList())
        val following = PeriodLedger.report(LocalDate.of(2026, 11, 20), second, listOf(account), listOf(loss), emptyList(), emptyList())
        assertEquals(-50_000L, drained.accounts.single().actualMinor)
        assertEquals(-50_000L, following.accounts.single().startingMinor)
    }

    @Test
    fun undoPopup_staysVisibleForAboutTwoSeconds() {
        assertTrue(UndoTiming.VISIBLE_MILLIS in 1_500L..2_000L)
    }

    private fun asOf(rows: List<LedgerTransaction>, date: LocalDate) = BalanceMath.balanceAsOf(account, rows, date)

    private fun tx(id: String, amount: Long, effect: Long, type: TransactionType, date: LocalDate) = LedgerTransaction(
        id = id,
        accountId = account.id,
        counterAccountId = null,
        linkId = null,
        amountMinor = amount,
        balanceEffectMinor = effect,
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
}
