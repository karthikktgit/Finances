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
import com.ledgerline.app.finance.transactions.FeedRow
import com.ledgerline.app.finance.transactions.PaymentFilter
import com.ledgerline.app.finance.transactions.TransactionFeed
import com.ledgerline.app.ui.transactions.DateRange
import com.ledgerline.app.ui.transactions.TransactionFilter
import com.ledgerline.app.ui.transactions.matches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionStatusFilterTest {
    private val today = LocalDate.of(2026, 10, 25)

    private val paid = tx("paid", TransactionStatus.CONFIRMED)
    private val reconciled = tx("rec", TransactionStatus.RECONCILED)
    private val modified = tx("mod", TransactionStatus.MODIFIED)
    private val pending = tx("pend", TransactionStatus.DETECTED)
    private val ignored = tx("ign", TransactionStatus.IGNORED)
    private val duplicate = tx("dup", TransactionStatus.DUPLICATE)
    private val removed = tx("gone", TransactionStatus.CONFIRMED).copy(deletedAtEpochMillis = 5)
    private val all = listOf(paid, reconciled, modified, pending, ignored, duplicate, removed)

    private fun ids(filter: TransactionFilter) = all.filter { it.matches(filter, today) }.map { it.id }

    @Test
    fun allShowsEveryLiveTransaction() {
        assertEquals(listOf("paid", "rec", "mod", "pend", "ign", "dup"), ids(TransactionFilter()))
    }

    @Test
    fun paidShowsOnlyCompletedTransactions() {
        assertEquals(listOf("paid", "rec", "mod"), ids(TransactionFilter(status = PaymentFilter.PAID)))
    }

    @Test
    fun notPaidShowsPendingTransactions() {
        assertEquals(listOf("pend"), ids(TransactionFilter(status = PaymentFilter.UNPAID)))
    }

    @Test
    fun ignoredAndDuplicateRowsAreInNeitherStatusGroup() {
        assertFalse("ign" in ids(TransactionFilter(status = PaymentFilter.PAID)) || "ign" in ids(TransactionFilter(status = PaymentFilter.UNPAID)))
        assertFalse("dup" in ids(TransactionFilter(status = PaymentFilter.PAID)) || "dup" in ids(TransactionFilter(status = PaymentFilter.UNPAID)))
    }

    @Test
    fun returningToAllRestoresTheFullList() {
        val filtered = TransactionFilter(status = PaymentFilter.PAID)
        assertEquals(ids(TransactionFilter()), ids(filtered.copy(status = PaymentFilter.ALL)))
    }

    @Test
    fun filteringNeverChangesARecord() {
        val before = all.map { it.copy() }
        PaymentFilter.entries.forEach { ids(TransactionFilter(status = it)) }
        assertEquals(before, all)
        assertEquals(TransactionStatus.DETECTED, pending.status)
    }

    @Test
    fun statusWorksTogetherWithSearchAccountTypeAndDate() {
        val rows = listOf(
            tx("coffee", TransactionStatus.CONFIRMED, merchant = "Coffee Bar", account = "a"),
            tx("rent", TransactionStatus.CONFIRMED, merchant = "Landlord", account = "b"),
            tx("coffee2", TransactionStatus.DETECTED, merchant = "Coffee Bar", account = "a"),
            tx("old", TransactionStatus.CONFIRMED, merchant = "Coffee Bar", account = "a", date = LocalDate.of(2026, 8, 1)),
            tx("inc", TransactionStatus.CONFIRMED, merchant = "Coffee Bar", account = "a", type = TransactionType.INCOME),
        )
        val filter = TransactionFilter(
            query = "coffee", accountId = "a", type = TransactionType.EXPENSE, range = DateRange.THIS_MONTH, status = PaymentFilter.PAID,
        )
        assertEquals(listOf("coffee"), rows.filter { it.matches(filter, today) }.map { it.id })
        assertEquals(listOf("coffee2"), rows.filter { it.matches(filter.copy(status = PaymentFilter.UNPAID), today) }.map { it.id })
    }

    @Test
    fun categoryFilterWorksWithStatus() {
        val a = tx("a", TransactionStatus.CONFIRMED).copy(categoryId = "cat_groceries")
        val b = tx("b", TransactionStatus.CONFIRMED).copy(categoryId = "cat_rent")
        val filter = TransactionFilter(categoryScope = setOf("cat_groceries_gas", "cat_groceries", "cat_fuel"), status = PaymentFilter.PAID)
        assertEquals(listOf("a"), listOf(a, b).filter { it.matches(filter, today) }.map { it.id })
    }

    @Test
    fun aScheduledItemIsNotPaidUntilARealTransactionExists() {
        val bill = due("rent")
        assertFalse(bill.matches(TransactionFilter(status = PaymentFilter.PAID), today))
        assertTrue(bill.matches(TransactionFilter(status = PaymentFilter.UNPAID), today))
        assertTrue(bill.matches(TransactionFilter(status = PaymentFilter.ALL), today))
    }

    @Test
    fun scheduledItemsFollowTheOtherFilters() {
        val rent = due("rent", account = "a")
        val salary = due("pay", direction = CashFlowDirection.INFLOW, kind = ScheduledKind.INCOME, account = "b")
        assertTrue(rent.matches(TransactionFilter(type = TransactionType.EXPENSE), today))
        assertFalse(rent.matches(TransactionFilter(type = TransactionType.INCOME), today))
        assertTrue(salary.matches(TransactionFilter(type = TransactionType.INCOME), today))
        assertFalse(rent.matches(TransactionFilter(accountId = "b"), today))
        assertFalse(rent.matches(TransactionFilter(query = "salary"), today))
        assertTrue(rent.matches(TransactionFilter(query = "RENT"), today))
        assertFalse(rent.matches(TransactionFilter(unplannedOnly = true), today))
        assertFalse(rent.matches(TransactionFilter(range = DateRange.LAST_MONTH), today))
    }

    @Test
    fun theFeedKeepsPostedAndScheduledRowsApart() {
        val feed = TransactionFeed.merge(listOf(paid), listOf(due("rent")))
        assertEquals(2, feed.size)
        assertTrue(feed.any { it is FeedRow.Posted })
        assertTrue(feed.any { it is FeedRow.Scheduled })
        assertEquals(feed.map { it.key }.toSet().size, feed.size)
    }

    @Test
    fun scheduledWindowDropsDuplicatesAndFarFutureItems() {
        val rent = due("rent")
        val far = due("far", date = today.plusDays(TransactionFeed.SCHEDULE_DAYS + 1L))
        val window = TransactionFeed.scheduledWindow(today, listOf(rent, rent.copy(), far))
        assertEquals(listOf("rent"), window.map { it.recurringId })
    }

    private fun due(
        id: String,
        date: LocalDate = today.plusDays(3),
        direction: CashFlowDirection = CashFlowDirection.OUTFLOW,
        kind: ScheduledKind = ScheduledKind.BILL,
        account: String = "a",
    ) = ScheduledOccurrence(id, id, date, 5_000, direction, PaymentPriority.MANDATORY, kind, account, account, IncomeConfidence.HIGH, true, 0, false)

    private fun tx(
        id: String,
        status: TransactionStatus,
        merchant: String = id,
        account: String = "a",
        date: LocalDate = today.minusDays(2),
        type: TransactionType = TransactionType.EXPENSE,
    ) = LedgerTransaction(
        id, account, null, null, 1_000, -1_000, "USD", type, merchant, merchant.lowercase(), null, merchant, null,
        date, date, DetectionSource.MANUAL, 100, null, status, true, "", id, 0, 0, null,
    )
}
