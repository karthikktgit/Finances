package com.ledgerline.app.ui.insights

import androidx.compose.foundation.layout.height
import kotlinx.coroutines.flow.flowOn
import com.ledgerline.app.ui.theme.depth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.CategorySpend
import com.ledgerline.app.domain.usecase.ObserveInsights
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.components.KeyValue
import com.ledgerline.app.ui.components.LedgerCard
import androidx.compose.material.icons.filled.Lightbulb
import com.ledgerline.app.ui.components.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    insights: ObserveInsights,
    accounts: com.ledgerline.app.domain.repository.AccountRepository,
    transactions: com.ledgerline.app.domain.repository.TransactionRepository,
    categories: com.ledgerline.app.domain.repository.CategoryRepository,
    clock: com.ledgerline.app.domain.time.AppClock,
) : ViewModel() {
    val insights = insights.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Your usual month: average balance kept, spending a day and a month, and each category's average. */
    val averages = kotlinx.coroutines.flow.combine(accounts.observe(), transactions.observeAll(), categories.observe()) { a, t, c ->
        com.ledgerline.app.finance.insights.InsightAverages.compute(clock.today(), a, t, c)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Where your money goes, at a glance: this month against last, your usual balance and spending, each category against
 * its own average, the places you spend most, needs against wants, and subscriptions. Numbers first, words only where
 * they help.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(contentPadding: PaddingValues, viewModel: InsightsViewModel = hiltViewModel()) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val averages by viewModel.averages.collectAsStateWithLifecycle()
    val colors = com.ledgerline.app.ui.theme.LocalLedgerColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.ledgerline.app.ui.components.ScreenHeader("Insights", subtitle = "Where your money goes")
        val data = insights
        if (data == null) {
            Text("Loading spending…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val c = data.currency
        // This month against last, as two bars.
        LedgerCard {
            val max = maxOf(data.thisMonthNetMinor, data.lastMonthNetMinor, 1L).toFloat()
            val up = data.thisMonthNetMinor > data.lastMonthNetMinor && data.lastMonthNetMinor > 0
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Spent this month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(MoneyFormat.format(data.thisMonthNetMinor, c), style = MaterialTheme.typography.headlineSmall)
                }
                Text(changeText(data.thisMonthNetMinor, data.lastMonthNetMinor), style = MaterialTheme.typography.labelLarge, color = if (up) colors.negative else colors.positive)
            }
            Bar("This month", data.thisMonthNetMinor, max, MaterialTheme.colorScheme.secondary, c)
            Bar("Last month", data.lastMonthNetMinor, max, MaterialTheme.colorScheme.outline, c)
        }
        averages?.let { avg ->
            // Your usual numbers, as three tiles.
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Avg balance kept", avg.balanceMinor, c, if (avg.balanceMinor >= 0) colors.positive else colors.negative, Modifier.weight(1f))
                Tile("Avg a day", avg.perDayMinor, c, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                Tile("Avg a month", avg.perMonthMinor, c, colors.caution, Modifier.weight(1f))
            }
            if (avg.days < 60) {
                Text("From your last ${avg.days} days of records.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (avg.categories.isNotEmpty()) {
                com.ledgerline.app.ui.components.SectionHeader("Categories · this month vs usual")
                LedgerCard {
                    val top = avg.categories.maxOf { maxOf(it.averageMinor, it.thisMonthMinor) }.coerceAtLeast(1).toFloat()
                    avg.categories.take(8).forEach { row -> CategoryAverageRow(row, top, c) }
                }
            }
        }
        if (data.topMerchants.isNotEmpty()) {
            com.ledgerline.app.ui.components.SectionHeader("Top places")
            LedgerCard {
                val top = data.topMerchants.maxOf { it.amountMinor }.coerceAtLeast(1).toFloat()
                data.topMerchants.forEach { row -> Bar(row.name, row.amountMinor, top, colors.caution, c) }
            }
        }
        if (data.byNecessity.any { it.amountMinor > 0 }) {
            com.ledgerline.app.ui.components.SectionHeader("Needs vs wants")
            LedgerCard { Split(data.byNecessity.filter { it.amountMinor > 0 }, c) }
        }
        if (data.byAccount.any { it.amountMinor > 0 }) {
            com.ledgerline.app.ui.components.SectionHeader("By account")
            LedgerCard {
                val top = data.byAccount.maxOf { it.amountMinor }.coerceAtLeast(1).toFloat()
                data.byAccount.forEach { row -> Bar(row.name, row.amountMinor, top, MaterialTheme.colorScheme.secondary, c) }
            }
        }
        if (data.subscriptionMonthMinor > 0) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Subscriptions a month", data.subscriptionMonthMinor, c, colors.debt, Modifier.weight(1f))
                Tile("A year", data.subscriptionAnnualMinor, c, colors.debt, Modifier.weight(1f))
            }
        }
        if (data.leaks.isNotEmpty() || data.anomalies.isNotEmpty()) {
            com.ledgerline.app.ui.components.SectionHeader("Worth a look")
            (data.leaks + data.anomalies).forEach { note ->
                com.ledgerline.app.ui.home.CompactAlert(note.title, "", androidx.compose.material.icons.Icons.Filled.Lightbulb) {}
            }
        }
    }
}

/** A label, a bar scaled to the largest in its list, and the amount. */
@Composable
private fun Bar(label: String, minor: Long, max: Float, color: androidx.compose.ui.graphics.Color, currency: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        androidx.compose.foundation.layout.Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
            Text(MoneyFormat.format(minor, currency), style = MaterialTheme.typography.titleSmall)
        }
        com.ledgerline.app.ui.components.ProgressTrack(minor / max, color = color, height = 6.dp)
    }
}

/** A small number tile: the value large, its label under it. */
@Composable
private fun Tile(label: String, minor: Long, currency: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier.depth(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(com.ledgerline.app.finance.calendar.BillMonth.compact(minor, currency), style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** A category this month as a bar, with a thin mark where its average month falls. Red when it is above average. */
@Composable
private fun CategoryAverageRow(row: com.ledgerline.app.finance.insights.CategoryAverage, max: Float, currency: String) {
    val colors = com.ledgerline.app.ui.theme.LocalLedgerColors.current
    val above = row.thisMonthMinor > row.averageMinor && row.averageMinor > 0
    val tone = if (above) colors.negative else colors.positive
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val mark = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            com.ledgerline.app.ui.components.IconBubble(com.ledgerline.app.ui.components.CategoryVisuals.forName(row.name), tone, size = 28.dp)
            Text(row.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1)
            Text(
                "${MoneyFormat.format(row.thisMonthMinor, currency)} / ${com.ledgerline.app.finance.calendar.BillMonth.compact(row.averageMinor, currency)} avg",
                style = MaterialTheme.typography.labelLarge,
                color = if (above) colors.negative else MaterialTheme.colorScheme.onSurface,
            )
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(track, size = size, cornerRadius = r)
            drawRoundRect(tone, size = androidx.compose.ui.geometry.Size(size.width * (row.thisMonthMinor / max).coerceIn(0f, 1f), size.height), cornerRadius = r)
            val x = size.width * (row.averageMinor / max).coerceIn(0f, 1f)
            drawLine(mark, androidx.compose.ui.geometry.Offset(x, -3f), androidx.compose.ui.geometry.Offset(x, size.height + 3f), strokeWidth = 4f)
        }
    }
}

/** Parts of a whole as one bar, with each part named under it. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Split(parts: List<com.ledgerline.app.domain.model.NamedAmount>, currency: String) {
    val tones = listOf(0xFF6F8FCF, 0xFFC9A24B, 0xFF4FA3A5, 0xFF9A9AA3).map { androidx.compose.ui.graphics.Color(it) }
    val total = parts.sumOf { it.amountMinor }.coerceAtLeast(1).toFloat()
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        var x = 0f
        parts.forEachIndexed { i, part ->
            val w = size.width * part.amountMinor / total
            drawRoundRect(tones[i % tones.size], androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size((w - 2f).coerceAtLeast(1f), size.height), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            x += w
        }
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        parts.forEachIndexed { i, part ->
            com.ledgerline.app.ui.home.Swatch(tones[i % tones.size], "${part.name} ${MoneyFormat.format(part.amountMinor, currency)}")
        }
    }
}

private fun changeText(current: Long, previous: Long): String = when {
    previous == 0L && current == 0L -> "No spending in either month"
    previous == 0L -> "No spending last month to compare"
    else -> {
        val percent = ((current - previous) * 100) / previous
        when {
            percent > 0 -> "$percent% more than last month"
            percent < 0 -> "${-percent}% less than last month"
            else -> "Same as last month"
        }
    }
}
