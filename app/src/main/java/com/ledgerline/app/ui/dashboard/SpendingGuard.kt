package com.ledgerline.app.ui.dashboard

import com.ledgerline.app.ui.theme.depth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ledgerline.app.finance.forecast.DailyLimit
import com.ledgerline.app.finance.forecast.ImpulseCheck
import com.ledgerline.app.finance.forecast.PaceForecast
import com.ledgerline.app.finance.forecast.PaceVerdict
import com.ledgerline.app.finance.forecast.SpendingPace
import com.ledgerline.app.finance.money.AmountInput
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.components.ProgressTrack
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.theme.LocalLedgerColors
import com.ledgerline.app.ui.transactions.BigAmountField
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The number that matters in the moment: how much more you can spend today and still be fine until payday.
 * It turns red with what tomorrow's limit becomes as soon as today goes over.
 */
@Composable
fun TodayCard(limit: DailyLimit, safeMinor: Long, cycleEnd: LocalDate, currency: String, onBreakdown: () -> Unit) {
    val over = limit.over
    val container = if (over || safeMinor < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val content = if (over || safeMinor < 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(onClick = onBreakdown)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (over) "Over today's limit by" else "Left to spend today", style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.85f))
        Text(
            MoneyFormat.format(if (over) -limit.leftTodayMinor else limit.leftTodayMinor, currency),
            style = MaterialTheme.typography.displaySmall,
            color = content,
            maxLines = 1,
        )
        ProgressTrack(
            fraction = if (limit.limitMinor > 0) limit.spentTodayMinor.toFloat() / limit.limitMinor.toFloat() else if (limit.spentTodayMinor > 0) 1f else 0f,
            color = content,
            trackColor = content.copy(alpha = 0.25f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Text(
            when {
                safeMinor < 0 -> "Bills due before ${cycleEnd.medium()} are more than what is left, so there is no spending room today."
                over && limit.tomorrowLimitMinor != null ->
                    "Spent ${MoneyFormat.format(limit.spentTodayMinor, currency)} of ${MoneyFormat.format(limit.limitMinor, currency)}. " +
                        "Tomorrow's limit drops to ${MoneyFormat.format(limit.tomorrowLimitMinor, currency)}."
                else -> "Spent ${MoneyFormat.format(limit.spentTodayMinor, currency)} of today's ${MoneyFormat.format(limit.limitMinor, currency)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = content.copy(alpha = 0.92f),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Safe to spend until ${cycleEnd.medium()}", style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.8f))
                Text(MoneyFormat.format(safeMinor, currency), style = MaterialTheme.typography.titleMedium, color = content)
            }
            Text("How it's worked out ›", style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

/** Where spending is heading at the pace so far, with a small chart of your pace against your daily limit. */
@Composable
fun PaceCard(
    pace: PaceForecast,
    limit: DailyLimit,
    safeMinor: Long,
    cycleEnd: LocalDate,
    currency: String,
    onCashFlow: () -> Unit,
    onCheckPurchase: () -> Unit,
) {
    val colors = LocalLedgerColors.current
    val (tone, title) = when (pace.verdict) {
        PaceVerdict.RUNS_OUT -> colors.negative to "At this pace, money runs out ${pace.runsOutOn?.medium()}"
        PaceVerdict.TIGHT -> colors.caution to "Cutting it close"
        PaceVerdict.ON_TRACK -> colors.positive to "On track until ${cycleEnd.medium()}"
        PaceVerdict.NO_SPENDING_YET -> MaterialTheme.colorScheme.primary to "No everyday spending yet this cycle"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .depth(RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Spending forecast", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onCashFlow) { Text("See cash flow ›") }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = tone)
        if (pace.verdict != PaceVerdict.NO_SPENDING_YET) {
            Text(
                buildString {
                    append("You spend about ${MoneyFormat.format(pace.dailyAverageMinor, currency)} a day. ")
                    when (pace.verdict) {
                        PaceVerdict.RUNS_OUT -> {
                            val days = pace.runsOutOn?.let { java.time.temporal.ChronoUnit.DAYS.between(it, cycleEnd) } ?: 0
                            append("That is $days days short of payday. ")
                            append("To last, keep to ${MoneyFormat.format(limit.limitMinor, currency)} a day.")
                        }
                        PaceVerdict.TIGHT -> append("You would finish with only ${MoneyFormat.format(pace.endMinor.coerceAtLeast(0), currency)} left.")
                        else -> append("You would finish with about ${MoneyFormat.format(pace.endMinor, currency)} left.")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PaceChart(pace, safeMinor, tone)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendLine(tone, dashed = false, "At your pace")
                LegendLine(MaterialTheme.colorScheme.primary, dashed = true, "At your daily limit")
            }
        } else {
            Text(
                "Your daily limit is ${MoneyFormat.format(limit.limitMinor, currency)}. The forecast starts once you record a spend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onCheckPurchase, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Thinking of buying something?", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun PaceChart(pace: PaceForecast, safeMinor: Long, tone: Color) {
    val (atPace, atLimit) = remember(pace, safeMinor) { SpendingPace.curve(safeMinor, pace.dailyAverageMinor, pace.daysLeft) }
    val plan = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val description = "Chart: remaining money falls to ${if (pace.runsOutOn != null) "zero on ${pace.runsOutOn.medium()}" else "${pace.endMinor / 100} by payday"} at your pace."
    Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = description }) {
        val all = atPace + atLimit + 0L
        val top = all.max().toFloat()
        val bottom = all.min().toFloat()
        val span = (top - bottom).takeIf { it > 0f } ?: 1f
        val steps = (atPace.size - 1).coerceAtLeast(1)
        fun x(i: Int) = size.width * i / steps
        fun y(v: Long) = size.height * (top - v) / span
        // Zero line: below it is money you do not have.
        drawLine(axis, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 2f)
        for (i in 0 until atLimit.size - 1) {
            drawLine(
                plan,
                Offset(x(i), y(atLimit[i])),
                Offset(x(i + 1), y(atLimit[i + 1])),
                strokeWidth = 4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )
        }
        for (i in 0 until atPace.size - 1) {
            drawLine(tone, Offset(x(i), y(atPace[i])), Offset(x(i + 1), y(atPace[i + 1])), strokeWidth = 6f)
        }
    }
}

@Composable
private fun LegendLine(color: Color, dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 8.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 5f,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * Pause before an impulse buy: type the price and see it in days of your limit and what it does to the rest of
 * the cycle. "Wait a day" saves it to your spending notes for tomorrow, which is often enough to let the urge pass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseCheckSheet(
    safeMinor: Long,
    today: LocalDate,
    cycleEnd: LocalDate,
    currency: String,
    onDismiss: () -> Unit,
    onWait: (String, Long) -> Unit,
    onBuy: (String, Long) -> Unit,
) {
    val colors = LocalLedgerColors.current
    var text by rememberSaveable { mutableStateOf("") }
    var what by rememberSaveable { mutableStateOf("") }
    val digits = MoneyFormat.fractionDigits(currency)
    val price = AmountInput.toMinor(text, digits)
    val check = remember(price, safeMinor) { ImpulseCheck.check(price, safeMinor, today, cycleEnd) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Before you buy", style = MaterialTheme.typography.titleLarge)
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            BigAmountField(
                text = text,
                currency = currency,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = androidx.compose.ui.Modifier.focusRequester(focus),
            ) { text = AmountInput.clean(it, digits) }
            OutlinedTextField(
                value = what,
                onValueChange = { what = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What is it? (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (price > 0) {
                val days = check.daysOfLimit
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (check.unaffordable) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val onTint = if (check.unaffordable) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    Text(
                        when {
                            check.unaffordable -> "This would dip into money set aside for bills and your buffer."
                            days.isInfinite() -> "There is no spending room left this cycle."
                            days < 1 -> "That is ${(days * 100).roundToInt()}% of today's limit."
                            else -> "That is ${"%.1f".format(days)} days of your daily limit."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = onTint,
                    )
                    if (!check.unaffordable) {
                        Text(
                            "Your daily limit would go from ${MoneyFormat.format(check.limitBeforeMinor, currency)} to " +
                                "${MoneyFormat.format(check.limitAfterMinor, currency)} until ${cycleEnd.medium()}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onTint,
                        )
                    } else {
                        Text(
                            "You would be ${MoneyFormat.format(-check.safeAfterMinor, currency)} short before ${cycleEnd.medium()}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onTint,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onWait(what, price) }, enabled = price > 0, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Wait a day", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = { onBuy(what, price) }, enabled = price > 0, modifier = Modifier.weight(1f)) {
                    Text("Buy it", color = if (check.unaffordable) colors.negative else MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "Wait a day saves it to your spending notes with tomorrow's date. If you still want it then, buy it with a clear head.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
