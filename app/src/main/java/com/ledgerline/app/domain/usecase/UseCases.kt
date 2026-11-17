package com.ledgerline.app.domain.usecase

import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.Dashboard
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtMonthPlan
import com.ledgerline.app.domain.model.DebtSettlement
import com.ledgerline.app.domain.model.Insights
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.BudgetRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.DebtRepository
import com.ledgerline.app.domain.repository.LedgerReadModel
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.dashboard.DashboardAssembler
import com.ledgerline.app.finance.export.LedgerExport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The dashboard is the heaviest calculation in the app (balances, schedules, forecast, debts). It is computed once,
 * off the main thread, and shared by every screen that shows it, so opening or swiping back to a screen does not
 * recalculate it while an animation is running.
 */
@javax.inject.Singleton
class ObserveDashboard @Inject constructor(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val recurring: RecurringRepository,
    private val budgets: BudgetRepository,
    private val settings: SettingsRepository,
    private val debts: DebtRepository,
    private val clock: AppClock,
    private val goals: com.ledgerline.app.domain.repository.GoalRepository,
    private val cyclePlans: com.ledgerline.app.domain.repository.CyclePlanRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Results, not values, are shared: a failure reaches each screen's own error handling instead of the shared scope.
    private val shared: Flow<Result<Dashboard>> by lazy {
        build()
            .map { Result.success(it) }
            .catch { error -> emit(Result.failure(error)) }
            .flowOn(Dispatchers.Default)
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    fun observe(): Flow<Dashboard> = shared.map { it.getOrThrow() }

    private fun build(): Flow<Dashboard> = combine(
        combine(accounts.observe(), transactions.observeAll(), categories.observe(), ::Triple),
        combine(
            recurring.observe(),
            recurring.observeAdjustments(),
            budgets.observe(),
            settings.observe(),
        ) { series, adjustments, budgetRows, userSettings ->
            PlanningSlice(series, adjustments, budgetRows, userSettings)
        },
        combine(debts.observe(), debts.observePlans(), debts.observeSettlements(), ::Triple),
        combine(goals.observe(), goals.observeContributions(), cyclePlans.observeAmounts(), ::Triple),
    ) { left, right, debtRows, goalRows ->
        DashboardAssembler.assemble(
            today = clock.today(),
            settings = right.settings,
            accounts = left.first,
            categories = left.third,
            transactions = left.second,
            recurring = right.recurring,
            budgets = right.budgets,
            adjustments = right.adjustments,
            debts = debtRows.first,
            debtPlans = debtRows.second,
            debtSettlements = debtRows.third,
            goals = goalRows.first,
            goalContributions = goalRows.second,
            cycleAmounts = goalRows.third,
        )
    }

    suspend fun current(): Dashboard {
        val today = clock.today()
        return DashboardAssembler.assemble(
            today = today,
            settings = settings.observe().first(),
            accounts = accounts.observe().first(),
            categories = categories.observe().first(),
            transactions = transactions.observeAll().first(),
            recurring = recurring.observe().first(),
            budgets = budgets.observe().first(),
            adjustments = recurring.observeAdjustments().first(),
            debts = debts.observe().first(),
            debtPlans = debts.observePlans().first(),
            debtSettlements = debts.observeSettlements().first(),
            goals = goals.observe().first(),
            goalContributions = goals.observeContributions().first(),
            cycleAmounts = cyclePlans.observeAmounts().first(),
        )
    }
}

private data class PlanningSlice(
    val recurring: List<RecurringPayment>,
    val adjustments: List<OccurrenceAdjustment>,
    val budgets: List<Budget>,
    val settings: UserSettings,
)

class ObserveInsights @Inject constructor(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val recurring: RecurringRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    fun observe(): Flow<Insights> = build().flowOn(Dispatchers.Default)

    private fun build(): Flow<Insights> = combine(
        accounts.observe(),
        transactions.observeAll(),
        categories.observe(),
        recurring.observe(),
        settings.observe(),
    ) { accountRows, transactionRows, categoryRows, recurringRows, userSettings ->
        DashboardAssembler.insights(clock.today(), userSettings, accountRows, categoryRows, transactionRows, recurringRows)
    }
}

class ExportLedger @Inject constructor(
    private val readModel: LedgerReadModel,
    private val clock: AppClock,
) {
    suspend fun csv(): String = LedgerExport.csv(readModel.transactions(), readModel.accountsIncludingDeleted(), readModel.categories())

    suspend fun json(): String = LedgerExport.jsonDocument(
        exportedAtEpochMillis = clock.now().toEpochMilli(),
        settings = readModel.settings(),
        accounts = readModel.accountsIncludingDeleted(),
        categories = readModel.categories(),
        transactions = readModel.transactions(),
        recurring = readModel.recurring(),
        budgets = readModel.budgets(),
    )
}
