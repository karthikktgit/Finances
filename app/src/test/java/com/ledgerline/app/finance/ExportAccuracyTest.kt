package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.export.LedgerExport
import com.ledgerline.app.finance.export.XlsxCell
import com.ledgerline.app.finance.export.XlsxReports
import com.ledgerline.app.finance.export.XlsxSheet
import com.ledgerline.app.finance.export.XlsxWorkbook
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.period.PeriodLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import java.util.zip.ZipInputStream

/** Storage is integer minor units. These tests check that every export converts to major units exactly once. */
class ExportAccuracyTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val bank = Account(
        "a", "Bank", "", AccountType.CHECKING, null, 100_000, null, "USD", true, true, true,
        SyncStatus.MANUAL, null, null, 0,
    )

    private val samples = listOf(
        1L to "0.01",
        100L to "1.00",
        4_500L to "45.00",
        123_456L to "1234.56",
        0L to "0.00",
        -4_500L to "-45.00",
        -1L to "-0.01",
        99_999_999_999L to "999999999.99",
    )

    @Test
    fun plainFormatConvertsMinorToMajorExactlyOnce() {
        samples.forEach { (minor, expected) -> assertEquals(expected, MoneyFormat.formatPlain(minor, "USD")) }
        assertEquals("500", MoneyFormat.formatPlain(500, "JPY"))
        assertEquals("1.234", MoneyFormat.formatPlain(1_234, "KWD"))
    }

    @Test
    fun plainFormatIgnoresTheDeviceLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1234.56", MoneyFormat.formatPlain(123_456, "USD"))
            assertEquals("1234.56", LedgerExport.csvNumber(123_456, "USD"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun csvAmountsAreBareDecimalNumbers() {
        samples.forEach { (minor, expected) ->
            val effect = if (minor < 0) minor else -minor
            val row = LedgerExport.csv(listOf(tx("t", abs(minor), effect, TransactionType.EXPENSE)), listOf(bank), emptyList())
                .lines()
            val columns = row[0].split(",")
            val values = row[1].split(",")
            val amount = values[columns.indexOf("amount")]
            val balanceEffect = values[columns.indexOf("balance_effect")]
            assertEquals(MoneyFormat.formatPlain(abs(minor), "USD"), amount)
            assertEquals(MoneyFormat.formatPlain(effect, "USD"), balanceEffect)
            // A spreadsheet must read both as numbers: no quotes, symbols, apostrophes, or separators.
            assertTrue(amount, amount.matches(Regex("-?[0-9]+\\.[0-9]{2}")))
            assertTrue(balanceEffect, balanceEffect.matches(Regex("-?[0-9]+\\.[0-9]{2}")))
            assertEquals(BigDecimal(expected.removePrefix("-")), BigDecimal(amount))
        }
    }

    @Test
    fun csvKeepsNegativeNumbersAsNumbersAndTextGuarded() {
        val csv = LedgerExport.csv(listOf(tx("t", 4_500, -4_500, TransactionType.EXPENSE, merchant = "-cmd")), listOf(bank), emptyList())
        assertTrue(csv.contains(",-45.00,"))
        assertFalse(csv.contains("'-45.00"))
        assertTrue(csv.contains("\"'-cmd\""))
        assertFalse(csv.contains("amount_minor"))
    }

    @Test
    fun csvKeepsTheCurrencyColumnAndDates() {
        val csv = LedgerExport.csv(listOf(tx("t", 4_500, -4_500, TransactionType.EXPENSE)), listOf(bank), emptyList())
        assertTrue(csv.lines()[0].contains("currency"))
        assertTrue(csv.lines()[1].contains("2026-10-20"))
        assertTrue(csv.lines()[1].contains("\"USD\""))
    }

    @Test
    fun workbookWritesNumericCellsNotText() {
        samples.forEach { (minor, expected) ->
            val bytes = XlsxWorkbook.write(listOf(XlsxSheet("S", listOf(listOf(XlsxCell.Money(minor, "USD"))))))
            val xml = part(bytes, "xl/worksheets/sheet1.xml")
            assertTrue(xml, xml.contains("<v>$expected</v>"))
            assertFalse(xml, xml.contains("inlineStr"))
            assertTrue(xml, xml.contains("s=\"4\""))
        }
    }

    @Test
    fun workbookDeclaresNumberFormatsAndDateCells() {
        val bytes = XlsxWorkbook.write(
            listOf(XlsxSheet("S", listOf(listOf(XlsxCell.Date(LocalDate.of(2026, 10, 20)), XlsxCell.Money(500, "JPY"), XlsxCell.Count(3), "text")))),
        )
        val styles = part(bytes, "xl/styles.xml")
        assertTrue(styles.contains("numFmtId=\"4\""))
        assertTrue(styles.contains("yyyy"))
        val sheet = part(bytes, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("<c r=\"A1\" s=\"1\"><v>${LocalDate.of(2026, 10, 20).toEpochDay() + 25_569}</v></c>"))
        assertTrue(sheet.contains("<c r=\"B1\" s=\"2\"><v>500</v></c>"))
        assertTrue(sheet.contains("<c r=\"C1\"><v>3</v></c>"))
        assertTrue(sheet.contains("t=\"inlineStr\""))
        assertTrue(part(bytes, "[Content_Types].xml").contains("/xl/styles.xml"))
        assertTrue(part(bytes, "xl/_rels/workbook.xml.rels").contains("styles.xml"))
    }

    @Test
    fun excelSerialMatchesKnownDates() {
        // 2000-01-01 is serial 36526 in Excel's 1900 system, and 1970-01-01 is 25569.
        assertEquals(36_526L, XlsxWorkbook.serial(LocalDate.of(2000, 1, 1)))
        assertEquals(25_569L, XlsxWorkbook.serial(LocalDate.of(1970, 1, 1)))
    }

    @Test
    fun reportAmountsAreMajorUnitsAndTotalsMatchTheApp() {
        val rows = listOf(
            tx("salary", 200_000, 200_000, TransactionType.INCOME, date = LocalDate.of(2026, 10, 20)),
            tx("coffee", 450, -450, TransactionType.EXPENSE, date = LocalDate.of(2026, 10, 22)),
            tx("shoes", 12_345, -12_345, TransactionType.EXPENSE, date = LocalDate.of(2026, 10, 23)),
            tx("refund", 2_000, 2_000, TransactionType.REFUND, date = LocalDate.of(2026, 10, 24)),
        )
        val period = PayPeriod.containing(today)
        val bytes = XlsxReports.ledger(today, "1", listOf(bank), rows, emptyList(), period, null)
        val details = part(bytes, "xl/worksheets/sheet1.xml")
        assertTrue(details.contains("<v>4.50</v>"))
        assertTrue(details.contains("<v>-4.50</v>"))
        assertTrue(details.contains("<v>123.45</v>"))
        assertFalse(details.contains("<v>450</v>"))
        assertFalse(details.contains("<v>12345</v>"))
        val info = part(bytes, "xl/worksheets/sheet4.xml")
        val report = PeriodLedger.report(today, period, listOf(bank), rows, emptyList(), emptyList())
        assertTrue(info.contains("<v>${MoneyFormat.formatPlain(report.incomeMinor, "USD")}</v>"))
        assertTrue(info.contains("<v>${MoneyFormat.formatPlain(report.expenseMinor, "USD")}</v>"))
        assertEquals(2_000_00L, report.incomeMinor)
        assertEquals(12_795L, report.expenseMinor)
        assertTrue(info.contains("<v>20.00</v>"))
        // Net spending is expenses minus refunds, the same figure Home shows.
        assertTrue(info.contains("<v>107.95</v>"))
        assertFalse(info.contains("<v>10795</v>"))
    }

    @Test
    fun accountSummaryBalanceMatchesTheLedgerAndKeepsTransfersOutOfTotals() {
        val other = bank.copy(id = "b", name = "Savings", openingBalanceMinor = 0)
        val rows = listOf(
            tx("out", 30_000, -30_000, TransactionType.TRANSFER, counter = "b"),
            tx("in", 30_000, 30_000, TransactionType.TRANSFER, accountId = "b", counter = "a"),
            tx("pay", 5_000, -5_000, TransactionType.PAYMENT, counter = "b"),
        )
        val bytes = XlsxReports.ledger(today, "1", listOf(bank, other), rows, emptyList(), null, "a")
        val summary = part(bytes, "xl/worksheets/sheet2.xml")
        val actual = com.ledgerline.app.finance.balance.BalanceMath.currentBalanceMinor(bank, rows)
        assertEquals(65_000L, actual)
        assertTrue(summary.contains("<v>650.00</v>"))
        val info = part(bytes, "xl/worksheets/sheet4.xml")
        // Transfers and payments are not income or expenses, and the other account is not in this report.
        assertTrue(Regex("<v>0\\.00</v>").findAll(info).count() >= 4)
        assertFalse(info.contains("300.00"))
    }

    @Test
    fun zeroAndLargeAmountsExport() {
        val bytes = XlsxWorkbook.write(
            listOf(XlsxSheet("S", listOf(listOf(XlsxCell.Money(0, "USD"), XlsxCell.Money(Long.MAX_VALUE / 1000, "USD"))))),
        )
        val xml = part(bytes, "xl/worksheets/sheet1.xml")
        assertTrue(xml.contains("<v>0.00</v>"))
        assertTrue(xml.contains("<v>${BigDecimal.valueOf(Long.MAX_VALUE / 1000).movePointLeft(2).toPlainString()}</v>"))
    }

    @Test
    fun exportedDatesDoNotShiftWithTheTimeZone() {
        val saved = java.util.TimeZone.getDefault()
        try {
            listOf("Pacific/Kiritimati", "Pacific/Pago_Pago", "UTC").forEach { zone ->
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
                val csv = LedgerExport.csv(listOf(tx("t", 100, -100, TransactionType.EXPENSE, date = LocalDate.of(2026, 3, 8))), listOf(bank), emptyList())
                assertTrue(zone, csv.lines()[1].contains("2026-03-08"))
                val bytes = XlsxReports.ledger(today, "1", listOf(bank), listOf(tx("t", 100, -100, TransactionType.EXPENSE, date = LocalDate.of(2026, 3, 8))), emptyList(), null, null)
                val serial = LocalDate.of(2026, 3, 8).toEpochDay() + 25_569
                assertTrue(zone, part(bytes, "xl/worksheets/sheet1.xml").contains("<v>$serial</v>"))
            }
        } finally {
            java.util.TimeZone.setDefault(saved)
        }
    }

    @Test
    fun csvRoundTripsThroughMinorUnits() {
        // The app has no import feature, so this checks that the exported decimals convert back to the stored integers.
        samples.filter { it.first >= 0 }.forEach { (minor, _) ->
            val text = LedgerExport.csvNumber(minor, "USD")
            assertEquals(minor, BigDecimal(text).movePointRight(2).longValueExact())
        }
    }

    private fun abs(value: Long) = kotlin.math.abs(value)

    private fun part(bytes: ByteArray, name: String): String = ZipInputStream(bytes.inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.first { it.name == name }
        zip.readBytes().toString(Charsets.UTF_8)
    }

    private fun tx(
        id: String,
        amount: Long,
        effect: Long,
        type: TransactionType,
        accountId: String = "a",
        counter: String? = null,
        date: LocalDate = LocalDate.of(2026, 10, 20),
        merchant: String = id,
    ) = LedgerTransaction(
        id, accountId, counter, null, amount, effect, "USD", type, merchant, merchant, null, id, null,
        date, date, DetectionSource.MANUAL, 100, null, TransactionStatus.CONFIRMED, true, "", "fp", 0, 0, null,
    )
}
