package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.finance.forecast.AccountForecast
import com.ledgerline.app.finance.forecast.ForecastAccounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AccountForecastTest {
    private val today = LocalDate.of(2026, 10, 12)
    private val accounts = listOf(
        account("a", "Checking", AccountType.CHECKING, 40_000),
        account("b", "Savings", AccountType.SAVINGS, 80_000),
        account("c", "Card", AccountType.CREDIT_CARD, -5_000),
        account("d", "Loan", AccountType.LOAN, -20_000),
    )
    private val openings = accounts.associate { it.id to it.openingBalanceMinor }

    @Test
    fun oneAccount_usesOnlyThatAccountsEvents() {
        val result = project(setOf("a"), events())
        assertEquals(listOf("a"), result.selectedIds)
        assertEquals(40_000L, result.accounts.single().forecast.openingMinor)
        assertEquals(46_000L, result.accounts.single().forecast.endingMinor)
        assertEquals(46_000L, result.combined!!.endingMinor)
    }

    @Test
    fun twoOfFour_excludesTheOtherAccounts() {
        val result = project(setOf("a", "c"), events())
        assertEquals(listOf("a", "c"), result.selectedIds)
        assertEquals(40_000L - 5_000L, result.combined!!.openingMinor)
        assertTrue(result.accounts.none { it.accountId == "b" || it.accountId == "d" })
    }

    @Test
    fun everySubsetOfFourAccounts_matchesTheSumOfThoseOpenings() {
        val ids = accounts.map { it.id }
        val subsets = (0 until 16).map { mask -> ids.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet() }
        assertEquals(16, subsets.size)
        subsets.forEach { selected ->
            val result = project(selected, events())
            if (selected.isEmpty()) {
                assertNull(result.combined)
            } else {
                val expected = accounts.filter { it.id in selected }.sumOf { it.openingBalanceMinor }
                assertEquals(expected, result.combined!!.openingMinor)
                assertEquals(selected.sorted(), result.selectedIds.sorted())
            }
        }
    }

    @Test
    fun selectAllAndDeselectAll() {
        val ids = accounts.map { it.id }.toSet()
        assertEquals(ids, ForecastAccounts.reconcile(touched = true, selected = ids, activeIds = ids))
        assertEquals(emptySet<String>(), ForecastAccounts.reconcile(touched = true, selected = emptySet(), activeIds = ids))
    }

    @Test
    fun noneSelected_hasNoTotals() {
        val result = project(emptySet(), events())
        assertNull(result.combined)
        assertTrue(result.accounts.isEmpty())
    }

    @Test
    fun addingOrRemovingAnAccount_refreshesTheDefaultAndDropsAMissingId() {
        val before = setOf("a", "b", "c", "d")
        val added = before + "e"
        assertEquals(added, ForecastAccounts.reconcile(touched = false, selected = before, activeIds = added))
        val removed = before - "d"
        assertEquals(setOf("a", "b"), ForecastAccounts.reconcile(touched = true, selected = setOf("a", "b", "d"), activeIds = removed))
    }

    @Test
    fun changingTheHorizon_keepsTheSameSelectedAccounts() {
        val selected = setOf("a", "c")
        val one = project(selected, events(), 1)
        val three = project(selected, events(), 3)
        val six = project(selected, events(), 6)
        assertEquals(one.selectedIds, three.selectedIds)
        assertEquals(one.selectedIds, six.selectedIds)
        assertEquals(1, one.combined!!.months.size)
        assertEquals(3, three.combined!!.months.size)
        assertEquals(6, six.combined!!.months.size)
    }

    @Test
    fun transferBetweenTwoSelectedAccounts_isNotIncomeAndEachAccountKeepsItsOwnLeg() {
        val out = event("a", "move-out", 5_000, CashFlowDirection.OUTFLOW, ScheduledKind.TRANSFER)
        val into = event("b", "move-in", 5_000, CashFlowDirection.INFLOW, ScheduledKind.TRANSFER)
        val result = project(setOf("a", "b"), listOf(out, into))
        val checking = result.accounts.first { it.accountId == "a" }.forecast
        val savings = result.accounts.first { it.accountId == "b" }.forecast
        assertEquals(35_000L, checking.endingMinor)
        assertEquals(85_000L, savings.endingMinor)
        assertEquals(0L, result.combined!!.incomingMinor)
        assertEquals(0L, result.combined!!.outgoingMinor)
        assertEquals(120_000L, result.combined!!.endingMinor)
    }

    @Test
    fun transferAcrossTheSelection_movesOnlyTheSelectedAccount() {
        val out = event("a", "move-out", 5_000, CashFlowDirection.OUTFLOW, ScheduledKind.TRANSFER)
        val result = project(setOf("a"), listOf(out))
        assertEquals(35_000L, result.combined!!.endingMinor)
        assertEquals(0L, result.combined!!.outgoingMinor)
        assertTrue(result.accounts.none { it.accountId == "b" })
    }

    @Test
    fun cardPurchaseAndPayment_areNotBothCountedAsSpending() {
        val payment = event("a", "card-payment", 5_000, CashFlowDirection.OUTFLOW, ScheduledKind.DEBT)
        val result = project(setOf("a", "c"), listOf(payment))
        val checking = result.accounts.first { it.accountId == "a" }.forecast
        val card = result.accounts.first { it.accountId == "c" }.forecast
        assertEquals(5_000L, checking.outgoingMinor)
        assertEquals(35_000L, checking.endingMinor)
        assertEquals(0L, card.outgoingMinor)
        assertEquals(-5_000L, card.endingMinor)
        assertEquals(30_000L, result.combined!!.endingMinor)
    }

    @Test
    fun incomeSubscriptionBillDebtAndRefund_stayOnTheirAccount() {
        val rows = listOf(
            event("a", "pay", 10_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
            event("a", "netflix", 1_000, CashFlowDirection.OUTFLOW, ScheduledKind.SUBSCRIPTION),
            event("a", "rent", 4_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL),
            event("a", "loan", 2_000, CashFlowDirection.OUTFLOW, ScheduledKind.DEBT),
            event("a", "return", 500, CashFlowDirection.INFLOW, ScheduledKind.OTHER),
        )
        val result = project(setOf("a"), rows)
        assertEquals(10_500L, result.accounts.single().forecast.incomingMinor)
        assertEquals(7_000L, result.accounts.single().forecast.outgoingMinor)
        assertEquals(43_500L, result.accounts.single().forecast.endingMinor)
    }

    @Test
    fun oneAccountCanGoNegativeWhileTheCombinedSelectionStaysPositive() {
        val bill = event("a", "rent", 50_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL)
        val result = project(setOf("a", "b"), listOf(bill), 1)
        val checking = result.accounts.first { it.accountId == "a" }
        assertTrue(checking.forecast.months.single().negative)
        assertTrue(result.combined!!.endingMinor > 0)
    }

    @Test
    fun homeAndMoreShareTheSameProjection() {
        val fromHome = project(setOf("a", "b"), events(), 3)
        val fromMore = project(setOf("a", "b"), events(), 3)
        assertEquals(fromHome.combined!!.endingMinor, fromMore.combined!!.endingMinor)
        assertEquals(fromHome.selectedIds, fromMore.selectedIds)
    }

    @Test
    fun anUntouchedSelectionFollowsTheActiveAccounts() {
        val ids = setOf("a", "b")
        assertEquals(ids, ForecastAccounts.reconcile(touched = false, selected = emptySet(), activeIds = ids))
        assertEquals(setOf("a"), ForecastAccounts.reconcile(touched = true, selected = setOf("a"), activeIds = ids))
    }

    private fun project(selected: Set<String>, rows: List<ScheduledOccurrence>, months: Int = 1) =
        AccountForecast.project(today, months, accounts, selected, openings, rows, 0)

    private fun events() = listOf(
        event("a", "pay", 10_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
        event("a", "rent", 4_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL),
        event("b", "save", 1_000, CashFlowDirection.INFLOW, ScheduledKind.INCOME),
        event("c", "charge", 2_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL),
        event("", "loose", 9_000, CashFlowDirection.OUTFLOW, ScheduledKind.BILL),
    )

    private fun event(accountId: String, name: String, amount: Long, direction: CashFlowDirection, kind: ScheduledKind) =
        ScheduledOccurrence(
            recurringId = name,
            name = name,
            date = LocalDate.of(2026, 10, 20),
            amountMinor = amount,
            direction = direction,
            priority = PaymentPriority.MANDATORY,
            kind = kind,
            accountId = accountId,
            accountName = if (accountId.isBlank()) "Account not assigned" else accountId,
            incomeConfidence = IncomeConfidence.HIGH,
            countedInSafeToSpend = false,
            liquidDeltaMinor = 0,
            overdue = false,
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
}
