package com.ledgerline.app.ui.calendar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

import com.ledgerline.app.ui.format.weekday

import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringDraft
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SeriesScope
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.DebtRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import com.ledgerline.app.ui.components.AmountField
import com.ledgerline.app.ui.components.DateField
import com.ledgerline.app.ui.components.EmptyPane
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.format.paymentLabel
import com.ledgerline.app.ui.format.signWord
import com.ledgerline.app.ui.navigation.RecurringEditorRoute
import com.ledgerline.app.ui.theme.LocalLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

internal enum class EndMode { NEVER, ON_DATE, AFTER_COUNT }

internal data class CalendarLedger(
    val items: List<RecurringPayment>,
    val adjustments: List<OccurrenceAdjustment>,
    val accounts: List<Account>,
    val settled: Set<Pair<String, LocalDate>>,
    val transactions: List<LedgerTransaction>,
)

internal data class CalendarState(
    val month: YearMonth,
    val selected: LocalDate,
    val today: LocalDate,
    val currency: String,
    val summary: com.ledgerline.app.finance.calendar.BillMonthSummary,
    /** Grid dates and day cells for the shown month and the months either side. */
    val grids: Map<YearMonth, Pair<List<LocalDate>, Map<LocalDate, com.ledgerline.app.finance.calendar.BillDay>>>,
    val cycle: com.ledgerline.app.finance.period.PayPeriod,
    val dayEntries: List<com.ledgerline.app.finance.calendar.BillEntry>,
    val daySpending: List<LedgerTransaction>,
    /** Spends planned for the chosen day and not paid yet. */
    val dayPlanned: List<LedgerTransaction> = emptyList(),
    /** Spends planned in the shown month and not paid yet, for "Still to pay". */
    val monthPlanned: List<LedgerTransaction> = emptyList(),
    val agenda: List<Pair<LocalDate, List<com.ledgerline.app.finance.calendar.BillEntry>>>,
    val accounts: List<Account>,
    val empty: Boolean,
    /** Expected cash at the end of each coming day: bills, income, and usual everyday spending. */
    val forecast: Map<LocalDate, Long> = emptyMap(),
    val bufferMinor: Long = 0,
    /** Average everyday spending a day this cycle, as on Home. */
    val dailySpendMinor: Long = 0,
    val safeNowMinor: Long = 0,
    val cycleEnd: LocalDate = today,
    /** Each account's start, ins, outs, and end on the chosen day. */
    val dayAccounts: List<com.ledgerline.app.finance.calendar.DayAccountRow> = emptyList(),
    /** Savings goals by day, over the shown month and the months either side. */
    val goalDays: Map<LocalDate, List<com.ledgerline.app.finance.calendar.GoalDay>> = emptyMap(),
) {
    val dayGoals: List<com.ledgerline.app.finance.calendar.GoalDay> get() = goalDays[selected].orEmpty()

    /** Left to spend at the end of [date] at the current pace, for days in the current pay cycle. */
    fun leftOn(date: LocalDate): Long? =
        if (date.isAfter(today) && !date.isAfter(cycleEnd)) {
            com.ledgerline.app.finance.forecast.SpendingPace.leftOn(safeNowMinor, dailySpendMinor, today, date)
        } else {
            null
        }
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    observeDashboard: com.ledgerline.app.domain.usecase.ObserveDashboard,
    private val recurring: RecurringRepository,
    accounts: AccountRepository,
    private val transactionRepo: TransactionRepository,
    debts: DebtRepository,
    settingsRepository: com.ledgerline.app.domain.repository.SettingsRepository,
    private val clock: AppClock,
    goals: com.ledgerline.app.domain.repository.GoalRepository,
    cyclePlans: com.ledgerline.app.domain.repository.CyclePlanRepository,
) : ViewModel() {
    private val monthFlow = kotlinx.coroutines.flow.MutableStateFlow(YearMonth.from(clock.today()))
    private val selectedFlow = kotlinx.coroutines.flow.MutableStateFlow(clock.today())
    val month: YearMonth get() = monthFlow.value
    val selected: LocalDate get() = selectedFlow.value
    var cycleMode by mutableStateOf(false)
        private set
    var highlightCycle by mutableStateOf(true)
        private set

    /** Colours each day by how much went out (red) or came in (green). */
    var heatMap by mutableStateOf(false)
        private set

    fun showHeat(value: Boolean) { heatMap = value }
    var message by mutableStateOf<String?>(null)
        private set
    val debts = debts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val debtPlans = debts.observePlans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val debtSettlements = debts.observeSettlements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = settingsRepository.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.ledgerline.app.domain.model.UserSettings("USD", 0, 30, false),
    )
    internal val ledger = combine(
        recurring.observe(),
        recurring.observeAdjustments(),
        accounts.observe(),
        transactionRepo.observeAll(),
    ) { items, adjustments, accountRows, transactionRows ->
        val settled = transactionRows.mapNotNull { transaction ->
            val recurringId = transaction.recurringPaymentId ?: return@mapNotNull null
            if (!BalanceMath.isActive(transaction)) return@mapNotNull null
            recurringId to transaction.occurredOn
        }.toSet()
        CalendarLedger(items, adjustments, accountRows, settled, transactionRows)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarLedger(emptyList(), emptyList(), emptyList(), emptySet(), emptyList()),
    )

    /**
     * Everything the calendar shows, worked out on a background thread. The months either side are included so a
     * swipe to the next or previous month has its amounts ready while the grid slides.
     */
    internal val state = combine(
        combine(monthFlow, selectedFlow) { m, d -> m to d },
        ledger,
        combine(this.debts, debtPlans, debtSettlements) { a, b, c -> Triple(a, b, c) },
        combine(settings, observeDashboard.observe()) { s, d -> s to d },
        combine(goals.observeContributions(), cyclePlans.observeAmounts()) { c, a -> c to a },
    ) { (shownMonth, day), book, (debtRows, plans, settlements), (userSettings, dashboard), (contributions, cycleAmounts) ->
        val today = clock.today()
        val linked = debtRows.mapNotNull { it.recurringPaymentId }.toSet()
        fun entriesFor(m: YearMonth): List<com.ledgerline.app.finance.calendar.BillEntry> {
            val dates = com.ledgerline.app.finance.calendar.CalendarGrid.dates(m)
            val events = com.ledgerline.app.finance.schedule.OccurrenceMaterializer.project(
                book.items, book.adjustments, book.settled, dates.first(), dates.last(), today,
            )
            val debtItems = dates.map { YearMonth.from(it) }.distinct().flatMap { gridMonth ->
                com.ledgerline.app.finance.calendar.DebtCalendar.items(gridMonth, debtRows, plans, settlements, today, respectVisibility = true)
            }
            return com.ledgerline.app.finance.calendar.BillMonth.entries(events, debtItems, linked)
        }
        val months = listOf(shownMonth.minusMonths(1), shownMonth, shownMonth.plusMonths(1))
        val byMonth = months.associateWith { m -> entriesFor(m) }
        val entries = byMonth.getValue(shownMonth)
        val settlementLinks = settlements.mapNotNull { it.linkId }.toSet()
        CalendarState(
            month = shownMonth,
            selected = day,
            today = today,
            currency = userSettings.primaryCurrency,
            summary = com.ledgerline.app.finance.calendar.BillMonth.summary(entries, shownMonth),
            grids = months.associateWith { m ->
                val dates = com.ledgerline.app.finance.calendar.CalendarGrid.dates(m)
                dates to com.ledgerline.app.finance.calendar.BillMonth.days(byMonth.getValue(m), book.transactions, dates)
            },
            cycle = com.ledgerline.app.finance.period.CycleSchedule.containing(day, userSettings),
            dayEntries = entries.filter { it.date == day },
            daySpending = book.transactions.filter {
                BalanceMath.isActive(it) && it.occurredOn == day && it.recurringPaymentId == null && it.linkId !in settlementLinks
            },
            dayPlanned = book.transactions.filter { BalanceMath.isPlannedUnpaid(it) && it.occurredOn == day },
            monthPlanned = book.transactions.filter { BalanceMath.isPlannedUnpaid(it) && YearMonth.from(it.occurredOn) == shownMonth }.sortedBy { it.occurredOn },
            agenda = entries.filter { YearMonth.from(it.date) == shownMonth }.groupBy { it.date }.toList(),
            accounts = book.accounts,
            empty = book.items.isEmpty() && debtRows.isEmpty(),
            forecast = dashboard.forecast.associate { it.date to it.balanceAfterMinor },
            bufferMinor = userSettings.minimumBufferMinor,
            dailySpendMinor = run {
                // The same pace Home uses, so the calendar and Home never disagree.
                val liquid = dashboard.accounts.filter { it.account.includeInSafeToSpend }.map { it.account.id }.toSet()
                val cycleNow = com.ledgerline.app.finance.period.PayPeriod(dashboard.cycleStart, dashboard.cycleEnd)
                val spent = com.ledgerline.app.finance.forecast.SpendingPace.everyday(
                    book.transactions.filter { it.accountId in liquid && cycleNow.contains(it.occurredOn) && !it.occurredOn.isAfter(today) },
                )
                com.ledgerline.app.finance.forecast.SpendingPace.forecast(spent, dashboard.safeToSpend.amountMinor, today, cycleNow).dailyAverageMinor
            },
            safeNowMinor = dashboard.safeToSpend.amountMinor,
            cycleEnd = dashboard.cycleEnd,
            dayAccounts = com.ledgerline.app.finance.calendar.DayAccounts.of(
                day,
                today,
                book.accounts,
                book.transactions,
                if (!day.isBefore(today)) com.ledgerline.app.finance.schedule.OccurrenceMaterializer.project(book.items, book.adjustments, book.settled, today, day, today) else emptyList(),
            ),
            goalDays = run {
                val own = cycleAmounts.associate { (it.cycleStart to it.itemKey) to it.amountMinor }
                com.ledgerline.app.finance.calendar.GoalCalendar.days(
                    dashboard.goals,
                    contributions,
                    userSettings,
                    com.ledgerline.app.finance.calendar.CalendarGrid.dates(months.first()).first(),
                    com.ledgerline.app.finance.calendar.CalendarGrid.dates(months.last()).last(),
                ) { start, goalId -> own[start to com.ledgerline.app.finance.cycle.CycleAmounts.goalKey(goalId)] }
            },
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previous() = shiftMonth(-1)
    fun next() = shiftMonth(1)
    fun goToToday() {
        val cursor = com.ledgerline.app.finance.calendar.CalendarToday.go(clock.today())
        monthFlow.value = cursor.month
        selectedFlow.value = cursor.selected
    }
    fun select(date: LocalDate) {
        selectedFlow.value = date
        monthFlow.value = YearMonth.from(date)
    }
    fun showDay() { cycleMode = false }
    fun showCycle() { cycleMode = true }
    fun setHighlight(value: Boolean) { highlightCycle = value }
    fun today() = clock.today()

    /** Shows [target] after a swipe, keeping the chosen day. */
    fun showMonth(target: YearMonth) {
        if (monthFlow.value != target) monthFlow.value = target
    }

    private fun shiftMonth(delta: Long) {
        monthFlow.value = monthFlow.value.plusMonths(delta)
    }

    /** Marks a planned spend paid today. */
    fun markSpendPaid(id: String) {
        viewModelScope.launch { transactionRepo.markSpendPaid(id, clock.today()) }
    }

    fun markPaid(id: String, date: LocalDate) {
        viewModelScope.launch {
            message = (recurring.markPaid(id, date) as? SaveResult.Invalid)?.message
        }
    }

    fun skip(id: String, date: LocalDate) {
        viewModelScope.launch {
            message = (recurring.skip(id, date) as? SaveResult.Invalid)?.message
        }
    }
}

@HiltViewModel
class RecurringEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recurring: RecurringRepository,
    private val autoPay: com.ledgerline.app.finance.schedule.AutoPay,
    private val clockForAutopay: AppClock,
    accounts: AccountRepository,
    categories: CategoryRepository,
    clock: AppClock,
    private val homeGroups: com.ledgerline.app.domain.repository.HomeGroupRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<RecurringEditorRoute>()

    /** Your Home groups, so a bill lands in the one you pick ("Monthly expenses", "Subscriptions", ...). */
    val groups = homeGroups.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val placements = homeGroups.observeItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The group picked here; null keeps where it already is (or the usual place for a new bill). */
    var groupId by mutableStateOf<String?>(null)
    val isExisting = args.recurringId.isNotBlank()
    val editingId: String = args.recurringId
    val occurrenceOn: LocalDate? = args.occurrenceOn.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    val accounts = accounts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = categories.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var name by mutableStateOf("")
    var amountMinor by mutableStateOf(0L)
    var amountText by mutableStateOf("")
        private set
    var accountId by mutableStateOf("")
    var categoryId by mutableStateOf<String?>(null)
    var direction by mutableStateOf(if (args.income) CashFlowDirection.INFLOW else CashFlowDirection.OUTFLOW)
    var kind by mutableStateOf(if (args.income) ScheduledKind.INCOME else ScheduledKind.BILL)
    var repeats by mutableStateOf(true)
    var frequency by mutableStateOf(RecurrenceFrequency.MONTHLY)
    var intervalDays by mutableStateOf("14")
    var start by mutableStateOf(
        args.startOn.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: clock.today(),
    )
    internal var endMode by mutableStateOf(EndMode.NEVER)
    var endOn by mutableStateOf(clock.today().plusYears(1))
    var occurrenceCount by mutableStateOf("12")
    var scope by mutableStateOf(SeriesScope.ENTIRE_SERIES)
    var priority by mutableStateOf(PaymentPriority.MANDATORY)
    var confidence by mutableStateOf(IncomeConfidence.HIGH)
    var active by mutableStateOf(true)
    var note by mutableStateOf("")

    /** Paid automatically on each due date, so you never have to tap Paid for it. New subscriptions start on. */
    var autopay by mutableStateOf(false)
    private var autopaySince: LocalDate? = null
    private var autopayTouched = false

    fun setAutopayOn(value: Boolean) {
        autopayTouched = true
        autopay = value
    }
    var error by mutableStateOf<String?>(null)
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()

    init {
        if (args.recurringId.isNotBlank()) {
            viewModelScope.launch {
                val item = recurring.get(args.recurringId) ?: return@launch
                name = item.name
                amountMinor = item.amountMinor
                amountText = com.ledgerline.app.finance.money.AmountInput.fromMinor(
                    item.amountMinor,
                    com.ledgerline.app.finance.money.MoneyFormat.fractionDigits(item.currency),
                )
                accountId = item.accountId
                categoryId = item.categoryId
                direction = item.direction
                kind = item.kind
                repeats = item.frequency != RecurrenceFrequency.ONCE
                frequency = if (item.frequency == RecurrenceFrequency.ONCE) RecurrenceFrequency.MONTHLY else item.frequency
                intervalDays = item.intervalDays?.toString() ?: "14"
                start = item.startOn
                endOn = item.endOn ?: item.startOn.plusYears(1)
                occurrenceCount = item.maxOccurrences?.takeIf { item.frequency != RecurrenceFrequency.ONCE }?.toString() ?: "12"
                endMode = when {
                    item.frequency == RecurrenceFrequency.ONCE -> EndMode.NEVER
                    item.maxOccurrences != null -> EndMode.AFTER_COUNT
                    item.endOn != null -> EndMode.ON_DATE
                    else -> EndMode.NEVER
                }
                priority = item.priority
                confidence = item.incomeConfidence
                active = item.active
                note = item.note
                autopaySince = item.autopaySince
                autopay = item.autopaySince != null
                autopayTouched = true
            }
        }
    }

    init {
        // New bills start on your main spending account, so most need only a name, amount, and date.
        if (!isExisting) {
            val preset = com.ledgerline.app.finance.calendar.BillPresets.let { presets ->
                (presets.bills + presets.subscriptions + presets.income + presets.loans).firstOrNull { it.name == args.preset }
            }
            if (preset != null) {
                viewModelScope.launch { applyPreset(preset, this@RecurringEditorViewModel.categories.first { it.isNotEmpty() }) }
            }
            viewModelScope.launch {
                val live = this@RecurringEditorViewModel.accounts.first { it.isNotEmpty() }.filter { it.active }
                if (accountId.isBlank()) accountId = (live.firstOrNull { it.includeInSafeToSpend } ?: live.firstOrNull())?.id.orEmpty()
            }
        }
    }

    fun onAmountText(raw: String, currency: String) {
        val digits = com.ledgerline.app.finance.money.MoneyFormat.fractionDigits(currency)
        amountText = com.ledgerline.app.finance.money.AmountInput.clean(raw, digits)
        amountMinor = com.ledgerline.app.finance.money.AmountInput.toMinor(amountText, digits)
    }

    /** Bill, subscription, income, loan payment, or transfer: sets direction and kind together. */
    fun chooseKind(value: ScheduledKind) {
        kind = value
        // Subscriptions and loan payments are almost always charged automatically.
        if (!autopayTouched && !isExisting) autopay = value == ScheduledKind.SUBSCRIPTION || value == ScheduledKind.DEBT
        direction = if (value == ScheduledKind.INCOME) CashFlowDirection.INFLOW else CashFlowDirection.OUTFLOW
    }

    fun applyPreset(preset: com.ledgerline.app.finance.calendar.BillPresets.Preset, categoryRows: List<com.ledgerline.app.domain.model.Category>) {
        name = preset.name
        chooseKind(preset.kind)
        repeats = preset.frequency != RecurrenceFrequency.ONCE
        frequency = if (preset.frequency == RecurrenceFrequency.ONCE) RecurrenceFrequency.MONTHLY else preset.frequency
        com.ledgerline.app.finance.calendar.BillPresets.category(preset.categoryHint, categoryRows)?.let { categoryId = it.id }
    }

    /** Whether this item is set aside from Safe to spend (bills) or counted on (income). */
    val counted: Boolean
        get() = if (direction == CashFlowDirection.INFLOW) confidence != IncomeConfidence.LOW else priority in setOf(PaymentPriority.MANDATORY, PaymentPriority.IMPORTANT)

    fun setCounted(value: Boolean) {
        if (direction == CashFlowDirection.INFLOW) {
            confidence = if (value) IncomeConfidence.HIGH else IncomeConfidence.LOW
        } else {
            priority = if (value) PaymentPriority.MANDATORY else PaymentPriority.OPTIONAL
        }
    }

    fun save() {
        viewModelScope.launch {
            val before = if (isExisting) emptySet() else recurring.observe().first().map { it.id }.toSet()
            val repeating = repeats
            val chosenFrequency = if (repeating) frequency else RecurrenceFrequency.ONCE
            val splitStart = if (scope == SeriesScope.THIS_AND_FUTURE) occurrenceOn ?: start else start
            val result = recurring.save(
                RecurringDraft(
                    existingId = args.recurringId.ifBlank { null },
                    name = name,
                    accountId = accountId,
                    categoryId = categoryId,
                    direction = direction,
                    kind = kind,
                    amountMinor = amountMinor,
                    frequency = chosenFrequency,
                    intervalDays = intervalDays.toIntOrNull(),
                    dayOfMonth = splitStart.dayOfMonth,
                    dayOfWeek = splitStart.dayOfWeek.value,
                    startOn = splitStart,
                    priority = priority,
                    incomeConfidence = confidence,
                    active = active,
                    note = note,
                    endOn = if (repeating && endMode == EndMode.ON_DATE) endOn else null,
                    // Keep the original start when autopay was already on, so earlier dates are not re-processed.
                    autopaySince = if (autopay) autopaySince ?: clockForAutopay.today() else null,
                    maxOccurrences = if (repeating && endMode == EndMode.AFTER_COUNT) occurrenceCount.toIntOrNull() else null,
                ),
                scope = if (occurrenceOn == null) SeriesScope.ENTIRE_SERIES else scope,
                occurrenceOn = occurrenceOn,
            )
            when (result) {
                SaveResult.Saved -> {
                    // A bill due today on autopay is recorded straight away.
                    if (autopay) runCatching { autoPay.settleDue() }
                    groupId?.let { group ->
                        val id = args.recurringId.ifBlank { null } ?: recurring.observe().first().firstOrNull { it.id !in before }?.id
                        id?.let { homeGroups.place("bill:$it", group) }
                    }
                    _done.emit(Unit)
                }
                is SaveResult.Invalid -> error = result.message
                is SaveResult.PossibleDuplicate -> Unit
            }
        }
    }

    fun delete(scope: SeriesScope) {
        val id = args.recurringId
        if (id.isBlank()) return
        viewModelScope.launch {
            val result = if (scope == SeriesScope.THIS_OCCURRENCE || scope == SeriesScope.THIS_AND_FUTURE) {
                if (scope == SeriesScope.THIS_OCCURRENCE) {
                    recurring.skip(id, occurrenceOn ?: return@launch)
                } else {
                    recurring.delete(id, scope, occurrenceOn)
                    SaveResult.Saved
                }
            } else {
                recurring.delete(id, SeriesScope.ENTIRE_SERIES, null)
                SaveResult.Saved
            }
            when (result) {
                SaveResult.Saved -> _done.emit(Unit)
                is SaveResult.Invalid -> error = result.message
                is SaveResult.PossibleDuplicate -> Unit
            }
        }
    }
}
