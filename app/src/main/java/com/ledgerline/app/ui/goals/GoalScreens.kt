package com.ledgerline.app.ui.goals

import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.GoalContribution
import com.ledgerline.app.domain.model.GoalDraft
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.GoalRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.domain.usecase.ObserveDashboard
import com.ledgerline.app.finance.goals.GoalPresets
import com.ledgerline.app.finance.goals.GoalPreview
import com.ledgerline.app.finance.goals.GoalProgress
import com.ledgerline.app.finance.money.AmountInput
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.components.ConfirmDialog
import com.ledgerline.app.ui.components.GroupCard
import com.ledgerline.app.ui.components.ListRow
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.components.ProgressTrack
import com.ledgerline.app.ui.components.SectionHeader
import com.ledgerline.app.ui.components.StatusPill
import com.ledgerline.app.ui.components.SubScreen
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.format.withYear
import com.ledgerline.app.ui.navigation.GoalDetailRoute
import com.ledgerline.app.ui.navigation.GoalEditorRoute
import com.ledgerline.app.ui.theme.LocalLedgerColors
import com.ledgerline.app.ui.transactions.BigAmountField
import com.ledgerline.app.ui.transactions.DatePickerDialogOnly
import com.ledgerline.app.ui.transactions.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The status chip shared by goal cards on Plan, Home, and the goal screen. */
@Composable
fun GoalStatus(progress: GoalProgress) {
    val colors = LocalLedgerColors.current
    when {
        progress.reached -> StatusPill("Reached", colors.positive)
        progress.onTrack == true -> StatusPill("On track", colors.positive)
        progress.onTrack == false -> StatusPill("Behind", colors.caution)
        progress.planMonthlyMinor == null -> StatusPill("No plan yet", MaterialTheme.colorScheme.onSurfaceVariant)
        else -> StatusPill("${MoneyFormat.format(progress.planMonthlyMinor, progress.goal.currency)}/month", MaterialTheme.colorScheme.primary)
    }
}

/** A goal as a compact card row: name, bar, saved of target, and status. */
@Composable
fun GoalRow(progress: GoalProgress, onClick: () -> Unit) {
    val goal = progress.goal
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            GoalStatus(progress)
        }
        ProgressTrack(progress.fraction, color = if (progress.reached) LocalLedgerColors.current.positive else LocalLedgerColors.current.positive.copy(alpha = 0.8f))
        Text(
            "${MoneyFormat.format(progress.savedMinor, goal.currency)} of ${MoneyFormat.format(goal.targetMinor, goal.currency)}" +
                (goal.targetDate?.let { " · by ${it.withYear()}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** In plain words: what it takes, and whether the current plan gets there in time. */
fun planSentence(progress: GoalProgress): String {
    val goal = progress.goal
    val c = goal.currency
    return when {
        progress.reached -> "You've reached it. Spend it on what you saved for, or add to it."
        progress.planMonthlyMinor == null -> "Set a date or a monthly amount and you'll get a plan."
        goal.targetDate != null && progress.onTrack == false ->
            "At ${MoneyFormat.format(progress.planMonthlyMinor, c)} a month you'd get there ${progress.finishOn?.withYear()}, after ${goal.targetDate.withYear()}. " +
                "Save ${MoneyFormat.format(progress.neededPerMonthMinor ?: 0, c)} a month to make it on time."
        goal.targetDate != null ->
            "Save ${MoneyFormat.format(progress.planMonthlyMinor, c)} a month to reach it by ${goal.targetDate.withYear()}."
        else -> "At ${MoneyFormat.format(progress.planMonthlyMinor, c)} a month you'll reach it around ${progress.finishOn?.withYear()}."
    }
}

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observe: ObserveDashboard,
    private val goals: GoalRepository,
    accounts: AccountRepository,
    private val clock: AppClock,
    transactions: com.ledgerline.app.domain.repository.TransactionRepository,
    settings: com.ledgerline.app.domain.repository.SettingsRepository,
    cyclePlans: com.ledgerline.app.domain.repository.CyclePlanRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<GoalDetailRoute>()
    /** Worked out here rather than taken from Home, so a finished goal still opens with its history. */
    val progress = kotlinx.coroutines.flow.combine(
        goals.observe(),
        goals.observeContributions(),
        kotlinx.coroutines.flow.combine(accounts.observe(), transactions.observeAll(), ::Pair),
        settings.observe(),
        cyclePlans.observeAmounts(),
    ) { goalRows, contributions, (accountRows, txs), userSettings, amounts ->
        goalRows.firstOrNull { it.id == args.goalId }?.let { goal ->
            val today = clock.today()
            val cycle = com.ledgerline.app.finance.period.CycleSchedule.containing(today, userSettings)
            val own = amounts.firstOrNull { it.cycleStart == cycle.start && it.itemKey == com.ledgerline.app.finance.cycle.CycleAmounts.goalKey(goal.id) }?.amountMinor
            com.ledgerline.app.finance.goals.GoalMath.progress(goal, contributions, accountRows, txs, today, cycle, if (goal.closed) 0L else own)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What became of the money: still held, spent on the goal, or used for something else (null reopens). */
    fun setOutcome(outcome: com.ledgerline.app.domain.model.GoalOutcome?) {
        viewModelScope.launch { goals.setOutcome(args.goalId, outcome, clock.today()) }
    }
    val history = goals.observeContributions().map { rows -> rows.filter { it.goalId == args.goalId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts = accounts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var message by mutableStateOf<String?>(null)

    /** Cycle by cycle: saved in each past one, this one's plan, and the plan for each one ahead until it is done. */
    val timeline = kotlinx.coroutines.flow.combine(
        progress,
        goals.observeContributions(),
        kotlinx.coroutines.flow.combine(accounts.observe(), transactions.observeAll(), ::Pair),
        settings.observe(),
        cyclePlans.observeAmounts(),
    ) { row, contributions, (accountRows, txs), userSettings, amounts ->
        row?.let { p ->
            val own = amounts.filter { it.itemKey == com.ledgerline.app.finance.cycle.CycleAmounts.goalKey(p.goal.id) }.associate { it.cycleStart to it.amountMinor }
            com.ledgerline.app.finance.goals.GoalTimeline.build(p, contributions, accountRows, txs, clock.today(), userSettings) { own[it] }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(amountMinor: Long, note: String) {
        viewModelScope.launch { message = (goals.addContribution(args.goalId, amountMinor, clock.today(), note) as? SaveResult.Invalid)?.message }
    }

    /** Money spent from what was saved: on the goal or on something else, optionally added as a spend. */
    fun recordSpend(amountMinor: Long, note: String, onGoal: Boolean, recordTransaction: Boolean) {
        viewModelScope.launch {
            message = (goals.recordSpend(args.goalId, amountMinor, clock.today(), note, onGoal, recordTransaction) as? SaveResult.Invalid)?.message
        }
    }

    fun remove(id: String) {
        viewModelScope.launch { goals.deleteContribution(id) }
    }
}

/** One goal: how far along, the plan, this cycle, and the money put in and taken out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onTransfer: () -> Unit,
    onRecordPurchase: (String, Long) -> Unit = { _, _ -> },
    viewModel: GoalDetailViewModel = hiltViewModel(),
) {
    val loaded by viewModel.progress.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    var moving by remember { mutableStateOf<Int?>(null) }
    var spending by remember { mutableStateOf(false) }
    val progress = loaded
    SubScreen(
        progress?.goal?.name ?: "Goal",
        onBack,
        actions = { progress?.let { IconButton(onClick = { onEdit(it.goal.id) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit goal") } } },
    ) { padding ->
        if (progress == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This goal is no longer here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@SubScreen
        }
        val goal = progress.goal
        val c = goal.currency
        val colors = LocalLedgerColors.current
        val account = accounts.firstOrNull { it.id == goal.accountId }
        val byBalance = goal.useAccountBalance && account != null
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressRing(progress.fraction, if (progress.reached) colors.positive else colors.positive.copy(alpha = 0.8f))
                    Text("${MoneyFormat.format(progress.savedMinor, c)} of ${MoneyFormat.format(goal.targetMinor, c)}", style = MaterialTheme.typography.titleLarge)
                    if (!progress.reached) {
                        Text("${MoneyFormat.format(progress.remainingMinor, c)} to go", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GoalStatus(progress)
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("The plan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(planSentence(progress), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    if (progress.planMonthlyMinor != null && !progress.reached) {
                        val thisCycle = minOf(progress.planMonthlyMinor, progress.remainingMinor + progress.savedThisCycleMinor)
                        ProgressTrack(
                            if (thisCycle > 0) progress.savedThisCycleMinor.toFloat() / thisCycle else 1f,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "This pay cycle: ${MoneyFormat.format(progress.savedThisCycleMinor, c)} of ${MoneyFormat.format(thisCycle, c)} put aside." +
                                if (progress.setAsideNowMinor > 0) " The other ${MoneyFormat.format(progress.setAsideNowMinor, c)} is held back from Safe to spend until you save it." else " Done for this cycle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            item {
                Text(
                    when {
                        progress.accountMissing -> "The account this was kept in is gone. Edit the goal to choose another, or track it yourself."
                        byBalance -> "Kept in ${account!!.name}. Its balance is the progress, so moving money there is all you need to do."
                        account != null && account.includeInSafeToSpend ->
                            "Kept in ${account.name}. The money saved for it is held back from Safe to spend so you don't spend it by accident."
                        account != null -> "Kept in ${account.name}. Add money here each time you put some aside."
                        else -> "You track this one yourself: add money here each time you put some aside."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (progress.accountMissing) colors.caution else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                if (byBalance) {
                    Button(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) { Text("Move money to ${account!!.name}") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { moving = 1 }, modifier = Modifier.weight(1f)) { Text("Add money") }
                        OutlinedButton(onClick = { moving = -1 }, modifier = Modifier.weight(1f)) { Text("Take out") }
                    }
                }
            }
            viewModel.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                MoneyOutcome(
                    progress = progress,
                    accountName = account?.name,
                    onSpend = { spending = true },
                    onChoose = viewModel::setOutcome,
                    onRecordPurchase = { onRecordPurchase(goal.name, progress.savedMinor) },
                )
            }
            if (!goal.closed) timeline?.let { t ->
                item { CycleSummary(t, progress, c) }
                if (t.rows.isNotEmpty()) {
                    item { SectionHeader("Cycle by cycle") }
                    item { GroupCard { t.rows.forEach { row -> CycleLine(row, c) } } }
                }
            }
            if (history.isNotEmpty()) {
                item { SectionHeader("History") }
                item {
                    GroupCard {
                        history.forEach { row -> HistoryRow(row, c, viewModel::remove) }
                    }
                }
            }
        }
    }
    if (spending && progress != null) {
        SpendSheet(
            goalName = progress.goal.name,
            heldMinor = progress.heldMinor,
            currency = progress.goal.currency,
            accountName = accounts.firstOrNull { it.id == progress.goal.accountId }?.name,
            onDismiss = { spending = false },
        ) { amount, note, onGoal, record ->
            viewModel.recordSpend(amount, note, onGoal, record)
            spending = false
        }
    }
    moving?.let { sign ->
        MoneySheet(
            title = if (sign > 0) "Add to ${progress?.goal?.name}" else "Take out of ${progress?.goal?.name}",
            currency = progress?.goal?.currency ?: "USD",
            onDismiss = { moving = null },
        ) { amount, note ->
            viewModel.add(amount * sign, note)
            moving = null
        }
    }
}

/**
 * Where the saved money is now. Still holding it (it stays held in the account and back from Safe to spend), spent
 * on the goal (the goal is done and the money is free; record the purchase as a spend), or used for something else
 * (the goal closes and the money is free). A finished goal can be reopened.
 */
@Composable
private fun MoneyOutcome(
    progress: GoalProgress,
    accountName: String?,
    onSpend: () -> Unit,
    onChoose: (com.ledgerline.app.domain.model.GoalOutcome?) -> Unit,
    onRecordPurchase: () -> Unit,
) {
    val goal = progress.goal
    val c = goal.currency
    val colors = LocalLedgerColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Where's the money?", style = MaterialTheme.typography.titleMedium)
        when (goal.outcome) {
            null -> {
                // Saved, spent on the goal, used for something else, and what is still held: the whole story at a glance.
                SummaryLine("Saved for it", MoneyFormat.format(progress.savedMinor + progress.usedElsewhereMinor, c))
                if (progress.spentOnGoalMinor > 0) SummaryLine("Spent on the goal", "− " + MoneyFormat.format(progress.spentOnGoalMinor, c), colors.positive)
                if (progress.usedElsewhereMinor > 0) SummaryLine("Used for something else", "− " + MoneyFormat.format(progress.usedElsewhereMinor, c), colors.caution)
                SummaryLine("Still holding" + (accountName?.let { " in $it" } ?: ""), MoneyFormat.format(progress.heldMinor, c))
                Text(
                    "What is still held stays out of what is free to spend. Record a spend when you use some of it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.Button(onClick = onSpend, enabled = progress.heldMinor > 0, modifier = Modifier.fillMaxWidth()) {
                    Text("Record a spend from it")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Finish the goal:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onChoose(com.ledgerline.app.domain.model.GoalOutcome.SPENT_ON_GOAL) }) { Text("Done") }
                    TextButton(onClick = { onChoose(com.ledgerline.app.domain.model.GoalOutcome.USED_ELSEWHERE) }) { Text("Dropped") }
                }
            }
            com.ledgerline.app.domain.model.GoalOutcome.SPENT_ON_GOAL -> {
                Text(
                    "Spent on ${goal.name}" + (goal.outcomeOn?.let { " on ${it.withYear()}" } ?: "") + ". The money is no longer held.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.positive,
                )
                Text(
                    "If you have not added the purchase yet, record it so your balance matches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilledTonalButton(onClick = onRecordPurchase, modifier = Modifier.weight(1f)) { Text("Record the purchase", maxLines = 1) }
                    TextButton(onClick = { onChoose(null) }) { Text("Still holding") }
                }
            }
            com.ledgerline.app.domain.model.GoalOutcome.USED_ELSEWHERE -> {
                Text(
                    "Used for something else" + (goal.outcomeOn?.let { " on ${it.withYear()}" } ?: "") + ". The goal is closed and the money is free.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.caution,
                )
                TextButton(onClick = { onChoose(null) }) { Text("Reopen: I'm still holding it") }
            }
        }
    }
}

/** Last cycle, this cycle, and what is still needed, in a few short lines. */
@Composable
private fun CycleSummary(t: com.ledgerline.app.finance.goals.GoalTimelineResult, progress: GoalProgress, c: String) {
    val money = { minor: Long -> MoneyFormat.format(minor, c) }
    val colors = LocalLedgerColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryLine("Last cycle you saved", t.lastCycleSavedMinor?.let(money) ?: "—")
        t.thisCycle?.let { now ->
            SummaryLine("This cycle the plan is", now.plannedMinor?.let(money) ?: "No plan")
            SummaryLine("Saved so far this cycle", money(now.savedMinor), colors.positive)
        }
        if (progress.reached) {
            SummaryLine("Goal reached", money(progress.savedMinor), colors.positive)
        } else {
            SummaryLine("Still to put aside this cycle", money(t.stillThisCycleMinor), if (t.stillThisCycleMinor > 0) colors.caution else colors.positive)
            val ahead = t.rows.filter { it.whenIs == com.ledgerline.app.finance.goals.GoalCycleWhen.AHEAD }
            if (ahead.isNotEmpty()) {
                SummaryLine("After this cycle", "${money(ahead.sumOf { it.plannedMinor ?: 0 })} over ${ahead.size} ${if (ahead.size == 1) "cycle" else "cycles"}")
                Text(
                    "Last saving on payday ${ahead.last().cycle.start.withYear()}" + (progress.goal.targetDate?.let { " · goal date ${it.withYear()}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, tone: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = tone)
    }
}

/** One cycle: its dates, what was planned, and what was saved (or, ahead, what is planned). */
@Composable
private fun CycleLine(row: com.ledgerline.app.finance.goals.GoalCycleRow, c: String) {
    val colors = LocalLedgerColors.current
    val money = { minor: Long -> MoneyFormat.format(minor, c) }
    val label = "${row.cycle.start.medium()} – ${row.cycle.end.medium()}"
    val ahead = row.whenIs == com.ledgerline.app.finance.goals.GoalCycleWhen.AHEAD
    val met = row.plannedMinor != null && row.savedMinor >= row.plannedMinor
    ListRow(
        title = label + if (row.whenIs == com.ledgerline.app.finance.goals.GoalCycleWhen.NOW) " · now" else "",
        subtitle = when {
            ahead -> "Planned · ${money(row.totalMinor)} saved by then"
            row.plannedMinor == null -> "Saved · ${money(row.totalMinor)} in all"
            met -> "Plan ${money(row.plannedMinor)} · met · ${money(row.totalMinor)} in all"
            row.whenIs == com.ledgerline.app.finance.goals.GoalCycleWhen.NOW -> "Plan ${money(row.plannedMinor)} · ${money(row.plannedMinor - row.savedMinor)} to go"
            else -> "Plan ${money(row.plannedMinor)} · short ${money(row.plannedMinor - row.savedMinor)}"
        },
        icon = if (ahead) Icons.Filled.Event else Icons.Filled.Savings,
        iconTint = if (ahead) MaterialTheme.colorScheme.onSurfaceVariant else colors.positive,
    ) {
        Text(
            if (ahead) money(row.plannedMinor ?: 0) else money(row.savedMinor),
            style = MaterialTheme.typography.titleSmall,
            color = if (ahead) MaterialTheme.colorScheme.onSurfaceVariant else if (row.savedMinor > 0) colors.positive else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HistoryRow(row: GoalContribution, currency: String, onRemove: (String) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val colors = LocalLedgerColors.current
    val (title, tone) = when (row.kind) {
        com.ledgerline.app.domain.model.GoalEntryKind.SAVED -> "Saved" to colors.positive
        com.ledgerline.app.domain.model.GoalEntryKind.TAKEN_OUT -> "Taken out" to colors.caution
        com.ledgerline.app.domain.model.GoalEntryKind.SPENT_ON_GOAL -> "Spent on the goal" to colors.positive
        com.ledgerline.app.domain.model.GoalEntryKind.USED_ELSEWHERE -> "Used for something else" to colors.caution
    }
    ListRow(
        title = title,
        subtitle = listOfNotNull(row.onDate.withYear(), row.note.takeIf { it.isNotBlank() }, "added as a spend".takeIf { row.transactionId != null }).joinToString(" · "),
        icon = if (row.kind == com.ledgerline.app.domain.model.GoalEntryKind.SAVED) Icons.Filled.Savings else Icons.Filled.ShoppingBag,
        iconTint = tone,
    ) {
        Text(MoneyFormat.signedLabel(row.amountMinor, currency), style = MaterialTheme.typography.titleSmall, color = tone)
        IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, contentDescription = "Remove this entry") }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Remove this entry?",
            body = "${MoneyFormat.signedLabel(row.amountMinor, currency)} on ${row.onDate.medium()} will no longer count toward the goal.",
            confirm = "Remove",
            onConfirm = {
                confirm = false
                onRemove(row.id)
            },
            onDismiss = { confirm = false },
        )
    }
}

/**
 * Spend from what was saved: how much, what for (the goal, or something else), a note, and whether to add it as a
 * spend from the account the money is kept in so the balance matches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpendSheet(
    goalName: String,
    heldMinor: Long,
    currency: String,
    accountName: String?,
    onDismiss: () -> Unit,
    onDone: (Long, String, Boolean, Boolean) -> Unit,
) {
    val digits = MoneyFormat.fractionDigits(currency)
    var text by rememberSaveable { mutableStateOf(AmountInput.fromMinor(heldMinor, digits)) }
    var note by rememberSaveable { mutableStateOf("") }
    var onGoal by rememberSaveable { mutableStateOf(true) }
    var record by rememberSaveable { mutableStateOf(accountName != null) }
    val amount = AmountInput.toMinor(text, digits)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Spend from $goalName", style = MaterialTheme.typography.titleLarge)
            Text("Holding ${MoneyFormat.format(heldMinor, currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigAmountField(text = text, currency = currency, color = MaterialTheme.colorScheme.onSurface) { text = AmountInput.clean(it, digits) }
            Text("What was it for?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(onGoal, { onGoal = true }, { Text("The goal") })
                androidx.compose.material3.FilterChip(!onGoal, { onGoal = false }, { Text("Something else") })
            }
            Text(
                if (onGoal) "It still counts as saved toward the goal; it is just no longer held."
                else "The goal is that much less funded; its plan asks for it again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(if (onGoal) "What did you buy? (optional)" else "What was it for? (optional)") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            if (accountName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Also add it as a spend from $accountName", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = record, onCheckedChange = { record = it })
                }
            }
            PrimaryButton("Save", enabled = amount > 0 && amount <= heldMinor) { onDone(amount, note, onGoal, record) }
            if (amount > heldMinor) Text("More than you are holding for it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneySheet(title: String, currency: String, onDismiss: () -> Unit, onDone: (Long, String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val digits = MoneyFormat.fractionDigits(currency)
    val amount = AmountInput.toMinor(text, digits)
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            BigAmountField(
                text = text,
                currency = currency,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = androidx.compose.ui.Modifier.focusRequester(focus),
            ) { text = AmountInput.clean(it, digits) }
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            PrimaryButton("Save", enabled = amount > 0) { onDone(amount, note) }
        }
    }
}

@Composable
private fun ProgressRing(fraction: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(140.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
    }
}

@HiltViewModel
class GoalEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val goals: GoalRepository,
    observe: ObserveDashboard,
    accounts: AccountRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<GoalEditorRoute>()
    val isExisting = args.goalId.isNotBlank()
    val accounts = accounts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val progress = observe.observe().map { d -> d.goals.firstOrNull { it.goal.id == args.goalId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    var name by mutableStateOf(args.preset)
    var targetText by mutableStateOf("")
        private set
    var targetMinor by mutableStateOf(0L)
        private set
    var date by mutableStateOf<LocalDate?>(null)
    var accountId by mutableStateOf<String?>(null)
    var useBalance by mutableStateOf(false)
    var monthlyText by mutableStateOf("")
        private set
    var monthlyMinor by mutableStateOf(0L)
        private set

    /** False: the same amount every month. True: it varies; each month you are told what is needed to still hit the date. */
    var varies by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()

    fun today(): LocalDate = clock.today()

    /** Saved so far, for the live plan while editing an existing goal. */
    fun savedSoFar(): Long = progress.value?.savedMinor ?: 0L

    init {
        if (isExisting) {
            viewModelScope.launch {
                val goal = goals.get(args.goalId) ?: return@launch
                name = goal.name
                targetMinor = goal.targetMinor
                targetText = AmountInput.fromMinor(goal.targetMinor, 2)
                date = goal.targetDate
                accountId = goal.accountId
                useBalance = goal.useAccountBalance
                monthlyMinor = goal.monthlyMinor ?: 0L
                monthlyText = AmountInput.fromMinor(monthlyMinor, 2)
                varies = goal.monthlyMinor == null && goal.targetDate != null
            }
        }
    }

    fun onTarget(raw: String) {
        targetText = AmountInput.clean(raw, 2)
        targetMinor = AmountInput.toMinor(targetText, 2)
    }

    fun onMonthly(raw: String) {
        monthlyText = AmountInput.clean(raw, 2)
        monthlyMinor = AmountInput.toMinor(monthlyText, 2)
    }

    fun useMonthly(minor: Long) = onMonthly(AmountInput.fromMinor(minor, 2))

    fun save() {
        viewModelScope.launch {
            val result = goals.save(
                GoalDraft(
                    existingId = args.goalId.ifBlank { null },
                    name = name,
                    targetMinor = targetMinor,
                    // A fixed monthly amount sets the pace; a date only matters when the amount varies.
                    targetDate = if (varies) date else null,
                    accountId = accountId,
                    useAccountBalance = useBalance,
                    monthlyMinor = monthlyMinor.takeIf { it > 0 && !varies },
                ),
            )
            when (result) {
                SaveResult.Saved -> _done.emit(Unit)
                is SaveResult.Invalid -> error = result.message
                is SaveResult.PossibleDuplicate -> Unit
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            goals.delete(args.goalId)
            _done.emit(Unit)
        }
    }
}

/**
 * Set up a goal in the order people think about it: what it is, how much, by when, where the money lives, and how
 * much a month. The plan updates as you type, so you can see straight away if the numbers work.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalEditorScreen(onDone: () -> Unit, viewModel: GoalEditorViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    val today = viewModel.today()
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = LocalLedgerColors.current
    val currency = accounts.firstOrNull { it.id == viewModel.accountId }?.currency ?: accounts.firstOrNull()?.currency ?: "USD"
    val preview = GoalPreview.plan(viewModel.targetMinor, viewModel.savedSoFar(), viewModel.date, viewModel.monthlyMinor.takeIf { !viewModel.varies } ?: 0L, today)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isExisting) "Edit goal" else "New savings goal") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close without saving") } },
                actions = {
                    if (viewModel.isExisting) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete goal") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding().padding(16.dp)) {
                PrimaryButton(
                    if (viewModel.isExisting) "Save changes" else "Save goal",
                    enabled = viewModel.name.isNotBlank() && viewModel.targetMinor > 0,
                    onClick = viewModel::save,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Just the essentials: what it is for, how much, how much a month (or a date, if it varies), and where.
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                value = viewModel.targetText,
                onValueChange = viewModel::onTarget,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Target amount") },
                prefix = { Text(currencySymbol(currency)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            SwitchRow(
                title = "Different amount each month",
                body = "Pick a date instead; each month shows what is needed to still reach it.",
                checked = viewModel.varies,
            ) { viewModel.varies = it }
            if (!viewModel.varies) {
                OutlinedTextField(
                    value = viewModel.monthlyText,
                    onValueChange = viewModel::onMonthly,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Save each month") },
                    prefix = { Text(currencySymbol(currency)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            } else {
                Label("Reach it by")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3L to "3 months", 6L to "6 months", 12L to "1 year").forEach { (months, label) ->
                        val d = today.plusMonths(months)
                        FilterChip(viewModel.date == d, { viewModel.date = d }, { Text(label) })
                    }
                    val custom = viewModel.date?.takeIf { d -> listOf(3L, 6L, 12L).none { today.plusMonths(it) == d } }
                    FilterChip(custom != null, { picking = true }, { Text(custom?.withYear() ?: "Pick a date") }, leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) })
                }
            }
            // The answer, worked out as you type.
            if (viewModel.targetMinor > 0) {
                val left = (viewModel.targetMinor - viewModel.savedSoFar()).coerceAtLeast(0)
                val (headline, detail) = when {
                    !viewModel.varies && viewModel.monthlyMinor > 0 && preview.finishOn != null -> {
                        val months = ((left + viewModel.monthlyMinor - 1) / viewModel.monthlyMinor)
                        "${months} ${if (months == 1L) "month" else "months"} to go" to "You'll reach it around ${preview.finishOn.withYear()}."
                    }
                    viewModel.varies && preview.neededPerMonthMinor != null && viewModel.date != null ->
                        "${MoneyFormat.format(preview.neededPerMonthMinor, currency)} this month" to "To reach ${MoneyFormat.format(viewModel.targetMinor, currency)} by ${viewModel.date!!.withYear()}. It updates as you save."
                    viewModel.varies -> "Pick a date" to "Then you'll see what each month needs."
                    else -> "Enter a monthly amount" to "and you'll see how long it takes."
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(headline, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Label("Held in")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(viewModel.accountId == null, { viewModel.accountId = null; viewModel.useBalance = false }, { Text("Track it myself") })
                accounts.filter { it.active }.forEach { account ->
                    FilterChip(viewModel.accountId == account.id, { viewModel.accountId = account.id }, { Text(account.name) })
                }
            }
            val chosen: Account? = accounts.firstOrNull { it.id == viewModel.accountId }
            if (chosen != null) {
                SwitchRow(
                    title = "${chosen.name} is only for this goal",
                    body = "Its balance is the progress.",
                    checked = viewModel.useBalance,
                ) { viewModel.useBalance = it }
            }
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (picking) {
        DatePickerDialogOnly(viewModel.date ?: today.plusMonths(6), onDismiss = { picking = false }) {
            viewModel.date = it
            picking = false
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${viewModel.name.ifBlank { "this goal" }}?",
            body = "The goal and its history are removed. Your accounts and their money are not touched.",
            confirm = "Delete",
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun currencySymbol(currency: String): String =
    runCatching { java.util.Currency.getInstance(currency).getSymbol(Locale.getDefault()) }.getOrDefault(currency)
