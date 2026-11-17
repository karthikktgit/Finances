package com.ledgerline.app.ui.plan

import com.ledgerline.app.ui.theme.depth
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Dashboard
import com.ledgerline.app.domain.model.ExpenseNote
import com.ledgerline.app.domain.model.ExpenseNoteOrder
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.repository.DebtRepository
import com.ledgerline.app.domain.repository.ExpenseNoteRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.domain.usecase.ObserveDashboard
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.subscription.SubscriptionRollup
import com.ledgerline.app.ui.components.BudgetMeter
import com.ledgerline.app.ui.components.CategoryVisuals
import com.ledgerline.app.ui.components.GroupCard
import com.ledgerline.app.ui.components.IconBubble
import com.ledgerline.app.ui.components.ListRow
import com.ledgerline.app.ui.components.ScreenHeader
import com.ledgerline.app.ui.components.SectionHeader
import com.ledgerline.app.ui.dashboard.SafeToSpendBreakdown
import com.ledgerline.app.ui.format.cycleRange
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.theme.LocalLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import javax.inject.Inject

data class PlanState(
    val dashboard: Dashboard,
    val recurring: List<RecurringPayment>,
    val notes: List<ExpenseNote>,
    val debtCount: Int,
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    observeDashboard: ObserveDashboard,
    recurring: RecurringRepository,
    notes: ExpenseNoteRepository,
    debts: DebtRepository,
    private val clock: AppClock,
) : ViewModel() {
    val state = combine(observeDashboard.observe(), recurring.observe(), notes.observe(), debts.observe()) { dashboard, items, noteRows, debtRows ->
        PlanState(dashboard, items, noteRows, debtRows.size)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = clock.today()
}

/**
 * Plan answers one question top to bottom: what comes in, what must go out, what you allow for the rest,
 * and what that leaves. Each part has its own add button so the next step is always visible.
 */
@Composable
fun PlanScreen(
    contentPadding: PaddingValues,
    onSettings: () -> Unit,
    onAddIncome: () -> Unit,
    onAddBill: () -> Unit,
    onEditRecurring: (String) -> Unit,
    onAddBudget: () -> Unit,
    onEditBudget: (String) -> Unit,
    onNotes: () -> Unit,
    onAddNote: () -> Unit,
    onEditNote: (String) -> Unit,
    onCalendar: () -> Unit,
    onForecast: () -> Unit,
    onWhatIf: () -> Unit,
    onSubscriptions: () -> Unit,
    onDebt: () -> Unit,
    onPayCycle: () -> Unit,
    onAddGoal: () -> Unit = {},
    onOpenGoal: (String) -> Unit = {},
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state
    val today = viewModel.today()
    val colors = LocalLedgerColors.current
    LazyColumn(
        Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // The page title is in the top bar; this line only says which pay cycle the plan is for.
            Text(
                data?.let { "Pay cycle ${cycleRange(it.dashboard.cycleStart, it.dashboard.cycleEnd)}" }.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (data == null) {
            item { Text("Loading your plan…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@LazyColumn
        }
        val dashboard = data.dashboard
        val currency = dashboard.currency
        val live = data.recurring.filter { it.active }
        val income = live.filter { it.direction == CashFlowDirection.INFLOW }
        val bills = live.filter { it.direction == CashFlowDirection.OUTFLOW && it.kind != ScheduledKind.TRANSFER }
        val nextDates = (dashboard.upcoming + dashboard.forecastSchedule)
            .groupBy { it.recurringId }
            .mapValues { (_, rows) -> rows.minOf { it.date } }
        val planIsEmpty = income.isEmpty() && bills.isEmpty() && dashboard.budgets.isEmpty()

        if (planIsEmpty) {
            item { GettingStarted(onAddIncome, onAddBill, onAddBudget) }
        } else {
            item {
                Text(
                    "List what comes in and what must go out. Bills due this cycle, your budgets, and what you're saving for are " +
                        "set aside from the money you have now; what is left is safe to spend.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 1. Money coming in.
        item {
            SectionHeader("1 · Income", action = "Add", onAction = onAddIncome)
        }
        item {
            GroupCard {
                if (income.isEmpty()) {
                    HintRow("Add your salary or other regular income so the plan knows what is coming.", onAddIncome)
                }
                income.forEach { item ->
                    RecurringRow(item, nextDates[item.id], today, positive = true) { onEditRecurring(item.id) }
                }
            }
        }

        // 2. Money that must go out.
        item {
            SectionHeader(
                "2 · Bills and subscriptions",
                action = "Add",
                onAction = onAddBill,
            )
        }
        item {
            GroupCard {
                val monthly = SubscriptionRollup.monthlyMinor(bills)
                if (bills.isEmpty()) {
                    HintRow("Rent, phone, insurance, streaming: add what repeats so it is set aside before you spend.", onAddBill)
                } else if (monthly > 0) {
                    Text(
                        "About ${MoneyFormat.format(monthly, currency)} a month in repeating bills",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    )
                }
                bills.sortedBy { nextDates[it.id] ?: LocalDate.MAX }.forEach { item ->
                    RecurringRow(item, nextDates[item.id], today, positive = false) { onEditRecurring(item.id) }
                }
            }
        }

        // 3. Limits for everyday spending.
        item { SectionHeader("3 · Budgets", action = "Add", onAction = onAddBudget) }
        item {
            GroupCard {
                if (dashboard.budgets.isEmpty()) {
                    HintRow("Set a monthly limit for groceries, eating out, or anything you want to keep in check.", onAddBudget)
                }
                dashboard.budgets.forEach { snapshot ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onEditBudget(snapshot.budget.id) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(snapshot.budget.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                if (snapshot.remainingMinor >= 0) "${MoneyFormat.format(snapshot.remainingMinor, snapshot.budget.currency)} left" else "Over by ${MoneyFormat.format(-snapshot.remainingMinor, snapshot.budget.currency)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (snapshot.remainingMinor >= 0) colors.positive else colors.negative,
                            )
                        }
                        BudgetMeter(snapshot.spentMinor, snapshot.budget.amountMinor, snapshot.budget.currency)
                        Text(snapshot.pace.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 4. Saving toward something: paid first, before what's left.
        item { SectionHeader("4 · Savings goals", action = "Add", onAction = onAddGoal) }
        item {
            GroupCard {
                if (dashboard.goals.isEmpty()) {
                    HintRow("Saving for a room deposit, stocks, or a trip? Add a goal and get a monthly plan.", onAddGoal)
                }
                dashboard.goals.forEach { progress ->
                    com.ledgerline.app.ui.goals.GoalRow(progress) { onOpenGoal(progress.goal.id) }
                }
            }
        }

        // 5. The result.
        item { SectionHeader("5 · What's left") }
        item { SafeToSpendBreakdown(dashboard) }

        // Upcoming one-off purchases you want to remember.
        val open = ExpenseNoteOrder.open(data.notes, today)
        item { SectionHeader("Spending notes", action = if (open.isEmpty()) "Add" else "See all", onAction = if (open.isEmpty()) onAddNote else onNotes) }
        item {
            GroupCard {
                if (open.isEmpty()) {
                    HintRow("Jot down purchases you expect, like a birthday gift or car service. They do not change balances.", onAddNote)
                }
                open.take(3).forEach { note ->
                    ListRow(
                        title = note.name,
                        subtitle = note.dueOn?.let { if (ExpenseNoteOrder.isOverdue(note, today)) "Was due ${it.medium()}" else "By ${it.medium()}" } ?: "No date",
                        icon = Icons.Filled.EditNote,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = { onEditNote(note.id) },
                    ) {
                        note.amountMinor?.let { Text(MoneyFormat.format(it, currency), style = MaterialTheme.typography.titleSmall) }
                    }
                }
            }
        }

        item { SectionHeader("Planning tools") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolTile("Calendar", "Every bill by date", Icons.Filled.CalendarMonth, Modifier.weight(1f), onCalendar)
                    ToolTile("Cash flow", "Balance day by day", Icons.AutoMirrored.Filled.ShowChart, Modifier.weight(1f), onForecast)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolTile("What if", "Test a big purchase", Icons.Filled.Psychology, Modifier.weight(1f), onWhatIf)
                    ToolTile("Subscriptions", "Yearly cost", Icons.Filled.Subscriptions, Modifier.weight(1f), onSubscriptions)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolTile(
                        "Debt payoff",
                        if (data.debtCount == 0) "Plan loans and cards" else "${data.debtCount} tracked",
                        Icons.Filled.AccountBalance,
                        Modifier.weight(1f),
                        onDebt,
                    )
                    ToolTile("Pay cycle", "When your month starts", Icons.Filled.Event, Modifier.weight(1f), onPayCycle)
                }
            }
        }
    }
}

@Composable
private fun GettingStarted(onAddIncome: () -> Unit, onAddBill: () -> Unit, onAddBudget: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Make a plan in three steps", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            "Tell Ledgerline what comes in and what must go out. It sets bills aside and shows what is safe to spend.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        StepRow("1", "Add your income", onAddIncome)
        StepRow("2", "Add regular bills", onAddBill)
        StepRow("3", "Set a budget (optional)", onAddBudget)
    }
}

@Composable
private fun StepRow(number: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .depth(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HintRow(text: String, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onAdd).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) { Text("Add") }
    }
}

@Composable
private fun RecurringRow(item: RecurringPayment, next: LocalDate?, today: LocalDate, positive: Boolean, onClick: () -> Unit) {
    val colors = LocalLedgerColors.current
    val nextText = when {
        next == null -> "Nothing scheduled soon"
        next.isBefore(today) -> "Overdue since ${next.medium()}"
        next == today -> "Next: today"
        else -> "Next: ${next.medium()}"
    }
    ListRow(
        title = item.name,
        subtitle = "${item.frequency.label()} · $nextText",
        icon = when {
            positive -> Icons.AutoMirrored.Filled.TrendingUp
            item.kind == ScheduledKind.SUBSCRIPTION -> Icons.Filled.Subscriptions
            item.kind == ScheduledKind.DEBT -> Icons.Filled.AccountBalance
            else -> CategoryVisuals.forName(item.name)
        },
        iconTint = if (positive) colors.positive else colors.scheduled,
        onClick = onClick,
    ) {
        Text(
            (if (positive) "+" else "") + MoneyFormat.format(item.amountMinor, item.currency),
            style = MaterialTheme.typography.titleSmall,
            color = if (positive) colors.positive else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun ToolTile(title: String, body: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .depth(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconBubble(icon, MaterialTheme.colorScheme.primary, size = 36.dp)
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
