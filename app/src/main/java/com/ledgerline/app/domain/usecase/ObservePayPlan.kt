package com.ledgerline.app.domain.usecase

import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.BudgetRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.DebtRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.calendar.BillMonth
import com.ledgerline.app.finance.calendar.DebtCalendar
import com.ledgerline.app.finance.envelopes.EnvelopePlan
import com.ledgerline.app.finance.envelopes.PayPlan
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import com.ledgerline.app.preferences.UiPreferences
import com.ledgerline.app.domain.time.AppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This pay cycle as envelopes, worked out once off the main thread and shared by Home and the pay planner. Bills come
 * from the calendar the same way the Calendar tab reads them, so a bill marked paid there is paid here too.
 */
@Singleton
class ObservePayPlan @Inject constructor(
    private val dashboard: ObserveDashboard,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val recurring: RecurringRepository,
    private val budgets: BudgetRepository,
    private val debts: DebtRepository,
    private val preferences: UiPreferences,
    private val clock: AppClock,
    private val homeGroups: com.ledgerline.app.domain.repository.HomeGroupRepository,
    private val cyclePlans: com.ledgerline.app.domain.repository.CyclePlanRepository,
    private val settings: com.ledgerline.app.domain.repository.SettingsRepository,
    private val goals: com.ledgerline.app.domain.repository.GoalRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val shared: Flow<PayPlan> by lazy {
        combine(
            dashboard.observe(),
            combine(accounts.observe(), transactions.observeAll(), categories.observe(), ::Triple),
            combine(recurring.observe(), recurring.observeAdjustments(), budgets.observe(), cyclePlans.observeAmounts()) { series, adjustments, budgetRows, amounts ->
                Triple(series, adjustments, budgetRows to amounts)
            },
            combine(debts.observe(), debts.observePlans(), debts.observeSettlements(), ::Triple),
            combine(preferences.payIncomeMinor, homeGroups.observe(), homeGroups.observeItems(), ::Triple),
        ) { board, (accountRows, txs, categoryRows), (series, adjustments, budgetSlice), (debtRows, plans, settlements), (income, groupRows, placements) ->
            val today = clock.today()
            val cycle = PayPeriod(board.cycleStart, board.cycleEnd)
            // This cycle's own amounts (set when the cycle was set up) over the usual ones.
            val budgetRows = com.ledgerline.app.finance.cycle.CycleAmounts.budgetsFor(budgetSlice.first, budgetSlice.second, cycle.start)
            val settled = txs.mapNotNull { tx -> tx.recurringPaymentId?.takeIf { BalanceMath.isActive(tx) }?.let { it to tx.occurredOn } }.toSet()
            // Three months back as well, so bills left unpaid from earlier cycles are carried into this one.
            val from = cycle.start.minusMonths(3)
            val events = OccurrenceMaterializer.project(series, adjustments, settled, from, cycle.end, today)
            var month = YearMonth.from(from)
            val debtItems = buildList {
                while (!month.isAfter(YearMonth.from(cycle.end))) {
                    addAll(DebtCalendar.items(month, debtRows, plans, settlements, today, respectVisibility = true))
                    month = month.plusMonths(1)
                }
            }
            val entries = BillMonth.entries(events, debtItems, debtRows.mapNotNull { it.recurringPaymentId }.toSet())
            EnvelopePlan.build(
                cycle = cycle,
                today = today,
                accounts = accountRows,
                transactions = txs,
                categories = categoryRows,
                budgets = budgetRows,
                bills = entries,
                goals = board.goals,
                incomeOverrideMinor = income,
                currency = board.currency,
                groups = groupRows,
                placements = placements,
            )
        }.flowOn(Dispatchers.Default)
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    fun observe(): Flow<PayPlan> = shared

    /**
     * Another pay cycle, [offset] cycles from this one (-1 is the last, 1 the next), worked out the same way so Home
     * can show its groups and accounts. A past cycle is seen as at its last day: its spending, its bills paid or not,
     * and balances as they were then. A coming cycle starts from today's balances with its bills and items ahead.
     * Offset 0 is this cycle, shared with everything else.
     */
    fun observeCycle(offset: Int): Flow<PayPlan> = if (offset == 0) shared else combine(
        combine(settings.observe(), dashboard.observe(), ::Pair),
        combine(accounts.observe(), transactions.observeAll(), categories.observe(), ::Triple),
        combine(recurring.observe(), recurring.observeAdjustments(), budgets.observe(), cyclePlans.observeAmounts()) { series, adjustments, budgetRows, amounts ->
            Triple(series, adjustments, budgetRows to amounts)
        },
        combine(debts.observe(), debts.observePlans(), debts.observeSettlements(), ::Triple),
        combine(preferences.payIncomeMinor, homeGroups.observe(), homeGroups.observeItems(), goals.observe(), goals.observeContributions()) { income, groupRows, placements, goalRows, contributions ->
            PlanExtras(income, groupRows, placements, goalRows, contributions)
        },
    ) { (userSettings, board), (accountRows, allTxs, categoryRows), (series, adjustments, budgetSlice), (debtRows, plans, settlements), extras ->
        val realToday = clock.today()
        var cycle = com.ledgerline.app.finance.period.CycleSchedule.containing(realToday, userSettings)
        repeat(kotlin.math.abs(offset)) {
            cycle = if (offset < 0) com.ledgerline.app.finance.period.CycleSchedule.previous(cycle, userSettings)
            else com.ledgerline.app.finance.period.CycleSchedule.next(cycle, userSettings)
        }
        // A past cycle is looked at from its last day; anything after it has not happened yet.
        val today = if (offset < 0) cycle.end else realToday
        val txs = if (offset < 0) allTxs.filter { !it.occurredOn.isAfter(cycle.end) } else allTxs
        val budgetRows = com.ledgerline.app.finance.cycle.CycleAmounts.budgetsFor(budgetSlice.first, budgetSlice.second, cycle.start)
        val settled = txs.mapNotNull { tx -> tx.recurringPaymentId?.takeIf { BalanceMath.isActive(tx) }?.let { it to tx.occurredOn } }.toSet()
        val from = cycle.start.minusMonths(3)
        val events = OccurrenceMaterializer.project(series, adjustments, settled, from, cycle.end, today)
        var month = YearMonth.from(from)
        val debtItems = buildList {
            while (!month.isAfter(YearMonth.from(cycle.end))) {
                addAll(DebtCalendar.items(month, debtRows, plans, settlements, today, respectVisibility = true))
                month = month.plusMonths(1)
            }
        }
        val entries = BillMonth.entries(events, debtItems, debtRows.mapNotNull { it.recurringPaymentId }.toSet())
        val goalProgress = com.ledgerline.app.finance.goals.GoalMath.progressAll(
            extras.goals, extras.contributions, accountRows, txs, today, cycle,
            com.ledgerline.app.finance.cycle.CycleAmounts.goalPlans(budgetSlice.second, cycle.start),
        )
        EnvelopePlan.build(
            cycle = cycle,
            today = today,
            accounts = accountRows,
            transactions = txs,
            categories = categoryRows,
            budgets = budgetRows,
            bills = entries,
            goals = goalProgress,
            incomeOverrideMinor = extras.income,
            currency = board.currency,
            groups = extras.groups,
            placements = extras.placements,
        )
    }.flowOn(Dispatchers.Default)
}

private data class PlanExtras(
    val income: Long?,
    val groups: List<com.ledgerline.app.domain.model.HomeGroup>,
    val placements: List<com.ledgerline.app.domain.model.HomeGroupItem>,
    val goals: List<com.ledgerline.app.domain.model.SavingsGoal>,
    val contributions: List<com.ledgerline.app.domain.model.GoalContribution>,
)
