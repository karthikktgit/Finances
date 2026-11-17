package com.ledgerline.app.domain.model

import java.time.LocalDate

data class AccountSnapshot(
    val account: Account,
    val currentBalanceMinor: Long,
    val availableCreditMinor: Long?,
)

data class ScheduledOccurrence(
    val recurringId: String,
    val name: String,
    val date: LocalDate,
    val amountMinor: Long,
    val direction: CashFlowDirection,
    val priority: PaymentPriority,
    val kind: ScheduledKind,
    val accountId: String,
    val accountName: String,
    val incomeConfidence: IncomeConfidence,
    val countedInSafeToSpend: Boolean,
    val liquidDeltaMinor: Long,
    val overdue: Boolean = false,
)

data class BudgetSnapshot(
    val budget: Budget,
    val categoryName: String,
    val spentMinor: Long,
    val remainingMinor: Long,
    val projectedMinor: Long,
    val pace: BudgetPace,
)

data class BalanceCheckpoint(
    val date: LocalDate,
    val label: String,
    val deltaMinor: Long,
    val balanceAfterMinor: Long,
)

data class FinancialWarning(
    val severity: WarningSeverity,
    val title: String,
    val detail: String,
)

data class SafeToSpendLine(
    val label: String,
    val minor: Long,
)

data class SafeToSpendResult(
    val amountMinor: Long,
    val lines: List<SafeToSpendLine>,
    val disclaimer: String,
)

data class Dashboard(
    val goals: List<com.ledgerline.app.finance.goals.GoalProgress> = emptyList(),
    val asOf: LocalDate,
    val currency: String,
    val horizonDays: Int,
    val liquidMinor: Long,
    val netWorthMinor: Long,
    val availableCreditMinor: Long,
    val accounts: List<AccountSnapshot>,
    val upcoming: List<ScheduledOccurrence>,
    val committedPaymentsMinor: Long,
    val expectedIncomeMinor: Long,
    val safeToSpend: SafeToSpendResult,
    val monthSpendingMinor: Long,
    val monthRefundsMinor: Long,
    val monthIncomeMinor: Long,
    val recent: List<LedgerTransaction>,
    val warnings: List<FinancialWarning>,
    val budgets: List<BudgetSnapshot>,
    val preview: List<BalanceCheckpoint>,
    val excludedCurrencyCount: Int,
    val needsReviewCount: Int,
    val forecast: List<ForecastDay>,
    val discretionaryDailyMinor: Long,
    val cycleStart: java.time.LocalDate = asOf,
    val cycleEnd: java.time.LocalDate = asOf,
    val cycleOutgoingsMinor: Long = 0,
    val forecastSchedule: List<ScheduledOccurrence> = emptyList(),
)

data class CategorySpend(
    val categoryId: String,
    val name: String,
    val necessity: Necessity,
    val thisMonthMinor: Long,
    val lastMonthMinor: Long,
)

data class NamedAmount(
    val id: String,
    val name: String,
    val amountMinor: Long,
)

data class Insights(
    val currency: String,
    val thisMonthNetMinor: Long,
    val lastMonthNetMinor: Long,
    val byCategory: List<CategorySpend>,
    val byNecessity: List<NamedAmount>,
    val topMerchants: List<NamedAmount>,
    val byAccount: List<NamedAmount>,
    val leaks: List<InsightNote>,
    val anomalies: List<InsightNote>,
    val subscriptionMonthMinor: Long,
    val subscriptionAnnualMinor: Long,
)

data class InsightNote(
    val title: String,
    val detail: String,
)

data class ForecastDay(
    val date: LocalDate,
    val scheduledMinor: Long,
    val discretionaryMinor: Long,
    val balanceAfterMinor: Long,
)
