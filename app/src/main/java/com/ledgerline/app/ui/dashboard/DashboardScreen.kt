package com.ledgerline.app.ui.dashboard

import com.ledgerline.app.ui.theme.depth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.Dashboard
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.domain.model.WarningSeverity
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.CaptureRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.domain.usecase.ObserveDashboard
import com.ledgerline.app.finance.balance.DayLedger
import com.ledgerline.app.finance.dashboard.HomeFeed
import com.ledgerline.app.finance.dashboard.HomeMath
import com.ledgerline.app.finance.forecast.SpendingPace
import com.ledgerline.app.finance.dashboard.HomeView
import com.ledgerline.app.finance.dashboard.PlannedActual
import com.ledgerline.app.finance.dashboard.UpcomingWindow
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.finance.period.PeriodLedger
import com.ledgerline.app.finance.period.PeriodReport
import com.ledgerline.app.preferences.UiPreferences
import com.ledgerline.app.ui.components.BannerCard
import com.ledgerline.app.ui.components.EmptyPane
import com.ledgerline.app.ui.components.GroupCard
import com.ledgerline.app.ui.components.KeyValue
import com.ledgerline.app.ui.components.LedgerCard
import com.ledgerline.app.ui.components.ProgressTrack
import com.ledgerline.app.ui.components.ScreenHeader
import com.ledgerline.app.ui.components.SectionHeader
import com.ledgerline.app.ui.components.SubScreen
import com.ledgerline.app.ui.format.cycleRange
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.theme.LocalLedgerColors
import com.ledgerline.app.ui.transactions.ScheduledRow
import com.ledgerline.app.ui.transactions.TransactionRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.ledgerline.app.domain.model.LedgerTransaction
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeDashboard: ObserveDashboard,
    accounts: AccountRepository,
    private val transactionsRepo: TransactionRepository,
    categories: CategoryRepository,
    captures: CaptureRepository,
    private val recurring: RecurringRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: AppClock,
    private val preferences: UiPreferences,
    private val notes: com.ledgerline.app.domain.repository.ExpenseNoteRepository,
    payPlan: com.ledgerline.app.domain.usecase.ObservePayPlan,
    cyclePlans: com.ledgerline.app.domain.repository.CyclePlanRepository,
) : ViewModel() {
    /**
     * The new-cycle card: shown until this cycle is set up (or you hide it for this cycle), with how many of its
     * three steps are done.
     */
    val cycleCard = kotlinx.coroutines.flow.combine(payPlan.observe(), cyclePlans.observeReviews(), preferences.cycleCardHiddenFor, accounts.observe()) { p, reviews, hidden, accountRows ->
        val review = reviews.firstOrNull { it.cycleStart == p.cycle.start } ?: com.ledgerline.app.domain.model.CycleReview(p.cycle.start)
        val ids = accountRows.filter { it.active && !it.deleted }.map { it.id }.toSet()
        if (review.completed || hidden == p.cycle.start.toString() || ids.isEmpty()) null
        else CycleCard(p.cycle.start, p.cycle.end, com.ledgerline.app.finance.cycle.CycleSetup.stepsDone(review, ids))
    }.catch { emit(null) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun hideCycleCard(start: java.time.LocalDate) {
        viewModelScope.launch { preferences.hideCycleCard(start) }
    }

    /** Which pay cycle Home shows: 0 is this one, -1 the last, 1 the next. */
    val cycleOffset = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun shiftCycle(by: Int) {
        cycleOffset.value = (cycleOffset.value + by).coerceIn(-24, 12)
    }

    fun resetCycle() {
        cycleOffset.value = 0
    }

    /** The chosen pay cycle as envelopes: what each part of your pay is for, what is used, and what is left. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val plan = cycleOffset
        .flatMapLatest { offset -> payPlan.observeCycle(offset) }
        .map<com.ledgerline.app.finance.envelopes.PayPlan, com.ledgerline.app.finance.envelopes.PayPlan?> { it }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** This pay cycle, whatever Home is showing: today's summary always reads from it. */
    val currentPlan = payPlan.observe()
        .map<com.ledgerline.app.finance.envelopes.PayPlan, com.ledgerline.app.finance.envelopes.PayPlan?> { it }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Home groups money by envelope or by account. Only changes what Home shows. */
    val byAccount = preferences.homeByAccount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setByAccount(value: Boolean) {
        viewModelScope.launch { preferences.setHomeByAccount(value) }
    }

    /** Saves a purchase you decided to sleep on, due tomorrow, in your spending notes. */
    fun waitADay(name: String, amountMinor: Long) {
        viewModelScope.launch {
            notes.save(
                com.ledgerline.app.domain.model.ExpenseNoteDraft(
                    existingId = null,
                    name = name.ifBlank { "Something I wanted" },
                    amountMinor = amountMinor,
                    dueOn = clock.today().plusDays(1),
                    note = "Waited instead of buying on ${clock.today()}. Still want it?",
                    completed = false,
                ),
            )
        }
    }

    private val periodOverride = MutableStateFlow<PayPeriod?>(null)
    private val cycleSettings = settingsRepository.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings("USD", 0, 30, false),
    )
    val selectedPeriod = combine(periodOverride, settingsRepository.observe()) { override, settings ->
        override ?: CycleSchedule.containing(clock.today(), settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleSchedule.containing(clock.today(), cycleSettings.value))
    fun previousPeriod() {
        periodOverride.value = CycleSchedule.previous(selectedPeriod.value, cycleSettings.value)
    }

    fun nextPeriod() {
        periodOverride.value = CycleSchedule.next(selectedPeriod.value, cycleSettings.value)
    }

    fun currentPeriod() {
        periodOverride.value = null
    }

    val dashboard: StateFlow<Result<Dashboard>?> = observeDashboard.observe()
        .map { Result.success(it) }
        .catch { error -> emit(Result.failure(error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True when the dashboard could not be read. Changes rarely, so it does not redraw Home. */
    val failed: StateFlow<Boolean> = dashboard.map { it?.isFailure == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val ledgerRows = combine(
        transactionsRepo.observeAll(),
        accounts.observeIncludingDeleted(),
        categories.observe(),
    ) { txs, accountRows, categoryRows -> Triple(txs, accountRows, categoryRows) }

    private val schedules = combine(recurring.observe(), recurring.observeAdjustments()) { series, adjustments -> series to adjustments }

    private val chrome = combine(
        captures.observePending().map { it.size },
        preferences.notificationDetection,
        preferences.captureTipDismissed,
        preferences.sampleData,
    ) { pending, captureOn, tipHidden, sample -> HomeChrome(pending, captureOn, tipHidden, sample) }

    /**
     * Everything Home shows, worked out together on a background thread. Home then redraws once per change with
     * finished numbers, instead of once per data source with the arithmetic done while drawing.
     */
    val home: StateFlow<HomeState?> = combine(
        observeDashboard.observe(),
        selectedPeriod,
        ledgerRows,
        schedules,
        chrome,
    ) { dashboard, selected, (txs, accountRows, categoryRows), (series, adjustments), flags ->
        val today = clock.today()
        val liveAccounts = accountRows.filter { !it.deleted }
        val report = PeriodLedger.report(today, selected, liveAccounts, txs, series, adjustments)
        val figures = DayLedger.cycleFigures(selected, liveAccounts, txs)
        val liquidIds = dashboard.accounts.filter { it.account.includeInSafeToSpend }.map { it.account.id }.toSet()
        // Today's limit and the pace forecast use spending from the accounts Safe to spend is built on.
        val liquidTxs = txs.filter { it.accountId in liquidIds }
        val currentCycle = PayPeriod(dashboard.cycleStart, dashboard.cycleEnd)
        val safe = dashboard.safeToSpend.amountMinor
        val spentToday = SpendingPace.everyday(liquidTxs.filter { it.occurredOn == today })
        val cycleSpent = SpendingPace.everyday(liquidTxs.filter { currentCycle.contains(it.occurredOn) && !it.occurredOn.isAfter(today) })
        HomeState(
            limit = SpendingPace.dailyLimit(safe, spentToday, today, dashboard.cycleEnd),
            pace = SpendingPace.forecast(cycleSpent, safe, today, currentCycle),
            dashboard = dashboard,
            period = selected,
            isCurrent = selected.contains(today),
            summary = PlannedActual.summarize(
                period = selected,
                today = today,
                items = dashboard.forecastSchedule,
                liquidAccountIds = liquidIds,
                actualLiquidMinor = if (selected.end.isBefore(today)) figures.endMinor else dashboard.liquidMinor,
                incomeReceivedMinor = report.incomeMinor,
                spentMinor = figures.spentMinor,
            ),
            unplannedMinor = figures.unplannedMinor,
            due = (HomeFeed.overdue(dashboard.upcoming) + UpcomingWindow.scheduled(today, 30, dashboard.upcoming + dashboard.forecastSchedule))
                .distinctBy { Triple(it.recurringId, it.date, it.accountId) }
                .take(HomeFeed.UPCOMING_LIMIT),
            // Deleted transactions are kept for undo and history but never shown on Home.
            // What happened: planned spends not paid yet wait in Activity's "Coming up" and on the calendar instead.
            recent = txs.filter { it.deletedAtEpochMillis == null && it.paid }
                .sortedWith(compareByDescending<LedgerTransaction> { it.occurredOn }.thenByDescending { it.createdAtEpochMillis }).take(5),
            transactions = txs.filter { it.deletedAtEpochMillis == null },
            accounts = accountRows,
            categories = categoryRows,
            chrome = flags,
        )
    }.map<HomeState, HomeState?> { it }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val homeView = preferences.homeView.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeView.ACTUAL)

    /** Only changes what Home shows. No transaction or schedule is touched. */
    fun setHomeView(view: HomeView) {
        viewModelScope.launch { preferences.setHomeView(view) }
    }


    fun hideCaptureTip() {
        viewModelScope.launch { preferences.dismissCaptureTip() }
    }

    var actionMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun markPaid(id: String, date: LocalDate) {
        viewModelScope.launch {
            actionMessage = (recurring.markPaid(id, date) as? SaveResult.Invalid)?.message
        }
    }

    /** A planned spend paid today: it counts from now on, the same as Paid on the calendar or in Activity. */
    fun markSpendPaid(id: String) {
        viewModelScope.launch { transactionsRepo.markSpendPaid(id, clock.today()) }
    }

    fun skip(id: String, date: LocalDate) {
        viewModelScope.launch {
            actionMessage = (recurring.skip(id, date) as? SaveResult.Invalid)?.message
        }
    }

    fun today(): LocalDate = clock.today()
}

data class HomeChrome(val pendingCaptures: Int, val captureOn: Boolean, val tipHidden: Boolean, val sample: Boolean)

data class HomeState(
    val limit: com.ledgerline.app.finance.forecast.DailyLimit,
    val pace: com.ledgerline.app.finance.forecast.PaceForecast,
    val dashboard: Dashboard,
    val period: PayPeriod,
    val isCurrent: Boolean,
    val summary: com.ledgerline.app.finance.dashboard.PlannedActualSummary,
    val unplannedMinor: Long,
    val due: List<com.ledgerline.app.domain.model.ScheduledOccurrence>,
    val recent: List<LedgerTransaction>,
    /** Every transaction, for the lists behind a ring, an account, or other spending. */
    val transactions: List<LedgerTransaction> = emptyList(),
    val accounts: List<com.ledgerline.app.domain.model.Account>,
    val categories: List<com.ledgerline.app.domain.model.Category>,
    val chrome: HomeChrome,
)

@Composable
fun DashboardScreen(
    onSafeToSpend: () -> Unit,
    onTransaction: (String) -> Unit,
    onAccounts: () -> Unit,
    onReview: () -> Unit,
    onCaptures: () -> Unit,
    onCaptureSetup: () -> Unit,
    onEditSchedule: (String, String) -> Unit,
    onCalendar: () -> Unit,
    onActivity: () -> Unit,
    onSettings: () -> Unit,
    onPlan: () -> Unit,
    onUnplanned: (String, String) -> Unit,
    contentPadding: PaddingValues,
    onBuy: (String, Long) -> Unit = { _, _ -> },
    onCashFlow: () -> Unit = {},
    onOpenGoal: (String) -> Unit = {},
    onAddGoal: () -> Unit = {},
    onAddAccount: () -> Unit = {},
    onEditAccount: (String) -> Unit = {},
    onCycleSetup: () -> Unit = {},
    onCycleReview: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val home by viewModel.home.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val state = home
    when {
        failed -> Text(
            "The ledger could not be read. Your records were not changed.",
            modifier = Modifier.padding(contentPadding).padding(20.dp),
        )
        state == null -> Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> HomeBody(
            state = state,
            viewModel = viewModel,
            onSafeToSpend = onSafeToSpend,
            onTransaction = onTransaction,
            onAccounts = onAccounts,
            onReview = onReview,
            onCaptures = onCaptures,
            onCaptureSetup = onCaptureSetup,
            onEditSchedule = onEditSchedule,
            onCalendar = onCalendar,
            onActivity = onActivity,
            onSettings = onSettings,
            onPlan = onPlan,
            onUnplanned = onUnplanned,
            onBuy = onBuy,
            onCashFlow = onCashFlow,
            onOpenGoal = onOpenGoal,
            onAddGoal = onAddGoal,
            onAddAccount = onAddAccount,
            onEditAccount = onEditAccount,
            onCycleSetup = onCycleSetup,
            onCycleReview = onCycleReview,
            contentPadding = contentPadding,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeBody(
    state: HomeState,
    viewModel: DashboardViewModel,
    onSafeToSpend: () -> Unit,
    onTransaction: (String) -> Unit,
    onAccounts: () -> Unit,
    onReview: () -> Unit,
    onCaptures: () -> Unit,
    onCaptureSetup: () -> Unit,
    onEditSchedule: (String, String) -> Unit,
    onCalendar: () -> Unit,
    onActivity: () -> Unit,
    onSettings: () -> Unit,
    onPlan: () -> Unit,
    onUnplanned: (String, String) -> Unit,
    onBuy: (String, Long) -> Unit,
    onCashFlow: () -> Unit,
    onOpenGoal: (String) -> Unit,
    onAddGoal: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (String) -> Unit,
    onCycleSetup: () -> Unit,
    onCycleReview: () -> Unit,
    contentPadding: PaddingValues,
    edit: com.ledgerline.app.ui.home.HomeEditViewModel = hiltViewModel(),
) {
    val cycleCard by viewModel.cycleCard.collectAsStateWithLifecycle()
    val payPlan by viewModel.plan.collectAsStateWithLifecycle()
    val thisCycle by viewModel.currentPlan.collectAsStateWithLifecycle()
    val cycleOffset by viewModel.cycleOffset.collectAsStateWithLifecycle()
    val byAccount by viewModel.byAccount.collectAsStateWithLifecycle()
    val groups by edit.groupList.collectAsStateWithLifecycle()
    val editAccounts by edit.accounts.collectAsStateWithLifecycle()
    var naming by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) } // "new" or a group id
    var removing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val dashboard = state.dashboard
    val accounts = state.accounts
    val categories = state.categories
    val currency = dashboard.currency
    val today = dashboard.asOf
    val plan = payPlan
    val colors = LocalLedgerColors.current
    // Which list is open over Home: "item:<group>:<key>", "account:<id>", or "outside".
    var sheet by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val todayLines = remember(thisCycle, state.transactions, dashboard, today) {
        val names = accounts.associate { it.id to it.name }
        val bills = thisCycle?.bills?.bills.orEmpty().filter { !it.paid && (it.date == today) }.map { bill ->
            com.ledgerline.app.ui.home.TodayLine(
                bill.name, names[bill.accountId].orEmpty(), -bill.amountMinor, bill.overdue,
                onPaid = bill.recurringId?.let { id -> { viewModel.markPaid(id, bill.date) } },
            )
        }
        val income = dashboard.forecastSchedule.filter { it.date == today && it.direction.name == "INFLOW" && it.kind.name == "INCOME" }
            .distinctBy { it.recurringId }
            .map { com.ledgerline.app.ui.home.TodayLine(it.name, it.accountName, it.amountMinor) }
        val planned = state.transactions.filter { com.ledgerline.app.finance.balance.BalanceMath.isPlannedUnpaid(it) && it.occurredOn == today }
            .map { tx ->
                com.ledgerline.app.ui.home.TodayLine(tx.merchantRaw.ifBlank { "Planned spend" }, names[tx.accountId].orEmpty(), -tx.amountMinor, onPaid = { viewModel.markSpendPaid(tx.id) })
            }
        bills + planned + income
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(greeting(), style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = onCashFlow) { Icon(Icons.Filled.ShowChart, contentDescription = "Cash flow") }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
            }
        }
        cycleCard?.let { card ->
            item(key = "cycle") { NewCycleCard(card, onOpen = onCycleSetup, onHide = { viewModel.hideCycleCard(card.start) }) }
        }
        // The last few days of a cycle: a nudge to look back at it before the next one starts.
        val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, dashboard.cycleEnd)
        if (dashboard.accounts.isNotEmpty() && daysLeft in 0..3) {
            item(key = "cycle-review") {
                com.ledgerline.app.ui.home.CompactAlert(
                    if (daysLeft == 0L) "Cycle ends today · see how it went" else "Cycle ends in $daysLeft ${if (daysLeft == 1L) "day" else "days"} · see how it went",
                    "Review",
                    Icons.Filled.Insights,
                    onCycleReview,
                )
            }
        }
        if (dashboard.accounts.isEmpty()) {
            item { com.ledgerline.app.ui.home.CompactAlert("Add your first account to start", "Add", Icons.Filled.AccountBalance, onAccounts) }
        }
        if (state.chrome.pendingCaptures > 0) {
            item(key = "captures") {
                com.ledgerline.app.ui.home.CompactAlert(
                    if (state.chrome.pendingCaptures == 1) "1 spend from your alerts to add" else "${state.chrome.pendingCaptures} spends from your alerts to add",
                    "Review",
                    Icons.Filled.Inbox,
                    onCaptures,
                )
            }
        }
        if (dashboard.needsReviewCount > 0) {
            item(key = "review") { com.ledgerline.app.ui.home.CompactAlert("${dashboard.needsReviewCount} to check before they count", "Check", Icons.Filled.Inbox, onReview) }
        }
        // Everything shows even before anything is entered; empty sections hold one faded example.
        item(key = "today") { com.ledgerline.app.ui.home.TodayStrip(todayLines, currency) }
        if (plan != null) {
            item(key = "pay") {
                com.ledgerline.app.ui.home.PayCard(plan, currency, offset = cycleOffset, onShift = viewModel::shiftCycle, onToday = viewModel::resetCycle) {
                    edit.startPay(plan.incomeMinor, plan.calendarIncomeMinor)
                }
            }
            stickyHeader(key = "switch") {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 2.dp)) {
                    com.ledgerline.app.ui.home.ViewSwitch(byAccount, viewModel::setByAccount)
                }
            }
            val colorsByAccount = com.ledgerline.app.ui.home.accountColors(dashboard.accounts.map { it.account })
            if (dashboard.accounts.isNotEmpty()) {
                item(key = "acct-key") { com.ledgerline.app.ui.home.AccountKey(dashboard.accounts.map { it.account }, colorsByAccount) }
            }
            if (byAccount) {
                item(key = "acct-all") { com.ledgerline.app.ui.envelopes.AccountsOverall(plan, currency) }
                item(key = "acct-rings") {
                    com.ledgerline.app.ui.home.AccountRings(
                        plan.accounts,
                        plan.accounts.associate { it.account.id to com.ledgerline.app.ui.home.accountHolds(plan, it.account.id) },
                        colorsByAccount,
                        currency,
                        onAccount = { sheet = "account:$it" },
                        onAdd = onAddAccount,
                    )
                }
            } else {
                plan.sections.forEachIndexed { index, section ->
                    item(key = "g-${section.id}") {
                        com.ledgerline.app.ui.home.GroupHeader(
                            section = section,
                            tone = com.ledgerline.app.ui.home.groupColor(index),
                            currency = currency,
                            onAdd = { edit.startNew(section.id, plan.cycle.start) },
                            onRename = { naming = section.id },
                            onDelete = { removing = section.id },
                        )
                    }
                    item(key = "r-${section.id}") {
                        com.ledgerline.app.ui.home.RingGrid(
                            section.items, colorsByAccount, currency, today,
                            onItem = { sheet = "item:${section.id}:${it.key}" },
                            onAdd = { edit.startNew(section.id, plan.cycle.start) },
                            sample = com.ledgerline.app.ui.home.sampleFor(section.name),
                        )
                    }
                }
                item(key = "g-savings") {
                    com.ledgerline.app.ui.home.SectionTitle("Savings goals", com.ledgerline.app.ui.home.savingsTone, null, onAddGoal)
                }
                item(key = "r-savings") {
                    com.ledgerline.app.ui.home.RingGrid(plan.goalItems, colorsByAccount, currency, today, onItem = { it.goalId?.let(onOpenGoal) }, onAdd = onAddGoal, sample = "Emergency fund")
                }
                if (plan.outsideMinor > 0) {
                    item(key = "outside") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .depth(RoundedCornerShape(16.dp))
                                .clickable { sheet = "outside" }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Inbox, contentDescription = null, tint = colors.caution, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text("Un-marked spending", style = MaterialTheme.typography.titleMedium)
                                Text("${plan.outsideCount} not picked for any item", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(MoneyFormat.format(plan.outsideMinor, currency), style = MaterialTheme.typography.titleMedium, color = colors.caution)
                        }
                    }
                }
                item(key = "new-group") {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { naming = "new" },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" New group", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item(key = "recent-h") { SectionHeader("Recent", action = "See all", onAction = onActivity) }
        item(key = "recent") {
            if (state.recent.isEmpty()) {
                GroupCard { EmptyPane("No spending yet", "Tap Add to note a spend.") }
            } else {
                GroupCard {
                    state.recent.take(4).forEach { tx -> TransactionRow(tx, accounts, categories) { onTransaction(tx.id) } }
                }
            }
        }
    }

    sheet?.let { open ->
        val byNewest = compareByDescending<LedgerTransaction> { it.occurredOn }.thenByDescending { it.createdAtEpochMillis }
        when {
            open == "outside" && plan != null -> com.ledgerline.app.ui.home.TransactionsSheet(
                title = "Un-marked spending",
                subtitle = "Open one and pick what it is for · ${MoneyFormat.format(plan.outsideMinor, currency)}",
                rows = state.transactions.filter { it.id in plan.outsideIds }.sortedWith(byNewest),
                accounts = accounts,
                categories = categories,
                onEdit = null,
                onPaid = null,
                onOpen = { sheet = null; onTransaction(it) },
                onDismiss = { sheet = null },
            )
            open.startsWith("account:") -> {
                val id = open.removePrefix("account:")
                val account = accounts.firstOrNull { it.id == id }
                val row = plan?.accounts?.firstOrNull { it.account.id == id }
                val holds = plan?.let { com.ledgerline.app.ui.home.accountHolds(it, id) }.orEmpty()
                com.ledgerline.app.ui.home.AccountSheet(
                    title = account?.name ?: "Account",
                    subtitle = row?.let { "Balance ${MoneyFormat.format(it.balanceMinor, currency)}" },
                    holds = holds,
                    rows = state.transactions.filter { it.accountId == id && it.deletedAtEpochMillis == null }.sortedWith(byNewest).take(60),
                    accounts = accounts,
                    categories = categories,
                    currency = currency,
                    onEdit = { sheet = null; onEditAccount(id) },
                    onOpen = { sheet = null; onTransaction(it) },
                    onDismiss = { sheet = null },
                )
            }
            open.startsWith("item:") && plan != null -> {
                val (groupId, key) = open.removePrefix("item:").split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                val item = plan.sections.flatMap { it.items }.firstOrNull { it.key == key }
                if (item == null) {
                    sheet = null
                } else {
                    val rows = if (item.recurringId != null) {
                        state.transactions.filter { it.recurringPaymentId == item.recurringId && it.deletedAtEpochMillis == null }
                    } else {
                        state.transactions.filter { it.id in item.transactionIds }
                    }
                    com.ledgerline.app.ui.home.TransactionsSheet(
                        title = item.name,
                        subtitle = "${MoneyFormat.format(item.spentMinor, currency)} of ${MoneyFormat.format(item.plannedMinor, currency)}" +
                            (item.nextDue?.takeIf { !item.paid }?.let { " · due ${it.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}" } ?: ""),
                        rows = rows.sortedWith(byNewest),
                        accounts = accounts,
                        categories = categories,
                        onEdit = { sheet = null; edit.startEdit(item, groupId, plan.cycle.start) },
                        onPaid = if (item.recurringId != null && item.nextDue != null && !item.paid) {
                            { viewModel.markPaid(item.recurringId, item.nextDue); sheet = null }
                        } else {
                            null
                        },
                        onOpen = { sheet = null; onTransaction(it) },
                        onDismiss = { sheet = null },
                    )
                }
            }
            else -> sheet = null
        }
    }
    edit.paying?.let { pay ->
        com.ledgerline.app.ui.home.PayDialog(pay, currency, edit::onPayText, edit::useCalendarPay, edit::savePay, edit::dismissPay)
    }
    edit.editing?.let { current ->
        com.ledgerline.app.ui.home.ItemSheet(
            edit = current,
            error = edit.error,
            groups = groups.ifEmpty { plan?.sections?.map { com.ledgerline.app.domain.model.HomeGroup(it.id, it.name, 0) }.orEmpty() },
            accounts = editAccounts,
            currency = currency,
            onChange = edit::change,
            onAmount = edit::onAmount,
            onSave = edit::save,
            onPaid = edit::markPaid,
            onDelete = edit::delete,
            onMore = {
                val id = current.recurringId
                edit.dismiss()
                if (id != null) onEditSchedule(id, (current.nextDue ?: today).toString())
            },
            onDismiss = edit::dismiss,
        )
    }
    naming?.let { target ->
        com.ledgerline.app.ui.home.GroupNameDialog(
            title = if (target == "new") "New group" else "Rename group",
            initial = if (target == "new") "" else plan?.sections?.firstOrNull { it.id == target }?.name.orEmpty(),
            onDone = { name ->
                if (target == "new") edit.createGroup(name) else edit.renameGroup(target, name)
                naming = null
            },
            onDismiss = { naming = null },
        )
    }
    removing?.let { target ->
        val section = plan?.sections?.firstOrNull { it.id == target }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Delete ${section?.name ?: "group"}?") },
            text = { Text("Its bills and spending stay; they move to your first group.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { edit.deleteGroup(target); removing = null }) {
                    Text("Delete", color = colors.negative)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val base = modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
    Column((if (onClick != null) base.clickable(onClick = onClick) else base).heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
fun SafeToSpendScreen(onBack: () -> Unit, contentPadding: PaddingValues, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.dashboard.collectAsStateWithLifecycle()
    SubScreen("Safe to spend", onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dashboard = state?.getOrNull()
            if (dashboard == null) {
                Text("Loading the estimate…")
            } else {
                SafeToSpendBreakdown(dashboard)
            }
        }
    }
}

/** The safe-to-spend math as a short list that adds up, shared by Home's detail screen and Plan. */
@Composable
fun SafeToSpendBreakdown(dashboard: Dashboard) {
    val colors = LocalLedgerColors.current
    LedgerCard {
        HomeMath.readableLines(dashboard.safeToSpend.lines).forEach { line ->
            KeyValue(line.label, MoneyFormat.signedLabel(line.minor, dashboard.currency))
        }
        androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Safe to spend", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                MoneyFormat.format(dashboard.safeToSpend.amountMinor, dashboard.currency),
                style = MaterialTheme.typography.titleLarge,
                color = if (dashboard.safeToSpend.amountMinor < 0) colors.negative else colors.positive,
            )
        }
    }
    Text(
        "Only bills marked mandatory or important are set aside; optional ones stay on the calendar. " +
            "Income you are still expecting is not counted until it arrives, so this number only goes up when you get paid.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(dashboard.safeToSpend.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

data class CycleCard(val start: java.time.LocalDate, val end: java.time.LocalDate, val stepsDone: Int)

/** A new cycle has begun: three small steps (statements, balances, plan) and a way into the set-up page. */
@Composable
private fun NewCycleCard(card: CycleCard, onOpen: () -> Unit, onHide: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .depth(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(scheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("New cycle · ${com.ledgerline.app.ui.format.cycleRange(card.start, card.end)}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier.size(width = 18.dp, height = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .background(if (i < card.stepsDone) LocalLedgerColors.current.positive else scheme.surfaceContainerHighest),
                    )
                }
                Text("  Statements · balances · plan", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text("Set up", style = MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.padding(horizontal = 6.dp))
        IconButton(onClick = onHide, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Hide for this cycle", tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}
