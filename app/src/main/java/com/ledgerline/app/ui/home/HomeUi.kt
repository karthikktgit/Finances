package com.ledgerline.app.ui.home

import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.ledgerline.app.ui.theme.depthTinted
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import com.ledgerline.app.ui.theme.depth
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.HomeGroup
import com.ledgerline.app.finance.calendar.BillMonth
import com.ledgerline.app.finance.envelopes.HomeItem
import com.ledgerline.app.finance.envelopes.HomeItemKind
import com.ledgerline.app.finance.envelopes.HomeSection
import com.ledgerline.app.finance.envelopes.PayPlan
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.theme.LocalLedgerColors
import java.util.Locale

/** Group colours on the pay bar, in group order. */
private val groupPalette = listOf(
    Color(0xFF6F8FCF), Color(0xFF8E7CC3), Color(0xFF4FA3A5), Color(0xFFCF8A66), Color(0xFFD27D8A), Color(0xFF6FAE87),
)

/** Fallback colours for items with no account. */
private val itemPalette = listOf(
    Color(0xFF6F8FCF), Color(0xFF4FA3A5), Color(0xFFCF8A66), Color(0xFF8E7CC3), Color(0xFFD27D8A), Color(0xFF6FAE87),
    Color(0xFFB07AA1), Color(0xFFC9A24B),
)

private val savingsColor = Color(0xFF6FAE87)
private val outsideColor = Color(0xFF9A9AA3)

fun groupColor(index: Int): Color = groupPalette[index % groupPalette.size]

val savingsTone = Color(0xFF6FAE87)

/** Each item keeps its colour by its place among its group's items, ordered by key so it does not change with amounts. */
fun itemColors(section: HomeSection): Map<String, Color> =
    section.items.map { it.key }.sorted().mapIndexed { i, key -> key to itemPalette[i % itemPalette.size] }.toMap()

private fun short(minor: Long, currency: String): String = BillMonth.compact(minor, currency)

/**
 * Which pay cycle Home shows: arrows step back and forward a cycle; the middle names it ("This cycle", "Last
 * cycle"...) with its dates. Away from this cycle, a "Today" pill comes back.
 */
@Composable
private fun CycleSwitcher(plan: PayPlan, offset: Int, onShift: (Int) -> Unit, onToday: () -> Unit) {
    val name = when (offset) {
        0 -> "This cycle"
        -1 -> "Last cycle"
        1 -> "Next cycle"
        else -> if (offset < 0) "${-offset} cycles ago" else "In $offset cycles"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShift(-1) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier cycle")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Text("${plan.cycle.start.medium()} – ${plan.cycle.end.medium()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (offset != 0) {
            Text(
                "Today",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).clickable(onClick = onToday).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        IconButton(onClick = { onShift(1) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later cycle")
        }
    }
}

/** One part of the pay bar, with the short explanation shown when you tap it. */
private data class PayPart(val name: String, val color: Color, val plannedMinor: Long, val usedMinor: Long, val brief: String)

/**
 * The pay cycle at a glance: what is left to spend, the days to payday, and one bar of where the pay goes, each part
 * labelled on the bar. Tap a part's label for how it is worked out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayCard(
    plan: PayPlan,
    currency: String,
    offset: Int = 0,
    onShift: ((Int) -> Unit)? = null,
    onToday: () -> Unit = {},
    onOpen: () -> Unit,
) {
    val colors = LocalLedgerColors.current
    val measurer = rememberTextMeasurer()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
    fun m(v: Long) = MoneyFormat.format(v, currency)
    val parts = buildList {
        plan.sections.forEachIndexed { i, section ->
            if (section.plannedMinor > 0) {
                add(PayPart(section.name, groupColor(i), section.plannedMinor, section.spentMinor,
                    "Everything in ${section.name} planned for this pay cycle: ${m(section.plannedMinor)}. " +
                        "Paid or spent so far: ${m(section.spentMinor)}. Still to go: ${m(section.leftMinor)}."))
            }
        }
        val saving = plan.savings.sumOf { it.plannedMinor }
        if (saving > 0) add(PayPart("Goals", savingsColor, saving, plan.savings.sumOf { it.spentMinor },
            "What your goals ask for this cycle: ${m(saving)}. Put aside so far: ${m(plan.savings.sumOf { it.spentMinor })}."))
        if (plan.outsideMinor > 0) add(PayPart("Un-marked", outsideColor, plan.outsideMinor, plan.outsideMinor,
            "Spending this cycle not picked for any item: ${m(plan.outsideMinor)} across ${plan.outsideCount} spends."))
    }
    val scale = maxOf(plan.incomeMinor, parts.sumOf { it.plannedMinor }, 1L).toFloat()
    val unplanned = plan.incomeMinor - parts.sumOf { it.plannedMinor }
    var brief by remember { mutableStateOf<PayPart?>(null) }
    fun pct(minor: Long) = if (plan.incomeMinor > 0) ((minor * 100 + plan.incomeMinor / 2) / plan.incomeMinor) else 0
    val plannedPct = pct(parts.sumOf { it.plannedMinor })
    val freePart = PayPart("Free", track, unplanned.coerceAtLeast(0), 0, "Pay ${m(plan.incomeMinor)} less everything planned (${m(plan.incomeMinor - unplanned)}): ${m(unplanned)} has no job yet.")
    Column(
        Modifier.fillMaxWidth().depth(RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onShift != null) CycleSwitcher(plan, offset, onShift, onToday)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(
                    when {
                        offset < 0 -> "Left at the end of that cycle"
                        offset > 0 -> "Left to spend that cycle"
                        else -> "Left to spend this cycle"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(m(plan.leftOfPayMinor), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = if (plan.leftOfPayMinor < 0) colors.negative else MaterialTheme.colorScheme.onSurface)
            }
            if (offset == 0) {
                Text("${plan.daysLeft} days to payday", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (plan.incomeMinor > 0) {
            Text(
                if (unplanned >= 0) "$plannedPct% of your pay is planned · tap a part for details" else "Planned is ${pct(-unplanned)}% more than your pay",
                style = MaterialTheme.typography.labelLarge,
                color = if (unplanned < 0) colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            Modifier.fillMaxWidth().height(44.dp)
                .pointerInput(parts, unplanned) {
                    // Tap a part of the bar to see how it is worked out.
                    detectTapGestures { tap ->
                        var x = 0f
                        val hit = parts.firstOrNull { part ->
                            val w = size.width * part.plannedMinor / scale
                            (tap.x >= x && tap.x < x + w).also { x += w }
                        }
                        brief = hit ?: freePart.takeIf { unplanned > 0 }
                    }
                }
                .semantics {
                contentDescription = parts.joinToString(". ") { "${it.name} ${m(it.plannedMinor)}" } + if (unplanned > 0) ". Not planned ${m(unplanned)}" else ""
            },
        ) {
            drawRoundRect(track, size = size, cornerRadius = CornerRadius(12.dp.toPx()))
            var x = 0f
            parts.forEach { part ->
                val w = size.width * part.plannedMinor / scale
                val used = w * (part.usedMinor.toFloat() / part.plannedMinor).coerceIn(0f, 1f)
                val shape = CornerRadius(10.dp.toPx())
                // Planned: a soft fill. Used so far: the solid part.
                drawRoundRect(part.color.copy(alpha = 0.38f), Offset(x, 0f), Size((w - 3f).coerceAtLeast(1f), size.height), shape)
                if (used > 0f) {
                    drawRoundRect(part.color, Offset(x, 0f), Size((used - 3f).coerceAtLeast(1f), size.height), shape)
                }
                // How much of the part is used so far (paid or spent), so the bar reads as progress even with no pay set.
                val usedPct = if (part.plannedMinor > 0) ((part.usedMinor * 100 + part.plannedMinor / 2) / part.plannedMinor) else 0
                val text = measurer.measure("${part.name.substringBefore(' ')} $usedPct%", labelStyle)
                val small = measurer.measure("$usedPct%", labelStyle)
                val shown = if (text.size.width + 12 < w) text else if (small.size.width + 8 < w) small else null
                shown?.let { drawText(it, topLeft = Offset(x + (w - it.size.width) / 2f, (size.height - it.size.height) / 2f)) }
                x += w
            }
            if (unplanned > 0 && x < size.width) {
                val text = measurer.measure("Free ${pct(unplanned)}%", labelStyle.copy(color = Color(0xFF8A8A93)))
                val w = size.width - x
                if (text.size.width + 12 < w) drawText(text, topLeft = Offset(x + (w - text.size.width) / 2f, (size.height - text.size.height) / 2f))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            parts.forEach { part ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable { brief = part }.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(part.color))
                    // What is left of each part, the way the group headers say it; un-marked is only spent.
                    val left = (part.plannedMinor - part.usedMinor).coerceAtLeast(0)
                    Text(
                        when (part.name) {
                            "Un-marked" -> "${part.name} ${short(part.usedMinor, currency)} spent"
                            "Goals" -> "${part.name} ${short(left, currency)} left to save of ${short(part.plannedMinor, currency)}"
                            else -> "${part.name} ${short(left, currency)} left of ${short(part.plannedMinor, currency)}"
                        },
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp))
                }
            }
            if (unplanned > 0) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable { brief = freePart }.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(track))
                    Text("Free ${short(unplanned, currency)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp))
                }
            }
        }
    }
    brief?.let { part ->
        AlertDialog(
            onDismissRequest = { brief = null },
            title = { Text(part.name) },
            text = { Text(part.brief, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { brief = null }) { Text("Got it") } },
        )
    }
}

@Composable
fun Swatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Group or account view. Small and out of the way. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSwitch(byAccount: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(40.dp)) {
        SegmentedButton(!byAccount, { onChange(false) }, SegmentedButtonDefaults.itemShape(0, 2), icon = {}) { Text("Groups", style = MaterialTheme.typography.labelLarge) }
        SegmentedButton(byAccount, { onChange(true) }, SegmentedButtonDefaults.itemShape(1, 2), icon = {}) { Text("Accounts", style = MaterialTheme.typography.labelLarge) }
    }
}

/** A group's name, what is left in it, and small add and menu buttons. */
@Composable
fun GroupHeader(section: HomeSection, tone: Color, currency: String, onAdd: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(tone))
        Text(section.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        if (section.plannedMinor > 0) {
            Text(
                "  ${short(section.leftMinor, currency)} left of ${short(section.plannedMinor, currency)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Add to ${section.name}", modifier = Modifier.size(20.dp))
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "${section.name} options", modifier = Modifier.size(20.dp))
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete group") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Account colours: each account keeps one colour everywhere, in a fixed order by id so it never changes. */
private val accountPalette = listOf(
    Color(0xFF6F8FCF), Color(0xFF4FA3A5), Color(0xFFC9A24B), Color(0xFFD27D8A), Color(0xFF8E7CC3), Color(0xFF6FAE87),
    Color(0xFFB07AA1), Color(0xFFCF8A66),
)

fun accountColors(accounts: List<Account>): Map<String, Color> =
    accounts.map { it.id }.distinct().sorted().mapIndexed { i, id -> id to accountPalette[i % accountPalette.size] }.toMap()

private val tickGreen = Color(0xFF3FA772)

/** What is in the account colours: a small key shown above the rings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountKey(accounts: List<Account>, colors: Map<String, Color>) {
    val shown = accounts.filter { it.active && !it.deleted && it.id in colors }
    if (shown.size < 2) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEach { account -> Swatch(colors.getValue(account.id), account.name) }
    }
}

/** A title with a small add button, for a row of rings. */
@Composable
fun SectionTitle(name: String, tone: Color, detail: String?, onAdd: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(tone))
        Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        Text(detail?.let { "  $it" }.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
        if (onAdd != null) {
            IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Add, contentDescription = "Add to $name", modifier = Modifier.size(22.dp)) }
        }
    }
}

/**
 * Items three to a row, with an Add tile at the end. With no items yet, a faded [sample] ring shows what one looks
 * like; tapping it adds a real one.
 */
@Composable
fun RingGrid(items: List<HomeItem>, colors: Map<String, Color>, currency: String, today: java.time.LocalDate, onItem: (HomeItem) -> Unit, onAdd: () -> Unit, sample: String? = null) {
    val cells: List<HomeItem?> = items + listOf(null)
    if (items.isEmpty() && sample != null) {
        SampleGrid(sample, currency, onAdd)
        return
    }
    Column(
        Modifier.fillMaxWidth().depth(RoundedCornerShape(22.dp)).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (item == null) AddTile(onClick = onAdd) else RingTile(item, colors, currency, today) { onItem(item) }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The ring: a flat, quiet track with a fine line of colour running round it for what is used, a share for each
 * account. No glow or bevel, so a page of rings reads like a clean chart. [segments] are colour and share of a full
 * circle, in order.
 */
@Composable
fun RingDisc(segments: List<Pair<Color, Float>>, track: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(74.dp)) {
            val line = 4.dp.toPx()
            val inset = line / 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawCircle(trackColor, radius = size.minDimension / 2 - inset, style = Stroke(line))
            var start = -90f
            segments.forEach { (color, share) ->
                val sweep = 360f * share
                val gap = if (segments.size > 1) 6f else 0f
                drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(1f), false, topLeft, arcSize, style = Stroke(line, cap = StrokeCap.Round))
                start += sweep
            }
        }
        content()
    }
}

/**
 * One item as a ring in the colours of the accounts that paid it: a share of the ring per account. Inside: what has
 * been spent from all accounts over what was set aside, or a green tick with the amount once it is paid. Just its
 * name underneath.
 */
@Composable
fun RingTile(item: HomeItem, colors: Map<String, Color>, currency: String, today: java.time.LocalDate, onClick: () -> Unit) {
    val ledger = LocalLedgerColors.current
    val over = item.kind == HomeItemKind.ENVELOPE && item.overMinor > 0
    val done = item.paid && !over
    val base = item.accountId?.let { colors[it] } ?: MaterialTheme.colorScheme.primary
    val segments = when {
        over -> listOf(ledger.negative to 1f)
        item.segments.isNotEmpty() -> item.segments.map { (account, share) -> (colors[account] ?: base) to share }
        done -> listOf(base to 1f)
        else -> emptyList()
    }
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(2.dp).width(112.dp)
            .semantics {
                contentDescription = "${item.name}. " + when {
                    done -> "Paid, ${MoneyFormat.format(item.spentMinor, currency)}"
                    else -> "${MoneyFormat.format(item.spentMinor, currency)} of ${MoneyFormat.format(item.plannedMinor, currency)}" +
                        if (item.kind == HomeItemKind.BILL && item.overdue) ", overdue" else ""
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RingDisc(segments, base.copy(alpha = 0.16f)) {
            if (done) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = tickGreen, modifier = Modifier.size(22.dp))
                    // What was actually paid, which can differ from the bill's usual amount.
                    Text(short(item.spentMinor, currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tickGreen, maxLines = 1)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        short(item.spentMinor, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (over) ledger.negative else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text("/ " + short(item.plannedMinor, currency), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Text(
            item.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (item.kind == HomeItemKind.BILL && item.overdue && !done) ledger.overdue else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AddTile(label: String = "Add", onClick: () -> Unit) {
    val line = MaterialTheme.colorScheme.outline
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(2.dp).width(112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(84.dp)) {
                val stroke = 2.dp.toPx()
                drawCircle(line, radius = size.minDimension / 2 - stroke - 6.dp.toPx(), style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** An empty section: one faded example ring and the Add tile, so the layout is clear before anything is entered. */
@Composable
private fun SampleGrid(name: String, currency: String, onAdd: () -> Unit, addLabel: String = "Add") {
    Row(Modifier.fillMaxWidth().depth(RoundedCornerShape(22.dp)).padding(vertical = 12.dp, horizontal = 4.dp)) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { SampleTile(name, currency, onAdd) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { AddTile(addLabel, onAdd) }
        Spacer(Modifier.weight(1f))
    }
}

/** A faded ring at zero, standing in for the first real item. */
@Composable
private fun SampleTile(name: String, currency: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(2.dp)
            .width(112.dp)
            .alpha(0.5f)
            .semantics { contentDescription = "Example: $name. Tap to add your own." },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RingDisc(emptyList(), Color.Transparent) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(short(0, currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("/ " + short(0, currency), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** The example name for an empty group, from what the group is called. */
fun sampleFor(groupName: String): String {
    val name = groupName.lowercase()
    return when {
        "subscri" in name -> "Streaming"
        "monthly" in name || "bill" in name -> "Rent"
        "saving" in name || "goal" in name -> "Emergency fund"
        else -> "Example"
    }
}

/**
 * Accounts as rings, the same as the groups: the arc is what is planned out of the account, in its colour; inside, what
 * is free over its balance. Tap one for its transactions; the last tile adds an account.
 */
@Composable
fun AccountRings(
    rows: List<com.ledgerline.app.finance.envelopes.AccountPlan>,
    holds: Map<String, List<Hold>>,
    colors: Map<String, Color>,
    currency: String,
    onAccount: (String) -> Unit,
    onAdd: () -> Unit,
) {
    if (rows.isEmpty()) {
        // No accounts yet: one example bank at zero, so the layout is clear.
        SampleGrid("My bank", currency, onAdd, addLabel = "Add account")
        return
    }
    val ledger = LocalLedgerColors.current
    val cells: List<com.ledgerline.app.finance.envelopes.AccountPlan?> = rows + listOf(null)
    Column(
        Modifier.fillMaxWidth().depth(RoundedCornerShape(22.dp)).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.chunked(3).forEach { chunk ->
            Row(Modifier.fillMaxWidth()) {
                chunk.forEach { row ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (row == null) {
                            AddTile("Add account", onAdd)
                        } else if (com.ledgerline.app.finance.balance.BalanceMath.isDebtType(row.account.type)) {
                            DebtRing(row, colors[row.account.id] ?: MaterialTheme.colorScheme.primary, currency) { onAccount(row.account.id) }
                        } else {
                            val tone = colors[row.account.id] ?: MaterialTheme.colorScheme.primary
                            val short = row.freeMinor < 0
                            val spoken = if (row.balanceMinor > 0) (row.spokenForMinor.toFloat() / row.balanceMinor).coerceIn(0f, 1f) else 1f
                            Column(
                                Modifier.clip(RoundedCornerShape(16.dp)).clickable { onAccount(row.account.id) }.padding(2.dp).width(112.dp).semantics {
                                    contentDescription = "${row.account.name}. ${MoneyFormat.format(row.balanceMinor, currency)}. " +
                                        if (short) "Short ${MoneyFormat.format(-row.freeMinor, currency)}" else "${MoneyFormat.format(row.freeMinor, currency)} free"
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // The ring is split the way the balance is held: each bill, item, and saving in its colour.
                                val parts = holds[row.account.id].orEmpty().filter { it.amountMinor > 0 && it.name != "Free" }
                                val whole = maxOf(row.balanceMinor, parts.sumOf { it.amountMinor }, 1L).toFloat()
                                RingDisc(
                                    if (short) listOf(ledger.negative to 1f) else parts.map { it.color to it.amountMinor / whole }.ifEmpty { listOf(tone to spoken).filter { it.second > 0f } },
                                    tone.copy(alpha = 0.16f),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            (if (short) "−" else "") + short(kotlin.math.abs(row.freeMinor), currency),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (short) ledger.negative else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                        )
                                        Text("/ " + short(row.balanceMinor, currency), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                                Text(row.account.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A card or loan as a ring: inside, what is owed; with a credit limit, the arc is how much of the limit is used, in
 * red. It is shown for reference and is not counted in "free until payday".
 */
@Composable
private fun DebtRing(row: com.ledgerline.app.finance.envelopes.AccountPlan, tone: Color, currency: String, onClick: () -> Unit) {
    val ledger = LocalLedgerColors.current
    val owed = (-row.balanceMinor).coerceAtLeast(0)
    val limit = row.account.creditLimitMinor?.takeIf { it > 0 }
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(2.dp).width(112.dp).semantics {
            contentDescription = "${row.account.name}. ${MoneyFormat.format(owed, currency)} owed" + (limit?.let { " of ${MoneyFormat.format(it, currency)} limit" } ?: "")
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RingDisc(
            when {
                limit != null && owed > 0 -> listOf(ledger.negative to (owed.toFloat() / limit).coerceIn(0.02f, 1f))
                owed > 0 -> listOf(tone to 1f)
                else -> emptyList()
            },
            tone.copy(alpha = 0.16f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(short(owed, currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (owed > 0) ledger.negative else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(limit?.let { "/ " + short(it, currency) } ?: "owed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text(row.account.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** A short list of transactions in a sheet: an item's, an account's, or spending outside every item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsSheet(
    title: String,
    subtitle: String?,
    rows: List<com.ledgerline.app.domain.model.LedgerTransaction>,
    accounts: List<Account>,
    categories: List<com.ledgerline.app.domain.model.Category>,
    onEdit: (() -> Unit)?,
    onPaid: (() -> Unit)?,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (onPaid != null) {
                    FilledTonalButton(onClick = onPaid, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(36.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paid", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit $title") }
                }
            }
            if (rows.isEmpty()) {
                Text("Nothing recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).depth(RoundedCornerShape(20.dp)).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    rows.forEach { tx -> com.ledgerline.app.ui.transactions.TransactionRow(tx, accounts, categories) { onOpen(tx.id) } }
                }
            }
        }
    }
}

/** One thing due today, for the small strip at the top of Home. */
data class TodayLine(
    val name: String,
    val detail: String,
    val signedMinor: Long,
    val overdue: Boolean = false,
    /** Marks it paid, the same as Paid on the calendar. Null for money coming in. */
    val onPaid: (() -> Unit)? = null,
)

/**
 * Today's summary at the top of Home: how many payments are scheduled today and their total, then each one. A long
 * list scrolls inside the card instead of pushing the page down.
 */
@Composable
fun TodayStrip(lines: List<TodayLine>, currency: String) {
    val ledger = LocalLedgerColors.current
    val out = lines.filter { it.signedMinor < 0 }.sumOf { -it.signedMinor }
    Column(
        Modifier.fillMaxWidth().depth(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Today's summary", style = MaterialTheme.typography.titleLarge)
        if (lines.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = ledger.positive, modifier = Modifier.size(22.dp))
                Text("No payments scheduled today", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            Text(
                (if (lines.size == 1) "1 payment scheduled today" else "${lines.size} payments scheduled today") +
                    if (out > 0) " · ${MoneyFormat.format(out, currency)}" else "",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // The first two, then "+N more" that opens (and closes) the rest, so the summary stays small.
            var more by remember { mutableStateOf(false) }
            val shown = if (more) lines else lines.take(2)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (line.signedMinor > 0) ledger.positive else if (line.overdue) ledger.overdue else MaterialTheme.colorScheme.primary))
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(line.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (line.detail.isNotBlank()) Text(line.detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text(
                            MoneyFormat.signedLabel(line.signedMinor, currency),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (line.signedMinor > 0) ledger.positive else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                        line.onPaid?.let { pay ->
                            FilledTonalButton(onClick = pay, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.padding(start = 8.dp).height(32.dp)) {
                                Text("Paid", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (lines.size > 2) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).clickable { more = !more }.background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (more) "Show less" else "+${lines.size - 2} more",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Icon(
                            if (more) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Add or change one bill or spending item. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemSheet(
    edit: ItemEdit,
    error: String?,
    groups: List<HomeGroup>,
    accounts: List<Account>,
    currency: String,
    onChange: ((ItemEdit) -> ItemEdit) -> Unit,
    onAmount: (String) -> Unit,
    onSave: () -> Unit,
    onPaid: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val symbol = remember(currency) { runCatching { java.util.Currency.getInstance(currency).getSymbol(Locale.getDefault()) }.getOrDefault(currency) }
    var confirmDelete by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(edit.isNew) }
    val live = accounts.filter { it.active && !it.deleted }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (edit.isNew) "Add" else edit.name, style = MaterialTheme.typography.titleLarge)
            if (edit.isNew) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(40.dp)) {
                    SegmentedButton(edit.kind == HomeItemKind.BILL, { onChange { it.copy(kind = HomeItemKind.BILL) } }, SegmentedButtonDefaults.itemShape(0, 2), icon = {}) {
                        Text("Fixed bill")
                    }
                    SegmentedButton(edit.kind == HomeItemKind.ENVELOPE, { onChange { it.copy(kind = HomeItemKind.ENVELOPE) } }, SegmentedButtonDefaults.itemShape(1, 2), icon = {}) {
                        Text("Spending")
                    }
                }
                val names = when {
                    edit.kind == HomeItemKind.ENVELOPE -> ItemPresets.spending
                    edit.groupId == com.ledgerline.app.finance.envelopes.HomeSections.SUBSCRIPTIONS -> ItemPresets.subscriptions
                    else -> ItemPresets.bills + ItemPresets.subscriptions.take(2)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { name -> SuggestionChip(onClick = { onChange { it.copy(name = name) } }, label = { Text(name) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = edit.name,
                    onValueChange = { text -> onChange { it.copy(name = text) } },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1.4f).onFocusChanged { if (it.isFocused) keypad = false },
                )
                com.ledgerline.app.ui.components.AmountBox(
                    text = edit.amountText,
                    symbol = symbol,
                    label = if (edit.kind == HomeItemKind.BILL) "Each month" else "Each pay",
                    open = keypad,
                    onOpen = { keypad = !keypad },
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
            }
            if (keypad) com.ledgerline.app.ui.components.AmountKeypad(edit.amountText, onAmount, onDone = { keypad = false })
            if (!edit.isNew) {
                // A changed amount: just for the cycle on screen, or the usual amount from now on.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Amount for", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(edit.thisCycleOnly, { onChange { it.copy(thisCycleOnly = true) } }, { Text("This cycle only") })
                    FilterChip(!edit.thisCycleOnly, { onChange { it.copy(thisCycleOnly = false) } }, { Text("All cycles") })
                }
                edit.usualMinor?.let { usual ->
                    Text(
                        "Usually ${MoneyFormat.format(usual, currency)}" + if (edit.thisCycleOnly) " · other cycles stay the same" else " · this becomes the usual amount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (edit.kind == HomeItemKind.ENVELOPE || (edit.kind == HomeItemKind.BILL && !edit.isNew)) {
                // How long it runs: every cycle, a number of cycles (or payments), or until a date.
                if (edit.kind == HomeItemKind.ENVELOPE) {
                    com.ledgerline.app.ui.components.ScheduleEndPicker(edit.end, edit.cycleStart ?: java.time.LocalDate.now(), unit = "cycles") { e -> onChange { it.copy(end = e) } }
                } else {
                    com.ledgerline.app.ui.components.ScheduleEndPicker(edit.end, edit.nextDue ?: java.time.LocalDate.now()) { e -> onChange { it.copy(end = e) } }
                }
            }
            if (edit.kind == HomeItemKind.BILL && edit.isNew) {
                com.ledgerline.app.ui.components.RepeatEveryPicker(edit.frequency) { f -> onChange { it.copy(frequency = f) } }
                // The first due date on a calendar; it repeats from there. (A typed day number could not be cleared to
                // type a new one, so "10" could only become "12", never "22".)
                val first = edit.firstDue ?: java.time.LocalDate.now()
                var picking by remember { mutableStateOf(false) }
                com.ledgerline.app.ui.components.FieldRow(
                    label = "First due",
                    value = first.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", java.util.Locale.getDefault())) +
                        if (edit.frequency == com.ledgerline.app.domain.model.RecurrenceFrequency.MONTHLY) " · then the ${com.ledgerline.app.ui.transactions.ordinal(first.dayOfMonth)} each month" else "",
                ) { picking = true }
                if (picking) {
                    com.ledgerline.app.ui.transactions.DatePickerDialogOnly(first, onDismiss = { picking = false }) { d ->
                        picking = false
                        onChange { it.copy(firstDue = d, day = d.dayOfMonth) }
                    }
                }
                com.ledgerline.app.ui.components.ScheduleEndPicker(edit.end, first, edit.frequency) { e -> onChange { it.copy(end = e) } }
            }
            Text(if (edit.kind == HomeItemKind.BILL) "Paid from" else "Kept in", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (edit.kind == HomeItemKind.ENVELOPE) FilterChip(edit.accountId == null, { onChange { it.copy(accountId = null) } }, { Text("Any") })
                live.forEach { account -> FilterChip(edit.accountId == account.id, { onChange { it.copy(accountId = account.id) } }, { Text(account.name) }) }
            }
            if (groups.size > 1) {
                Text("Group", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { group -> FilterChip(edit.groupId == group.id, { onChange { it.copy(groupId = group.id) } }, { Text(group.name) }) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!edit.isNew) {
                    TextButton(onClick = { confirmDelete = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    if (edit.recurringId != null) TextButton(onClick = onMore, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Date & more") }
                }
                Spacer(Modifier.weight(1f))
                if (edit.recurringId != null && edit.nextDue != null) {
                    FilledTonalButton(onClick = onPaid, contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paid", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Button(onClick = onSave, contentPadding = PaddingValues(horizontal = 22.dp)) { Text("Save") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${edit.name}?") },
            text = {
                Text(
                    if (edit.kind == HomeItemKind.BILL) "It comes off your calendar. Payments already recorded stay."
                    else "It stops being planned. Spending already recorded stays.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Sets what you get each pay. */
@Composable
fun PayDialog(text: String, currency: String, onText: (String) -> Unit, onCalendar: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    val symbol = remember(currency) { runCatching { java.util.Currency.getInstance(currency).getSymbol(Locale.getDefault()) }.getOrDefault(currency) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay this cycle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.ledgerline.app.ui.components.AmountBox(text, symbol, "Pay", open = true, onOpen = {}, modifier = Modifier.fillMaxWidth())
                com.ledgerline.app.ui.components.AmountKeypad(text, onText, onDone = onSave)
                TextButton(onClick = onCalendar, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Use income from my calendar") }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A one-field dialog for naming a group. */
@Composable
fun GroupNameDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("e.g. Car, Kids, Fun") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A one-line alert: an icon, a short message, and its action. Takes little room. */
@Composable
fun CompactAlert(message: String, action: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().depthTinted(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp), elevation = 2.dp).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** One part of an account's balance: what it is set aside for, or free. */
data class Hold(
    val name: String,
    val amountMinor: Long,
    val color: Color,
    /** Shown when you tap the hold: what it is and how it is worked out. */
    val lines: List<String> = emptyList(),
)

/**
 * An account: its transactions, or what its balance holds (bills still to pay from it, money left in the items kept
 * in it, savings, and what is free), as one bar with the parts listed under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    title: String,
    subtitle: String?,
    holds: List<Hold>,
    rows: List<com.ledgerline.app.domain.model.LedgerTransaction>,
    accounts: List<Account>,
    categories: List<com.ledgerline.app.domain.model.Category>,
    currency: String,
    onEdit: () -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showHolds by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit $title") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(40.dp)) {
                SegmentedButton(!showHolds, { showHolds = false }, SegmentedButtonDefaults.itemShape(0, 2), icon = {}) { Text("Transactions") }
                SegmentedButton(showHolds, { showHolds = true }, SegmentedButtonDefaults.itemShape(1, 2), icon = {}) { Text("Holds") }
            }
            if (showHolds) {
                val total = holds.sumOf { it.amountMinor.coerceAtLeast(0) }.coerceAtLeast(1)
                Column(Modifier.fillMaxWidth().depth(RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                        drawRoundRect(Color.Gray.copy(alpha = 0.15f), size = size, cornerRadius = CornerRadius(9.dp.toPx()))
                        var x = 0f
                        holds.filter { it.amountMinor > 0 }.forEach { hold ->
                            val w = size.width * hold.amountMinor / total
                            drawRoundRect(hold.color, Offset(x, 0f), Size((w - 2f).coerceAtLeast(1f), size.height), CornerRadius(6.dp.toPx()))
                            x += w
                        }
                    }
                    holds.forEach { hold ->
                        // Tap a hold for what it is and how it is worked out.
                        var open by remember(hold.name) { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = hold.lines.isNotEmpty()) { open = !open }.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(hold.color))
                                Text(hold.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).weight(1f))
                                Text(
                                    MoneyFormat.format(hold.amountMinor, currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (hold.amountMinor < 0) LocalLedgerColors.current.negative else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                )
                                if (hold.lines.isNotEmpty()) {
                                    Icon(
                                        if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = if (open) "Hide details" else "Show details",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (open) {
                                hold.lines.forEach { line ->
                                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 2.dp))
                                }
                            }
                        }
                    }
                }
            } else if (rows.isEmpty()) {
                Text("Nothing recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).depth(RoundedCornerShape(20.dp)).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    rows.forEach { tx -> com.ledgerline.app.ui.transactions.TransactionRow(tx, accounts, categories) { onOpen(tx.id) } }
                }
            }
        }
    }
}

/** What an account's balance is set aside for: each unpaid bill from it, what is left in items kept in it, savings, then free. */
fun accountHolds(plan: PayPlan, accountId: String): List<Hold> {
    val row = plan.accounts.firstOrNull { it.account.id == accountId } ?: return emptyList()
    val billShades = listOf(0xFF6366F1, 0xFF818CF8, 0xFF4338CA, 0xFFA5B4FC, 0xFF3730A3, 0xFF6D7BF0)
    val itemShades = listOf(0xFF4FA3A5, 0xFFCF8A66, 0xFF8E7CC3, 0xFFD27D8A, 0xFFB07AA1, 0xFFC9A24B)
    val c = row.account.currency
    fun m(minor: Long) = MoneyFormat.format(minor, c)
    return buildList {
        plan.bills?.bills.orEmpty().filter { !it.paid && it.accountId == accountId }.groupBy { it.name }.entries.forEachIndexed { i, (name, bills) ->
            add(
                Hold(
                    name, bills.sumOf { it.amountMinor }, Color(billShades[i % billShades.size]),
                    listOf("Bill not paid yet, paid from this account.") + bills.sortedBy { it.date }.map { bill ->
                        "Due ${bill.date.medium()} · ${m(bill.amountMinor)}" + if (bill.overdue) " · overdue" else ""
                    },
                ),
            )
        }
        row.envelopes.filter { it.leftMinor > 0 }.forEachIndexed { i, envelope ->
            val saving = envelope.kind == com.ledgerline.app.finance.envelopes.EnvelopeKind.SAVINGS
            val tone = if (saving) 0xFF6FAE87 else itemShades[i % itemShades.size]
            add(
                Hold(
                    if (saving) "To save: ${envelope.name}" else envelope.name, envelope.leftMinor, Color(tone),
                    if (saving) listOf("Still to put aside this cycle for the goal.", "Planned ${m(envelope.plannedMinor)} · put aside ${m(envelope.spentMinor)}.")
                    else listOf("Left of what is planned for this item this cycle.", "Planned ${m(envelope.plannedMinor)} · spent ${m(envelope.spentMinor)} · left ${m(envelope.leftMinor)}."),
                ),
            )
        }
        row.savedGoals.forEach { (name, saved) ->
            add(
                Hold(
                    "Saved: $name", saved, Color(0xFF6FAE87),
                    listOf("Already saved for this goal and still in this account.", "It stays held until you spend it on the goal or mark it used for something else (open the goal)."),
                ),
            )
        }
        add(
            Hold(
                if (row.freeMinor < 0) "Short" else "Free", row.freeMinor, Color(0xFF9CCFAE),
                listOf(
                    "Balance ${m(row.balanceMinor)}",
                    "− bills ${m(row.billsDueMinor)} − items left ${m(row.envelopesLeftMinor.coerceAtLeast(0))} − to save ${m(row.savingsDueMinor)} − saved for goals ${m(row.goalsSavedMinor)}",
                    "= ${m(row.freeMinor)} " + if (row.freeMinor < 0) "short" else "with no job yet",
                ),
            ),
        )
    }
}
