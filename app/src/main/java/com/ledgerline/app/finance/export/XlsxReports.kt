package com.ledgerline.app.finance.export

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.finance.account.AccountDeletion
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.period.PeriodLedger
import java.time.LocalDate

/**
 * Amounts are stored as integer minor units. Every amount cell here is converted to
 * major units once, by [XlsxCell.Money], and written as a number. Nothing is formatted twice.
 */
object XlsxReports {
    fun ledger(
        today: LocalDate,
        versionName: String,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        recurring: List<RecurringPayment>,
        period: PayPeriod?,
        accountId: String?,
        categories: List<Category> = emptyList(),
    ): ByteArray {
        val scoped = transactions.filter { row ->
            val accountMatches = accountId == null || row.accountId == accountId
            accountMatches && (period == null || period.contains(row.occurredOn))
        }
        val names = accounts.associate { it.id to AccountDeletion.label(it.id, accounts) }
        val currencies = accounts.associate { it.id to it.currency }
        val reportCurrency = accountId?.let { currencies[it] } ?: accounts.firstOrNull { !it.deleted }?.currency ?: "USD"
        val detailHeader = listOf("ID", "Date", "Description", "Type", "Category", "Account", "Other account", "Amount", "Effect", "Currency", "Status", "Recurring id", "Deleted")
        val detailRows = scoped.map { row ->
            listOf(
                row.id,
                XlsxCell.Date(row.occurredOn),
                row.merchantRaw.ifBlank { row.description },
                row.type.name,
                row.categoryId?.let { CategoryTree.label(it, categories) } ?: "",
                names[row.accountId] ?: "Account not assigned",
                row.counterAccountId?.let { names[it] } ?: "",
                XlsxCell.Money(row.amountMinor, row.currency),
                XlsxCell.Money(row.balanceEffectMinor, row.currency),
                row.currency,
                row.status.name,
                row.recurringPaymentId ?: "",
                (row.deletedAtEpochMillis != null).toString(),
            )
        }
        val detail: List<List<Any>> = if (detailRows.isEmpty()) {
            listOf(listOf("No transactions in this report"))
        } else {
            listOf(detailHeader) + detailRows
        }
        val deletedIds = accounts.filter { it.deleted }.map { it.id }.toSet()
        val liveAccounts = accounts.filterNot { it.deleted }
        val liveTransactions = AccountDeletion.live(transactions, deletedIds)
        val summaryPeriod = period ?: PayPeriod(LocalDate.of(1970, 1, 1), LocalDate.of(2100, 12, 31))
        val report = PeriodLedger.report(today, summaryPeriod, liveAccounts, liveTransactions, recurring.filter { it.accountId !in deletedIds }, emptyList())
        val lines = report.accounts.filter { accountId == null || it.accountId == accountId }
        fun money(minor: Long, accountCurrency: String = reportCurrency) = XlsxCell.Money(minor, accountCurrency)
        val summary: List<List<Any>> = listOf(
            listOf(
                "Account", "Type", "Actual", "Period income", "Period expenses", "Transfers in", "Transfers out",
                "Unpaid bills", "Projected end", "Refunds", "Adjustments", "Currency",
            ),
        ) + lines.map { line ->
            val currency = currencies[line.accountId] ?: reportCurrency
            listOf(
                line.name,
                line.type.name,
                money(line.actualMinor, currency),
                money(line.incomeMinor, currency),
                money(line.expenseMinor, currency),
                money(line.transfersInMinor, currency),
                money(line.transfersOutMinor, currency),
                money(line.unpaidBillsMinor, currency),
                money(line.projectedEndingMinor, currency),
                money(line.refundsMinor, currency),
                money(line.adjustmentsMinor, currency),
                currency,
            )
        }
        val scheduled: List<List<Any>> = listOf(listOf("ID", "Name", "Account", "Direction", "Amount", "Frequency", "Start", "Active", "Currency")) +
            recurring.filter { item ->
                val accountMatches = accountId == null || item.accountId == accountId
                val inRange = period == null ||
                    (!item.startOn.isAfter(period.end) && (item.endOn == null || !item.endOn.isBefore(period.start)))
                accountMatches && inRange
            }.map { item ->
                listOf(
                    item.id,
                    item.name,
                    names[item.accountId] ?: "Account not assigned",
                    item.direction.name,
                    XlsxCell.Money(item.amountMinor, item.currency),
                    item.frequency.name,
                    XlsxCell.Date(item.startOn),
                    item.active.toString(),
                    item.currency,
                )
            }
        // Totals come from the same lines the app shows, limited to the account chosen for the report.
        val income = lines.sumOf { it.incomeMinor }
        val expenses = lines.sumOf { it.expenseMinor }
        val refunds = lines.sumOf { it.refundsMinor }
        val info: List<List<Any>> = listOf(
            listOf("Field", "Value"),
            listOf("Version", versionName),
            listOf("Date range", if (period == null) "All dates" else "${period.start} to ${period.end}"),
            listOf("Account", accountId?.let { id -> names[id] } ?: "All accounts"),
            listOf("Currency", reportCurrency),
            listOf("Completed income", money(income)),
            listOf("Completed expenses", money(expenses)),
            listOf("Refunds", money(refunds)),
            listOf("Net spending (expenses minus refunds)", money(expenses - refunds)),
            listOf("Transfers excluded from those totals", "yes"),
            listOf("Rows in this report", XlsxCell.Count(scoped.count { BalanceMath.isActive(it) }.toLong())),
        )
        return XlsxWorkbook.write(
            listOf(
                XlsxSheet("Transaction Details", detail),
                XlsxSheet("Account Summary", summary),
                XlsxSheet("Scheduled Payments", scheduled),
                XlsxSheet("Report Information", info),
            ),
        )
    }
}
