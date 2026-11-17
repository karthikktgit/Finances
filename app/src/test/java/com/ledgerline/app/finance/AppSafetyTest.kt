package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.finance.dashboard.HomeFeed
import com.ledgerline.app.finance.dashboard.ResetPolicy
import com.ledgerline.app.finance.export.XlsxSheet
import com.ledgerline.app.finance.export.XlsxWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.zip.ZipInputStream

class AppSafetyTest {
    @Test
    fun homeFeed_showsFiveUpcomingAndKeepsOverdueSeparate() {
        val items = (1..8).map { day -> occurrence("b$day", LocalDate.of(2026, 11, day), overdue = false) } +
            occurrence("late", LocalDate.of(2026, 10, 1), overdue = true)
        assertEquals(5, HomeFeed.upcoming(items).size)
        assertEquals(listOf("late"), HomeFeed.overdue(items).map { it.recurringId })
        assertFalse(HomeFeed.upcoming(items).any { it.overdue })
    }

    @Test
    fun resetOnLaunch_requiresADebugBuildAndAnExplicitSwitch() {
        assertFalse(ResetPolicy.resetOnLaunch(debugBuild = false, enabledByDeveloper = true))
        assertFalse(ResetPolicy.resetOnLaunch(debugBuild = true, enabledByDeveloper = false))
        assertTrue(ResetPolicy.resetOnLaunch(debugBuild = true, enabledByDeveloper = true))
    }

    @Test
    fun workbook_containsTheTransactionAndTheReportSheets() {
        val bytes = XlsxWorkbook.write(
            listOf(
                XlsxSheet("Transaction Details", listOf(listOf("ID"), listOf("txn-salary"))),
                XlsxSheet("Report Information", listOf(listOf("Version", "0.2.0"))),
            ),
        )
        val names = ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertTrue(names.contains("[Content_Types].xml"))
        assertTrue(names.contains("xl/worksheets/sheet1.xml"))
        val sheet = ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "xl/worksheets/sheet1.xml" }
            zip.readBytes().toString(Charsets.UTF_8)
        }
        assertTrue(sheet.contains("txn-salary"))
    }

    private fun occurrence(id: String, date: LocalDate, overdue: Boolean) = ScheduledOccurrence(
        id, id, date, 100, CashFlowDirection.OUTFLOW, PaymentPriority.MANDATORY, ScheduledKind.BILL,
        "checking", "Checking", IncomeConfidence.NONE, true, -100, overdue,
    )
}
