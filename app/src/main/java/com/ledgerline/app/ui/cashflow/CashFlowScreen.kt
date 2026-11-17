package com.ledgerline.app.ui.cashflow

import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.foundation.border
import com.ledgerline.app.finance.calendar.BillMonth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.DateRange
import com.ledgerline.app.ui.theme.depth
import com.ledgerline.app.ui.transactions.DatePickerDialogOnly
import com.ledgerline.app.finance.money.AmountInput
import com.ledgerline.app.finance.forecast.WhatIfResult
import com.ledgerline.app.finance.forecast.WhatIfBuy
import com.ledgerline.app.finance.forecast.WhatIf
import com.ledgerline.app.finance.forecast.PastDay
import com.ledgerline.app.finance.forecast.CashFlowHistory
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.usecase.ObserveDashboard
import com.ledgerline.app.finance.forecast.CashFlowPlan
import com.ledgerline.app.finance.forecast.DailyAccountForecast
import com.ledgerline.app.finance.forecast.DayBalance
import com.ledgerline.app.finance.forecast.FlowDay
import com.ledgerline.app.finance.forecast.FlowRisk
import com.ledgerline.app.finance.forecast.FlowSummary
import com.ledgerline.app.finance.forecast.SpendingPace
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.ui.components.GroupCard
import com.ledgerline.app.ui.components.SectionHeader
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.theme.LocalLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** The periods offered, with short labels. */
private val periods = listOf(30 to "30 days", 60 to "60 days", 90 to "3 months", 180 to "6 months", 360 to "1 year")

/** What the cash-flow numbers are built from besides the dashboard. */
private data class Extras(
    val drafts: List<BuyDraft>,
    val plan: com.ledgerline.app.finance.envelopes.PayPlan,
    val series: List<com.ledgerline.app.domain.model.RecurringPayment>,
    val adjustments: List<com.ledgerline.app.domain.model.OccurrenceAdjustment>,
    val budgets: List<com.ledgerline.app.domain.model.Budget>,
    val categories: List<com.ledgerline.app.domain.model.Category>,
    val cycleAmounts: List<com.ledgerline.app.domain.model.CycleAmount> = emptyList(),
)

/** One account's balance at the end of each day shown: what it was before today, then where it is heading. */
data class AccountLine(val id: String, val name: String, val byDate: Map<LocalDate, Long>)

/** A purchase you are trying out, as typed. Nothing here is saved. */
data class BuyDraft(val id: Int, val text: String, val date: LocalDate)

data class CashFlowState(
    val today: LocalDate,
    val currency: String,
    val days: List<DayBalance>,
    val summary: FlowSummary?,
    val timeline: List<FlowDay>,
    val paydays: List<LocalDate>,
    val bufferMinor: Long,
    /** Your everyday spending a day this cycle; the projection assumes you keep it up. */
    val paceMinor: Long,
    val periodDays: Int,
    /** Each chosen account's balance over the dates shown, for its own line on the graph. */
    val accountLines: List<AccountLine> = emptyList(),
    /** The first and last day shown. */
    val rangeFrom: LocalDate = today,
    val rangeTo: LocalDate = today,
    /** Past cash had the purchases you are trying been made; null when there are none. */
    val pastScenario: List<Long>? = null,
    /** Planned against paid spending over the dates shown. */
    val track: com.ledgerline.app.finance.forecast.SpendingTrackResult? = null,
    /** The dates picked by hand, if any. */
    val customRange: Pair<LocalDate, LocalDate>? = null,
    val accounts: List<Account>,
    val selected: Set<String>,
    val accountShort: Boolean,
    /** Safe to spend now, so the headline can say when cash is fine but budgets and the buffer already take it all. */
    val safeMinor: Long = 0,
    /** What actually happened on the days before today. */
    val past: List<PastDay> = emptyList(),
    val bigSpends: Set<LocalDate> = emptySet(),
    val buys: List<WhatIfBuy> = emptyList(),
    /** The projection with the purchases you are trying out; null when there are none. */
    val scenario: List<DayBalance>? = null,
    val whatIf: WhatIfResult? = null,
)

@HiltViewModel
class CashFlowViewModel @Inject constructor(
    observe: ObserveDashboard,
    transactions: TransactionRepository,
    settings: SettingsRepository,
    payPlan: com.ledgerline.app.domain.usecase.ObservePayPlan,
    recurring: com.ledgerline.app.domain.repository.RecurringRepository,
    budgets: com.ledgerline.app.domain.repository.BudgetRepository,
    categories: com.ledgerline.app.domain.repository.CategoryRepository,
    cyclePlans: com.ledgerline.app.domain.repository.CyclePlanRepository,
) : ViewModel() {
    /** Dates you picked. Null means this pay cycle. Can reach into the past, the future, or both. */
    private val range = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    private val chosenAccounts = MutableStateFlow<Set<String>?>(null)
    private val drafts = MutableStateFlow<List<BuyDraft>>(emptyList())
    private var nextId = 0

    /**
     * The purchases as typed, read by the text fields directly. The projection catches up from [drafts] a moment
     * later; reading the fields from it would drop keys typed faster than it recalculates.
     */
    var typed by androidx.compose.runtime.mutableStateOf<List<BuyDraft>>(emptyList())
        private set

    private fun update(list: List<BuyDraft>) {
        typed = list
        drafts.value = list
    }

    val state = combine(
        observe.observe(),
        transactions.observeAll(),
        settings.observe(),
        combine(chosenAccounts, range) { chosen, picked -> chosen to picked },
        combine(drafts, payPlan.observe(), recurring.observe(), recurring.observeAdjustments(), combine(budgets.observe(), categories.observe(), cyclePlans.observeAmounts(), ::Triple)) { d, p, series, adjustments, (b, c, a) ->
            Extras(d, p, series, adjustments, b, c, a)
        },
    ) { dashboard, txs, userSettings, (chosen, picked), extras ->
        val drafted = extras.drafts
        val plan = extras.plan
        val today = dashboard.asOf
        val cyclePeriod = PayPeriod(dashboard.cycleStart, dashboard.cycleEnd)
        val (from, to) = picked ?: (cyclePeriod.start to cyclePeriod.end)
        val days = (java.time.temporal.ChronoUnit.DAYS.between(today, to).toInt() + 1).coerceIn(1, 1_100)
        val lookback = java.time.temporal.ChronoUnit.DAYS.between(from, today).toInt().coerceIn(0, 1_100)
        val active = dashboard.accounts.map { it.account }.filter { it.active }.distinctBy { it.id }
        val activeIds = active.map { it.id }.toSet()
        val liquid = active.filter { it.includeInSafeToSpend }.map { it.id }.toSet()
        // Start with the spending accounts Safe to spend uses, so the cash here matches Home.
        val selected = chosen?.intersect(activeIds) ?: liquid.ifEmpty { activeIds }
        val cycle = PayPeriod(dashboard.cycleStart, dashboard.cycleEnd)
        val pace = SpendingPace.forecast(
            SpendingPace.everyday(txs.filter { it.accountId in liquid && cycle.contains(it.occurredOn) && !it.occurredOn.isAfter(today) }),
            dashboard.safeToSpend.amountMinor,
            today,
            cycle,
        ).dailyAverageMinor
        // Only what is really scheduled moves the line: bills, income, spends you planned (on their day, or today if
        // the day has passed), and anything recorded for a future date. No daily spending is assumed.
        val ahead = txs.map { tx ->
            if (com.ledgerline.app.finance.balance.BalanceMath.isPlannedUnpaid(tx)) tx.copy(paid = true, occurredOn = maxOf(tx.occurredOn, today)) else tx
        }
        val result = DailyAccountForecast.project(today, days, active, selected, ahead, dashboard.forecastSchedule, 0L)
        val combined = result.combined.orEmpty()
        val paydays = CashFlowPlan.paydays(dashboard.forecastSchedule, selected, today)
        val buffer = userSettings.minimumBufferMinor
        val accountShort = result.accounts.any { row ->
            row.days.any { day -> day.negative && combined.firstOrNull { it.date == day.date }?.negative == false }
        }
        val past = CashFlowHistory.startOn(today, lookback, active, selected, txs)
            ?.let { from -> CashFlowHistory.days(from, today, active, selected, txs) }
            .orEmpty()
        val digits = runCatching { java.util.Currency.getInstance(dashboard.currency).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
        val last = combined.lastOrNull()?.date ?: today
        // A purchase you try can be on any day shown, past or future: in the past it shows what your cash would have been.
        val buys = drafted
            .map { WhatIfBuy(AmountInput.toMinor(it.text, digits), it.date.coerceIn(minOf(from, today), maxOf(last, today))) }
            .filter { it.amountMinor > 0 }
        val summary = CashFlowPlan.summarize(combined, paydays, buffer)
        val scenario = if (buys.isEmpty()) null else WhatIf.apply(combined, buys)
        val scenarioSummary = scenario?.let { CashFlowPlan.summarize(it, paydays, buffer) }
        CashFlowState(
            today = today,
            currency = dashboard.currency,
            days = combined,
            summary = summary,
            timeline = CashFlowPlan.timeline(combined, dashboard.forecastSchedule, ahead, selected, active.associate { it.id to it.name }),
            track = run {
                // Planned spending over the dates shown: every bill due in them (paid or not), plans, and budgets at their
                // daily rate for this pay cycle.
                val settled = txs.mapNotNull { tx -> tx.recurringPaymentId?.takeIf { com.ledgerline.app.finance.balance.BalanceMath.isActive(tx) }?.let { it to tx.occurredOn } }.toSet()
                // Three months back too, so bills still unpaid from before the range are on the planned line.
                val bills = com.ledgerline.app.finance.forecast.SpendingTrack.billsFor(
                    com.ledgerline.app.finance.schedule.OccurrenceMaterializer.project(extras.series, extras.adjustments, settled, from.minusMonths(3), to, today),
                    from,
                )
                // A planned spend for a spending item is already in that item's budget; count it once.
                val activeBudgets = com.ledgerline.app.finance.cycle.CycleAmounts.budgetsFor(extras.budgets, extras.cycleAmounts, cyclePeriod.start)
                    .filter { it.active && it.currency == dashboard.currency }
                val planned = txs.filter { it.plannedOn != null || com.ledgerline.app.finance.balance.BalanceMath.isPlannedUnpaid(it) }
                val covered = com.ledgerline.app.finance.envelopes.EnvelopePlan.owners(activeBudgets, extras.categories, planned.map { it.copy(paid = true) })
                    .filterKeys { it != null }.values.flatten().map { it.id }.toSet()
                val cycleDays = (java.time.temporal.ChronoUnit.DAYS.between(cyclePeriod.start, cyclePeriod.end) + 1).coerceAtLeast(1)
                val rangeDays = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
                val budgets = plan.envelopes.filter { it.kind == com.ledgerline.app.finance.envelopes.EnvelopeKind.SPENDING }.sumOf { it.plannedMinor }
                com.ledgerline.app.finance.forecast.SpendingTrack.build(PayPeriod(from, to), today, bills, budgets * rangeDays / cycleDays, txs) { it.id in covered }
            },
            rangeFrom = from,
            rangeTo = to,
            // One line per chosen account: its recorded past from the same first day as the total, then its projection.
            accountLines = active.filter { it.id in selected }.map { account ->
                val history = past.firstOrNull()?.date?.let { start -> CashFlowHistory.days(start, today, active, setOf(account.id), txs) }.orEmpty()
                val ahead = result.accounts.firstOrNull { it.accountId == account.id }?.days.orEmpty()
                AccountLine(account.id, account.name, (history.map { it.date to it.endMinor } + ahead.map { it.date to it.endMinor }).toMap())
            },
            pastScenario = if (buys.isEmpty()) null else past.map { day -> day.endMinor - buys.filter { !it.date.isAfter(day.date) }.sumOf { it.amountMinor } },
            paydays = paydays,
            bufferMinor = buffer,
            paceMinor = pace,
            periodDays = 0,
            customRange = picked,
            accounts = active,
            selected = selected,
            accountShort = accountShort,
            safeMinor = dashboard.safeToSpend.amountMinor,
            past = past,
            bigSpends = CashFlowHistory.bigSpendDays(past),
            buys = buys,
            scenario = scenario,
            whatIf = if (summary != null && scenarioSummary != null) WhatIf.result(summary, scenarioSummary, buys, pace, dashboard.safeToSpend.amountMinor) else null,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Back to this pay cycle. */
    fun resetRange() {
        range.value = null
    }

    /** Shows [from] to [to]: the real past up to today, then the projection to the end date. */
    fun chooseRange(from: LocalDate, to: LocalDate) {
        range.value = if (to.isBefore(from)) to to from else from to to
    }

    /** Starts trying out a purchase on [date]. At most four at once, so the chart stays readable. */
    fun addBuy(date: LocalDate) {
        if (typed.size >= 4) return
        update(typed + BuyDraft(nextId++, "", date))
    }

    fun editBuy(id: Int, text: String? = null, date: LocalDate? = null) {
        update(typed.map { if (it.id == id) it.copy(text = text?.let { t -> AmountInput.clean(t, 2) } ?: it.text, date = date ?: it.date) else it })
    }

    fun removeBuy(id: Int) {
        update(typed.filter { it.id != id })
    }

    fun clearBuys() {
        update(emptyList())
    }

    fun toggleAccount(id: String, current: Set<String>) {
        chosenAccounts.value = if (id in current) current - id else current + id
    }

    fun chooseAccounts(ids: Set<String>) {
        chosenAccounts.value = ids
    }
}

/**
 * Cash flow answers "will my money last?" From the top: the headline (when it runs out, or its lowest point), a chart
 * you can drag along, a slider to see what spending less a day would change, and only the days money actually moves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(contentPadding: PaddingValues, viewModel: CashFlowViewModel = hiltViewModel()) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val state = loaded
    var pickingAccounts by rememberSaveable { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var whatIfOpen by rememberSaveable { mutableStateOf(false) }
    var showWhatIf by rememberSaveable { mutableStateOf(true) }
    // Which list sits under the chart: what is coming, what already happened, or every day ahead.
    var list by rememberSaveable { mutableStateOf("ahead") }
    if (state == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text("Working out your cash flow…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val currency = state.currency
    LazyColumn(
        Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val summary = state.summary
        if (summary == null) {
            item {
                Text(
                    "Choose at least one account to see its cash flow.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            item { Headline(summary, currency, state.today, state.safeMinor) }
            item {
                UnifiedChart(
                    state = state,
                    summary = summary,
                    whatIfOpen = whatIfOpen,
                    showWhatIf = showWhatIf,
                    onShowWhatIf = { showWhatIf = it },
                    onWhatIf = {
                        whatIfOpen = !whatIfOpen
                        if (whatIfOpen && viewModel.typed.isEmpty()) viewModel.addBuy(state.today)
                    },
                    onFrom = { pickFrom = true },
                    onTo = { pickTo = true },
                    onReset = viewModel::resetRange,
                    onAccounts = { pickingAccounts = true },
                )
            }
            if (whatIfOpen) item {
                WhatIfCard(
                    state = state,
                    drafts = viewModel.typed,
                    onAdd = viewModel::addBuy,
                    onAmount = { id, text -> viewModel.editBuy(id, text = text) },
                    onDate = { id, date -> viewModel.editBuy(id, date = date) },
                    onRemove = viewModel::removeBuy,
                    onClear = viewModel::clearBuys,
                )
            }
            if (state.accountShort) {
                item {
                    Text(
                        "One of the chosen accounts goes below zero even though the total does not. Move money before that day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalLedgerColors.current.caution,
                    )
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(list == "ahead", { list = "ahead" }, { Text("Coming up") })
                FilterChip(list == "past", { list = "past" }, { Text("What happened") })
                FilterChip(list == "every", { list = "every" }, { Text("Every day ahead") })
            }
        }
        when (list) {
            "every" -> {
                item {
                    GroupCard {
                        state.days.forEach { day -> EveryDayRow(day, state, currency) }
                    }
                }
            }
            "past" -> {
                val moved = state.past.filter { it.inMinor != 0L || it.spentMinor > 0 || it.billsMinor > 0 }.reversed()
                if (moved.isEmpty()) {
                    item {
                        Text(
                            "Nothing recorded on these accounts before today yet. Spending you add or capture shows up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        GroupCard {
                            moved.forEach { day -> PastRow(day, state, currency) }
                        }
                    }
                }
            }
            else -> {
                if (state.timeline.isEmpty()) {
                    item {
                        Text(
                            "No bills or income in this period. Add them on the Calendar and they will show here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.timeline, key = { "flow-${it.date}" }) { day -> TimelineDay(day, state, currency) }
            }
        }
        item {
            Text(
                "Before today: what you recorded. From today: your cash with only bills and income on their dates. Everyday spending is not guessed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (pickFrom) {
        DatePickerDialogOnly(state.rangeFrom, onDismiss = { pickFrom = false }) { date ->
            viewModel.chooseRange(date, state.rangeTo)
            pickFrom = false
        }
    }
    if (pickTo) {
        DatePickerDialogOnly(state.rangeTo, onDismiss = { pickTo = false }) { date ->
            viewModel.chooseRange(state.rangeFrom, date)
            pickTo = false
        }
    }
    if (pickingAccounts) {
        ModalBottomSheet(onDismissRequest = { pickingAccounts = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Accounts in the forecast", style = MaterialTheme.typography.titleLarge)
                state.accounts.forEach { account ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = account.id in state.selected, role = Role.Checkbox) { viewModel.toggleAccount(account.id, state.selected) }
                            .heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = account.id in state.selected, onCheckedChange = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(account.name, style = MaterialTheme.typography.titleSmall)
                            if (!account.includeInSafeToSpend) {
                                Text("Not a spending account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row {
                    TextButton(onClick = { viewModel.chooseAccounts(state.accounts.filter { it.includeInSafeToSpend }.map { it.id }.toSet()) }) {
                        Text("Spending accounts")
                    }
                    TextButton(onClick = { viewModel.chooseAccounts(state.accounts.map { it.id }.toSet()) }) { Text("All") }
                }
            }
        }
    }
}

@Composable
private fun Headline(summary: FlowSummary, currency: String, today: LocalDate, safeMinor: Long) {
    val colors = LocalLedgerColors.current
    val risk = CashFlowPlan.risk(summary)
    val (tone, title) = when (risk) {
        FlowRisk.SHORT -> colors.negative to "Cash runs out ${summary.runsOutOn!!.medium()}"
        FlowRisk.TIGHT -> colors.caution to "Tight: lowest ${MoneyFormat.format(summary.lowestMinor, currency)} on ${summary.lowestOn.medium()}"
        FlowRisk.FINE -> colors.positive to "Lowest point ${MoneyFormat.format(summary.lowestMinor, currency)} on ${summary.lowestOn.medium()}"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(tone.copy(alpha = 0.12f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = tone)
        Text(
            when (risk) {
                FlowRisk.SHORT -> "At this rate you would be ${MoneyFormat.format(-summary.lowestMinor, currency)} short at the lowest point" +
                    (summary.nextPayday?.let { " and before payday on ${it.medium()}." } ?: ".")
                FlowRisk.TIGHT -> "Dips into your safety buffer."
                FlowRisk.FINE -> "Stays above your safety buffer."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("End of today", MoneyFormat.format(summary.todayMinor, currency), Modifier.weight(1f))
            if (summary.nextPayday != null && summary.beforePaydayMinor != null) {
                Stat("Before payday ${summary.nextPayday.medium()}", MoneyFormat.format(summary.beforePaydayMinor, currency), Modifier.weight(1f))
            }
            Stat(summary.endOn.medium(), MoneyFormat.format(summary.endMinor, currency), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .depth(RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

private fun indexAt(x: Float, width: Float, count: Int): Int =
    if (count <= 1 || width <= 0f) 0 else ((x / width) * (count - 1) + 0.5f).toInt().coerceIn(0, count - 1)

@Composable
private fun LineKey(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 8.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun Dot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * The impulse check, on the calendar: "what if I buy this, on that day?" Type an amount, pick the day, and see it on
 * the chart as a dashed line, with what it does to your lowest point and payday. Nothing is saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatIfCard(
    state: CashFlowState,
    drafts: List<BuyDraft>,
    onAdd: (LocalDate) -> Unit,
    onAmount: (Int, String) -> Unit,
    onDate: (Int, LocalDate) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalLedgerColors.current
    val currency = state.currency
    val summary = state.summary ?: return
    val symbol = remember(currency) {
        runCatching { java.util.Currency.getInstance(currency).getSymbol(Locale.getDefault()) }.getOrDefault(currency)
    }
    var datingId by remember { mutableStateOf<Int?>(null) }
    // The newest purchase opens with the keypad ready.
    var keypadFor by remember { mutableStateOf<Int?>(drafts.lastOrNull()?.takeIf { it.text.isEmpty() }?.id) }
    LaunchedEffect(drafts.size) { drafts.lastOrNull()?.takeIf { it.text.isEmpty() }?.let { keypadFor = it.id } }
    val today = state.today
    val saturday = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SATURDAY))
    val beforePayday = summary.nextPayday?.minusDays(1)?.takeIf { it.isAfter(today) }
    Column(
        Modifier
            .fillMaxWidth()
            .depth(RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("What if I buy this?", style = MaterialTheme.typography.titleMedium)
        if (drafts.isEmpty()) {
            Text(
                "Pick a day, type an amount, see your cash. Nothing is saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onAdd(today) }, label = { Text("Today") })
                AssistChip(onClick = { onAdd(today.plusDays(1)) }, label = { Text("Tomorrow") })
                if (saturday.isAfter(today.plusDays(1))) AssistChip(onClick = { onAdd(saturday) }, label = { Text("This weekend") })
                if (beforePayday != null && beforePayday.isAfter(today.plusDays(1))) AssistChip(onClick = { onAdd(beforePayday) }, label = { Text("Before payday") })
                AssistChip(onClick = { onAdd(today) }, label = { Text("Pick a day…") })
            }
        }
        drafts.forEach { draft ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.ledgerline.app.ui.components.AmountBox(
                    text = draft.text,
                    symbol = symbol,
                    label = "Amount",
                    open = keypadFor == draft.id,
                    onOpen = { keypadFor = if (keypadFor == draft.id) null else draft.id },
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = { datingId = draft.id },
                    label = { Text(if (draft.date == today) "Today" else draft.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))) },
                )
                IconButton(onClick = { onRemove(draft.id) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove this purchase")
                }
            }
            if (keypadFor == draft.id) {
                com.ledgerline.app.ui.components.AmountKeypad(draft.text, { onAmount(draft.id, it) }, onDone = { keypadFor = null })
            }
        }
        val result = state.whatIf
        if (result != null) {
            val (tone, title) = when {
                result.newlyShort -> colors.negative to "You would run out of cash on ${result.runsOutOn!!.medium()}"
                result.runsOutOn != null -> colors.negative to "You would be ${MoneyFormat.format(-result.lowestMinor, currency)} short at the lowest point"
                result.newlyIntoBuffer -> colors.caution to "That dips into your safety buffer"
                else -> colors.positive to "Your cash can take it"
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(tone.copy(alpha = 0.12f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tone)
                WhatIfLine("Lowest point", "${MoneyFormat.format(summary.lowestMinor, currency)} → ${MoneyFormat.format(result.lowestMinor, currency)}", result.lowestOn.medium())
                if (summary.nextPayday != null && result.beforePaydayMinor != null && summary.beforePaydayMinor != null) {
                    WhatIfLine(
                        "Left before payday",
                        "${MoneyFormat.format(summary.beforePaydayMinor, currency)} → ${MoneyFormat.format(result.beforePaydayMinor, currency)}",
                        summary.nextPayday.medium(),
                    )
                }
                result.daysOfSpending?.takeIf { it >= 1 }?.let { days ->
                    Text(
                        if (days > 90) "That is more than three months of your usual everyday spending."
                        else "That is about $days ${if (days == 1L) "day" else "days"} of your usual everyday spending.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (result.overSafe) {
                    Text(
                        if (state.safeMinor <= 0) "Safe to spend is already used up, so all of it would come out of money planned for bills, envelopes, or savings."
                        else "It is more than your Safe to spend right now (${MoneyFormat.format(state.safeMinor, currency)}), so the rest would come out of money planned for something else.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.caution,
                    )
                }
            }
        }
        if (drafts.isNotEmpty()) {
            Row {
                if (drafts.size < 4) TextButton(onClick = { onAdd(today) }) { Text("Another purchase") }
                Box(Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
    datingId?.let { id ->
        val draft = drafts.firstOrNull { it.id == id }
        if (draft == null) {
            datingId = null
        } else {
            DatePickerDialogOnly(draft.date, onDismiss = { datingId = null }) { date ->
                onDate(id, date.coerceIn(minOf(state.rangeFrom, today), maxOf(state.days.last().date, today)))
                datingId = null
            }
        }
    }
}

@Composable
private fun WhatIfLine(label: String, value: String, on: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(on, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PastRow(day: PastDay, state: CashFlowState, currency: String) {
    val colors = LocalLedgerColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(day.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (day.inMinor > 0) Text("+${MoneyFormat.format(day.inMinor, currency)} in", style = MaterialTheme.typography.bodySmall, color = colors.positive)
                if (day.billsMinor > 0) Text("${MoneyFormat.format(day.billsMinor, currency)} bills", style = MaterialTheme.typography.bodySmall, color = colors.negative)
                if (day.spentMinor > 0) {
                    Text(
                        "${MoneyFormat.format(day.spentMinor, currency)} spent" + if (day.date in state.bigSpends) " · big day" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (day.date in state.bigSpends) colors.safe else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            MoneyFormat.format(day.endMinor, currency),
            style = MaterialTheme.typography.titleSmall,
            color = if (day.endMinor < 0) colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimelineDay(day: FlowDay, state: CashFlowState, currency: String) {
    val colors = LocalLedgerColors.current
    val endTint = when {
        day.endMinor < 0 -> colors.negative
        day.endMinor < state.bufferMinor -> colors.caution
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                day.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) +
                    if (day.date in state.paydays) " · Payday" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("Then ${MoneyFormat.format(day.endMinor, currency)}", style = MaterialTheme.typography.labelLarge, color = endTint)
        }
        GroupCard {
            day.items.forEach { item ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            listOfNotNull(
                                when {
                                    item.transfer -> "Transfer"
                                    item.signedMinor > 0 -> "Income"
                                    else -> "Bill"
                                },
                                item.accountName.takeIf { it.isNotBlank() },
                                if (!item.scheduled) "already recorded" else null,
                                if (item.overdue) "overdue" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        MoneyFormat.signedLabel(item.signedMinor, currency),
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            item.overdue -> colors.overdue
                            item.transfer -> colors.transfer
                            item.signedMinor > 0 -> colors.positive
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EveryDayRow(day: DayBalance, state: CashFlowState, currency: String) {
    val colors = LocalLedgerColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            day.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (day.incomingMinor > 0) Text("+${MoneyFormat.format(day.incomingMinor, currency)}  ", style = MaterialTheme.typography.bodySmall, color = colors.positive)
        if (day.outgoingMinor > 0) Text("−${MoneyFormat.format(day.outgoingMinor, currency)}  ", style = MaterialTheme.typography.bodySmall, color = colors.negative)
        Text(
            MoneyFormat.format(day.endMinor, currency),
            style = MaterialTheme.typography.titleSmall,
            color = when {
                day.endMinor < 0 -> colors.negative
                day.endMinor < state.bufferMinor -> colors.caution
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * Everything on one graph over the dates you chose: your cash (what happened, then what is coming from bills and
 * income), what you planned to spend by each day, what you actually paid, and any purchases you are trying. Drag
 * along it to read a day. The dates are under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnifiedChart(
    state: CashFlowState,
    summary: FlowSummary,
    whatIfOpen: Boolean,
    showWhatIf: Boolean,
    onShowWhatIf: (Boolean) -> Unit,
    onWhatIf: () -> Unit,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onReset: () -> Unit,
    onAccounts: () -> Unit,
) {
    val colors = LocalLedgerColors.current
    val currency = state.currency
    val past = state.past.filter { !it.date.isBefore(state.rangeFrom) }
    val ahead = state.days.filter { !it.date.isAfter(state.rangeTo) }.ifEmpty { state.days.take(1) }
    val dates = past.map { it.date } + ahead.map { it.date }
    val cash = past.map { it.endMinor } + ahead.map { it.endMinor }
    val pastScenario = state.pastScenario?.takeLast(past.size)
    val accountColor = com.ledgerline.app.ui.home.accountColors(state.accounts)
    val balances = state.accountLines.map { line -> line to dates.map { line.byDate[it] } }
    val trackBy = state.track?.points.orEmpty().associateBy { it.date }
    val planned = dates.map { trackBy[it]?.plannedMinor }
    val paid = dates.map { trackBy[it]?.paidMinor }
    // What if: your planned spending with the purchases you are trying added on their days.
    val whatIf: List<Long?>? = if (state.buys.isEmpty() || !showWhatIf) null else dates.mapIndexed { i, d ->
        planned[i]?.let { it + state.buys.filter { b -> !b.date.isAfter(d) }.sumOf { b -> b.amountMinor } }
    }
    val todayIndex = past.size
    var picked by remember(dates.size, state.today) { mutableIntStateOf(todayIndex.coerceAtMost(dates.size - 1)) }
    if (picked >= dates.size) picked = (dates.size - 1).coerceAtLeast(0)
    if (dates.isEmpty()) return
    val pastColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cashColor = MaterialTheme.colorScheme.primary
    val plannedColor = Color(0xFF6F8FCF)
    // Dark slate, so paid spending never looks like an account's line.
    val paidColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val whatIfColor = colors.debt
    val grid = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.onSurface
    val bufferColor = colors.caution
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val date = dates[picked]
    Column(
        Modifier.fillMaxWidth().depth(RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The day you are reading.
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) + when {
                        date == state.today -> " · Today"
                        date.isBefore(state.today) -> " · Happened"
                        else -> " · Ahead"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Cash ${MoneyFormat.format(cash[picked], currency)}", style = MaterialTheme.typography.titleMedium, color = if (cash[picked] < 0) colors.negative else MaterialTheme.colorScheme.onSurface)
                whatIf?.getOrNull(picked)?.let {
                    Text("Planned with what if ${MoneyFormat.format(it, currency)}", style = MaterialTheme.typography.labelLarge, color = whatIfColor)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                planned[picked]?.let { Text("Planned ${MoneyFormat.format(it, currency)}", style = MaterialTheme.typography.labelLarge, color = plannedColor) }
                paid[picked]?.let { Text("Paid ${MoneyFormat.format(it, currency)}", style = MaterialTheme.typography.labelLarge, color = paidColor) }
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .semantics {
                    contentDescription = "Cash and spending from ${dates.first().medium()} to ${dates.last().medium()}. " +
                        "Selected ${date.medium()}: cash ${MoneyFormat.format(cash[picked], currency)}."
                }
                .pointerInput(dates.size) { detectTapGestures { o -> picked = indexAt(o.x - 44.dp.toPx(), size.width - 44.dp.toPx(), dates.size) } }
                .pointerInput(dates.size) { detectHorizontalDragGestures { change, _ -> picked = indexAt(change.position.x - 44.dp.toPx(), size.width - 44.dp.toPx(), dates.size) } },
        ) {
            val left = 44.dp.toPx()
            val bottomPad = 18.dp.toPx()
            val w = size.width - left
            val h = size.height - bottomPad
            val all = whatIf.orEmpty().filterNotNull() + planned.filterNotNull() + paid.filterNotNull() + balances.flatMap { it.second.filterNotNull() } + listOf(0L)
            val top = all.max().toFloat()
            val bottom = all.min().toFloat()
            val span = (top - bottom).takeIf { it > 0f } ?: 1f
            val steps = (dates.size - 1).coerceAtLeast(1)
            fun x(i: Int) = left + w * i / steps
            fun y(v: Long) = h * 0.04f + h * 0.92f * (top - v) / span
            // Value labels and light grid lines.
            listOf(top, (top + bottom) / 2, bottom).forEach { v ->
                val yy = y(v.toLong())
                drawLine(grid, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1.5f)
                val label = measurer.measure(BillMonth.compact(v.toLong(), currency), axisStyle)
                drawText(label, topLeft = Offset(0f, (yy - label.size.height / 2f).coerceIn(0f, h - label.size.height)))
            }
            if (bottom < 0f) drawLine(grid.copy(alpha = 1f), Offset(left, y(0)), Offset(size.width, y(0)), strokeWidth = 2.5f)
            fun series(values: List<Long?>, color: Color, width: Float, dashed: Boolean = false, from: Int = 0, until: Int = values.size - 1) {
                for (i in from until until) {
                    val a = values.getOrNull(i) ?: continue
                    val b = values.getOrNull(i + 1) ?: continue
                    drawLine(color, Offset(x(i), y(a)), Offset(x(i + 1), y(b)), strokeWidth = width, cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(16f, 10f)) else null)
                }
            }
            // Account balances underneath, then spending on top.
            balances.forEach { (line, values) -> series(values, (accountColor[line.id] ?: cashColor).copy(alpha = 0.9f), 5f) }
            whatIf?.let { series(it, whatIfColor, 7f, dashed = true) }
            series(planned, plannedColor, 7f, dashed = true)
            series(paid, paidColor, 8f)
            // Today, and the day being read.
            if (todayIndex in dates.indices) {
                drawLine(marker.copy(alpha = 0.25f), Offset(x(todayIndex), 0f), Offset(x(todayIndex), h), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            drawLine(marker.copy(alpha = 0.4f), Offset(x(picked), 0f), Offset(x(picked), h), strokeWidth = 2f)
            balances.forEach { (line, values) -> values[picked]?.let { drawCircle(accountColor[line.id] ?: cashColor, 7f, Offset(x(picked), y(it))) } }
            planned[picked]?.let { drawCircle(plannedColor, 8f, Offset(x(picked), y(it))) }
            paid[picked]?.let { drawCircle(paidColor, 8f, Offset(x(picked), y(it))) }
            // Dates along the bottom: first, today, last.
            listOfNotNull(0, todayIndex.takeIf { it in 1 until dates.size - 1 }, dates.size - 1).distinct().forEach { i ->
                val text = if (i == todayIndex) "Today" else dates[i].format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                val label = measurer.measure(text, axisStyle)
                drawText(label, topLeft = Offset((x(i) - label.size.width / 2f).coerceIn(left, size.width - label.size.width), size.height - label.size.height))
            }
        }
        // Each account's balance on the day being read, in its line's colour.
        if (balances.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                balances.forEach { (line, values) ->
                    values[picked]?.let { v ->
                        Text("${line.name} ${MoneyFormat.format(v, currency)}", style = MaterialTheme.typography.labelLarge, color = accountColor[line.id] ?: cashColor)
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            balances.forEach { (line, _) -> LineKey(accountColor[line.id] ?: cashColor, line.name) }
            LineKey(plannedColor, "Planned spending", dashed = true)
            LineKey(paidColor, "Paid spending")
            if (whatIf != null) LineKey(whatIfColor, "What if", dashed = true)
        }
        state.track?.let { track ->
            // What the planned line is made of, so it is clear why it grows: spending items are spread over the days.
            Text(
                "Planned by today: bills ${MoneyFormat.format(track.plannedBillsToDateMinor, currency)} + spending items ${MoneyFormat.format(track.plannedBudgetsToDateMinor, currency)} (spread over the cycle)" +
                    if (track.plannedSpendsToDateMinor > 0) " + planned spends ${MoneyFormat.format(track.plannedSpendsToDateMinor, currency)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when {
                    track.aheadMinor > 0 -> "Paid ${MoneyFormat.format(track.aheadMinor, currency)} more than planned by today"
                    track.aheadMinor < 0 -> "${MoneyFormat.format(-track.aheadMinor, currency)} under plan by today"
                    else -> "Right on plan"
                } + if (track.unpaidPlannedMinor > 0) " · ${MoneyFormat.format(track.unpaidPlannedMinor, currency)} planned, not paid yet" else "",
                style = MaterialTheme.typography.labelLarge,
                color = if (track.aheadMinor > 0) colors.negative else colors.positive,
            )
        }
        // The dates shown: this pay cycle unless you pick others.
        // Which accounts have lines: the same choice as the accounts button below.
        AssistChip(
            onClick = onAccounts,
            label = { Text(if (state.selected.size == state.accounts.size) "All ${state.accounts.size} accounts" else "${state.selected.size} of ${state.accounts.size} accounts") },
            leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateButton("From", state.rangeFrom, onFrom, Modifier.weight(1f))
            DateButton("To", state.rangeTo, onTo, Modifier.weight(1f))
        }
        if (state.customRange != null) {
            TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(32.dp)) { Text("Back to this cycle") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = onWhatIf, modifier = Modifier.weight(1f).height(44.dp)) {
                Text(if (whatIfOpen) "Hide what if" else "What if… try spends on any day", style = MaterialTheme.typography.labelLarge)
            }
            // Shows or hides the what-if line without closing what you typed.
            androidx.compose.material3.Switch(checked = showWhatIf, onCheckedChange = onShowWhatIf)
        }
    }
}

/** A date you can tap to change, with a small label above it; two of these sit side by side, the same size. */
@Composable
private fun DateButton(label: String, date: LocalDate, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(" " + date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleSmall)
        }
    }
}
