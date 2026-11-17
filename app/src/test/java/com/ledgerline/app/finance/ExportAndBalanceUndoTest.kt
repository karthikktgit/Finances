package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.export.XlsxReports
import com.ledgerline.app.finance.period.PayPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.zip.ZipInputStream

class ExportAndBalanceUndoTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val period = PayPeriod.containing(today)
    private val bankA = account("a", "Bank A", 100_000)
    private val bankB = account("b", "Bank B", 40_000)
    private val early = tx("early", "a", TransactionType.INCOME, 10_000, 10_000, LocalDate.of(2026, 10, 1))
    private val salary = tx("salary", "a", TransactionType.INCOME, 200_000, 200_000, LocalDate.of(2026, 10, 20))
    private val transferOut = tx("xfer-a", "a", TransactionType.TRANSFER, 30_000, -30_000, LocalDate.of(2026, 10, 21), counter = "b")
    private val transferIn = tx("xfer-b", "b", TransactionType.TRANSFER, 30_000, 30_000, LocalDate.of(2026, 10, 21), counter = "a")
    private val spendB = tx("spend-b", "b", TransactionType.EXPENSE, 5_000, -5_000, LocalDate.of(2026, 10, 22))
    private val rows = listOf(early, salary, transferOut, transferIn, spendB)
    private val bill = RecurringPayment(
        "rent", "Rent", "a", null, CashFlowDirection.OUTFLOW, ScheduledKind.BILL, 50_000, "USD",
        RecurrenceFrequency.ONCE, null, 5, null, LocalDate.of(2026, 11, 5), null,
        PaymentPriority.MANDATORY, IncomeConfidence.NONE, true, "", 1,
    )

    @Test
    fun currentPeriodAllAccounts_excludesHistoryBeforeThe20th() {
        val sheet = details(XlsxReports.ledger(today, "0.2.0", listOf(bankA, bankB), rows, listOf(bill), period, null))
        assertTrue(sheet.contains("salary"))
        assertFalse(sheet.contains("early"))
        assertTrue(sheet.contains("xfer-a"))
        assertTrue(sheet.contains("spend-b"))
        val info = reportInfo(XlsxReports.ledger(today, "0.2.0", listOf(bankA, bankB), rows, listOf(bill), period, null))
        assertTrue(info.contains("2026-10-20 to 2026-11-19"))
        assertTrue(info.contains("All accounts"))
        assertTrue(info.contains(">2000.00<"))
        assertFalse(info.contains(">2100.00<"))
    }

    @Test
    fun allDatesAllAccounts_includesHistoryBeforeThePeriod() {
        val sheet = details(XlsxReports.ledger(today, "0.2.0", listOf(bankA, bankB), rows, listOf(bill), null, null))
        assertTrue(sheet.contains("early"))
        assertTrue(sheet.contains("salary"))
        assertTrue(sheet.contains("spend-b"))
        val info = reportInfo(XlsxReports.ledger(today, "0.2.0", listOf(bankA, bankB), rows, listOf(bill), null, null))
        assertTrue(info.contains("All dates"))
    }

    @Test
    fun selectedAccount_keepsOnlyThatAccountsRows() {
        val periodSheet = details(XlsxReports.ledger(today, "0.2.0", listOf(bankA, bankB), rows, listOf(bill), period, "a"))
        assertTrue(periodSheet.contains("salary"))
        assertTrue(periodSheet.contains("xfer-a"))
        assertFalse(periodSheet.contains("spend-b"))
        assertFalse(periodSheet.contains("xfer-b"))
        val history = details(XlsxReports.ledger(today, "0.2.0", listOf(bankA, bankB), rows, listOf(bill), null, "a"))
        assertTrue(history.contains("early"))
        assertFalse(history.contains("spend-b"))
        val info = reportInfo(XlsxReports.ledger(today, "0.2.0", listOf(bankA, bankB), rows, listOf(bill), period, "a"))
        assertTrue(info.contains("Bank A"))
    }

    @Test
    fun emptyAccount_exportsAClearEmptyDetailSheet() {
        val empty = account("c", "PayPal", 0)
        val sheet = details(XlsxReports.ledger(today, "0.2.0", listOf(bankA, empty), rows, emptyList(), period, "c"))
        assertTrue(sheet.contains("No transactions in this report"))
        assertFalse(sheet.contains("salary"))
    }

    @Test
    fun undoingAnAccountBalanceAdjustmentRestoresOnlyThatAccount() {
        val adjustment = tx("adj", "a", TransactionType.ADJUSTMENT, 25_000, 25_000, today)
        val other = spendB
        val withChange = listOf(salary, adjustment, other)
        assertEquals(100_000 + 200_000 + 25_000, BalanceMath.currentBalanceMinor(bankA, withChange))
        val undone = withChange.map { if (it.id == "adj") it.copy(deletedAtEpochMillis = 1L) else it }
        assertEquals(100_000 + 200_000, BalanceMath.currentBalanceMinor(bankA, undone))
        assertEquals(40_000 - 5_000, BalanceMath.currentBalanceMinor(bankB, undone))
    }

    private fun details(bytes: ByteArray): String = sheet(bytes, "xl/worksheets/sheet1.xml")

    private fun reportInfo(bytes: ByteArray): String = sheet(bytes, "xl/worksheets/sheet4.xml")

    private fun sheet(bytes: ByteArray, name: String): String = ZipInputStream(bytes.inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.first { it.name == name }
        zip.readBytes().toString(Charsets.UTF_8)
    }

    private fun account(id: String, name: String, opening: Long) = Account(
        id, name, "", AccountType.CHECKING, null, opening, null, "USD", true, true, true,
        SyncStatus.MANUAL, null, null, 0,
    )

    private fun tx(
        id: String,
        accountId: String,
        type: TransactionType,
        amount: Long,
        effect: Long,
        date: LocalDate,
        counter: String? = null,
    ) = LedgerTransaction(
        id, accountId, counter, null, amount, effect, "USD", type, id, id, null, id, null,
        date, date, DetectionSource.MANUAL, 100, null, TransactionStatus.CONFIRMED, true, "", "fp", 0, 0, null,
    )
}
