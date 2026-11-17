package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.CycleType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.calendar.CalendarToday
import com.ledgerline.app.finance.forecast.DailyAccountForecast
import com.ledgerline.app.finance.forecast.ForecastSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class DailyForecastTest {
    private val today = LocalDate.of(2026, 10, 12)

    @Test
    fun todayButtonSelectsTheLocalDateAndItsMonth() {
        val zone = ZoneId.of("America/New_York")
        val instant = Instant.parse("2026-10-13T02:00:00Z")
        val local = instant.atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 10, 12), local)
        assertEquals(LocalDate.of(2026, 10, 13), instant.atZone(ZoneOffset.UTC).toLocalDate())
        val cursor = CalendarToday.go(local)
        assertEquals(YearMonth.of(2026, 10), cursor.month)
        assertEquals(local, cursor.selected)
    }

    @Test
    fun todayKeepsDayOrCycleModeAndHonorsHighlight() {
        val cycleMode = true
        val settings = UserSettings("USD", 0, 30, true, CycleType.MONTHLY, LocalDate.of(2026, 1, 20), 20, 5)
        val cursor = CalendarToday.go(today)
        assertEquals(today, cursor.selected)
        assertEquals(true, cycleMode)
        val highlighted = CalendarToday.highlightedCycle(true, cursor.selected, settings)
        assertTrue(highlighted!!.contains(today))
        assertNull(CalendarToday.highlightedCycle(false, cursor.selected, settings))
    }

    @Test
    fun thirtyAndSixtyDaysIncludeToday() {
        val thirty = DailyAccountForecast.dates(today, 30)
        val sixty = DailyAccountForecast.dates(today, 60)
        assertEquals(30, thirty.size)
        assertEquals(60, sixty.size)
        assertEquals(today, thirty.first())
        assertEquals(today, sixty.first())
        assertEquals(today.plusDays(29), thirty.last())
        assertEquals(today.plusDays(59), sixty.last())
    }

    @Test
    fun dailyBalancesCarryForward() {
        val result = project(setOf("a"), listOf(event("a", "rent", today.plusDays(1), 400, CashFlowDirection.OUTFLOW, ScheduledKind.BILL)), 30)
        val days = result.combined!!
        assertEquals(10_000L, days[0].endMinor)
        assertEquals(9_600L, days[1].endMinor)
        assertEquals(days[1].endMinor, days[2].endMinor)
    }

    @Test
    fun oneAccountAndTwoAccountsUseOnlyTheSelection() {
        val one = project(setOf("a"), events(), 30)
        val two = project(setOf("a", "b"), events(), 30)
        assertEquals(listOf("a"), one.selectedIds)
        assertEquals(10_600L, one.combined!!.last().endMinor)
        assertEquals(10_000L + 8_000L + 600L + 1_000L, two.combined!!.last().endMinor)
    }

    @Test
    fun transferBetweenSelectedAccountsDoesNotInflateTheCombinedBalance() {
        val rows = listOf(
            event("a", "out", today, 3_000, CashFlowDirection.OUTFLOW, ScheduledKind.TRANSFER),
            event("b", "in", today, 3_000, CashFlowDirection.INFLOW, ScheduledKind.TRANSFER),
        )
        val result = project(setOf("a", "b"), rows, 30)
        assertEquals(7_000L, result.accounts.first { it.accountId == "a" }.days.last().endMinor)
        assertEquals(11_000L, result.accounts.first { it.accountId == "b" }.days.last().endMinor)
        assertEquals(18_000L, result.combined!!.last().endMinor)
        assertEquals(0L, result.combined!!.sumOf { it.incomingMinor })
        assertEquals(0L, result.combined!!.sumOf { it.outgoingMinor })
    }

    @Test
    fun transferAcrossTheSelectionMovesOnlyTheSelectedAccount() {
        val result = project(setOf("a"), listOf(event("a", "out", today, 3_000, CashFlowDirection.OUTFLOW, ScheduledKind.TRANSFER)), 30)
        assertEquals(7_000L, result.combined!!.last().endMinor)
        assertTrue(result.accounts.none { it.accountId == "b" })
    }

    @Test
    fun cardPurchasePaymentRefundSalarySubscriptionAndDebtAreCountedOnce() {
        val rows = listOf(
            event("c", "purchase", today, 2_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL),
            event("a", "payment", today, 5_000, CashFlowDirection.OUTFLOW, ScheduledKind.DEBT),
            event("a", "pay", today, 10_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
            event("a", "netflix", today, 1_000, CashFlowDirection.OUTFLOW, ScheduledKind.SUBSCRIPTION),
            event("a", "return", today, 500, CashFlowDirection.INFLOW, ScheduledKind.OTHER),
        )
        val result = project(setOf("a", "c"), rows, 30)
        val checking = result.accounts.first { it.accountId == "a" }.days
        val card = result.accounts.first { it.accountId == "c" }.days
        assertEquals(10_000L + 10_000L + 500L - 5_000L - 1_000L, checking.last().endMinor)
        assertEquals(-5_000L - 2_000L, card.last().endMinor)
        assertEquals(checking.last().endMinor + card.last().endMinor, result.combined!!.last().endMinor)
        assertEquals(2_000L, card.first().outgoingMinor)
        assertEquals(6_000L, checking.first().outgoingMinor)
    }

    @Test
    fun aSettledOccurrenceIsNotAddedAgain() {
        val paid = tx("rent-paid", "a", today, 400, -400, TransactionType.EXPENSE, "rent")
        val result = DailyAccountForecast.project(
            today, 30, accounts(), setOf("a"), listOf(paid),
            listOf(event("a", "rent", today, 400, CashFlowDirection.OUTFLOW, ScheduledKind.BILL)),
            0,
        )
        assertEquals(9_600L, result.combined!!.first().endMinor)
    }

    @Test
    fun oneAccountCanBeNegativeWhileTheCombinedBalanceStaysPositive() {
        val result = project(setOf("a", "b"), listOf(event("a", "rent", today, 12_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL)), 30)
        assertTrue(result.accounts.first { it.accountId == "a" }.days.first().negative)
        assertFalse(result.combined!!.first().negative)
    }

    @Test
    fun monthYearLeapYearAndMonthEndDatesStayOnTheRealCalendar() {
        val leapStart = LocalDate.of(2024, 2, 1)
        val leap = DailyAccountForecast.dates(leapStart, 60)
        assertEquals(60, leap.size)
        assertTrue(leap.contains(LocalDate.of(2024, 2, 29)))
        assertEquals(LocalDate.of(2024, 3, 31), leap.last())
        val yearEnd = DailyAccountForecast.dates(LocalDate.of(2026, 12, 20), 30)
        assertEquals(LocalDate.of(2027, 1, 18), yearEnd.last())
        assertTrue(yearEnd.contains(LocalDate.of(2027, 1, 1)))
        val monthEnd = projectOn(
            LocalDate.of(2024, 1, 31),
            setOf("a"),
            listOf(event("a", "rent", LocalDate.of(2024, 1, 31), 100, CashFlowDirection.OUTFLOW, ScheduledKind.BILL)),
            30,
        )
        assertEquals(9_900L, monthEnd.combined!!.first { it.date == LocalDate.of(2024, 2, 29) }.endMinor)
    }

    @Test
    fun aFutureTransactionDoesNotChangeEarlierDaysAndABackdatedOneIsAlreadyInTheBaseline() {
        val future = tx("later", "a", today.plusDays(5), 2_000, -2_000, TransactionType.EXPENSE, null)
        val later = DailyAccountForecast.project(today, 30, accounts(), setOf("a"), listOf(future), emptyList(), 0)
        assertEquals(10_000L, later.combined!![4].endMinor)
        assertEquals(8_000L, later.combined!![5].endMinor)
        val backdated = tx("old", "a", today.minusDays(3), 1_000, -1_000, TransactionType.EXPENSE, null)
        val history = DailyAccountForecast.project(today, 30, accounts(), setOf("a"), listOf(backdated), emptyList(), 0)
        assertTrue(history.combined!!.all { it.endMinor == 9_000L })
        assertTrue(history.combined!!.none { it.date == backdated.occurredOn })
    }

    @Test
    fun changingDurationOrSelectionRebuildsTheSameDailyList() {
        val active = setOf("a", "b", "c")
        val session = ForecastSession().toggle("c", active).chooseDays(60)
        assertEquals(setOf("a", "b"), session.selection(active))
        assertEquals(session.selection(active), session.chooseDays(30).selection(active))
        val short = project(session.selection(active), events(), session.chooseDays(30).days)
        val long = project(session.selection(active), events(), 60)
        assertEquals(30, short.combined!!.size)
        assertEquals(60, long.combined!!.size)
        assertEquals(short.combined!!.last().endMinor, long.combined!![29].endMinor)
    }

    @Test
    fun aMissingAccountIsMarkedAndDoesNotChangeTheBalance() {
        val loose = event("", "loose", today, 9_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL)
        val result = project(setOf("a"), listOf(loose), 30)
        assertTrue(result.combined!!.first().unreliable)
        assertEquals(10_000L, result.combined!!.first().endMinor)
    }

    private fun project(selected: Set<String>, rows: List<ScheduledOccurrence>, days: Int) =
        projectOn(today, selected, rows, days)

    private fun projectOn(start: LocalDate, selected: Set<String>, rows: List<ScheduledOccurrence>, days: Int) =
        DailyAccountForecast.project(start, days, accounts(), selected, emptyList(), rows, 0)

    private fun events() = listOf(
        event("a", "pay", today, 1_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
        event("a", "rent", today.plusDays(2), 400, CashFlowDirection.OUTFLOW, ScheduledKind.BILL),
        event("b", "save", today, 1_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
    )

    private fun event(accountId: String, name: String, date: LocalDate, amount: Long, direction: CashFlowDirection, kind: ScheduledKind) =
        ScheduledOccurrence(
            recurringId = name,
            name = name,
            date = date,
            amountMinor = amount,
            direction = direction,
            priority = PaymentPriority.MANDATORY,
            kind = kind,
            accountId = accountId,
            accountName = if (accountId.isBlank()) "Account not assigned" else accountId,
            incomeConfidence = IncomeConfidence.HIGH,
            countedInSafeToSpend = false,
            liquidDeltaMinor = 0,
        )

    private fun accounts() = listOf(
        account("a", "Checking", AccountType.CHECKING, 10_000),
        account("b", "Savings", AccountType.SAVINGS, 8_000),
        account("c", "Card", AccountType.CREDIT_CARD, -5_000),
    )

    private fun account(id: String, name: String, type: AccountType, opening: Long) = Account(
        id = id,
        name = name,
        institution = "",
        type = type,
        lastFour = null,
        openingBalanceMinor = opening,
        creditLimitMinor = null,
        currency = "USD",
        active = true,
        includeInNetWorth = true,
        includeInSafeToSpend = type == AccountType.CHECKING || type == AccountType.SAVINGS,
        syncStatus = SyncStatus.MANUAL,
        lastSyncedAtEpochMillis = null,
        syncError = null,
        sortOrder = 0,
    )

    private fun tx(id: String, accountId: String, date: LocalDate, amount: Long, effect: Long, type: TransactionType, recurringId: String?) =
        LedgerTransaction(
            id = id,
            accountId = accountId,
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
            recurringPaymentId = recurringId,
            status = TransactionStatus.CONFIRMED,
            userConfirmed = true,
            note = "",
            fingerprint = id,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
            deletedAtEpochMillis = null,
        )
}
