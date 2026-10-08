package com.ledgerline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.theme.LocalLedgerColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
fun LedgerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun EmptyPane(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text)
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
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Date ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))}")
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
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
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("$label: $value")
    }
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
fun BudgetMeter(spent: Long, budget: Long, currency: String) {
    val fraction = if (budget <= 0L) 0f else (spent.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Spent ${MoneyFormat.format(spent, currency)}", style = MaterialTheme.typography.bodyMedium)
        Text("Budget ${MoneyFormat.format(budget, currency)}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
