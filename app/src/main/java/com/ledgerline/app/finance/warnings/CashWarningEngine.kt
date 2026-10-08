package com.ledgerline.app.finance.warnings

import com.ledgerline.app.domain.model.BudgetPace
import com.ledgerline.app.domain.model.BudgetSnapshot
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.FinancialWarning
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.WarningSeverity
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.policy.FinancePolicy
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object CashWarningEngine {
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun build(
        today: LocalDate,
        currency: String,
        liquidMinor: Long,
        safeToSpendMinor: Long,
        bufferMinor: Long,
        upcoming: List<ScheduledOccurrence>,
        budgets: List<BudgetSnapshot>,
        thisMonthSpentMinor: Long,
        lastMonthSpentMinor: Long,
        dayOfMonth: Int,
        daysInMonth: Int,
        excludedCurrencyCount: Int,
    ): List<FinancialWarning> {
        val warnings = mutableListOf<FinancialWarning>()
        if (safeToSpendMinor < 0) {
            warnings += FinancialWarning(
                severity = WarningSeverity.CRITICAL,
                title = "Safe-to-spend estimate is below zero",
                detail = "After committed payments, your buffer, and reserved budgets, the estimate is ${
                    MoneyFormat.format(safeToSpendMinor, currency)
                }. This is based on current information.",
            )
        } else if (bufferMinor > 0 && safeToSpendMinor < bufferMinor) {
            warnings += FinancialWarning(
                severity = WarningSeverity.MEDIUM,
                title = "Safe-to-spend is inside your buffer",
                detail = "The estimate is ${MoneyFormat.format(safeToSpendMinor, currency)}, which is below your ${
                    MoneyFormat.format(bufferMinor, currency)
                } minimum buffer.",
            )
        }
        upcoming
            .filter { it.direction == CashFlowDirection.OUTFLOW }
            .filter { it.priority == PaymentPriority.MANDATORY || it.priority == PaymentPriority.IMPORTANT }
            .forEach { item ->
                val days = ChronoUnit.DAYS.between(today, item.date)
                if (days in 0..FinancePolicy.DUE_SOON_DAYS) {
                    warnings += FinancialWarning(
                        severity = if (item.priority == PaymentPriority.MANDATORY) {
                            WarningSeverity.HIGH
                        } else {
                            WarningSeverity.MEDIUM
                        },
                        title = "${item.name} is due ${relative(days)}",
                        detail = "${MoneyFormat.format(item.amountMinor, currency)} on ${item.date.format(dateFormat)} " +
                            "from ${item.accountName}. Priority: ${item.priority.name.lowercase(Locale.US)}.",
                    )
                }
            }
        val nextIncome = upcoming
            .filter { it.direction == CashFlowDirection.INFLOW }
            .filter { it.incomeConfidence == IncomeConfidence.HIGH || it.incomeConfidence == IncomeConfidence.MEDIUM }
            .minByOrNull { it.date }
        val committedBeforeIncome = upcoming.filter { item ->
            item.countedInSafeToSpend &&
                item.direction == CashFlowDirection.OUTFLOW &&
                (nextIncome == null || item.date.isBefore(nextIncome.date))
        }
        val committedSum = committedBeforeIncome.sumOf { it.amountMinor }
        if (committedSum > liquidMinor) {
            val whenText = nextIncome?.let { " before the next expected income on ${it.date.format(dateFormat)}" }
                ?: " in this horizon, and no reliable income is scheduled"
            warnings += FinancialWarning(
                severity = WarningSeverity.HIGH,
                title = "Committed payments exceed liquid funds",
                detail = "You have ${MoneyFormat.format(committedSum, currency)} of committed payments$whenText. " +
                    "Liquid funds are ${MoneyFormat.format(liquidMinor, currency)}.",
            )
        }
        if (excludedCurrencyCount > 0) {
            warnings += FinancialWarning(
                severity = WarningSeverity.INFO,
                title = "Some accounts are excluded from totals",
                detail = "$excludedCurrencyCount account(s) use a currency other than $currency. " +
                    "Ledgerline does not convert currencies, so those balances are left out of safe-to-spend.",
            )
        }
        upcoming
            .filter { it.direction == CashFlowDirection.INFLOW && it.incomeConfidence == IncomeConfidence.LOW }
            .forEach { item ->
                warnings += FinancialWarning(
                    severity = WarningSeverity.INFO,
                    title = "${item.name} is not included in safe-to-spend",
                    detail = "This ${MoneyFormat.format(item.amountMinor, currency)} inflow is marked low confidence.",
                )
            }
        budgets.forEach { snapshot ->
            when (snapshot.pace) {
                BudgetPace.OVER -> warnings += FinancialWarning(
                    severity = WarningSeverity.MEDIUM,
                    title = "${snapshot.budget.name} is over budget",
                    detail = "Spent ${MoneyFormat.format(snapshot.spentMinor, currency)} of ${
                        MoneyFormat.format(snapshot.budget.amountMinor, currency)
                    }.",
                )
                BudgetPace.LIKELY_OVER -> warnings += FinancialWarning(
                    severity = WarningSeverity.LOW,
                    title = "${snapshot.budget.name} is likely over budget",
                    detail = "Spent ${MoneyFormat.format(snapshot.spentMinor, currency)}. " +
                        "At the current pace the projected total is ${
                            MoneyFormat.format(snapshot.projectedMinor, currency)
                        } against ${MoneyFormat.format(snapshot.budget.amountMinor, currency)}.",
                )
                BudgetPace.ON_TRACK -> Unit
            }
        }
        if (dayOfMonth >= FinancePolicy.PACE_MIN_DAY_OF_MONTH && lastMonthSpentMinor > 0 && thisMonthSpentMinor > 0) {
            val projected = thisMonthSpentMinor * daysInMonth / dayOfMonth
            if (projected > lastMonthSpentMinor) {
                val percent = ((projected - lastMonthSpentMinor) * 100) / lastMonthSpentMinor
                if (percent >= FinancePolicy.PACE_WARNING_PERCENT) {
                    warnings += FinancialWarning(
                        severity = WarningSeverity.INFO,
                        title = "Spending pace is higher than last month",
                        detail = "If this pace continues, spending would be about $percent% higher than last month. " +
                            "This is a projection, not a forecast of individual purchases.",
                    )
                }
            }
        }
        return warnings.sortedBy { it.severity.ordinal }
    }

    private fun relative(days: Long): String = when (days) {
        0L -> "today"
        1L -> "tomorrow"
        else -> "in $days days"
    }
}
