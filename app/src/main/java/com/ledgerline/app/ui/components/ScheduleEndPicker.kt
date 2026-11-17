package com.ledgerline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.finance.schedule.EndMode
import com.ledgerline.app.finance.schedule.ScheduleEnd
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How often something repeats, as chips. The same choices everywhere a schedule is made. */
val repeatChoices = listOf(
    RecurrenceFrequency.WEEKLY to "Every week",
    RecurrenceFrequency.BIWEEKLY to "Every 2 weeks",
    RecurrenceFrequency.MONTHLY to "Every month",
    RecurrenceFrequency.QUARTERLY to "Every 3 months",
    RecurrenceFrequency.YEARLY to "Every year",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatEveryPicker(frequency: RecurrenceFrequency, onChange: (RecurrenceFrequency) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeatChoices.forEach { (choice, label) -> FilterChip(frequency == choice, { onChange(choice) }, { Text(label) }) }
    }
}

/**
 * When a schedule stops: never, after a number of times (with − and +), or on a date you pick. Shared by Add
 * transaction and the bill sheet on Home so a repeat works the same everywhere.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleEndPicker(
    end: ScheduleEnd,
    start: LocalDate,
    frequency: com.ledgerline.app.domain.model.RecurrenceFrequency = com.ledgerline.app.domain.model.RecurrenceFrequency.MONTHLY,
    /** What one repeat is called: "payments" for a bill, "cycles" for a spending item. */
    unit: String = "payments",
    onChange: (ScheduleEnd) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ends", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(end.mode == EndMode.NEVER, { onChange(end.copy(mode = EndMode.NEVER)) }, { Text("Never") })
            FilterChip(end.mode == EndMode.AFTER_COUNT, { onChange(end.copy(mode = EndMode.AFTER_COUNT)) }, { Text("After a number of $unit") })
            FilterChip(
                end.mode == EndMode.ON_DATE,
                { onChange(end.copy(mode = EndMode.ON_DATE, on = end.on ?: start.plusMonths(6))); picking = true },
                { Text(if (end.mode == EndMode.ON_DATE && end.on != null) "Until ${end.on.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))}" else "On a date") },
            )
        }
        if (end.mode == EndMode.AFTER_COUNT) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(unit.replaceFirstChar { it.uppercase() } + " in all", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onChange(end.copy(count = (end.count - 1).coerceAtLeast(1))) }) { Icon(Icons.Filled.Remove, contentDescription = "Fewer times") }
                Text("${end.count}", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
                IconButton(onClick = { onChange(end.copy(count = (end.count + 1).coerceAtMost(240))) }) { Icon(Icons.Filled.Add, contentDescription = "More times") }
            }
        }
        // In plain words: how many, and the first and last dates.
        Text(
            com.ledgerline.app.finance.schedule.EndSummary.describe(end, start, frequency, unit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (picking) {
        com.ledgerline.app.ui.transactions.DatePickerDialogOnly(end.on ?: start.plusMonths(6), onDismiss = { picking = false }) { date ->
            onChange(end.copy(mode = EndMode.ON_DATE, on = date))
            picking = false
        }
    }
}
