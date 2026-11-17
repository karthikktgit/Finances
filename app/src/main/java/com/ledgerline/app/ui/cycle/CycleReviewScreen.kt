package com.ledgerline.app.ui.cycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ledgerline.app.domain.repository.BudgetRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.usecase.ObservePayPlan
import com.ledgerline.app.finance.cycle.CycleNumbers
import com.ledgerline.app.finance.cycle.CycleReview
import com.ledgerline.app.finance.cycle.CycleReviewResult
import com.ledgerline.app.finance.cycle.SeriesLine
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.ui.components.SubScreen
import com.ledgerline.app.ui.format.cycleRange
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.navigation.CycleReviewRoute
import com.ledgerline.app.ui.theme.LocalLedgerColors
import com.ledgerline.app.ui.theme.depth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The end-of-cycle review: a cycle next to the one before it, how each spending item went, and which bills and pay
 * carry on, stop, or start next cycle. Any cycle can be reviewed with the arrows.
 */
@HiltViewModel
class CycleReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    payPlan: ObservePayPlan,
    settings: SettingsRepository,
    recurring: RecurringRepository,
    budgets: BudgetRepository,
) : ViewModel() {
    val offset = MutableStateFlow(savedStateHandle.toRoute<CycleReviewRoute>().offset.coerceIn(-24, 12))

    fun shift(by: Int) {
        offset.value = (offset.value + by).coerceIn(-24, 12)
    }

    val currency = settings.observe().map { it.primaryCurrency }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "USD")

    @OptIn(ExperimentalCoroutinesApi::class)
    val review = offset.flatMapLatest { shift ->
        combine(
            payPlan.observeCycle(shift),
            payPlan.observeCycle(shift - 1),
            settings.observe(),
            recurring.observe(),
            budgets.observe(),
        ) { plan, previous, userSettings, series, budgetRows ->
            CycleReview.build(plan, previous, CycleSchedule.next(plan.cycle, userSettings), series, budgetRows)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun CycleReviewScreen(onBack: () -> Unit, viewModel: CycleReviewViewModel = hiltViewModel()) {
    val review by viewModel.review.collectAsStateWithLifecycle()
    val offset by viewModel.offset.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    SubScreen("Cycle review", onBack) { padding ->
        val r = review
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@SubScreen
        }
        val money = { minor: Long -> MoneyFormat.format(minor, currency) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "switch") { Switcher(r, offset, viewModel::shift) }
            item(key = "side") { SideBySide(r.now, r.before, money) }
            if (r.items.isNotEmpty()) {
                item(key = "items") {
                    Card("Spending items · planned vs spent") {
                        r.items.forEach { item ->
                            PlanBar(item.name, item.plannedMinor, item.spentMinor, money)
                        }
                    }
                }
            }
            item(key = "next") {
                Card("Next cycle · ${cycleRange(r.next.start, r.next.end)}") {
                    Group("Carries on", r.continuing, LocalLedgerColors.current.positive, money, empty = "Nothing scheduled carries on.")
                    Group("Ends this cycle", r.ending, LocalLedgerColors.current.caution, money, empty = "Nothing ends.")
                    if (r.starting.isNotEmpty()) Group("Starts next cycle", r.starting, MaterialTheme.colorScheme.primary, money, empty = "")
                    if (r.itemsEnding.isNotEmpty()) Note("Spending items ending: ${r.itemsEnding.joinToString()}")
                    if (r.itemsStarting.isNotEmpty()) Note("Spending items starting: ${r.itemsStarting.joinToString()}")
                }
            }
            if (r.now.stillToPayMinor > 0) {
                item(key = "unpaid") {
                    Card("Still to pay this cycle") {
                        Text(money(r.now.stillToPayMinor), style = MaterialTheme.typography.headlineSmall, color = LocalLedgerColors.current.caution)
                        Note("Bills not marked paid yet. Pay them or skip them on the calendar before the cycle ends.")
                    }
                }
            }
        }
    }
}

@Composable
private fun Switcher(r: CycleReviewResult, offset: Int, onShift: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier cycle") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (offset) {
                    0 -> "This cycle"
                    -1 -> "Last cycle"
                    1 -> "Next cycle"
                    else -> if (offset < 0) "${-offset} cycles ago" else "In $offset cycles"
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Text(cycleRange(r.cycle.start, r.cycle.end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (offset != 0) TextButton(onClick = { onShift(-offset) }) { Text("Today") }
        IconButton(onClick = { onShift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later cycle") }
    }
}

/** This cycle and the one before in two columns, with the change between them. More spent shows red, more saved green. */
@Composable
private fun SideBySide(now: CycleNumbers, before: CycleNumbers?, money: (Long) -> String) {
    val colors = LocalLedgerColors.current
    Card("This cycle vs the one before") {
        Row(Modifier.fillMaxWidth()) {
            Spacer1()
            Text("This", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
            Text("Before", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
        }
        listOf(
            Triple("Money in", now.incomeMinor to before?.incomeMinor, true),
            Triple("Bills paid", now.billsPaidMinor to before?.billsPaidMinor, false),
            Triple("Spent", now.spentMinor to before?.spentMinor, false),
            Triple("· not marked", now.unmarkedMinor to before?.unmarkedMinor, false),
            Triple("Saved for goals", now.savedMinor to before?.savedMinor, true),
            Triple("All out", now.outMinor to before?.outMinor, false),
        ).forEach { (label, values, upIsGood) ->
            val (a, b) = values
            val change = b?.let { a - it } ?: 0L
            val tone = when {
                change == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                (change > 0) == upIsGood -> colors.positive
                else -> colors.negative
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.End) {
                    Text(money(a), style = MaterialTheme.typography.titleSmall)
                    if (b != null && change != 0L) {
                        Text((if (change > 0) "▲ " else "▼ ") + money(kotlin.math.abs(change)), style = MaterialTheme.typography.labelSmall, color = tone)
                    }
                }
                Text(b?.let(money) ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Spacer1() = Box(Modifier.weight(1f))

/** Planned as the track, spent as the fill; red past the plan. */
@Composable
private fun PlanBar(name: String, planned: Long, spent: Long, money: (Long) -> String) {
    val colors = LocalLedgerColors.current
    val over = spent > planned
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${money(spent)} of ${money(planned)}", style = MaterialTheme.typography.titleSmall, color = if (over) colors.negative else MaterialTheme.colorScheme.onSurface)
        }
        com.ledgerline.app.ui.components.ProgressTrack(
            if (planned > 0) spent.toFloat() / planned else if (spent > 0) 1f else 0f,
            color = if (over) colors.negative else colors.positive,
        )
        if (over) Text("Over by ${money(spent - planned)}", style = MaterialTheme.typography.labelSmall, color = colors.negative)
    }
}

@Composable
private fun Group(title: String, rows: List<SeriesLine>, tone: Color, money: (Long) -> String, empty: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tone))
        Text("  $title · ${rows.size}", style = MaterialTheme.typography.labelLarge)
    }
    if (rows.isEmpty() && empty.isNotBlank()) Note(empty)
    rows.forEach { row ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.date.medium(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                (if (row.incoming) "+" else "") + money(row.amountMinor),
                style = MaterialTheme.typography.titleSmall,
                color = if (row.incoming) LocalLedgerColors.current.positive else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().depth(RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        content()
        Box(Modifier.height(2.dp))
    }
}
