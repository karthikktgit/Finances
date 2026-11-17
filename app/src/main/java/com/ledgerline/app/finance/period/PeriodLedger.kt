package com.ledgerline.app.finance.period

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.category.SpendingRules
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import java.time.LocalDate
import java.time.YearMonth

/**
 * A salary cycle runs from the 20th through the 19th of the next month.
 * Posted activity uses the transaction date. Unpaid bills use the due date
 * and stay out of the actual balance.
 */
data class PayPeriod(
    val start: LocalDate,
    val end: LocalDate,
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun previous(): PayPeriod = containing(start.minusDays(1))

    fun next(): PayPeriod = containing(end.plusDays(1))

    companion object {
        const val START_DAY = 20

        fun containing(date: LocalDate): PayPeriod {
            val startMonth = if (date.dayOfMonth >= START_DAY) YearMonth.from(date) else YearMonth.from(date).minusMonths(1)
            val start = startMonth.atDay(START_DAY)
            val end = startMonth.plusMonths(1).atDay(START_DAY - 1)
            return PayPeriod(start, end)
        }
    }
}

data class AccountPeriodLine(
    val accountId: String,
    val name: String,
    val type: AccountType,
    val includeInSafeToSpend: Boolean,
    val includeInNetWorth: Boolean,
    val startingMinor: Long,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val transfersInMinor: Long,
    val transfersOutMinor: Long,
    val refundsMinor: Long,
    val adjustmentsMinor: Long,
    val unpaidBillsMinor: Long,
    val expectedIncomeMinor: Long,
    val actualMinor: Long,
    val projectedEndingMinor: Long,
    val income: List<LedgerTransaction>,
    val expenses: List<LedgerTransaction>,
    val other: List<LedgerTransaction>,
    val unpaid: List<ProjectedOccurrence>,
)

data class PeriodReport(
    val period: PayPeriod,
    val accounts: List<AccountPeriodLine>,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val netMinor: Long,
    val assetMinor: Long,
    val liabilityMinor: Long,
    val unassignedScheduled: Int,
)

object PeriodLedger {
    fun report(
        today: LocalDate,
        period: PayPeriod,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        recurring: List<RecurringPayment>,
        adjustments: List<OccurrenceAdjustment>,
    ): PeriodReport {
        val active = transactions.filter { BalanceMath.isActive(it) }
        val settled = active.mapNotNull { transaction ->
            val recurringId = transaction.recurringPaymentId ?: return@mapNotNull null
            recurringId to transaction.occurredOn
        }.toSet()
        val scheduled = OccurrenceMaterializer.project(
            items = recurring,
            adjustments = adjustments,
            settled = settled,
            start = period.start,
            end = period.end,
            today = today,
        ).filter { it.state == OccurrenceState.SCHEDULED }
        val lines = accounts.map { account -> line(account, period, active, scheduled, today) }
        val income = lines.sumOf { it.incomeMinor }
        val expense = lines.sumOf { it.expenseMinor }
        val assets = lines.filter { it.includeInNetWorth && !BalanceMath.isDebtType(it.type) }.sumOf { it.actualMinor }
        val liabilities = lines.filter { BalanceMath.isDebtType(it.type) }.sumOf { line ->
            if (line.actualMinor < 0) -line.actualMinor else 0L
        }
        return PeriodReport(
            period = period,
            accounts = lines,
            incomeMinor = income,
            expenseMinor = expense,
            netMinor = income - expense,
            assetMinor = assets,
            liabilityMinor = liabilities,
            unassignedScheduled = scheduled.count { it.accountId.isBlank() },
        )
    }

    private fun line(
        account: Account,
        period: PayPeriod,
        transactions: List<LedgerTransaction>,
        scheduled: List<ProjectedOccurrence>,
        today: LocalDate,
    ): AccountPeriodLine {
        val mine = transactions.filter { it.accountId == account.id }
        val before = mine.filter { it.occurredOn.isBefore(period.start) }
        val during = mine.filter { period.contains(it.occurredOn) }
        val starting = account.openingBalanceMinor + before.sumOf { it.balanceEffectMinor }
        val income = during.filter { it.type == TransactionType.INCOME }
        val expenses = during.filter { SpendingRules.countsAsSpending(it) }
        val refunds = during.filter { it.type == TransactionType.REFUND }
        val adjustments = during.filter { it.type == TransactionType.ADJUSTMENT }
        val transfers = during.filter { it.type == TransactionType.TRANSFER || it.type == TransactionType.PAYMENT }
        val unpaid = scheduled.filter { it.accountId == account.id }
        val unpaidOut = unpaid.filter { it.direction == CashFlowDirection.OUTFLOW }
        val unpaidIn = unpaid.filter { it.direction == CashFlowDirection.INFLOW }
        val postedEffect = during.sumOf { it.balanceEffectMinor }
        val scheduleEffect = unpaidIn.sumOf { it.amountMinor } - unpaidOut.sumOf { it.amountMinor }
        return AccountPeriodLine(
            accountId = account.id,
            name = account.name,
            type = account.type,
            includeInSafeToSpend = account.includeInSafeToSpend,
            includeInNetWorth = account.includeInNetWorth,
            startingMinor = starting,
            incomeMinor = income.sumOf { it.amountMinor },
            expenseMinor = expenses.sumOf { it.amountMinor },
            transfersInMinor = transfers.filter { it.balanceEffectMinor > 0 }.sumOf { it.amountMinor },
            transfersOutMinor = transfers.filter { it.balanceEffectMinor < 0 }.sumOf { it.amountMinor },
            refundsMinor = refunds.sumOf { it.amountMinor },
            adjustmentsMinor = adjustments.sumOf { it.balanceEffectMinor },
            unpaidBillsMinor = unpaidOut.sumOf { it.amountMinor },
            expectedIncomeMinor = unpaidIn.sumOf { it.amountMinor },
            actualMinor = BalanceMath.balanceAsOf(account, transactions, if (period.end.isBefore(today)) period.end else today),
            projectedEndingMinor = starting + postedEffect + scheduleEffect,
            income = income,
            expenses = expenses,
            other = during.filter { it.type != TransactionType.INCOME && !SpendingRules.countsAsSpending(it) },
            unpaid = unpaid,
        )
    }
}
