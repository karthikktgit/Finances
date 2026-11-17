package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.dashboard.UpcomingWindow
import com.ledgerline.app.finance.forecast.ForecastSession
import com.ledgerline.app.ui.components.CompactButtonSpec
import com.ledgerline.app.ui.navigation.TabNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeNavigationTest {
    private val today = LocalDate.of(2026, 10, 12)

    @Test
    fun cashFlowOpensForecastOptionsAndViewForecastKeepsTheSelection() {
        val active = setOf("checking", "savings", "card")
        val configured = ForecastSession()
            .toggle("card", active)
            .chooseDays(60)
        assertEquals(30, ForecastSession().days)
        assertEquals(setOf("checking", "savings"), configured.selection(active))
        assertEquals(60, configured.days)
        assertEquals(configured.selection(active), configured.chooseDays(30).selection(active))
    }

    @Test
    fun upcomingButtonsUseInclusiveLocalRanges() {
        assertEquals(LocalDate.of(2026, 10, 18), UpcomingWindow.inclusiveEnd(today, 7))
        assertEquals(LocalDate.of(2026, 10, 25), UpcomingWindow.inclusiveEnd(today, 14))
        assertEquals(LocalDate.of(2026, 11, 10), UpcomingWindow.inclusiveEnd(today, 30))
        val items = listOf(
            event("rent", today.plusDays(6), "checking"),
            event("far", today.plusDays(7), "checking"),
            event("rent", today.plusDays(6), "checking"),
        )
        val week = UpcomingWindow.scheduled(today, 7, items)
        assertEquals(listOf("rent"), week.map { it.name })
        assertEquals(2, UpcomingWindow.scheduled(today, 14, items).size)
    }

    @Test
    fun postedTransactionsStayCompletedAndAreNotMergedIntoUnpaidSchedules() {
        val scheduled = UpcomingWindow.scheduled(today, 7, listOf(event("rent", today.plusDays(1), "checking")))
        val posted = UpcomingWindow.posted(today, 7, listOf(tx("groceries", today.plusDays(1))))
        assertEquals("rent", scheduled.single().name)
        assertFalse(scheduled.single().overdue)
        assertEquals("groceries", posted.single().id)
    }

    @Test
    fun compactButtonsUseOneCornerRadiusAndTightPadding() {
        assertEquals(8, CompactButtonSpec.CORNER_RADIUS_DP)
        assertEquals(10, CompactButtonSpec.HORIZONTAL_PADDING_DP)
        assertEquals(6, CompactButtonSpec.VERTICAL_PADDING_DP)
    }

    @Test
    fun calendarViewAllAndTheCalendarTabUseTheSameOpenAction() {
        assertTrue(TabNavigation.opensDestination(currentIsTarget = false))
        assertFalse(TabNavigation.opensDestination(currentIsTarget = true))
    }

    @Test
    fun homeFromCalendarPopsWithoutRestoringTheCalendar() {
        assertFalse(TabNavigation.restoreSavedState(targetIsHome = true))
        assertTrue(TabNavigation.restoreSavedState(targetIsHome = false))
    }

    @Test
    fun repeatingHomeAndCalendarDoesNotOpenASecondCopy() {
        assertFalse(TabNavigation.opensDestination(currentIsTarget = true))
        assertTrue(TabNavigation.opensDestination(currentIsTarget = false))
    }

    private fun event(name: String, date: LocalDate, accountId: String) = ScheduledOccurrence(
        recurringId = name,
        name = name,
        date = date,
        amountMinor = 1_000,
        direction = CashFlowDirection.OUTFLOW,
        priority = PaymentPriority.MANDATORY,
        kind = ScheduledKind.BILL,
        accountId = accountId,
        accountName = accountId,
        incomeConfidence = IncomeConfidence.HIGH,
        countedInSafeToSpend = true,
        liquidDeltaMinor = -1_000,
    )

    private fun tx(id: String, date: LocalDate) = LedgerTransaction(
        id = id,
        accountId = "checking",
        counterAccountId = null,
        linkId = null,
        amountMinor = 500,
        balanceEffectMinor = -500,
        currency = "USD",
        type = TransactionType.EXPENSE,
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
