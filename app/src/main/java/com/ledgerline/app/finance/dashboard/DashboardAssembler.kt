package com.ledgerline.app.finance.dashboard

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetSnapshot
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.Dashboard
import com.ledgerline.app.domain.model.Insights
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.NamedAmount
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.domain.model.CategorySpend
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.budget.BudgetMath
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.policy.FinancePolicy
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.finance.safetospend.SafeToSpendEngine
import com.ledgerline.app.finance.anomaly.AnomalyDetector
import com.ledgerline.app.finance.forecast.CashFlowForecastEngine
import com.ledgerline.app.finance.leak.MoneyLeakEngine
import com.ledgerline.app.finance.schedule.PreviewEvent
import com.ledgerline.app.finance.schedule.RecurringSchedule
import com.ledgerline.app.finance.schedule.ScheduledBalancePreview
import com.ledgerline.app.finance.subscription.SubscriptionRollup
import com.ledgerline.app.finance.warnings.CashWarningEngine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

object DashboardAssembler {
    fun assemble(
        today: LocalDate,
        settings: UserSettings,
        accounts: List<Account>,
        categories: List<Category>,
        transactions: List<LedgerTransaction>,
        recurring: List<RecurringPayment>,
        budgets: List<Budget>,
    ): Dashboard {
        val currency = settings.primaryCurrency
        val activeTransactions = transactions.filter { BalanceMath.isActive(it) }
        val snapshots = accounts.map { account ->
            val balance = BalanceMath.currentBalanceMinor(account, activeTransactions)
            com.ledgerline.app.domain.model.AccountSnapshot(
                account = account,
                currentBalanceMinor = balance,
                availableCreditMinor = BalanceMath.availableCreditMinor(account, balance),
            )
        }
        val primarySnapshots = snapshots.filter { it.account.currency == currency }
        val excluded = snapshots.count { it.account.currency != currency }
        val liquidMinor = primarySnapshots
            .filter { it.account.includeInSafeToSpend }
            .sumOf { it.currentBalanceMinor }
        val netWorthMinor = primarySnapshots
            .filter { it.account.includeInNetWorth }
            .sumOf { it.currentBalanceMinor }
        val availableCredit = primarySnapshots.sumOf { it.availableCreditMinor ?: 0L }
        val liquidIds = primarySnapshots.filter { it.account.includeInSafeToSpend }.map { it.account.id }.toSet()
        val horizonEnd = today.plusDays(settings.horizonDays.toLong())
        val upcoming = recurring
            .filter { it.active && it.currency == currency }
            .flatMap { item ->
                val account = accounts.firstOrNull { it.id == item.accountId }
                RecurringSchedule.occurrencesBetween(item, today, horizonEnd).map { date ->
                    occurrence(item, date, account, liquidIds)
                }
            }
            .sortedWith(compareBy<ScheduledOccurrence> { it.date }.thenBy { it.name })
        val committed = upcoming
            .filter { it.countedInSafeToSpend && it.direction == CashFlowDirection.OUTFLOW && it.kind != ScheduledKind.TRANSFER }
            .sumOf { it.amountMinor }
        val transfers = upcoming
            .filter { it.countedInSafeToSpend && it.kind == ScheduledKind.TRANSFER && it.direction == CashFlowDirection.OUTFLOW }
            .sumOf { it.amountMinor }
        val income = upcoming
            .filter { it.countedInSafeToSpend && it.direction == CashFlowDirection.INFLOW }
            .sumOf { it.amountMinor }
        val month = YearMonth.from(today)
        val budgetSnapshots = budgets
            .filter { it.active && it.currency == currency }
            .map { budget -> snapshotBudget(budget, today, month, categories, activeTransactions) }
        val discretionary = budgetSnapshots
            .filter { it.budget.reservesSafeToSpend }
            .sumOf { it.remainingMinor.coerceAtLeast(0) }
        val safe = SafeToSpendEngine.calculate(
            SafeToSpendEngine.Input(
                liquidBalanceMinor = liquidMinor,
                committedPaymentsMinor = committed,
                minimumBufferMinor = settings.minimumBufferMinor,
                plannedTransfersMinor = transfers,
                expectedIncomeMinor = income,
                plannedDiscretionaryMinor = discretionary,
            ),
        )
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val monthTx = activeTransactions.filter { it.currency == currency && !it.occurredOn.isBefore(monthStart) && !it.occurredOn.isAfter(monthEnd) }
        val recentDiscretionary = recentDiscretionaryMinor(activeTransactions, categories, today, currency)
        val dailyDiscretionary = CashFlowForecastEngine.dailyDiscretionaryMinor(
            reservedRemainingMinor = discretionary,
            horizonDays = settings.horizonDays,
            recentDiscretionaryMinor = recentDiscretionary,
            recentDays = 30,
        )
        val forecast = CashFlowForecastEngine.project(
            start = today,
            days = settings.horizonDays,
            openingMinor = liquidMinor,
            events = upcoming.filter { it.liquidDeltaMinor != 0L }.map {
                CashFlowForecastEngine.DatedAmount(it.date, it.recurringId, it.name, it.liquidDeltaMinor)
            },
            dailyDiscretionaryMinor = dailyDiscretionary,
        )
        val preview = ScheduledBalancePreview.project(
            startingLiquidMinor = liquidMinor,
            events = upcoming.filter { it.liquidDeltaMinor != 0L }.map {
                PreviewEvent(it.date, it.name, it.liquidDeltaMinor)
            },
        )
        val warnings = CashWarningEngine.build(
            today = today,
            currency = currency,
            liquidMinor = liquidMinor,
            safeToSpendMinor = safe.amountMinor,
            bufferMinor = settings.minimumBufferMinor,
            upcoming = upcoming,
            budgets = budgetSnapshots,
            thisMonthSpentMinor = netSpend(monthTx),
            lastMonthSpentMinor = netSpend(inMonth(activeTransactions, month.minusMonths(1), currency)),
            dayOfMonth = today.dayOfMonth,
            daysInMonth = month.lengthOfMonth(),
            excludedCurrencyCount = excluded,
        )
        return Dashboard(
            asOf = today,
            currency = currency,
            horizonDays = settings.horizonDays,
            liquidMinor = liquidMinor,
            netWorthMinor = netWorthMinor,
            availableCreditMinor = availableCredit,
            accounts = snapshots.sortedBy { it.account.sortOrder },
            upcoming = upcoming,
            committedPaymentsMinor = committed,
            expectedIncomeMinor = income,
            safeToSpend = safe,
            monthSpendingMinor = monthTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor },
            monthRefundsMinor = monthTx.filter { it.type == TransactionType.REFUND }.sumOf { it.amountMinor },
            monthIncomeMinor = monthTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor },
            recent = activeTransactions.sortedWith(
                compareByDescending<LedgerTransaction> { it.occurredOn }.thenByDescending { it.createdAtEpochMillis },
            ).take(8),
            warnings = warnings,
            budgets = budgetSnapshots,
            preview = preview,
            excludedCurrencyCount = excluded,
            needsReviewCount = transactions.count { it.deletedAtEpochMillis == null && it.status == TransactionStatus.DETECTED },
            forecast = forecast,
            discretionaryDailyMinor = dailyDiscretionary,
        )
    }

    fun insights(
        today: LocalDate,
        settings: UserSettings,
        accounts: List<Account>,
        categories: List<Category>,
        transactions: List<LedgerTransaction>,
        recurring: List<RecurringPayment>,
    ): Insights {
        val currency = settings.primaryCurrency
        val active = transactions.filter { BalanceMath.isActive(it) && it.currency == currency }
        val thisMonth = YearMonth.from(today)
        val current = inMonth(active, thisMonth, currency)
        val previous = inMonth(active, thisMonth.minusMonths(1), currency)
        val byCategory = categories
            .filter { it.parentId == null }
            .map { root ->
                val ids = CategoryTree.scopeIds(root.id, categories)
                CategorySpend(
                    categoryId = root.id,
                    name = root.name,
                    necessity = root.necessity,
                    thisMonthMinor = netIn(current, ids),
                    lastMonthMinor = netIn(previous, ids),
                )
            }
            .filter { it.thisMonthMinor != 0L || it.lastMonthMinor != 0L }
            .sortedByDescending { it.thisMonthMinor }
        val necessity = Necessity.entries.map { necessity ->
            val ids = categories.filter { it.necessity == necessity }.map { it.id }.toSet()
            NamedAmount(necessity.name, necessity.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }, netIn(current, ids))
        }.filter { it.amountMinor != 0L }
        val merchants = current
            .groupBy { it.merchantNormalized.ifBlank { "Unknown merchant" } }
            .map { (name, rows) -> NamedAmount(name, name, netRows(rows)) }
            .filter { it.amountMinor != 0L }
            .sortedByDescending { it.amountMinor }
            .take(8)
        val byAccount = accounts.map { account ->
            NamedAmount(
                account.id,
                account.name,
                netRows(current.filter { it.accountId == account.id }),
            )
        }.filter { it.amountMinor != 0L }
        return Insights(
            currency = currency,
            thisMonthNetMinor = netSpend(current),
            lastMonthNetMinor = netSpend(previous),
            byCategory = byCategory,
            byNecessity = necessity,
            topMerchants = merchants,
            byAccount = byAccount,
            leaks = MoneyLeakEngine.analyze(today, currency, categories, active, recurring),
            anomalies = AnomalyDetector.analyze(today, currency, categories, active),
            subscriptionMonthMinor = SubscriptionRollup.monthlyMinor(SubscriptionRollup.active(recurring, currency)),
            subscriptionAnnualMinor = SubscriptionRollup.annualizedMinor(
                SubscriptionRollup.monthlyMinor(SubscriptionRollup.active(recurring, currency)),
            ),
        )
    }

    private fun recentDiscretionaryMinor(
        transactions: List<LedgerTransaction>,
        categories: List<Category>,
        today: LocalDate,
        currency: String,
    ): Long {
        val discretionaryIds = categories
            .filter { it.necessity == Necessity.DISCRETIONARY || it.necessity == Necessity.POTENTIALLY_UNNECESSARY || it.necessity == Necessity.UNKNOWN }
            .map { it.id }
            .toSet()
        val start = today.minusDays(30)
        return transactions
            .filter { it.currency == currency && !it.occurredOn.isBefore(start) && !it.occurredOn.isAfter(today) }
            .filter { it.categoryId == null || it.categoryId in discretionaryIds }
            .let { netRows(it) }
    }

    private fun occurrence(
        item: RecurringPayment,
        date: LocalDate,
        account: Account?,
        liquidIds: Set<String>,
    ): ScheduledOccurrence {
        val affectsLiquid = account != null && account.id in liquidIds
        val reliableIncome = item.direction == CashFlowDirection.INFLOW && item.incomeConfidence in FinancePolicy.reliableIncome
        val committedOut = item.direction == CashFlowDirection.OUTFLOW && item.priority in FinancePolicy.committedPriorities
        val counted = affectsLiquid && (reliableIncome || committedOut)
        val delta = when {
            !counted -> 0L
            item.direction == CashFlowDirection.INFLOW -> item.amountMinor
            else -> -item.amountMinor
        }
        return ScheduledOccurrence(
            recurringId = item.id,
            name = item.name,
            date = date,
            amountMinor = item.amountMinor,
            direction = item.direction,
            priority = item.priority,
            kind = item.kind,
            accountId = item.accountId,
            accountName = account?.name ?: "Unknown account",
            incomeConfidence = item.incomeConfidence,
            countedInSafeToSpend = counted,
            liquidDeltaMinor = delta,
        )
    }

    private fun snapshotBudget(
        budget: Budget,
        today: LocalDate,
        month: YearMonth,
        categories: List<Category>,
        transactions: List<LedgerTransaction>,
    ): BudgetSnapshot {
        val (start, end, elapsed, length) = if (budget.period == com.ledgerline.app.domain.model.BudgetPeriod.WEEKLY) {
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekEnd = weekStart.plusDays(6)
            val elapsedDays = (today.dayOfWeek.value).coerceAtLeast(1)
            PeriodWindow(weekStart, weekEnd, elapsedDays, 7)
        } else {
            PeriodWindow(month.atDay(1), month.atEndOfMonth(), today.dayOfMonth, month.lengthOfMonth())
        }
        val ids = budget.categoryId?.let { CategoryTree.scopeIds(it, categories) }
        val spent = transactions
            .filter { it.currency == budget.currency }
            .filter { !it.occurredOn.isBefore(start) && !it.occurredOn.isAfter(end) }
            .filter { ids == null || it.categoryId in ids }
            .filter { budget.accountId == null || it.accountId == budget.accountId }
            .let { netRows(it) }
        val math = BudgetMath.evaluate(spent, budget.amountMinor, elapsed, length)
        val categoryName = budget.categoryId?.let { CategoryTree.label(it, categories) } ?: budget.name
        return BudgetSnapshot(
            budget = budget,
            categoryName = categoryName,
            spentMinor = spent,
            remainingMinor = math.remainingMinor,
            projectedMinor = math.projectedMinor,
            pace = math.pace,
        )
    }

    private fun inMonth(transactions: List<LedgerTransaction>, month: YearMonth, currency: String): List<LedgerTransaction> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        return transactions.filter {
            it.currency == currency && !it.occurredOn.isBefore(start) && !it.occurredOn.isAfter(end)
        }
    }

    private fun netIn(transactions: List<LedgerTransaction>, categoryIds: Set<String>): Long =
        netRows(transactions.filter { it.categoryId in categoryIds })

    private fun netSpend(transactions: List<LedgerTransaction>): Long = netRows(transactions)

    private fun netRows(transactions: List<LedgerTransaction>): Long {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
        val refunds = transactions.filter { it.type == TransactionType.REFUND }.sumOf { it.amountMinor }
        return (expenses - refunds).coerceAtLeast(0)
    }

    private data class PeriodWindow(
        val start: LocalDate,
        val end: LocalDate,
        val elapsedDays: Int,
        val lengthDays: Int,
    )
}
