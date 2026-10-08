package com.ledgerline.app.ui.features

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.data.remote.FinancialDataProvider
import com.ledgerline.app.data.repository.RecurringSuggestionStore
import com.ledgerline.app.domain.model.DebtDraft
import com.ledgerline.app.domain.model.DebtStrategy
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.repository.DebtRepository
import com.ledgerline.app.domain.repository.NotificationSourceRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SyncLogRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.usecase.ObserveDashboard
import com.ledgerline.app.finance.debt.DebtPlanner
import com.ledgerline.app.finance.forecast.CashFlowForecastEngine
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.subscription.SubscriptionRollup
import com.ledgerline.app.finance.whatif.WhatIfEngine
import com.ledgerline.app.finance.whatif.WhatIfRequest
import com.ledgerline.app.preferences.UiPreferences
import com.ledgerline.app.ui.components.AmountField
import com.ledgerline.app.ui.components.KeyValue
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.components.SectionLabel
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.format.medium
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(private val transactions: TransactionRepository) : ViewModel() {
    val rows = transactions.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStatus(id: String, status: TransactionStatus) {
        viewModelScope.launch { transactions.setStatus(id, status) }
    }
}

@Composable
fun ReviewScreen(onOpen: (String) -> Unit, contentPadding: PaddingValues, viewModel: ReviewViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val pending = rows.filter { it.deletedAtEpochMillis == null && it.status == TransactionStatus.DETECTED }
    FeatureColumn(contentPadding) {
        Text("Needs review", style = MaterialTheme.typography.headlineSmall)
        Text("These rows do not change balances until you confirm them. Marking a duplicate keeps both records and stops the new one from counting.")
        if (pending.isEmpty()) Text("Nothing is waiting.")
        pending.forEach { tx ->
            Column {
                TextButton(onClick = { onOpen(tx.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("${tx.merchantRaw.ifBlank { tx.type.label() }} · ${MoneyFormat.format(tx.amountMinor, tx.currency)} · ${tx.occurredOn.medium()}")
                }
                TextButton(onClick = { viewModel.setStatus(tx.id, TransactionStatus.CONFIRMED) }) { Text("Count it") }
                TextButton(onClick = { viewModel.setStatus(tx.id, TransactionStatus.DUPLICATE) }) { Text("Mark duplicate") }
                TextButton(onClick = { viewModel.setStatus(tx.id, TransactionStatus.IGNORED) }) { Text("Ignore") }
            }
        }
    }
}

@HiltViewModel
class ForecastViewModel @Inject constructor(observe: ObserveDashboard) : ViewModel() {
    val dashboard = observe.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ForecastScreen(contentPadding: PaddingValues, viewModel: ForecastViewModel = hiltViewModel()) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    FeatureColumn(contentPadding) {
        Text("Cash-flow forecast", style = MaterialTheme.typography.headlineSmall)
        val data = dashboard
        if (data == null) {
            Text("Loading the projection…")
            return@FeatureColumn
        }
        Text(CashFlowForecastEngine.DISCLAIMER)
        KeyValue("Estimated discretionary per day", MoneyFormat.format(data.discretionaryDailyMinor, data.currency))
        data.forecast.forEach { day ->
            KeyValue(day.date.medium(), MoneyFormat.format(day.balanceAfterMinor, data.currency))
        }
    }
}

@HiltViewModel
class WhatIfViewModel @Inject constructor(
    observe: ObserveDashboard,
    recurring: RecurringRepository,
) : ViewModel() {
    val state = combine(observe.observe(), recurring.observe()) { dashboard, items -> dashboard to items }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun WhatIfScreen(contentPadding: PaddingValues, viewModel: WhatIfViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expense by remember { mutableStateOf(0L) }
    var save by remember { mutableStateOf(0L) }
    var cancelId by remember { mutableStateOf<String?>(null) }
    FeatureColumn(contentPadding) {
        Text("What if", style = MaterialTheme.typography.headlineSmall)
        Text("This scenario is temporary. It does not change accounts, transactions, or schedules.")
        val pair = state
        if (pair == null) {
            Text("Loading…")
            return@FeatureColumn
        }
        val (dashboard, recurring) = pair
        val choices = listOf<String?>(null) + SubscriptionRollup.active(recurring, dashboard.currency).map { it.id }
        Text("One-time expense")
        AmountField(expense, dashboard.currency) { expense = it }
        Text("Monthly amount to set aside")
        AmountField(save, dashboard.currency) { save = it }
        PickerField(
            "Stop a subscription",
            recurring.firstOrNull { it.id == cancelId }?.name ?: "None",
            choices,
            { id -> recurring.firstOrNull { it.id == id }?.name ?: "None" },
        ) { cancelId = it }
        val result = WhatIfEngine.evaluate(
            start = dashboard.asOf,
            days = dashboard.horizonDays,
            openingMinor = dashboard.liquidMinor,
            events = dashboard.upcoming.filter { it.liquidDeltaMinor != 0L }.map {
                CashFlowForecastEngine.DatedAmount(it.date, it.recurringId, it.name, it.liquidDeltaMinor)
            },
            dailyDiscretionaryMinor = dashboard.discretionaryDailyMinor,
            bufferMinor = dashboard.safeToSpend.lines.firstOrNull { it.label == "Minimum buffer" }?.minor?.let { -it } ?: 0L,
            request = WhatIfRequest(expense, dashboard.asOf, cancelId, save),
        )
        KeyValue("Current projected end", MoneyFormat.format(result.baselineEndMinor, dashboard.currency))
        KeyValue("Scenario projected end", MoneyFormat.format(result.scenarioEndMinor, dashboard.currency))
        Text(result.summary)
    }
}

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    recurring: RecurringRepository,
    private val store: RecurringSuggestionStore,
) : ViewModel() {
    val items = recurring.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var message by mutableStateOf<String?>(null)

    fun detect() {
        viewModelScope.launch {
            val created = store.storeNew()
            message = if (created == 0) "No new repeating merchants were found." else "Added $created inactive schedule${if (created == 1) "" else "s"}."
        }
    }
}

@Composable
fun SubscriptionsScreen(onEdit: (String) -> Unit, contentPadding: PaddingValues, viewModel: SubscriptionViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val currency = items.firstOrNull()?.currency ?: "USD"
    val active = SubscriptionRollup.active(items, currency)
    val monthly = SubscriptionRollup.monthlyMinor(active)
    FeatureColumn(contentPadding) {
        Text("Subscriptions", style = MaterialTheme.typography.headlineSmall)
        KeyValue("This month", MoneyFormat.format(monthly, currency))
        KeyValue("Annualized", MoneyFormat.format(SubscriptionRollup.annualizedMinor(monthly), currency))
        Text("Annualized is the monthly figure times 12. Weekly and yearly schedules are converted before that.")
        PrimaryButton("Look for repeating merchants", onClick = viewModel::detect)
        viewModel.message?.let { Text(it) }
        SectionLabel("Active")
        if (active.isEmpty()) Text("No active subscriptions.")
        active.forEach { item ->
            TextButton(onClick = { onEdit(item.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("${item.name} · ${MoneyFormat.format(item.amountMinor, item.currency)} · ${item.frequency.label()}")
            }
        }
        SectionLabel("Inactive suggestions")
        items.filter { !it.active }.forEach { item ->
            TextButton(onClick = { onEdit(item.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("${item.name} · inactive · ${item.note}")
            }
        }
    }
}

@HiltViewModel
class DebtViewModel @Inject constructor(private val debts: DebtRepository) : ViewModel() {
    val rows = debts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var name by mutableStateOf("")
    var balance by mutableStateOf(0L)
    var apr by mutableStateOf("0")
    var minimum by mutableStateOf(0L)
    var dueDay by mutableStateOf("1")
    var priority by mutableStateOf(PaymentPriority.MANDATORY)
    var strategy by mutableStateOf(DebtStrategy.AVALANCHE)
    var extra by mutableStateOf(0L)
    var message by mutableStateOf<String?>(null)

    fun save() {
        viewModelScope.launch {
            val points = apr.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toInt()
            if (points == null) {
                message = "Enter the APR as a percent, such as 19.99."
                return@launch
            }
            val result = debts.save(
                DebtDraft(null, name, balance, points, minimum, dueDay.toIntOrNull() ?: 0, priority),
            )
            message = if (result is SaveResult.Invalid) result.message else "Saved."
            if (result is SaveResult.Saved) name = ""
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { debts.delete(id) }
    }
}

@Composable
fun DebtScreen(contentPadding: PaddingValues, viewModel: DebtViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val currency = rows.firstOrNull()?.currency ?: "USD"
    val plan = DebtPlanner.plan(rows, viewModel.extra, viewModel.strategy)
    FeatureColumn(contentPadding) {
        Text("Debt plan", style = MaterialTheme.typography.headlineSmall)
        Text(DebtPlanner.ASSUMPTION)
        PickerField("Strategy", viewModel.strategy.name.lowercase(), DebtStrategy.entries.toList(), { it.name.lowercase() }) {
            viewModel.strategy = it
        }
        Text("Extra monthly payment")
        AmountField(viewModel.extra, currency) { viewModel.extra = it }
        KeyValue("Months", if (plan.unfinished) "More than ${plan.months}" else plan.months.toString())
        KeyValue("Estimated interest", MoneyFormat.format(plan.interestMinor, currency))
        KeyValue("Order", plan.payoffOrder.joinToString(", ").ifBlank { "None" })
        rows.forEach { debt ->
            TextButton(onClick = { viewModel.delete(debt.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("${debt.name} · ${MoneyFormat.format(debt.balanceMinor, debt.currency)} · ${aprLabel(debt.aprBasisPoints)} · remove")
            }
        }
        SectionLabel("Add a debt")
        OutlinedTextField(viewModel.name, { viewModel.name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        Text("Current balance")
        AmountField(viewModel.balance, currency) { viewModel.balance = it }
        OutlinedTextField(viewModel.apr, { viewModel.apr = it }, Modifier.fillMaxWidth(), label = { Text("APR percent") }, singleLine = true)
        Text("Minimum payment")
        AmountField(viewModel.minimum, currency) { viewModel.minimum = it }
        OutlinedTextField(viewModel.dueDay, { viewModel.dueDay = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(), label = { Text("Due day") }, singleLine = true)
        PickerField("Priority", viewModel.priority.label(), PaymentPriority.entries.toList(), { it.label() }) { viewModel.priority = it }
        PrimaryButton("Save debt", onClick = viewModel::save)
        viewModel.message?.let { Text(it) }
    }
}

@HiltViewModel
class DetectionViewModel @Inject constructor(
    private val preferences: UiPreferences,
    private val sources: NotificationSourceRepository,
) : ViewModel() {
    val enabled = preferences.notificationDetection.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val apps = sources.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val payments = preferences.reminderPayments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val buffer = preferences.reminderBuffer.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val budget = preferences.reminderBudget.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val anomaly = preferences.reminderAnomaly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val subscription = preferences.reminderSubscription.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch { sources.ensureCatalog() }
    }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { preferences.setNotificationDetection(value) }
    }

    fun setApp(id: String, value: Boolean) {
        viewModelScope.launch { sources.setEnabled(id, value) }
    }

    fun setReminder(kind: String, value: Boolean) {
        viewModelScope.launch { preferences.setReminder(kind, value) }
    }
}

@Composable
fun DetectionScreen(contentPadding: PaddingValues, viewModel: DetectionViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val buffer by viewModel.buffer.collectAsStateWithLifecycle()
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val anomaly by viewModel.anomaly.collectAsStateWithLifecycle()
    val subscription by viewModel.subscription.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    FeatureColumn(contentPadding) {
        Text("Transaction detection", style = MaterialTheme.typography.headlineSmall)
        Text("Notification text is read on this device. Ledgerline stores the extracted amount, merchant, and account ending. It does not upload the notification.")
        Text("Notification access ${if (enabled) "on" else "off"}")
        Switch(checked = enabled, onCheckedChange = viewModel::setEnabled)
        TextButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Open notification access settings") }
        apps.forEach { app ->
            TextButton(onClick = { viewModel.setApp(app.id, !app.enabled) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("${if (app.enabled) "On" else "Off"} · ${app.label}")
            }
        }
        SectionLabel("Reminders")
        Text("Reminders stay off until you enable a type. Android may also ask for notification permission.")
        if (Build.VERSION.SDK_INT >= 33) {
            TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Allow reminder notifications")
            }
        }
        ReminderRow("Payments", payments) { viewModel.setReminder("payments", it) }
        ReminderRow("Buffer", buffer) { viewModel.setReminder("buffer", it) }
        ReminderRow("Budgets", budget) { viewModel.setReminder("budget", it) }
        ReminderRow("Unusual amounts", anomaly) { viewModel.setReminder("anomaly", it) }
        ReminderRow("Subscriptions", subscription) { viewModel.setReminder("subscription", it) }
    }
}

@HiltViewModel
class BankViewModel @Inject constructor(
    private val provider: FinancialDataProvider,
    syncLog: SyncLogRepository,
) : ViewModel() {
    val records = syncLog.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var description by mutableStateOf("")

    init {
        viewModelScope.launch { description = provider.describe() }
    }

    fun refresh() {
        viewModelScope.launch { provider.refresh() }
    }
}

@Composable
fun BankScreen(contentPadding: PaddingValues, viewModel: BankViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    FeatureColumn(contentPadding) {
        Text("Bank connection", style = MaterialTheme.typography.headlineSmall)
        Text(viewModel.description.ifBlank { "Loading…" })
        PrimaryButton("Import development fixture", onClick = viewModel::refresh)
        records.take(8).forEach { record ->
            Text("${record.status}: ${record.message}")
        }
    }
}

@Composable
private fun FeatureColumn(contentPadding: PaddingValues, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun ReminderRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    TextButton(onClick = { onChange(!checked) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("$label · ${if (checked) "on" else "off"}")
    }
}

private fun aprLabel(basisPoints: Int): String {
    val percent = BigDecimal.valueOf(basisPoints.toLong()).movePointLeft(2).stripTrailingZeros().toPlainString()
    return "$percent% APR"
}
