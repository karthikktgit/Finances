package com.ledgerline.app.finance

import com.ledgerline.app.data.local.database.Migrations
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.CashFlowDirection
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
import com.ledgerline.app.finance.account.AccountDeletion
import com.ledgerline.app.finance.account.DebtLink
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.export.LedgerExport
import com.ledgerline.app.finance.export.XlsxReports
import com.ledgerline.app.finance.forecast.DailyAccountForecast
import com.ledgerline.app.finance.forecast.ForecastSession
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AccountDeletionTest {
    private val today = LocalDate.of(2026, 10, 12)
    private val checking = account("a", "Checking", AccountType.CHECKING, 10_000)
    private val savings = account("b", "Savings", AccountType.SAVINGS, 5_000)
    private val deletedChecking = checking.copy(active = false, deletedAtEpochMillis = 1_000)

    @Test
    fun emptyAccountHasNoImpact() {
        val impact = AccountDeletion.impact("a", emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(impact.empty)
        assertTrue(impact.lines().isEmpty())
        val plan = AccountDeletion.plan("a", emptyList(), emptyList(), emptyList())
        assertTrue(plan.pausedScheduleIds.isEmpty() && plan.unlinkedDebtIds.isEmpty() && plan.pausedBudgetIds.isEmpty())
    }

    @Test
    fun historyIsKeptLabeledAndLeavesActiveBalances() {
        val rows = listOf(
            tx("t1", "a", today.minusDays(3), 2_500, -2_500, TransactionType.EXPENSE),
            tx("t2", "b", today.minusDays(2), 1_000, -1_000, TransactionType.EXPENSE),
        )
        val before = rows.map { it.copy() }
        val impact = AccountDeletion.impact("a", rows, emptyList(), emptyList(), emptyList())
        assertEquals(1, impact.transactionCount)
        val live = AccountDeletion.live(rows, setOf("a"))
        assertEquals(listOf("t2"), live.map { it.id })
        // History itself is untouched: same dates, amounts, and meaning.
        assertEquals(before, rows)
        val all = listOf(deletedChecking, savings)
        assertEquals("Deleted account: Checking", AccountDeletion.label(rows[0].accountId, all))
        assertEquals("Savings", AccountDeletion.label("b", all))
    }

    @Test
    fun deletedAccountLabelDiffersFromUnassigned() {
        val all = listOf(deletedChecking, savings)
        assertEquals("Account not assigned", AccountDeletion.label("", all))
        assertEquals("Account not assigned", AccountDeletion.label(null, all))
        assertEquals("Account not assigned", AccountDeletion.label("missing", all))
        assertEquals("Deleted account: Checking", AccountDeletion.label("a", all))
    }

    @Test
    fun futureBillsAndSalaryStopWithoutBeingMarkedPaid() {
        val rent = recurring("rent", "a", CashFlowDirection.OUTFLOW, ScheduledKind.BILL)
        val salary = recurring("pay", "a", CashFlowDirection.INFLOW, ScheduledKind.INCOME)
        val gym = recurring("gym", "b", CashFlowDirection.OUTFLOW, ScheduledKind.SUBSCRIPTION)
        val series = listOf(rent, salary, gym)
        val plan = AccountDeletion.plan("a", series, emptyList(), emptyList())
        assertEquals(setOf("rent", "pay"), plan.pausedScheduleIds)
        val paused = series.map { if (it.id in plan.pausedScheduleIds) it.copy(active = false) else it }
        val projected = OccurrenceMaterializer.project(paused, emptyList(), emptySet(), today, today.plusMonths(3), today)
        assertTrue(projected.none { it.accountId == "a" })
        assertTrue(projected.none { it.state == OccurrenceState.PAID })
        assertTrue(projected.any { it.recurringId == "gym" })
        assertEquals(listOf("rent", "pay"), AccountDeletion.needsAccount(paused, setOf("a")).map { it.id })
    }

    @Test
    fun subscriptionsOnTheAccountAreCountedAndPaused() {
        val stream = recurring("stream", "a", CashFlowDirection.OUTFLOW, ScheduledKind.SUBSCRIPTION)
        val old = recurring("old", "a", CashFlowDirection.OUTFLOW, ScheduledKind.SUBSCRIPTION).copy(active = false)
        val impact = AccountDeletion.impact("a", emptyList(), listOf(stream, old), emptyList(), emptyList())
        assertEquals(1, impact.scheduleCount)
        assertEquals(setOf("stream"), AccountDeletion.plan("a", listOf(stream, old), emptyList(), emptyList()).pausedScheduleIds)
    }

    @Test
    fun debtLinksAreFlaggedForReviewWithoutTouchingOtherDebts() {
        val debts = listOf(
            DebtLink("loan", accountId = "a", paymentAccountId = null),
            DebtLink("card", accountId = "c", paymentAccountId = "a"),
            DebtLink("other", accountId = "b", paymentAccountId = "b"),
        )
        val impact = AccountDeletion.impact("a", emptyList(), emptyList(), debts, emptyList())
        assertEquals(2, impact.debtCount)
        assertEquals(setOf("loan", "card"), AccountDeletion.plan("a", emptyList(), debts, emptyList()).unlinkedDebtIds)
        val note = AccountDeletion.reviewNote("Car loan", "Checking", today)
        assertTrue(note.startsWith("Car loan\n"))
        assertTrue(note.contains("\"Checking\" was deleted on 2026-10-12"))
    }

    @Test
    fun accountScopedBudgetsAreTurnedOff() {
        val budgets = listOf(budget("food", "a"), budget("all", null), budget("fun", "b"))
        val plan = AccountDeletion.plan("a", emptyList(), emptyList(), budgets)
        assertEquals(setOf("food"), plan.pausedBudgetIds)
    }

    @Test
    fun transfersKeepTheOtherAccountsSide() {
        val outLeg = tx("x1", "a", today.minusDays(1), 3_000, -3_000, TransactionType.TRANSFER).copy(counterAccountId = "b", linkId = "L")
        val inLeg = tx("x2", "b", today.minusDays(1), 3_000, 3_000, TransactionType.TRANSFER).copy(counterAccountId = "a", linkId = "L")
        val rows = listOf(outLeg, inLeg)
        val impact = AccountDeletion.impact("a", rows, emptyList(), emptyList(), emptyList())
        assertEquals(2, impact.transferCount)
        val savingsBefore = BalanceMath.currentBalanceMinor(savings, rows)
        val live = AccountDeletion.live(rows, setOf("a"))
        assertEquals(listOf("x2"), live.map { it.id })
        assertEquals(savingsBefore, BalanceMath.currentBalanceMinor(savings, live))
        assertEquals(8_000L, savingsBefore)
    }

    @Test
    fun deletedAccountLeavesSelectorsAndForecasts() {
        val activeIds = setOf("b")
        val session = ForecastSession(touched = true, chosen = setOf("a", "b"))
        assertEquals(setOf("b"), session.selection(activeIds))
        val rows = listOf(tx("t1", "a", today, 900, -900, TransactionType.EXPENSE))
        val forecast = DailyAccountForecast.project(
            today = today,
            dayCount = 30,
            accounts = listOf(savings),
            selectedIds = setOf("a", "b"),
            transactions = AccountDeletion.live(rows, setOf("a")),
            events = emptyList(),
            dailyDiscretionaryMinor = 0,
        )
        assertEquals(listOf("b"), forecast.selectedIds)
        assertEquals(5_000L, forecast.combined!!.last().endMinor)
    }

    @Test
    fun unrelatedAccountsAreUnchanged() {
        val rows = listOf(
            tx("t1", "a", today, 900, -900, TransactionType.EXPENSE),
            tx("t2", "b", today, 400, -400, TransactionType.EXPENSE),
        )
        val before = BalanceMath.currentBalanceMinor(savings, rows)
        assertEquals(before, BalanceMath.currentBalanceMinor(savings, AccountDeletion.live(rows, setOf("a"))))
        val series = listOf(recurring("gym", "b", CashFlowDirection.OUTFLOW, ScheduledKind.SUBSCRIPTION))
        assertTrue(AccountDeletion.plan("a", series, emptyList(), emptyList()).pausedScheduleIds.isEmpty())
    }

    @Test
    fun cancelReadsImpactWithoutChangingAnything() {
        val rows = listOf(tx("t1", "a", today, 900, -900, TransactionType.EXPENSE))
        val series = listOf(recurring("rent", "a", CashFlowDirection.OUTFLOW, ScheduledKind.BILL))
        val snapshot = rows.toList() to series.toList()
        AccountDeletion.impact("a", rows, series, emptyList(), emptyList())
        assertEquals(snapshot, rows to series)
        assertTrue(series.single().active)
    }

    @Test
    fun exportsLabelDeletedAccountsAndDoNotCrash() {
        val rows = listOf(
            tx("t1", "a", today, 900, -900, TransactionType.EXPENSE),
            tx("t2", "b", today, 400, -400, TransactionType.EXPENSE),
        )
        val accounts = listOf(deletedChecking, savings)
        val csv = LedgerExport.csv(rows, accounts, emptyList())
        assertTrue(csv.contains("\"Deleted account: Checking\""))
        assertTrue(csv.contains("\"Savings\""))
        val bytes = XlsxReports.ledger(today, "test", accounts, rows, emptyList(), null, null)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun migrationOnlyAddsTheDeletedColumn() {
        assertEquals(6, Migrations.MIGRATION_6_7.startVersion)
        assertEquals(7, Migrations.MIGRATION_6_7.endVersion)
        // 7 to 8 follows it and only adds the captured-spend table.
        assertEquals(Migrations.MIGRATION_6_7, Migrations.ALL[5])
        assertEquals(7, Migrations.MIGRATION_7_8.startVersion)
        assertEquals(8, Migrations.MIGRATION_7_8.endVersion)
        assertEquals(Migrations.MIGRATION_7_8, Migrations.ALL[6])
        // 8 to 9 only adds the autopay column to bills.
        assertEquals(Migrations.MIGRATION_8_9, Migrations.ALL[7])
        assertFalse(checking.deleted)
        assertTrue(deletedChecking.deleted)
    }

    private fun account(id: String, name: String, type: AccountType, opening: Long) = Account(
        id = id, name = name, institution = "", type = type, lastFour = null, openingBalanceMinor = opening,
        creditLimitMinor = null, currency = "USD", active = true, includeInNetWorth = true,
        includeInSafeToSpend = true, syncStatus = SyncStatus.MANUAL, lastSyncedAtEpochMillis = null, syncError = null, sortOrder = 0,
    )

    private fun recurring(id: String, accountId: String, direction: CashFlowDirection, kind: ScheduledKind) = RecurringPayment(
        id = id, name = id, accountId = accountId, categoryId = null, direction = direction, kind = kind, amountMinor = 1_000,
        currency = "USD", frequency = RecurrenceFrequency.MONTHLY, intervalDays = null, dayOfMonth = 20, dayOfWeek = null,
        startOn = LocalDate.of(2026, 1, 20), endOn = null, priority = PaymentPriority.MANDATORY,
        incomeConfidence = IncomeConfidence.HIGH, active = true, note = "",
    )

    private fun budget(id: String, accountId: String?) =
        Budget(id, id, null, accountId, BudgetPeriod.MONTHLY, 10_000, "USD", reservesSafeToSpend = false, active = true)

    private fun tx(id: String, accountId: String, date: LocalDate, amount: Long, effect: Long, type: TransactionType) = LedgerTransaction(
        id = id, accountId = accountId, counterAccountId = null, linkId = null, amountMinor = amount, balanceEffectMinor = effect,
        currency = "USD", type = type, merchantRaw = id, merchantNormalized = id, merchantId = null, description = id,
        categoryId = null, occurredOn = date, postedOn = date, detectionSource = DetectionSource.MANUAL, confidencePercent = 100,
        recurringPaymentId = null, status = TransactionStatus.CONFIRMED, userConfirmed = true, note = "", fingerprint = id,
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
    )
}
