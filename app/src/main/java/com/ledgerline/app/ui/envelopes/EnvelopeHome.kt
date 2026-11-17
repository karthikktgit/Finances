package com.ledgerline.app.ui.envelopes

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerline.app.finance.envelopes.AccountPlan
import com.ledgerline.app.finance.envelopes.Envelope
import com.ledgerline.app.finance.envelopes.EnvelopeBill
import com.ledgerline.app.finance.envelopes.EnvelopeHealth
import com.ledgerline.app.finance.envelopes.EnvelopeKind
import com.ledgerline.app.finance.envelopes.PayPlan
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.theme.LocalLedgerColors

/**
 * Colours for envelopes, picked so neighbours on the pay bar never look alike. Bills and savings always keep the
 * same colour, so they are recognisable at a glance; spending envelopes take the rest in order.
 */
private val billsColor = Color(0xFF6F8FCF)
private val savingsColor = Color(0xFF6FAE87)
private val spendingColors = listOf(
    Color(0xFF4FA3A5),
    Color(0xFFCF8A66),
    Color(0xFF8E7CC3),
    Color(0xFFD27D8A),
    Color(0xFFB07AA1),
    Color(0xFFC9A24B),
)

/** The colour each envelope wears everywhere on Home: the pay bar, its card, and the account view. */
fun envelopeColors(plan: PayPlan): Map<String, Color> {
    // Spending envelopes take colours in the order they were made (by id), not by size, so an envelope keeps its
    // colour when you change its amount.
    val order = plan.envelopes.filter { it.kind == EnvelopeKind.SPENDING }.map { it.id }.sorted()
    return plan.envelopes.associate { envelope ->
        envelope.id to when (envelope.kind) {
            EnvelopeKind.BILLS -> billsColor
            EnvelopeKind.SAVINGS -> savingsColor
            EnvelopeKind.SPENDING -> spendingColors[order.indexOf(envelope.id).coerceAtLeast(0) % spendingColors.size]
        }
    }
}

/**
 * The top of Home: your pay, and where each part of it went. One bar shows every envelope's share, darker for what is
 * already used and lighter for what is left, with the part that has no job yet at the end. Then the three numbers
 * that matter: spent, still to pay, and left to spend.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayHero(plan: PayPlan, currency: String, onPlan: () -> Unit) {
    val colors = LocalLedgerColors.current
    val palette = envelopeColors(plan)
    val scale = maxOf(plan.incomeMinor, plan.plannedMinor, 1L).toFloat()
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val outsideColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .depth(RoundedCornerShape(28.dp))
            .clickable(onClick = onPlan)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Your pay · ${plan.cycle.start.medium()} – ${plan.cycle.end.medium()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (plan.incomeMinor > 0) MoneyFormat.format(plan.incomeMinor, currency) else "No pay set",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${plan.daysLeft}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(if (plan.daysLeft == 1L) "day to payday" else "days to payday", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // The pay bar.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .semantics {
                    contentDescription = plan.envelopes.joinToString(". ") { "${it.name} ${MoneyFormat.format(it.plannedMinor, currency)}, ${MoneyFormat.format(it.spentMinor, currency)} used" } +
                        if (plan.unassignedMinor > 0) ". ${MoneyFormat.format(plan.unassignedMinor, currency)} with no job" else ""
                },
        ) {
            val radius = CornerRadius(size.height / 2.6f)
            drawRoundRect(empty, size = size, cornerRadius = radius)
            var x = 0f
            val gap = 3f
            plan.envelopes.filter { it.plannedMinor > 0 }.forEach { envelope ->
                val width = size.width * envelope.plannedMinor / scale
                val tone = palette[envelope.id] ?: billsColor
                val used = width * envelope.fraction
                drawRoundRect(tone.copy(alpha = 0.28f), topLeft = Offset(x, 0f), size = Size((width - gap).coerceAtLeast(1f), size.height), cornerRadius = CornerRadius(6f))
                if (used > 0f) drawRoundRect(tone, topLeft = Offset(x, 0f), size = Size((used - gap).coerceAtLeast(1f), size.height), cornerRadius = CornerRadius(6f))
                x += width
            }
            // Spending outside every envelope still comes out of the pay, so it takes its share of what had no job.
            if (plan.outsideMinor > 0 && x < size.width) {
                val width = (size.width * plan.outsideMinor / scale).coerceAtMost(size.width - x)
                drawRoundRect(outsideColor, topLeft = Offset(x, 0f), size = Size(width.coerceAtLeast(1f), size.height), cornerRadius = CornerRadius(6f))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            plan.envelopes.filter { it.plannedMinor > 0 }.forEach { envelope ->
                Swatch(palette[envelope.id] ?: billsColor, "${envelope.name} ${MoneyFormat.format(envelope.plannedMinor, currency)}")
            }
            if (plan.outsideMinor > 0) Swatch(outsideColor, "Spent outside envelopes ${MoneyFormat.format(plan.outsideMinor, currency)}")
            if (plan.unassignedMinor - plan.outsideMinor > 0) Swatch(empty, "No job yet ${MoneyFormat.format(plan.unassignedMinor - plan.outsideMinor, currency)}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStat("Spent", MoneyFormat.format(plan.spentMinor, currency), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            HeroStat("Still to pay", MoneyFormat.format(plan.stillToPayMinor, currency), if (plan.stillToPayMinor > 0) colors.negative else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            HeroStat(
                "Left of pay",
                MoneyFormat.format(plan.leftOfPayMinor, currency),
                if (plan.leftOfPayMinor < 0) colors.negative else colors.positive,
                Modifier.weight(1f),
            )
        }
        val (tone, line) = when {
            plan.incomeMinor <= 0 -> MaterialTheme.colorScheme.primary to "Add your pay and split it into envelopes ›"
            plan.unassignedMinor < 0 -> colors.negative to "You planned ${MoneyFormat.format(-plan.unassignedMinor, currency)} more than you get. Trim an envelope ›"
            plan.unassignedMinor > 0 && plan.hasEnvelopes -> colors.caution to "${MoneyFormat.format(plan.unassignedMinor, currency)} has no job yet. Give it one ›"
            plan.unassignedMinor > 0 -> MaterialTheme.colorScheme.primary to "Split your pay: groceries, gas, fun… ›"
            else -> colors.positive to "Every dollar has a job ✓"
        }
        Text(
            line,
            style = MaterialTheme.typography.labelLarge,
            color = tone,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tone.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Swatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp))
    }
}

/** Envelopes or accounts: the same money, grouped by what it is for or where it sits. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GroupingToggle(byAccount: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !byAccount, onClick = { onChange(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("By envelope") }
        SegmentedButton(selected = byAccount, onClick = { onChange(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("By account") }
    }
}

/**
 * One spending envelope. What is left is the big number, because that is what you can still do something about;
 * the bar shows what is used, and the thin mark shows where you would be by today if it lasted evenly to payday.
 */
@Composable
fun EnvelopeCard(envelope: Envelope, tone: Color, accountName: String?, currency: String, onClick: () -> Unit) {
    val colors = LocalLedgerColors.current
    val health = envelope.health
    val leftTint = when (health) {
        EnvelopeHealth.OVER -> colors.negative
        EnvelopeHealth.FAST -> colors.caution
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier
            .fillMaxWidth()
            .depth(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(tone, CircleShape))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(envelope.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${MoneyFormat.format(envelope.spentMinor, currency)} of ${MoneyFormat.format(envelope.plannedMinor, currency)} · " + (accountName ?: "any account"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    MoneyFormat.format(if (envelope.leftMinor < 0) envelope.overMinor else envelope.leftMinor, currency),
                    style = MaterialTheme.typography.titleLarge,
                    color = leftTint,
                )
                Text(if (envelope.leftMinor < 0) "over" else "left", style = MaterialTheme.typography.labelSmall, color = leftTint)
            }
        }
        FillBar(
            fraction = envelope.fraction,
            marker = if (envelope.plannedMinor > 0) (envelope.expectedByTodayMinor.toFloat() / envelope.plannedMinor).coerceIn(0f, 1f) else null,
            tone = if (health == EnvelopeHealth.OVER) colors.negative else tone,
        )
        Text(
            when (health) {
                EnvelopeHealth.OVER -> "Over by ${MoneyFormat.format(envelope.overMinor, currency)}. It came out of money meant for something else."
                EnvelopeHealth.FAST -> "Going faster than the cycle. Keep to ${MoneyFormat.format(envelope.perDayMinor, currency)} a day to make it to payday."
                EnvelopeHealth.EMPTY_PLAN -> "Nothing put in yet. Tap to give it money."
                else -> if (envelope.spentMinor == 0L) {
                    "Untouched. About ${MoneyFormat.format(envelope.perDayMinor, currency)} a day for ${envelope.daysLeft} days."
                } else {
                    "On track. About ${MoneyFormat.format(envelope.perDayMinor, currency)} a day for ${envelope.daysLeft} days."
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = when (health) {
                EnvelopeHealth.OVER -> colors.negative
                EnvelopeHealth.FAST -> colors.caution
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** A rounded fill with an optional thin mark for where you should be by today. */
@Composable
private fun FillBar(fraction: Float, marker: Float?, tone: Color) {
    val track = tone.copy(alpha = 0.16f)
    val markColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, size = size, cornerRadius = r)
        if (fraction > 0f) drawRoundRect(tone, size = Size((size.width * fraction).coerceAtLeast(size.height), size.height), cornerRadius = r)
        marker?.let { m ->
            val x = size.width * m
            drawLine(markColor.copy(alpha = 0.55f), Offset(x, -3f), Offset(x, size.height + 3f), strokeWidth = 3f)
        }
    }
}

/** The bills envelope: what this cycle's bills add up to, which are paid, and a tap to mark the rest. */
@Composable
fun BillsCard(
    envelope: Envelope,
    currency: String,
    accountNames: Map<String, String>,
    onPaid: (EnvelopeBill) -> Unit,
    onOpen: (EnvelopeBill) -> Unit,
    onCalendar: () -> Unit,
) {
    val colors = LocalLedgerColors.current
    val unpaid = envelope.bills.count { !it.paid }
    Column(
        Modifier
            .fillMaxWidth()
            .depth(RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(billsColor, CircleShape))
            Text("Bills", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Text(
                "Calendar ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCalendar).padding(4.dp),
            )
        }
        Text(
            if (unpaid == 0) "All paid" else "${MoneyFormat.format(envelope.leftMinor, currency)} still to pay",
            style = MaterialTheme.typography.headlineSmall,
            color = if (unpaid == 0) colors.positive else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "${MoneyFormat.format(envelope.spentMinor, currency)} of ${MoneyFormat.format(envelope.plannedMinor, currency)} paid · " +
                "$unpaid of ${envelope.bills.size} left",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FillBar(envelope.fraction, marker = null, tone = billsColor)
        var all by rememberSaveable { mutableStateOf(false) }
        val shown = if (all) envelope.bills else envelope.bills.take(4)
        shown.forEach { bill ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(bill) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(bill.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            when {
                                bill.paid -> "Paid"
                                bill.overdue -> "Overdue since ${bill.date.medium()}"
                                else -> "Due ${bill.date.medium()}"
                            },
                            bill.accountId?.let { accountNames[it] },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bill.overdue && !bill.paid) colors.overdue else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    MoneyFormat.format(bill.amountMinor, currency),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (bill.paid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (bill.paid) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Paid", tint = colors.positive, modifier = Modifier.padding(horizontal = 14.dp))
                } else if (bill.recurringId != null) {
                    FilledTonalButton(onClick = { onPaid(bill) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Paid") }
                }
            }
        }
        if (envelope.bills.size > 4) {
            Text(
                if (all) "Show fewer" else "Show all ${envelope.bills.size} bills",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = !all }.padding(vertical = 6.dp),
            )
        }
    }
}

/** A savings goal's share of this pay: what is still to put aside, and how far the goal has come overall. */
@Composable
fun SavingsEnvelopeCard(envelope: Envelope, progress: Float, accountName: String?, currency: String, onClick: () -> Unit) {
    val colors = LocalLedgerColors.current
    val done = envelope.leftMinor <= 0
    Column(
        Modifier
            .fillMaxWidth()
            .depth(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(savingsColor, CircleShape))
            Text(envelope.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1)
            Text("${(progress * 100).toInt()}% of goal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (done) "Saved for this cycle ✓" else "${MoneyFormat.format(envelope.leftMinor, currency)} to put aside",
            style = MaterialTheme.typography.headlineSmall,
            color = if (done) colors.positive else MaterialTheme.colorScheme.onSurface,
        )
        FillBar(envelope.fraction, marker = null, tone = savingsColor)
        Text(
            "Pay yourself first: ${MoneyFormat.format(envelope.plannedMinor, currency)} this cycle" + (accountName?.let { ", kept in $it" } ?: "") + ".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Spending that landed outside every envelope, so it is visible instead of quietly eating into the plan. */
@Composable
fun OutsideCard(plan: PayPlan, currency: String, onOpen: () -> Unit) {
    val colors = LocalLedgerColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.caution.copy(alpha = 0.10f))
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Not in any envelope", style = MaterialTheme.typography.titleSmall)
            Text(
                "${plan.outsideCount} ${if (plan.outsideCount == 1) "spend" else "spends"} this cycle. Add an envelope for them, or an \"Everyday spending\" one to catch the rest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(MoneyFormat.format(plan.outsideMinor, currency), style = MaterialTheme.typography.titleMedium, color = colors.caution, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * One account as the plan sees it. The bar is the balance, split into what is spoken for (bills, envelopes,
 * savings) and what is truly free, so you can tell at a glance whether this account can cover what you gave it.
 */
@Composable
fun AccountPlanCard(row: AccountPlan, palette: Map<String, Color>, currency: String, onClick: () -> Unit) {
    val colors = LocalLedgerColors.current
    val short = row.freeMinor < 0
    val free = colors.positive
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        Modifier
            .fillMaxWidth()
            .depth(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.account.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(MoneyFormat.format(row.balanceMinor, currency), style = MaterialTheme.typography.titleMedium)
        }
        val parts = buildList {
            if (row.billsDueMinor > 0) add(billsColor to row.billsDueMinor)
            row.envelopes.filter { it.leftMinor > 0 }.forEach { add((palette[it.id] ?: savingsColor) to it.leftMinor) }
            if (row.freeMinor > 0) add(free.copy(alpha = 0.55f) to row.freeMinor)
        }
        val whole = maxOf(row.balanceMinor, row.spokenForMinor, 1L).toFloat()
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawRoundRect(empty, size = size, cornerRadius = CornerRadius(size.height / 2))
            var x = 0f
            parts.forEach { (tone, minor) ->
                val w = size.width * minor / whole
                drawRoundRect(tone, topLeft = Offset(x, 0f), size = Size((w - 2f).coerceAtLeast(1f), size.height), cornerRadius = CornerRadius(5f))
                x += w
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (row.billsDueMinor > 0) Swatch(billsColor, "Bills ${MoneyFormat.format(row.billsDueMinor, currency)}")
            if (row.envelopesLeftMinor > 0) Swatch(MaterialTheme.colorScheme.onSurfaceVariant, "Envelopes ${MoneyFormat.format(row.envelopesLeftMinor, currency)}")
            if (row.savingsDueMinor > 0) Swatch(savingsColor, "Saving ${MoneyFormat.format(row.savingsDueMinor, currency)}")
        }
        Text(
            when {
                short -> "Short ${MoneyFormat.format(-row.freeMinor, currency)}" + (row.nextDue?.let { " before ${it.name}, ${it.date.medium()}" } ?: "")
                else -> "${MoneyFormat.format(row.freeMinor, currency)} free"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (short) colors.negative else free,
        )
        if (row.envelopes.isNotEmpty()) {
            Text(
                row.envelopes.joinToString("  ·  ") { e ->
                    "${e.name} ${if (e.leftMinor < 0) "over" else MoneyFormat.format(e.leftMinor, currency) + " left"}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The account view's opening line: everything together, including envelopes not tied to one account. */
@Composable
fun AccountsOverall(plan: PayPlan, currency: String) {
    val colors = LocalLedgerColors.current
    val total = plan.countedAccounts.sumOf { it.balanceMinor }
    val free = plan.freeOverallMinor
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background((if (free < 0) colors.negative else colors.positive).copy(alpha = 0.10f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Spending accounts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(MoneyFormat.format(total, currency), style = MaterialTheme.typography.headlineSmall)
        Text(
            if (free < 0) "${MoneyFormat.format(-free, currency)} short until payday"
            else "${MoneyFormat.format(free, currency)} free until payday",
            style = MaterialTheme.typography.titleSmall,
            color = if (free < 0) colors.negative else colors.positive,
        )
        if (plan.anyAccountLeftMinor > 0) {
            Text(
                "Includes ${MoneyFormat.format(plan.anyAccountLeftMinor, currency)} planned without an account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
