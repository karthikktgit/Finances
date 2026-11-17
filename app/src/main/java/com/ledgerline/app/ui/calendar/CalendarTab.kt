package com.ledgerline.app.ui.calendar

import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Savings
import com.ledgerline.app.ui.theme.depth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.navigationBarsPadding
import com.ledgerline.app.ui.theme.depthTinted
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Autorenew
import com.ledgerline.app.finance.calendar.DayMark
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.calendar.BillDay
import com.ledgerline.app.finance.calendar.BillEntry
import com.ledgerline.app.finance.calendar.BillMonth
import com.ledgerline.app.finance.calendar.BillPresets
import com.ledgerline.app.finance.calendar.CalendarGrid
import com.ledgerline.app.finance.calendar.DebtCalendar
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import com.ledgerline.app.ui.components.CategoryVisuals
import com.ledgerline.app.ui.components.GroupCard
import com.ledgerline.app.ui.components.LedgerCard
import com.ledgerline.app.ui.components.ListRow
import com.ledgerline.app.ui.components.ProgressTrack
import com.ledgerline.app.ui.components.ScreenHeader
import com.ledgerline.app.ui.components.SectionHeader
import com.ledgerline.app.ui.components.StatusPill
import com.ledgerline.app.ui.components.amountColor
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.format.weekday
import com.ledgerline.app.ui.theme.LocalLedgerColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The calendar is where bills, subscriptions, and income are set and checked off. From the top: what this month's
 * bills add up to and how much is still to pay, a month grid showing the amount due on each day, the chosen day,
 * and every bill in the month as a list you can mark paid. Swipe the month sideways to change it.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onEdit: (String, String) -> Unit,
    onAddPayment: (String) -> Unit,
    onAddTransaction: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenDebt: (String) -> Unit,
    contentPadding: PaddingValues,
    onSelectedDay: (LocalDate) -> Unit = {},
    onPreset: (String, String) -> Unit = { _, _ -> },
    onOpenGoal: (String) -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val state = loaded ?: return
    val today = state.today
    val month = state.month
    val selected = state.selected
    val currency = state.currency
    val summary = state.summary
    val cycle = state.cycle
    val dayEntries = state.dayEntries
    val daySpending = state.daySpending
    LaunchedEffect(selected) { onSelectedDay(selected) }

    var showDue by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (showDue) {
        // Everything still to pay this month, soonest first, with a one-tap Paid.
        val due = state.agenda.flatMap { it.second }.filter { !it.paid && !it.incoming && !it.transfer }.sortedBy { it.date }
        val planned = state.monthPlanned
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showDue = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Still to pay", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(MoneyFormat.format(due.sumOf { it.amountMinor ?: 0L } + planned.sumOf { it.amountMinor }, currency), style = MaterialTheme.typography.titleLarge)
                }
                if (due.isEmpty() && planned.isEmpty()) {
                    Text("Nothing left to pay this month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    GroupCard {
                        due.forEach { entry ->
                            val account = state.accounts.firstOrNull { it.id == entry.accountId }?.name
                            BillRow(entry, currency, today, showDate = true, onEdit, onOpenDebt, viewModel::markPaid, account)
                        }
                        planned.forEach { tx -> PlannedRow(tx, state.accounts, onOpenTransaction, showDate = true) { viewModel.markSpendPaid(tx.id) } }
                    }
                }
            }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // A fixed height with the same button every month, so the calendar never shifts. Swipe to change month.
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        month.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                    )
                    Text(month.year.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                IconButton(onClick = viewModel::goToToday, enabled = month != YearMonth.from(today) || selected != today) {
                    Icon(Icons.Filled.Today, contentDescription = "Go to today")
                }
            }
        }
        item { MonthSummaryCard(summary, currency, state.monthPlanned.sumOf { it.amountMinor }) { showDue = true } }
        item {
            LedgerCard(compact = true) {
                WeekdayHeader()
                // The months sit side by side and follow your finger; a month is only loaded as it comes into view.
                val base = remember { YearMonth.from(today) }
                val origin = 600
                fun pageOf(m: YearMonth) = origin + java.time.temporal.ChronoUnit.MONTHS.between(base, m).toInt()
                val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = pageOf(month)) { origin * 2 }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect { page -> viewModel.showMonth(base.plusMonths((page - origin).toLong())) }
                }
                LaunchedEffect(month) {
                    val target = pageOf(month)
                    if (pager.currentPage != target && !pager.isScrollInProgress) pager.animateScrollToPage(target)
                }
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val shownMonth = base.plusMonths((page - origin).toLong())
                    val grid = state.grids[shownMonth]
                    val shownDates = grid?.first ?: CalendarGrid.dates(shownMonth)
                    MonthGrid(
                        month = shownMonth,
                        dates = shownDates,
                        days = grid?.second.orEmpty(),
                        leftOn = state::leftOn,
                        dailySpendMinor = state.dailySpendMinor,
                        today = today,
                        selected = selected,
                        cycle = if (viewModel.highlightCycle) cycle else null,
                        // Every date on the grid, the edge days of the months either side too.
                        heat = if (viewModel.heatMap) com.ledgerline.app.finance.calendar.HeatMap.of(grid?.second.orEmpty(), shownDates) else null,
                        currency = currency,
                        onSelect = viewModel::select,
                        goalDates = state.goalDays.keys,
                    )
                }
                CalendarControls(
                    shadeCycle = viewModel.highlightCycle,
                    heat = viewModel.heatMap,
                    onCycle = { viewModel.setHighlight(!viewModel.highlightCycle) },
                    onHeat = { viewModel.showHeat(!viewModel.heatMap) },
                )
            }
        }

        item { SectionHeader(selected.weekday() + if (selected == today) " · Today" else "") }
        item(key = "day-accounts") { DayAccountsDropdown(state.dayAccounts, currency, future = !selected.isBefore(today)) }
        item {
            GroupCard {
                if (dayEntries.isEmpty() && daySpending.isEmpty() && state.dayPlanned.isEmpty() && state.dayGoals.isEmpty()) {
                    Text(
                        "Nothing due or spent on this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                dayEntries.forEach { entry ->
                    BillRow(entry, currency, today, showDate = false, onEdit, onOpenDebt, viewModel::markPaid)
                }
                // Spends planned for this day and not paid yet, with Paid.
                state.dayPlanned.forEach { tx -> PlannedRow(tx, state.accounts, onOpenTransaction) { viewModel.markSpendPaid(tx.id) } }
                state.dayGoals.forEach { goal -> GoalRow(goal, currency) { onOpenGoal(goal.goalId) } }
                daySpending.forEach { tx ->
                    val account = state.accounts.firstOrNull { it.id == tx.accountId }?.name ?: "No account"
                    ListRow(
                        title = tx.merchantRaw.ifBlank { tx.type.label() },
                        subtitle = "Spent · $account",
                        icon = CategoryVisuals.forType(tx.type),
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { onOpenTransaction(tx.id) },
                    ) {
                        Text(
                            MoneyFormat.signedLabel(tx.balanceEffectMinor, tx.currency),
                            style = MaterialTheme.typography.titleSmall,
                            color = amountColor(tx.type, tx.balanceEffectMinor),
                        )
                    }
                }
                // One way to add, the same as Home's: a transaction on this day, which can repeat (a bill).
                com.ledgerline.app.ui.components.CashButton(
                    "Add transaction",
                    onClick = { onAddTransaction(selected.toString()) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    wide = true,
                )
            }
        }
        viewModel.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }

        item {
            SectionHeader("All of ${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}")
        }
        if (state.agenda.isEmpty()) {
            item {
                GroupCard {
                    Text(
                        "No bills or income this month yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        state.agenda.forEach { (date, rows) ->
            item(key = "agenda-$date") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) +
                            if (date == today) " · Today" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                    GroupCard {
                        rows.forEach { entry -> BillRow(entry, currency, today, showDate = false, onEdit, onOpenDebt, viewModel::markPaid) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(summary: com.ledgerline.app.finance.calendar.BillMonthSummary, currency: String, plannedMinor: Long, onOpen: () -> Unit) {
    val colors = LocalLedgerColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .depthTinted(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Still to pay", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                // Unpaid bills and spends planned this month but not paid yet.
                Text(
                    MoneyFormat.format(summary.leftMinor + plannedMinor, currency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Bills this month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                Text(MoneyFormat.format(summary.billsMinor, currency), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        ProgressTrack(
            fraction = if (summary.billsMinor > 0) summary.paidMinor.toFloat() / summary.billsMinor.toFloat() else 0f,
            color = colors.positive,
        )
        Text(
            buildString {
                append(if (summary.billCount == 0) "No bills yet" else "${summary.paidCount} of ${summary.billCount} paid")
                if (summary.overdueCount > 0) append(" · ${summary.overdueCount} overdue")
                if (summary.incomeMinor > 0) append(" · ${MoneyFormat.format(summary.incomeMinor, currency)} income expected")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (summary.overdueCount > 0) colors.overdue else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun WeekdayHeader() {
    val first = java.time.DayOfWeek.SUNDAY
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        (0 until 7).forEach { offset ->
            Text(
                first.plus(offset.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    dates: List<LocalDate>,
    days: Map<LocalDate, BillDay>,
    leftOn: (LocalDate) -> Long?,
    dailySpendMinor: Long,
    today: LocalDate,
    selected: LocalDate,
    cycle: com.ledgerline.app.finance.period.PayPeriod?,
    heat: Map<LocalDate, com.ledgerline.app.finance.calendar.Heat>?,
    currency: String,
    onSelect: (LocalDate) -> Unit,
    goalDates: Set<LocalDate> = emptySet(),
) {
    val colors = LocalLedgerColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        dates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val day = days[date]
                    val inMonth = YearMonth.from(date) == month
                    val isSelected = date == selected
                    val inCycle = cycle?.contains(date) == true
                    val amountText: String?
                    val amountTint: Color
                    when {
                        day == null || !inMonth -> {
                            amountText = null
                            amountTint = Color.Unspecified
                        }
                        day.toPayMinor > 0 -> {
                            amountText = BillMonth.compact(day.toPayMinor, currency)
                            amountTint = if (day.overdue) colors.overdue else colors.negative
                        }
                        day.incomeMinor > 0 -> {
                            amountText = "+" + BillMonth.compact(day.incomeMinor, currency)
                            amountTint = colors.positive
                        }
                        day.allPaid -> {
                            amountText = "✓ paid"
                            amountTint = MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        else -> {
                            amountText = null
                            amountTint = Color.Unspecified
                        }
                    }
                    val description = buildString {
                        append(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())))
                        if (day != null && day.toPayMinor > 0) append(". ${MoneyFormat.format(day.toPayMinor, currency)} to pay")
                        if (day != null && day.incomeMinor > 0) append(". ${MoneyFormat.format(day.incomeMinor, currency)} income")
                        if (day?.allPaid == true) append(". Bills paid")
                        if (day != null && day.spentMinor > 0) append(". ${MoneyFormat.format(day.spentMinor, currency)} spent")
                        if (date == today) append(". Today")
                        val left = leftOn(date)
                        if (left != null && left < 0) append(". At your pace you would be short by then")
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 62.dp)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    // Heat map: red for money out, green for money in, stronger for more; a quiet day is a faint
                                    // tile. With the pay cycle on, only the cycle's days are coloured (its days in the months
                                    // either side too); with it off, every day. Days outside the month are fainter. The
                                    // picked day keeps its colour and gets the outline (below).
                                    heat != null && (cycle == null || inCycle) -> heat[date]?.let { h ->
                                        (if (h.out) colors.negative else colors.positive).copy(alpha = (0.14f + 0.62f * h.level) * if (inMonth) 1f else 0.45f)
                                    } ?: MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (inMonth) 0.55f else 0.2f)
                                    heat != null -> Color.Transparent
                                    // The picked day: a soft lift and a fine outline (below), not a solid block.
                                    isSelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                    // Pay cycle: a clear green band, a shade stronger on its first and last day.
                                    // The cycle's days in the months either side show too, fainter, so you can see it carries on.
                                    inCycle && (date == cycle?.start || date == cycle?.end) -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (inMonth) 1f else 0.5f)
                                    inCycle -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (inMonth) 0.45f else 0.25f)
                                    else -> Color.Transparent
                                },
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                } else if (heat != null && inCycle) {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onSelect(date) }
                            .semantics {
                                contentDescription = description
                                this.selected = isSelected
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .then(
                                    if (date == today && !isSelected) {
                                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (date == today || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    !inMonth -> MaterialTheme.colorScheme.outline
                                    date == today -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        if (amountText != null) {
                            Text(
                                amountText,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.Medium,
                                color = amountTint,
                            )
                        }
                        val marks = if (inMonth) day?.marks.orEmpty() else emptyList()
                        val goal = inMonth && date in goalDates
                        if (marks.isNotEmpty() || goal) {
                            // One dot for each thing on the day: money in, a bill due or paid, spending, a goal.
                            Row(Modifier.padding(top = 1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                marks.forEach { mark ->
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .background(markColor(mark), CircleShape),
                                    )
                                }
                                if (goal) Box(Modifier.size(5.dp).background(com.ledgerline.app.ui.home.savingsTone, RoundedCornerShape(1.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The key for the dots (or the heat colours), and the two view buttons. */
@Composable
private fun CalendarControls(shadeCycle: Boolean, heat: Boolean, onCycle: () -> Unit, onHeat: () -> Unit) {
    val colors = LocalLedgerColors.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (heat) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Less", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(colors.negative.copy(alpha = 0.15f), colors.negative.copy(alpha = 0.75f)))))
                Text("Spent more", style = MaterialTheme.typography.labelMedium, color = colors.negative)
                Box(Modifier.size(width = 32.dp, height = 10.dp).clip(RoundedCornerShape(50)).background(colors.positive.copy(alpha = 0.7f)))
                Text("Money in", style = MaterialTheme.typography.labelMedium, color = colors.positive)
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LegendItem(markColor(DayMark.INCOME), "Income")
                LegendItem(markColor(DayMark.DUE), "Due")
                LegendItem(markColor(DayMark.PAID), "Paid")
                LegendItem(markColor(DayMark.SPENT), "Spent")
                LegendItem(markColor(DayMark.OVERDUE), "Overdue")
                LegendItem(com.ledgerline.app.ui.home.savingsTone, "Goal")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ViewPill("Pay cycle", Icons.Filled.Autorenew, shadeCycle, listOf(Color(0xFF6F8FCF), Color(0xFF6F8FCF)), Modifier.weight(1f), onCycle)
            ViewPill("Heat map", Icons.Filled.LocalFireDepartment, heat, listOf(Color(0xFFCF8A66), Color(0xFFCF8A66)), Modifier.weight(1f), onHeat)
        }
    }
}

/** A wide pill: solid ink when on, outlined when off, with a small coloured icon and its label. */
@Composable
private fun ViewPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, gradient: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = modifier
        .height(44.dp)
        .clip(shape)
        .then(
            if (on) Modifier.background(MaterialTheme.colorScheme.primary)
            else Modifier.background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        )
        .clickable(onClick = onClick)
    Row(base, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, tint = if (on) gradient.first() else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            " $label",
            style = MaterialTheme.typography.labelLarge,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { this.selected = on },
        )
    }
}

/** The dot colour for each kind of day mark, shared by the calendar and its legend. */
@Composable
private fun markColor(mark: DayMark): Color {
    val colors = LocalLedgerColors.current
    return when (mark) {
        DayMark.INCOME -> colors.positive
        DayMark.DUE -> colors.negative
        DayMark.OVERDUE -> colors.overdue
        DayMark.PAID -> colors.scheduled
        DayMark.SPENT -> colors.safe
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One bill or income line, with a one-tap Paid or Got it until it is done. */
@Composable
private fun BillRow(
    entry: BillEntry,
    currency: String,
    today: LocalDate,
    showDate: Boolean,
    onEdit: (String, String) -> Unit,
    onOpenDebt: (String) -> Unit,
    onMarkPaid: (String, LocalDate) -> Unit,
    accountName: String? = null,
) {
    val colors = LocalLedgerColors.current
    val tint = when {
        entry.paid -> MaterialTheme.colorScheme.onSurfaceVariant
        entry.overdue -> colors.overdue
        entry.incoming -> colors.positive
        entry.kind == ScheduledKind.DEBT -> colors.debt
        entry.transfer -> colors.transfer
        else -> colors.scheduled
    }
    val status = when {
        entry.paid -> if (entry.incoming) "Received" else "Paid"
        entry.overdue -> "Overdue"
        entry.date == today -> "Due today"
        entry.date == today.plusDays(1) -> "Due tomorrow"
        entry.date.isAfter(today) -> "Due ${entry.date.medium()}"
        else -> "Was due ${entry.date.medium()}"
    }
    val kindLabel = when (entry.kind) {
        ScheduledKind.SUBSCRIPTION -> "Subscription"
        ScheduledKind.INCOME -> "Income"
        ScheduledKind.DEBT -> "Loan or card"
        ScheduledKind.TRANSFER -> "Transfer"
        else -> "Bill"
    }
    ListRow(
        title = entry.name,
        subtitle = listOfNotNull(kindLabel, status, accountName).joinToString(" · "),
        icon = if (entry.paid) Icons.Filled.Check else kindIcon(entry.kind, entry.name),
        iconTint = tint,
        onClick = {
            when {
                entry.recurringId != null -> onEdit(entry.recurringId, entry.date.toString())
                entry.debtId != null -> onOpenDebt(entry.debtId)
            }
        },
    ) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                entry.amountMinor?.let { (if (entry.incoming) "+" else "") + MoneyFormat.format(it, currency) } ?: "Amount not set",
                style = MaterialTheme.typography.titleSmall,
                color = if (entry.paid) MaterialTheme.colorScheme.onSurfaceVariant else if (entry.incoming) colors.positive else MaterialTheme.colorScheme.onSurface,
            )
            if (entry.paid) {
                StatusPill(if (entry.incoming) "Received" else "Paid", colors.positive)
            }
        }
        if (!entry.paid && entry.recurringId != null) {
            FilledTonalButton(
                onClick = { onMarkPaid(entry.recurringId, entry.date) },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.semantics { contentDescription = "Mark ${entry.name} ${if (entry.incoming) "received" else "paid"}" },
            ) { Text(if (entry.incoming) "Got it" else "Paid") }
        }
    }
}

internal fun kindIcon(kind: ScheduledKind, name: String): ImageVector = when (kind) {
    ScheduledKind.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
    ScheduledKind.SUBSCRIPTION -> Icons.Filled.Subscriptions
    ScheduledKind.DEBT -> Icons.Filled.AccountBalance
    ScheduledKind.TRANSFER -> Icons.Filled.SwapHoriz
    else -> CategoryVisuals.forName(name).takeIf { it != Icons.Filled.Category } ?: Icons.AutoMirrored.Filled.ReceiptLong
}

private fun TransactionType.label(): String = when (this) {
    TransactionType.INCOME -> "Income"
    TransactionType.REFUND -> "Refund"
    TransactionType.TRANSFER -> "Transfer"
    TransactionType.PAYMENT -> "Payment"
    TransactionType.ADJUSTMENT -> "Adjustment"
    TransactionType.EXPENSE -> "Spend"
}

/** A sideways swipe of at least 64 dp moves one month; a tap still selects a date. */
private fun Modifier.monthSwipe(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                val threshold = 64.dp.toPx()
                when {
                    total > threshold -> onPrevious()
                    total < -threshold -> onNext()
                }
            },
        ) { _, amount -> total += amount }
    },
)

/**
 * A drop-down under the chosen day: each account's money that day, as a small table (start, in, out, end), with a
 * total row. A future day includes bills, income, and planned spends still to come, so its end is where you are heading.
 */
@Composable
private fun DayAccountsDropdown(rows: List<com.ledgerline.app.finance.calendar.DayAccountRow>, currency: String, future: Boolean) {
    if (rows.isEmpty()) return
    val colors = LocalLedgerColors.current
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Column(Modifier.fillMaxWidth().depth(RoundedCornerShape(18.dp))) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                if (open) "This day's account ins and outs" else "Tap to see this day's account ins and outs",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Hide" else "Show",
                modifier = Modifier.graphicsLayer { rotationZ = turn },
            )
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable
                fun Line(name: String, start: String, inText: String, outText: String, end: String, header: Boolean = false, endTint: Color = MaterialTheme.colorScheme.onSurface) {
                    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
                    val tint = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = if (header) style else MaterialTheme.typography.titleSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.3f))
                        Text(start, style = style, color = tint, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(inText, style = style, color = if (header) tint else colors.positive, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(outText, style = style, color = if (header) tint else colors.negative, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(end, style = if (header) style else MaterialTheme.typography.titleSmall, color = if (header) tint else endTint, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1.1f))
                    }
                }
                fun c(v: Long) = if (v < 0) "−" + BillMonth.compact(-v, currency) else BillMonth.compact(v, currency)
                Line("Account", "Start", "In", "Out", if (future) "End (est.)" else "End", header = true)
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                rows.forEach { row ->
                    Line(
                        row.name,
                        c(row.startMinor),
                        if (row.inMinor > 0) "+" + c(row.inMinor) else "—",
                        if (row.outMinor > 0) "−" + c(row.outMinor) else "—",
                        c(row.endMinor),
                        endTint = if (row.endMinor < 0) colors.negative else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Line(
                    "Total",
                    c(rows.sumOf { it.startMinor }),
                    rows.sumOf { it.inMinor }.let { if (it > 0) "+" + c(it) else "—" },
                    rows.sumOf { it.outMinor }.let { if (it > 0) "−" + c(it) else "—" },
                    c(rows.sumOf { it.endMinor }),
                )
                if (future) {
                    Text("Includes bills, income, and planned spends still to come.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A spend planned and not paid yet: its name, day and account, the amount, and a Paid button. */
/** A savings goal on the day: what to put aside this cycle, money saved, or its target date. */
@Composable
private fun GoalRow(goal: com.ledgerline.app.finance.calendar.GoalDay, currency: String, onOpen: () -> Unit) {
    val money = { minor: Long -> MoneyFormat.format(minor, currency) }
    ListRow(
        title = goal.name,
        subtitle = when (goal.kind) {
            com.ledgerline.app.finance.calendar.GoalDayKind.SET_ASIDE -> "Goal · put aside this cycle"
            com.ledgerline.app.finance.calendar.GoalDayKind.SAVED -> "Goal · saved"
            com.ledgerline.app.finance.calendar.GoalDayKind.TARGET -> "Goal date · ${money(goal.savedMinor)} of ${money(goal.targetMinor)}"
        },
        icon = Icons.Filled.Savings,
        iconTint = com.ledgerline.app.ui.home.savingsTone,
        onClick = onOpen,
    ) {
        Text(
            when (goal.kind) {
                com.ledgerline.app.finance.calendar.GoalDayKind.TARGET -> if (goal.amountMinor > 0) "${money(goal.amountMinor)} to go" else "Reached"
                else -> money(goal.amountMinor)
            },
            style = MaterialTheme.typography.titleSmall,
            color = com.ledgerline.app.ui.home.savingsTone,
        )
    }
}

@Composable
private fun PlannedRow(
    tx: com.ledgerline.app.domain.model.LedgerTransaction,
    accounts: List<com.ledgerline.app.domain.model.Account>,
    onOpen: (String) -> Unit,
    showDate: Boolean = false,
    onPaid: () -> Unit,
) {
    val account = accounts.firstOrNull { it.id == tx.accountId }?.name ?: "No account"
    ListRow(
        title = tx.merchantRaw.ifBlank { "Planned spend" },
        subtitle = listOfNotNull("Not paid yet", tx.occurredOn.takeIf { showDate }?.medium(), account).joinToString(" · "),
        icon = Icons.Filled.Event,
        iconTint = LocalLedgerColors.current.caution,
        onClick = { onOpen(tx.id) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(MoneyFormat.format(tx.amountMinor, tx.currency), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 8.dp))
            FilledTonalButton(onClick = onPaid, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(34.dp)) { Text("Paid") }
        }
    }
}
