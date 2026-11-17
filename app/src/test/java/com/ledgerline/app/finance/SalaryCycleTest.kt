package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.period.PeriodLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SalaryCycleTest {
    private val cycle = PayPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19))
    private val today = LocalDate.of(2026, 10, 25)

    @Test
    fun payPeriod_runsFromThe20thThroughThe19th() {
        assertEquals(cycle, PayPeriod.containing(LocalDate.of(2026, 10, 20)))
        assertEquals(cycle, PayPeriod.containing(LocalDate.of(2026, 11, 19)))
        assertEquals(cycle.previous(), PayPeriod.containing(LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun salaryCycle_reconcilesEachAccountAndDoesNotDoubleCountAPaidBill() {
        val bankA = account("a", "Bank A", 100_000)
        val bankB = account("b", "Bank B", 40_000)
        val paypal = account("p", "PayPal", 10_000)
        val accounts = listOf(bankA, bankB, paypal)
        val salary = tx("salary", "a", TransactionType.INCOME, 200_000, 200_000, LocalDate.of(2026, 10, 20), "Salary")
        val toB = tx("to-b", "a", TransactionType.TRANSFER, 30_000, -30_000, LocalDate.of(2026, 10, 21), "To Bank B", counter = "b", link = "l1")
        val fromAtoB = tx("from-a-b", "b", TransactionType.TRANSFER, 30_000, 30_000, LocalDate.of(2026, 10, 21), "From Bank A", counter = "a", link = "l1")
        val toPayPal = tx("to-p", "a", TransactionType.TRANSFER, 20_000, -20_000, LocalDate.of(2026, 10, 21), "To PayPal", counter = "p", link = "l2")
        val fromAtoP = tx("from-a-p", "p", TransactionType.TRANSFER, 20_000, 20_000, LocalDate.of(2026, 10, 21), "From Bank A", counter = "a", link = "l2")
        val paypalSpend = tx("p-spend", "p", TransactionType.EXPENSE, 15_000, -15_000, LocalDate.of(2026, 10, 22), "Shop")
        val bankBSpend = tx("b-spend", "b", TransactionType.EXPENSE, 12_000, -12_000, LocalDate.of(2026, 10, 23), "Groceries")
        val bill = RecurringPayment(
            id = "rent",
            name = "Rent",
            accountId = "a",
            categoryId = null,
            direction = CashFlowDirection.OUTFLOW,
            kind = ScheduledKind.BILL,
            amountMinor = 50_000,
            currency = "USD",
            frequency = RecurrenceFrequency.ONCE,
            intervalDays = null,
            dayOfMonth = 5,
            dayOfWeek = null,
            startOn = LocalDate.of(2026, 11, 5),
            endOn = null,
            priority = PaymentPriority.MANDATORY,
            incomeConfidence = IncomeConfidence.NONE,
            active = true,
            note = "",
            maxOccurrences = 1,
        )
        val open = PeriodLedger.report(today, cycle, accounts, listOf(salary, toB, fromAtoB, toPayPal, fromAtoP, paypalSpend, bankBSpend), listOf(bill), emptyList())
        val a = open.accounts.first { it.accountId == "a" }
        val b = open.accounts.first { it.accountId == "b" }
        val p = open.accounts.first { it.accountId == "p" }
        assertEquals(200_000, open.incomeMinor)
        assertEquals(27_000, open.expenseMinor)
        assertEquals(200_000, a.incomeMinor)
        assertEquals(50_000, a.transfersOutMinor)
        assertEquals(50_000, a.unpaidBillsMinor)
        assertEquals(100_000 + 200_000 - 50_000, a.actualMinor)
        assertEquals(a.actualMinor - 50_000, a.projectedEndingMinor)
        assertEquals(40_000 + 30_000 - 12_000, b.actualMinor)
        assertEquals(10_000 + 20_000 - 15_000, p.actualMinor)
        assertEquals(0, open.incomeMinor - a.incomeMinor)
        assertTrue(open.expenseMinor == b.expenseMinor + p.expenseMinor)

        val paid = tx("rent-paid", "a", TransactionType.EXPENSE, 50_000, -50_000, LocalDate.of(2026, 11, 5), "Rent", recurringId = "rent")
        val settled = PeriodLedger.report(
            LocalDate.of(2026, 11, 6),
            cycle,
            accounts,
            listOf(salary, toB, fromAtoB, toPayPal, fromAtoP, paypalSpend, bankBSpend, paid),
            listOf(bill),
            listOf(OccurrenceAdjustment("rent", LocalDate.of(2026, 11, 5), OccurrenceState.PAID, null, null, "rent-paid")),
        )
        val paidA = settled.accounts.first { it.accountId == "a" }
        assertEquals(0, paidA.unpaidBillsMinor)
        assertEquals(50_000, paidA.expenseMinor)
        assertEquals(a.actualMinor - 50_000, paidA.actualMinor)
        assertEquals(paidA.actualMinor, paidA.projectedEndingMinor)

        val earlier = PeriodLedger.report(today, cycle.previous(), accounts, listOf(salary, toB, fromAtoB, toPayPal, fromAtoP, paypalSpend, bankBSpend), listOf(bill), emptyList())
        assertEquals(0, earlier.incomeMinor)
        assertEquals(0, earlier.accounts.first { it.accountId == "a" }.incomeMinor)
        val again = PeriodLedger.report(today, cycle, accounts, listOf(salary, toB, fromAtoB, toPayPal, fromAtoP, paypalSpend, bankBSpend), listOf(bill), emptyList())
        assertEquals(open, again)
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
        merchant: String,
        counter: String? = null,
        link: String? = null,
        recurringId: String? = null,
    ) = LedgerTransaction(
        id, accountId, counter, link, amount, effect, "USD", type, merchant, merchant, null, merchant, null,
        date, date, DetectionSource.MANUAL, 100, recurringId, TransactionStatus.CONFIRMED, true, "", "fp", 0, 0, null,
    )
}
