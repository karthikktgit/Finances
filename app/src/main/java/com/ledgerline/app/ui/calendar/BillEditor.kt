package com.ledgerline.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SeriesScope
import com.ledgerline.app.finance.calendar.BillPresets
import com.ledgerline.app.finance.calendar.BillPreview
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.components.ConfirmDialog
import com.ledgerline.app.ui.components.DateField
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.components.SegmentedChoice
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.theme.LocalLedgerColors
import com.ledgerline.app.ui.transactions.AccountChooser
import com.ledgerline.app.ui.transactions.BigAmountField
import com.ledgerline.app.ui.transactions.DatePickerDialogOnly
import com.ledgerline.app.ui.transactions.SwitchRow
import java.time.LocalDate

private val kinds = listOf(
    ScheduledKind.BILL to "Bill",
    ScheduledKind.SUBSCRIPTION to "Subscription",
    ScheduledKind.INCOME to "Income",
    ScheduledKind.DEBT to "Loan",
)

private val commonRepeats = listOf(
    RecurrenceFrequency.MONTHLY to "Monthly",
    RecurrenceFrequency.BIWEEKLY to "Every 2 weeks",
    RecurrenceFrequency.WEEKLY to "Weekly",
    RecurrenceFrequency.YEARLY to "Yearly",
)

private val otherRepeats = listOf(
    RecurrenceFrequency.QUARTERLY to "Every 3 months",
    RecurrenceFrequency.DAILY to "Daily",
    RecurrenceFrequency.CUSTOM to "Every N days",
)

/**
 * Set a bill, subscription, income, or loan payment. The essentials are on one screen in the order people think
 * of them: what kind, how much, what it is called, how often and from when, and which account pays. The rest
 * (category, end date, priority detail, notes) waits under More options.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringEditorScreen(onDone: () -> Unit, viewModel: RecurringEditorViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    val activeAccounts = accounts.filter { it.active }
    val currency = activeAccounts.firstOrNull { it.id == viewModel.accountId }?.currency ?: activeAccounts.firstOrNull()?.currency ?: "USD"
    val colors = LocalLedgerColors.current
    var pendingDelete by remember { mutableStateOf<SeriesScope?>(null) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val incoming = viewModel.direction == CashFlowDirection.INFLOW
    val frequency = if (viewModel.repeats) viewModel.frequency else RecurrenceFrequency.ONCE
    val editingOneDate = viewModel.isExisting && viewModel.occurrenceOn != null
    val noun = when (viewModel.kind) {
        ScheduledKind.SUBSCRIPTION -> "subscription"
        ScheduledKind.INCOME -> "income"
        ScheduledKind.DEBT -> "loan payment"
        ScheduledKind.TRANSFER -> "transfer"
        else -> "bill"
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isExisting) "Edit $noun" else "New $noun") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close without saving") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding().padding(16.dp)) {
                PrimaryButton(
                    if (viewModel.isExisting) "Save changes" else "Save $noun",
                    enabled = activeAccounts.isNotEmpty() && viewModel.amountMinor > 0 && viewModel.name.isNotBlank(),
                    onClick = viewModel::save,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (viewModel.kind != ScheduledKind.TRANSFER) {
                SegmentedChoice(options = kinds, selected = viewModel.kind, onSelect = viewModel::chooseKind)
            }
            if (!viewModel.isExisting) {
                val presets = BillPresets.forKind(viewModel.kind)
                if (presets.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = viewModel.name == preset.name,
                                onClick = { viewModel.applyPreset(preset, categories) },
                                label = { Text(preset.name) },
                                leadingIcon = { Icon(kindIcon(preset.kind, preset.name), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
            BigAmountField(
                text = viewModel.amountText,
                currency = currency,
                color = if (incoming) colors.positive else MaterialTheme.colorScheme.onSurface,
            ) { viewModel.onAmountText(it, currency) }
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (incoming) "Where it comes from" else "Name, like Rent or Netflix") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )

            Label("How often")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (commonRepeats + (RecurrenceFrequency.ONCE to "Just once")).forEach { (option, text) ->
                    FilterChip(
                        selected = frequency == option,
                        onClick = {
                            if (option == RecurrenceFrequency.ONCE) {
                                viewModel.repeats = false
                            } else {
                                viewModel.repeats = true
                                viewModel.frequency = option
                            }
                        },
                        label = { Text(text) },
                    )
                }
                if (showMore || frequency in otherRepeats.map { it.first }) {
                    otherRepeats.forEach { (option, text) ->
                        FilterChip(
                            selected = frequency == option,
                            onClick = {
                                viewModel.repeats = true
                                viewModel.frequency = option
                            },
                            label = { Text(text) },
                        )
                    }
                }
            }
            if (frequency == RecurrenceFrequency.CUSTOM) {
                OutlinedTextField(
                    value = viewModel.intervalDays,
                    onValueChange = { viewModel.intervalDays = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Every how many days") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            // When: the first due date sets the day it repeats on.
            Label(if (editingOneDate && viewModel.scope == SeriesScope.THIS_OCCURRENCE) "This date" else if (frequency == RecurrenceFrequency.ONCE) "Due on" else "First due date")
            if (editingOneDate && viewModel.scope == SeriesScope.THIS_OCCURRENCE) {
                Text(viewModel.occurrenceOn!!.medium(), style = MaterialTheme.typography.titleMedium)
            } else {
                FilterChip(
                    selected = true,
                    onClick = { pickingDate = true },
                    label = { Text(viewModel.start.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))) },
                    leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) },
                )
            }
            val preview = remember(frequency, viewModel.start, viewModel.intervalDays) {
                BillPreview.nextDates(
                    frequency = frequency,
                    start = viewModel.start,
                    from = LocalDate.now(),
                    intervalDays = viewModel.intervalDays.toIntOrNull(),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Text(
                    BillPreview.describe(frequency, viewModel.start, viewModel.intervalDays.toIntOrNull()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (preview.isNotEmpty() && frequency != RecurrenceFrequency.ONCE) {
                    Text(
                        "Next: " + preview.joinToString(" · ") { it.medium() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (viewModel.start.dayOfMonth > 28 && frequency in setOf(RecurrenceFrequency.MONTHLY, RecurrenceFrequency.QUARTERLY)) {
                    Text(
                        "In shorter months it falls on the last day, then returns to the ${BillPreview.ordinal(viewModel.start.dayOfMonth)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            if (activeAccounts.isEmpty()) {
                Text("Add an account first: Settings › Accounts.", color = MaterialTheme.colorScheme.error)
            } else {
                AccountChooser(if (incoming) "Paid into" else "Paid from", activeAccounts, viewModel.accountId) { viewModel.accountId = it }
                // The Home group this bill shows in, the same groups as on Home.
                val groups by viewModel.groups.collectAsStateWithLifecycle()
                val placements by viewModel.placements.collectAsStateWithLifecycle()
                if (!incoming && groups.isNotEmpty()) {
                    val current = viewModel.groupId
                        ?: placements.firstOrNull { it.itemKey == "bill:${viewModel.editingId}" }?.groupId
                        ?: if (viewModel.kind == com.ledgerline.app.domain.model.ScheduledKind.SUBSCRIPTION && groups.any { it.id == "grp_subscriptions" }) "grp_subscriptions" else groups.first().id
                    Text("Show in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { group -> FilterChip(current == group.id, { viewModel.groupId = group.id }, { Text(group.name) }) }
                    }
                }
                if (viewModel.accountId.isNotBlank() && accounts.none { it.id == viewModel.accountId }) {
                    Text("The account this used was deleted. Choose another, then turn Active on in More options.", color = MaterialTheme.colorScheme.error)
                }
            }

            SwitchRow(
                title = if (incoming) "Arrives automatically" else "Paid automatically (autopay)",
                body = if (incoming) {
                    "Each payday it is recorded as received, so you do not have to tick it off."
                } else {
                    "Each due date it is recorded as paid from the account above, so the calendar and Safe to spend stay up to date without tapping Paid."
                },
                checked = viewModel.autopay,
                onChange = viewModel::setAutopayOn,
            )
            if (viewModel.kind != ScheduledKind.TRANSFER) {
                SwitchRow(
                    title = if (incoming) "Count on this income" else "Set aside before Safe to spend",
                    body = if (incoming) {
                        "Turn off for income that might not come, like a bonus. Expected income is never counted as money you have until it arrives."
                    } else {
                        "On for bills you must pay. Off for nice-to-haves you could skip; they still show on the calendar."
                    },
                    checked = viewModel.counted,
                    onChange = viewModel::setCounted,
                )
            }

            if (editingOneDate) {
                Label("Change")
                SegmentedChoice(
                    options = listOf(
                        SeriesScope.THIS_OCCURRENCE to "This date",
                        SeriesScope.THIS_AND_FUTURE to "From here on",
                        SeriesScope.ENTIRE_SERIES to "Every date",
                    ),
                    selected = viewModel.scope,
                    onSelect = { viewModel.scope = it },
                )
                if (viewModel.scope == SeriesScope.THIS_OCCURRENCE) {
                    Text(
                        "Only the amount and name change for ${viewModel.occurrenceOn!!.medium()}. Other dates stay as they are.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TextButton(onClick = { showMore = !showMore }) { Text(if (showMore) "Fewer options" else "More options") }
            if (showMore) {
                PickerField(
                    "Category",
                    categories.firstOrNull { it.id == viewModel.categoryId }?.let { CategoryTree.label(it.id, categories) } ?: "None",
                    categories.filter { !it.archived },
                    { CategoryTree.label(it.id, categories) },
                ) { viewModel.categoryId = it.id }
                if (viewModel.repeats) {
                    PickerField(
                        "Ends",
                        endLabel(viewModel.endMode),
                        EndMode.entries.toList(),
                        ::endLabel,
                    ) { viewModel.endMode = it }
                    if (viewModel.endMode == EndMode.ON_DATE) DateField(viewModel.endOn) { viewModel.endOn = it }
                    if (viewModel.endMode == EndMode.AFTER_COUNT) {
                        OutlinedTextField(
                            value = viewModel.occurrenceCount,
                            onValueChange = { viewModel.occurrenceCount = it.filter(Char::isDigit).take(4) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Number of payments") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
                if (!incoming) {
                    PickerField("Priority", viewModel.priority.label(), PaymentPriority.entries.toList(), { it.label() }) { viewModel.priority = it }
                }
                SwitchRow(
                    title = "Active",
                    body = "Paused items stay saved but leave the calendar and Safe to spend.",
                    checked = viewModel.active,
                ) { viewModel.active = it }
                OutlinedTextField(
                    value = viewModel.note,
                    onValueChange = { viewModel.note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                    shape = RoundedCornerShape(16.dp),
                )
            }
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (viewModel.isExisting) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (editingOneDate) {
                        TextButton(onClick = { pendingDelete = SeriesScope.THIS_OCCURRENCE }) { Text("Skip this date") }
                        TextButton(onClick = { pendingDelete = SeriesScope.THIS_AND_FUTURE }) { Text("Stop from here") }
                    }
                    TextButton(onClick = { pendingDelete = SeriesScope.ENTIRE_SERIES }) {
                        Text("Delete $noun", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (pickingDate) {
        DatePickerDialogOnly(viewModel.start, onDismiss = { pickingDate = false }) {
            viewModel.start = it
            pickingDate = false
        }
    }
    pendingDelete?.let { scope ->
        ConfirmDialog(
            title = when (scope) {
                SeriesScope.THIS_OCCURRENCE -> "Skip ${viewModel.name.ifBlank { "this" }} on ${viewModel.occurrenceOn?.medium()}?"
                SeriesScope.THIS_AND_FUTURE -> "Stop ${viewModel.name.ifBlank { "this" }} from ${viewModel.occurrenceOn?.medium()}?"
                SeriesScope.ENTIRE_SERIES -> "Delete ${viewModel.name.ifBlank { "this $noun" }}?"
            },
            body = when (scope) {
                SeriesScope.THIS_OCCURRENCE -> "Only this date is skipped. Other dates stay."
                SeriesScope.THIS_AND_FUTURE -> "Earlier dates and anything already paid stay in your history."
                SeriesScope.ENTIRE_SERIES -> "Every date is removed from the calendar. Payments already recorded stay in your history."
            } + " Amount ${MoneyFormat.format(viewModel.amountMinor, currency)}.",
            confirm = when (scope) {
                SeriesScope.THIS_OCCURRENCE -> "Skip"
                SeriesScope.THIS_AND_FUTURE -> "Stop"
                SeriesScope.ENTIRE_SERIES -> "Delete"
            },
            onConfirm = {
                pendingDelete = null
                viewModel.delete(scope)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun endLabel(mode: EndMode): String = when (mode) {
    EndMode.NEVER -> "Never"
    EndMode.ON_DATE -> "On a date"
    EndMode.AFTER_COUNT -> "After a number of payments"
}
