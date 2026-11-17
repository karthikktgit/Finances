package com.ledgerline.app.ui.components

import com.ledgerline.app.ui.theme.depth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.ledgerline.app.R
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.theme.LocalLedgerColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun MoneyText(minor: Long, currency: String, modifier: Modifier = Modifier, large: Boolean = false, signed: Boolean = false) {
    val colors = LocalLedgerColors.current
    val color = when {
        !signed -> MaterialTheme.colorScheme.onSurface
        minor > 0 -> colors.positive
        minor < 0 -> colors.negative
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = if (signed) MoneyFormat.signedLabel(minor, currency) else MoneyFormat.format(minor, currency),
        style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_brand),
        contentDescription = "Ledgerline",
        modifier = modifier.size(40.dp),
    )
}

@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().depth(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = if (compact) 12.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        content = content,
    )
}

@Composable
fun EmptyPane(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun AmountField(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    onMinor: (Long) -> Unit,
) {
    OutlinedTextField(
        value = MoneyFormat.format(minor, currency),
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(9)
            onMinor(digits.toLongOrNull() ?: 0L)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text("Amount") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    modifier: Modifier = Modifier,
    onDate: (LocalDate) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    FieldRow(
        label = "Date",
        value = date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())),
        modifier = modifier,
        onClick = { open = true },
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = com.ledgerline.app.finance.time.FinancialDate.toPickerMillis(date),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDate(com.ledgerline.app.finance.time.FinancialDate.fromPickerMillis(millis))
                    }
                    open = false
                }) { Text("Use this date") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun <T> PickerField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    FieldRow(label = label, value = value, modifier = modifier, onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        TextButton(
                            onClick = {
                                onSelect(option)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(optionLabel(option), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CompactButton(label: String, onClick: () -> Unit, selected: Boolean = false, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        // The tap area is at least 48 dp tall; only the smaller rounded shape is drawn.
        modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(
                horizontal = CompactButtonSpec.HORIZONTAL_PADDING_DP.dp + 4.dp,
                vertical = CompactButtonSpec.VERTICAL_PADDING_DP.dp + 2.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

@Composable
fun BudgetMeter(spent: Long, budget: Long, currency: String) {
    val fraction = if (budget <= 0L) 0f else (spent.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
    val colors = LocalLedgerColors.current
    ProgressTrack(fraction, color = if (spent > budget) colors.negative else MaterialTheme.colorScheme.primary)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Spent ${MoneyFormat.format(spent, currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("of ${MoneyFormat.format(budget, currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            softWrap = true,
        )
        Text(
            value,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            softWrap = false,
        )
    }
}
