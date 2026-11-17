package com.ledgerline.app.ui.cycle

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ledgerline.app.domain.model.AccountDraft
import com.ledgerline.app.domain.model.CycleReview
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.BudgetRepository
import com.ledgerline.app.domain.repository.CyclePlanRepository
import com.ledgerline.app.domain.repository.GoalRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.cycle.CycleBillRow
import com.ledgerline.app.finance.cycle.CycleItemRow
import com.ledgerline.app.finance.cycle.CycleSetup
import com.ledgerline.app.finance.cycle.CycleTotals
import com.ledgerline.app.finance.goals.GoalMath
import com.ledgerline.app.finance.money.AmountInput
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.period.PayPeriod
import com.ledgerline.app.ui.components.AmountBox
import com.ledgerline.app.ui.components.AmountKeypad
import com.ledgerline.app.ui.components.SubScreen
import com.ledgerline.app.ui.format.cycleRange
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.navigation.CycleSetupRoute
import com.ledgerline.app.ui.theme.LocalLedgerColors
import com.ledgerline.app.ui.theme.depth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** An account as the checklist shows it: its balance as you would read it on the statement (owed, for a card). */
data class CycleAccountLine(
    val id: String,
    val name: String,
    val lastFour: String?,
    val shownMinor: Long,
    val debt: Boolean,
    /** What it starts the cycle with, and what it would carry over from the cycle before (owed for a card). */
    val startShownMinor: Long = shownMinor,
    val carriedShownMinor: Long = shownMinor,
)

data class CycleSetupState(
    val loading: Boolean = true,
    val offset: Int = 0,
    val cycle: PayPeriod? = null,
    val currency: String = "USD",
    val accounts: List<CycleAccountLine> = emptyList(),
    val review: CycleReview? = null,
    val bills: List<CycleBillRow> = emptyList(),
    val leftovers: List<CycleBillRow> = emptyList(),
    val spending: List<CycleItemRow> = emptyList(),
    val goals: List<CycleItemRow> = emptyList(),
    val totals: CycleTotals = CycleTotals(0, 0, 0, 0),
    /** The cycle before has its own amounts to copy. */
    val canCopyPrevious: Boolean = false,
    /** This cycle starts with fresh balances instead of carrying on from the cycle before. */
    val fresh: Boolean = false,
)

/**
 * The start of a cycle, set up in one place: download and check each statement, confirm balances, then decide what
 * this cycle (or the next) starts with — each bill, spending item, and goal at its usual amount, its own amount for
 * this cycle only, or off. The usual amounts are never changed here.
 */
@HiltViewModel
class CycleSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val recurring: RecurringRepository,
    budgets: BudgetRepository,
    goals: GoalRepository,
    private val cyclePlans: CyclePlanRepository,
    private val clock: AppClock,
    preferences: com.ledgerline.app.preferences.UiPreferences,
) : ViewModel() {
    private val offset = MutableStateFlow(savedStateHandle.toRoute<CycleSetupRoute>().offset.coerceIn(-24, 12))

    val state = combine(
        combine(offset, settings.observe(), preferences.payIncomeMinor, ::Triple),
        combine(accounts.observe(), transactions.observeAll(), ::Pair),
        combine(recurring.observe(), recurring.observeAdjustments(), ::Pair),
        combine(budgets.observe(), goals.observe(), goals.observeContributions(), ::Triple),
        combine(cyclePlans.observeAmounts(), cyclePlans.observeReviews(), cyclePlans.observeFreshCycles(), ::Triple),
    ) { (shift, userSettings, payIncome), (accountRows, txs), (series, adjustments), (budgetRows, goalRows, contributions), (amounts, reviews, freshCycles) ->
        val today = clock.today()
        var cycle = CycleSchedule.containing(today, userSettings)
        repeat(kotlin.math.abs(shift)) {
            cycle = if (shift < 0) CycleSchedule.previous(cycle, userSettings) else CycleSchedule.next(cycle, userSettings)
        }
        val previous = CycleSchedule.containing(cycle.start.minusDays(1), userSettings)
        val active = txs.filter { BalanceMath.isActive(it) }
        val settled = active.mapNotNull { tx -> tx.recurringPaymentId?.let { it to tx.occurredOn } }.toSet()
        val live = accountRows.filter { it.active && !it.deleted }
        val bills = CycleSetup.bills(series, adjustments, settled, cycle, today)
        val spending = CycleSetup.spending(budgetRows, amounts, cycle, userSettings.primaryCurrency)
        val usualGoals = GoalMath.progressAll(goalRows, contributions, accountRows, txs, today, cycle)
        val goalRowsShown = CycleSetup.goals(usualGoals, amounts, cycle.start)
        CycleSetupState(
            loading = false,
            offset = shift,
            cycle = cycle,
            currency = userSettings.primaryCurrency,
            accounts = live.map { account ->
                val balance = BalanceMath.currentBalanceMinor(account, active)
                val debt = BalanceMath.isDebtType(account.type)
                CycleAccountLine(
                    account.id, account.name, account.lastFour, if (debt) -balance else balance, debt,
                    startShownMinor = com.ledgerline.app.finance.cycle.CycleBalances.shown(account, com.ledgerline.app.finance.cycle.CycleBalances.startMinor(account, txs, cycle.start)),
                    carriedShownMinor = com.ledgerline.app.finance.cycle.CycleBalances.shown(account, com.ledgerline.app.finance.cycle.CycleBalances.carriedMinor(account, txs, cycle.start)),
                )
            },
            fresh = cycle.start in freshCycles,
            review = reviews.firstOrNull { it.cycleStart == cycle.start } ?: CycleReview(cycle.start),
            bills = bills,
            leftovers = if (shift == 0) CycleSetup.leftovers(series, adjustments, settled, cycle, today) else emptyList(),
            spending = spending,
            goals = goalRowsShown,
            // Your pay figure from Home, when you set one, is what comes in; otherwise the income on the calendar.
            totals = CycleSetup.totals(bills, spending, goalRowsShown).let { t -> payIncome?.let { t.copy(inMinor = it) } ?: t },
            canCopyPrevious = amounts.any { it.cycleStart == previous.start },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleSetupState())

    private val current get() = state.value

    private suspend fun digits(): Int {
        val currency = settings.observe().first().primaryCurrency
        return runCatching { java.util.Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
    }

    fun shift(by: Int) {
        offset.value = (offset.value + by).coerceIn(-24, 12)
    }

    fun backToThisCycle() {
        offset.value = 0
    }

    /** Fresh balances for this cycle, or carry on from the cycle before. Earlier cycles are never changed. */
    fun setFresh(on: Boolean) {
        val cycle = current.cycle ?: return
        viewModelScope.launch { cyclePlans.setFresh(cycle.start, on) }
    }

    /** What an account starts this cycle with (owed, for a card or loan). */
    fun setStartBalance(accountId: String, text: String) {
        val cycle = current.cycle ?: return
        viewModelScope.launch {
            val account = accounts.get(accountId) ?: return@launch
            val entered = AmountInput.toMinor(text, digits())
            cyclePlans.setStartBalance(cycle.start, accountId, com.ledgerline.app.finance.cycle.CycleBalances.signed(account, entered))
        }
    }

    private fun saveReview(change: (CycleReview) -> CycleReview) {
        val cycle = current.cycle ?: return
        viewModelScope.launch { cyclePlans.saveReview(change(cyclePlans.review(cycle.start))) }
    }

    fun toggleStatement(accountId: String) = saveReview { review ->
        val ids = review.reviewedAccountIds
        review.copy(reviewedAccountIds = if (accountId in ids) ids - accountId else ids + accountId)
    }

    fun setBalancesChecked(checked: Boolean) = saveReview { it.copy(balancesChecked = checked) }

    fun finish(done: Boolean) = saveReview { it.copy(completedAtEpochMillis = if (done) clock.now().toEpochMilli() else null) }

    /** Sets an account to its real balance (owed, for a card or loan) with a correction dated today. */
    fun setBalance(accountId: String, text: String) {
        viewModelScope.launch {
            val account = accounts.get(accountId) ?: return@launch
            accounts.save(
                AccountDraft(
                    existingId = account.id,
                    name = account.name,
                    institution = account.institution,
                    type = account.type,
                    lastFour = account.lastFour.orEmpty(),
                    enteredBalanceMinor = AmountInput.toMinor(text, digits()),
                    applyBalance = true,
                    creditLimitMinor = account.creditLimitMinor,
                    active = account.active,
                    includeInNetWorth = account.includeInNetWorth,
                    includeInSafeToSpend = account.includeInSafeToSpend,
                ),
            )
        }
    }

    /** One date's own amount; null text goes back to the usual amount. */
    fun setBillAmount(row: CycleBillRow, text: String?) {
        viewModelScope.launch {
            val minor = text?.let { AmountInput.toMinor(it, digits()) }
            recurring.setOccurrence(row.recurringId, row.date, minor?.takeIf { it != row.usualMinor }, skipped = false)
        }
    }

    fun setBillOn(row: CycleBillRow, on: Boolean) {
        viewModelScope.launch {
            recurring.setOccurrence(row.recurringId, row.date, row.amountMinor.takeIf { it != row.usualMinor }, skipped = !on)
        }
    }

    fun skipLeftovers() {
        viewModelScope.launch {
            current.leftovers.forEach { recurring.setOccurrence(it.recurringId, it.date, null, skipped = true) }
        }
    }

    fun setItemAmount(row: CycleItemRow, text: String?) {
        val cycle = current.cycle ?: return
        viewModelScope.launch {
            val minor = text?.let { AmountInput.toMinor(it, digits()) }
            cyclePlans.setAmount(cycle.start, row.key, minor?.takeIf { it != row.usualMinor })
        }
    }

    fun setItemOn(row: CycleItemRow, on: Boolean) {
        val cycle = current.cycle ?: return
        viewModelScope.launch { cyclePlans.setAmount(cycle.start, row.key, if (on) null else 0L) }
    }

    /** Gives this cycle the same spending and goal amounts the cycle before had. */
    fun copyPrevious() {
        val cycle = current.cycle ?: return
        viewModelScope.launch {
            val userSettings = settings.observe().first()
            val previous = CycleSchedule.containing(cycle.start.minusDays(1), userSettings)
            val keys = (current.spending + current.goals).map { it.key }
            CycleSetup.copyAmounts(cyclePlans.observeAmounts().first(), previous.start, cycle.start, keys)
                .forEach { (key, minor) -> cyclePlans.setAmount(cycle.start, key, minor) }
        }
    }

    fun amountText(minor: Long): String = AmountInput.fromMinor(minor, 2)
}

/** What is being edited in the amount sheet. */
private sealed interface Editing {
    data class Balance(val line: CycleAccountLine) : Editing
    data class Start(val line: CycleAccountLine) : Editing
    data class Bill(val row: CycleBillRow) : Editing
    data class Item(val row: CycleItemRow) : Editing
}

@Composable
fun CycleSetupScreen(onBack: () -> Unit, viewModel: CycleSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    SubScreen("New cycle", onBack) { padding ->
        val cycle = state.cycle
        if (cycle == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@SubScreen
        }
        val review = state.review ?: CycleReview(cycle.start)
        val money = { minor: Long -> MoneyFormat.format(minor, state.currency) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "head") {
                Header(state, money, onShift = viewModel::shift, onToday = viewModel::backToThisCycle)
            }
            item(key = "statements") {
                val reviewed = state.accounts.count { it.id in review.reviewedAccountIds }
                Step(1, "Statements", "$reviewed of ${state.accounts.size}", done = state.accounts.isNotEmpty() && reviewed == state.accounts.size) {
                    Hint("Download each account's statement and review every line against the app.")
                    state.accounts.forEach { line ->
                        CheckRow(
                            checked = line.id in review.reviewedAccountIds,
                            title = line.name,
                            subtitle = line.lastFour?.let { "····$it" },
                            onToggle = { viewModel.toggleStatement(line.id) },
                        )
                    }
                }
            }
            item(key = "balances") {
                Step(2, "Balances", if (state.fresh) "Fresh" else if (review.balancesChecked) "Match" else "Carried", done = state.fresh || review.balancesChecked) {
                    FreshToggle(state.fresh, onChange = viewModel::setFresh)
                    if (state.fresh) {
                        Hint("Tap an amount to set what the account starts this cycle with. Earlier cycles stay as they were.")
                        state.accounts.forEach { line ->
                            AmountRow(
                                title = line.name,
                                subtitle = (if (line.debt) "Owed · " else "") + "carried over ${money(line.carriedShownMinor)}",
                                amount = money(line.startShownMinor),
                                onClick = { editing = Editing.Start(line) },
                            )
                        }
                    } else if (state.offset == 0) {
                        Hint("Carried on from last cycle. Tap an amount to set today's balance to the bank's figure; a correction is recorded and history stays.")
                        state.accounts.forEach { line ->
                            AmountRow(
                                title = line.name,
                                subtitle = if (line.debt) "Owed" else null,
                                amount = money(line.shownMinor),
                                onClick = { editing = Editing.Balance(line) },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        PillToggle(
                            on = review.balancesChecked,
                            onLabel = "Balances match",
                            offLabel = "All balances match the bank",
                            onClick = { viewModel.setBalancesChecked(!review.balancesChecked) },
                        )
                    } else {
                        Hint("Carried on from the cycle before. Turn on fresh balances to start this cycle with your own figures.")
                        state.accounts.forEach { line ->
                            AmountRow(line.name, if (line.debt) "Owed at the start" else "At the start", money(line.startShownMinor), muted = true, onClick = null)
                        }
                    }
                }
            }
            item(key = "bills") {
                val inRows = state.bills.filter { it.incoming }
                val outRows = state.bills.filter { !it.incoming }
                Step(3, "Coming in and bills", "${outRows.count { !it.skipped }} bills") {
                    Hint("Switch a date off to skip it this cycle, or tap its amount to change it for this cycle only.")
                    if (inRows.isNotEmpty()) {
                        Label("Coming in")
                        inRows.forEach { row -> BillLine(row, money, onToggle = { viewModel.setBillOn(row, it) }, onEdit = { editing = Editing.Bill(row) }) }
                    }
                    if (outRows.isNotEmpty()) {
                        Label("Going out")
                        outRows.forEach { row -> BillLine(row, money, onToggle = { viewModel.setBillOn(row, it) }, onEdit = { editing = Editing.Bill(row) }) }
                    }
                    if (state.bills.isEmpty()) Hint("Nothing scheduled in these dates.")
                    if (state.leftovers.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label("Unpaid from before · ${money(state.leftovers.sumOf { it.amountMinor })}", Modifier.weight(1f))
                            TextButton(onClick = viewModel::skipLeftovers) { Text("Skip all") }
                        }
                        state.leftovers.forEach { row ->
                            AmountRow(row.name, row.date.medium(), money(row.amountMinor), muted = true, onClick = null)
                        }
                    }
                }
            }
            item(key = "spending") {
                Step(4, "Spending", money(state.totals.spendingMinor)) {
                    if (state.spending.isEmpty()) Hint("No spending items yet. Add them on Home.")
                    state.spending.forEach { row -> ItemLine(row, money, onToggle = { viewModel.setItemOn(row, it) }, onEdit = { editing = Editing.Item(row) }) }
                }
            }
            item(key = "goals") {
                Step(5, "Savings goals", money(state.totals.goalsMinor)) {
                    if (state.goals.isEmpty()) Hint("No goals with a monthly plan.")
                    state.goals.forEach { row -> ItemLine(row, money, onToggle = { viewModel.setItemOn(row, it) }, onEdit = { editing = Editing.Item(row) }) }
                }
            }
            if (state.canCopyPrevious) {
                item(key = "copy") {
                    OutlinedButton(onClick = viewModel::copyPrevious, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("  Same amounts as last cycle")
                    }
                }
            }
            item(key = "finish") {
                if (review.completed) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = LocalLedgerColors.current.positive, modifier = Modifier.size(18.dp))
                        Text(
                            "  Set up" + (review.completedAtEpochMillis?.let { " · " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().medium() } ?: ""),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        TextButton(onClick = { viewModel.finish(false) }) { Text("Reopen") }
                    }
                } else {
                    Button(
                        onClick = { viewModel.finish(true); onBack() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(50),
                    ) { Text(if (state.offset == 0) "Start this cycle" else "Done with this cycle") }
                }
            }
        }
    }
    editing?.let { edit ->
        val (title, subtitle, start, usual) = when (edit) {
            is Editing.Balance -> Quad(edit.line.name, if (edit.line.debt) "Owed today, as on your statement" else "Balance today, as on your statement", edit.line.shownMinor, null)
            is Editing.Start -> Quad(edit.line.name, (if (edit.line.debt) "Owed" else "Balance") + " at the start of this cycle", edit.line.startShownMinor, null)
            is Editing.Bill -> Quad(edit.row.name, "${edit.row.date.medium()} · this cycle only", edit.row.amountMinor, edit.row.usualMinor)
            is Editing.Item -> Quad(edit.row.name, "This cycle only", edit.row.amountMinor, edit.row.usualMinor)
        }
        AmountSheet(
            title = title,
            subtitle = subtitle,
            start = viewModel.amountText(start),
            symbol = runCatching { java.util.Currency.getInstance(state.currency).symbol }.getOrDefault("$"),
            usualLabel = usual?.let { "Usual ${MoneyFormat.format(it, state.currency)}" },
            onUsual = {
                when (edit) {
                    is Editing.Bill -> viewModel.setBillAmount(edit.row, null)
                    is Editing.Item -> viewModel.setItemAmount(edit.row, null)
                    is Editing.Balance, is Editing.Start -> Unit
                }
                editing = null
            },
            onSave = { text ->
                when (edit) {
                    is Editing.Balance -> viewModel.setBalance(edit.line.id, text)
                    is Editing.Start -> viewModel.setStartBalance(edit.line.id, text)
                    is Editing.Bill -> viewModel.setBillAmount(edit.row, text)
                    is Editing.Item -> viewModel.setItemAmount(edit.row, text)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private data class Quad(val title: String, val subtitle: String, val start: Long, val usual: Long?)

@Composable
private fun Header(state: CycleSetupState, money: (Long) -> String, onShift: (Int) -> Unit, onToday: () -> Unit) {
    val cycle = state.cycle ?: return
    val colors = LocalLedgerColors.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Any cycle, back or ahead, the same as on Home.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = { onShift(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier cycle")
            }
            Text(
                when (state.offset) {
                    0 -> "This cycle"
                    -1 -> "Last cycle"
                    1 -> "Next cycle"
                    else -> if (state.offset < 0) "${-state.offset} cycles ago" else "In ${state.offset} cycles"
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (state.offset != 0) TextButton(onClick = onToday) { Text("Today") }
            androidx.compose.material3.IconButton(onClick = { onShift(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later cycle")
            }
        }
        Column(
            Modifier.fillMaxWidth().depth(RoundedCornerShape(22.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(cycle.start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(cycleRange(cycle.start, cycle.end), style = MaterialTheme.typography.headlineSmall)
            }
            val t = state.totals
            PlanBar(t)
            Row(Modifier.fillMaxWidth()) {
                Figure("In", money(t.inMinor), colors.positive, Modifier.weight(1f))
                Figure("Bills", money(t.billsMinor), billsTone, Modifier.weight(1f))
                Figure("Spending", money(t.spendingMinor), spendTone, Modifier.weight(1f))
                Figure("Goals", money(t.goalsMinor), goalTone, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val planned = t.billsMinor + t.spendingMinor + t.goalsMinor
                // With no income known there is nothing to be short of: show what is planned instead.
                val (label, value, tone) = when {
                    t.inMinor == 0L -> Triple("Planned out", planned, MaterialTheme.colorScheme.onSurface)
                    t.leftMinor >= 0 -> Triple("Left after plan", t.leftMinor, MaterialTheme.colorScheme.onSurface)
                    else -> Triple("Short", -t.leftMinor, colors.negative)
                }
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(money(value), style = MaterialTheme.typography.titleLarge, color = tone)
            }
        }
    }
}

private val billsTone = Color(0xFF6F8FCF)
private val spendTone = Color(0xFFCF8A66)
private val goalTone = Color(0xFF6FAE87)

/** Money coming in as one bar, filled by bills, spending, and goals in turn; what is left stays empty. */
@Composable
private fun PlanBar(t: CycleTotals) {
    val whole = maxOf(t.inMinor, t.billsMinor + t.spendingMinor + t.goalsMinor).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        listOf(t.billsMinor to billsTone, t.spendingMinor to spendTone, t.goalsMinor to goalTone).forEach { (minor, color) ->
            if (minor > 0) Box(Modifier.weight(minor.toFloat() / whole).height(10.dp).background(color))
        }
        val rest = whole - t.billsMinor - t.spendingMinor - t.goalsMinor
        if (rest > 0) Spacer(Modifier.weight(rest.toFloat() / whole))
    }
}

@Composable
private fun Figure(label: String, value: String, tone: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
            Text(" $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/** A numbered step: its number (a tick once done), title, a short status on the right, and its rows. */
@Composable
private fun Step(number: Int, title: String, status: String, done: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().depth(RoundedCornerShape(22.dp)).animateContentSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (done) LocalLedgerColors.current.positive else MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(15.dp))
                } else {
                    Text("$number", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("  $title", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 6.dp))
}

/** A round tick box: filled when on. */
@Composable
private fun Tick(checked: Boolean, onToggle: () -> Unit, label: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .semantics {
                role = Role.Checkbox
                contentDescription = label
                stateDescription = if (checked) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .then(if (checked) Modifier.background(colors.primary) else Modifier.border(1.5.dp, colors.outline, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, title: String, subtitle: String?, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tick(checked, onToggle, title)
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AmountRow(title: String, subtitle: String?, amount: String, muted: Boolean = false, onClick: (() -> Unit)?) {
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).heightIn(min = 44.dp).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(amount, style = MaterialTheme.typography.titleSmall, color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

/** A bill or pay date: on/off, name and date, and its amount (own amounts show the usual one, struck through). */
@Composable
private fun BillLine(row: CycleBillRow, money: (Long) -> String, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        if (row.paid) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = "Paid", tint = LocalLedgerColors.current.positive, modifier = Modifier.size(18.dp))
            }
        } else {
            Tick(!row.skipped, { onToggle(row.skipped) }, row.name)
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (row.skipped) colors.onSurfaceVariant else colors.onSurface,
                textDecoration = if (row.skipped) TextDecoration.LineThrough else null,
            )
            Text(
                row.date.medium() + when {
                    row.paid -> " · paid"
                    row.skipped -> " · skipped"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        AmountChip(money(row.amountMinor), row.own && !row.skipped, money(row.usualMinor), enabled = !row.paid && !row.skipped, onEdit)
    }
}

@Composable
private fun ItemLine(row: CycleItemRow, money: (Long) -> String, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Tick(!row.off, { onToggle(row.off) }, row.name)
        Text(
            row.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (row.off) colors.onSurfaceVariant else colors.onSurface,
            textDecoration = if (row.off) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        AmountChip(money(row.amountMinor), row.ownMinor != null && !row.off, money(row.usualMinor), enabled = !row.off, onEdit)
    }
}

/** The amount as a small tappable chip. An own amount is marked with a dot and the usual amount beside it. */
@Composable
private fun AmountChip(amount: String, own: Boolean, usual: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (own) {
            Text(usual, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            amount,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(if (own) Modifier.background(colors.secondaryContainer) else Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(50)))
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = "Change $amount" }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Fresh balances for this cycle, or carry on from the one before. */
@Composable
private fun FreshToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { onChange(!on) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Fresh balances for this cycle", style = MaterialTheme.typography.titleSmall)
            Text(
                if (on) "Starts from the amounts below. Bills, subscriptions, and items carry on as usual."
                else "Off: balances carry on from last cycle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun PillToggle(on: Boolean, onLabel: String, offLabel: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .then(if (on) Modifier.background(colors.secondaryContainer) else Modifier.border(1.dp, colors.outline, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = LocalLedgerColors.current.positive, modifier = Modifier.size(16.dp))
        Text(if (on) "  $onLabel" else offLabel, style = MaterialTheme.typography.labelLarge)
    }
}

/** Change one amount with the small keypad. "Usual" goes back to the item's normal amount. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSheet(
    title: String,
    subtitle: String,
    start: String,
    symbol: String,
    usualLabel: String?,
    onUsual: () -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(start.removeSuffix(".00")) }
    // The amount shown is selected, like a calculator: the first key replaces it, and delete clears it.
    var fresh by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmountBox(text, symbol, "Amount", open = true, onOpen = {}, modifier = Modifier.weight(1f))
                if (usualLabel != null) {
                    TextButton(onClick = onUsual) { Text(usualLabel) }
                }
            }
            AmountKeypad(
                text,
                onText = { typed ->
                    text = when {
                        !fresh -> AmountInput.clean(typed, 2)
                        typed.length > text.length -> AmountInput.clean(typed.substring(text.length), 2)
                        else -> ""
                    }
                    fresh = false
                },
                onDone = { onSave(text) },
            )
        }
    }
}
