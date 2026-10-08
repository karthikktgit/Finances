package com.ledgerline.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringDraft
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.schedule.RecurringSchedule
import com.ledgerline.app.ui.components.AmountField
import com.ledgerline.app.ui.components.DateField
import com.ledgerline.app.ui.components.EmptyPane
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.format.paymentLabel
import com.ledgerline.app.ui.format.signWord
import com.ledgerline.app.ui.navigation.RecurringEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    recurring: RecurringRepository,
    accounts: AccountRepository,
    private val clock: AppClock,
) : ViewModel() {
    var month by mutableStateOf(YearMonth.from(clock.today()))
        private set
    val data = combine(recurring.observe(), accounts.observe()) { items, accounts -> items to accounts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<RecurringPayment>() to emptyList())

    fun previous() { month = month.minusMonths(1) }
    fun next() { month = month.plusMonths(1) }
    fun today() = clock.today()
}

@Composable
fun CalendarScreen(
    onEdit: (String) -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val start = viewModel.month.atDay(1)
    val end = viewModel.month.atEndOfMonth()
    val events = data.first.flatMap { item ->
        RecurringSchedule.occurrencesBetween(item, start, end).map { date -> item to date }
    }.sortedBy { it.second }
    val currency = data.first.firstOrNull()?.currency ?: "USD"
    val outTotal = events.filter { it.first.direction == CashFlowDirection.OUTFLOW }.sumOf { it.first.amountMinor }
    val inTotal = events.filter { it.first.direction == CashFlowDirection.INFLOW }.sumOf { it.first.amountMinor }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 12.dp, 20.dp, contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Payment calendar", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::previous, modifier = Modifier.heightIn(min = 48.dp)) { Text("Previous") }
                Text(viewModel.month.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = viewModel::next, modifier = Modifier.heightIn(min = 48.dp)) { Text("Next") }
            }
            Text("Expected out ${MoneyFormat.format(outTotal, currency)}")
            Text("Expected in ${MoneyFormat.format(inTotal, currency)}")
            TextButton(onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add scheduled item") }
        }
        if (events.isEmpty()) {
            item { EmptyPane("Nothing scheduled", "Add rent, a paycheck, or a subscription. Optional items stay visible and are not treated as mandatory.") }
        } else {
            items(events.size) { index ->
                val (item, date) = events[index]
                val account = data.second.firstOrNull { it.id == item.accountId }
                TextButton(onClick = { onEdit(item.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${date.medium()} · ${item.name}", style = MaterialTheme.typography.titleMedium)
                        Text("${item.kind.label()} · ${item.priority.label()} · ${account?.name ?: "Account"}")
                        Text("${item.direction.signWord()} ${MoneyFormat.format(item.amountMinor, item.currency)}")
                    }
                }
            }
        }
    }
}

@HiltViewModel
class RecurringEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recurring: RecurringRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    clock: AppClock,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<RecurringEditorRoute>()
    val isExisting = args.recurringId.isNotBlank()
    val accounts = accounts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = categories.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var name by mutableStateOf("")
    var amountMinor by mutableStateOf(0L)
    var accountId by mutableStateOf("")
    var categoryId by mutableStateOf<String?>(null)
    var direction by mutableStateOf(CashFlowDirection.OUTFLOW)
    var kind by mutableStateOf(ScheduledKind.BILL)
    var frequency by mutableStateOf(RecurrenceFrequency.MONTHLY)
    var intervalDays by mutableStateOf("14")
    var start by mutableStateOf(clock.today())
    var priority by mutableStateOf(PaymentPriority.MANDATORY)
    var confidence by mutableStateOf(IncomeConfidence.HIGH)
    var active by mutableStateOf(true)
    var note by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()

    init {
        if (args.recurringId.isNotBlank()) {
            viewModelScope.launch {
                val item = recurring.get(args.recurringId) ?: return@launch
                name = item.name
                amountMinor = item.amountMinor
                accountId = item.accountId
                categoryId = item.categoryId
                direction = item.direction
                kind = item.kind
                frequency = item.frequency
                intervalDays = item.intervalDays?.toString() ?: "14"
                start = item.startOn
                priority = item.priority
                confidence = item.incomeConfidence
                active = item.active
                note = item.note
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            val result = recurring.save(
                RecurringDraft(
                    existingId = args.recurringId.ifBlank { null },
                    name = name,
                    accountId = accountId,
                    categoryId = categoryId,
                    direction = direction,
                    kind = kind,
                    amountMinor = amountMinor,
                    frequency = frequency,
                    intervalDays = intervalDays.toIntOrNull(),
                    dayOfMonth = start.dayOfMonth,
                    dayOfWeek = start.dayOfWeek.value,
                    startOn = start,
                    priority = priority,
                    incomeConfidence = confidence,
                    active = active,
                    note = note,
                ),
            )
            when (result) {
                SaveResult.Saved -> _done.emit(Unit)
                is SaveResult.Invalid -> error = result.message
                is SaveResult.PossibleDuplicate -> Unit
            }
        }
    }

    fun delete() {
        val id = args.recurringId
        if (id.isBlank()) return
        viewModelScope.launch {
            recurring.delete(id)
            _done.emit(Unit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditorScreen(onDone: () -> Unit, viewModel: RecurringEditorViewModel = hiltViewModel()) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    val activeAccounts = accounts.filter { it.active }
    val currency = activeAccounts.firstOrNull { it.id == viewModel.accountId }?.currency ?: "USD"
    LaunchedEffect(activeAccounts) {
        if (viewModel.accountId.isBlank() && activeAccounts.isNotEmpty()) viewModel.accountId = activeAccounts.first().id
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Scheduled item") },
            navigationIcon = { TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
            )
            AmountField(viewModel.amountMinor, currency) { viewModel.amountMinor = it }
            PickerField("Direction", viewModel.direction.signWord(), CashFlowDirection.entries.toList(), { it.signWord() }) { viewModel.direction = it }
            PickerField("Kind", viewModel.kind.label(), ScheduledKind.entries.toList(), { it.label() }) { viewModel.kind = it }
            PickerField("Priority", viewModel.priority.label(), PaymentPriority.entries.toList(), { it.label() }) { viewModel.priority = it }
            if (viewModel.direction == CashFlowDirection.INFLOW) {
                PickerField("Income confidence", viewModel.confidence.name, IncomeConfidence.entries.toList(), { it.name }) { viewModel.confidence = it }
            }
            PickerField("Repeats", viewModel.frequency.label(), RecurrenceFrequency.entries.toList(), { it.label() }) { viewModel.frequency = it }
            if (viewModel.frequency == RecurrenceFrequency.CUSTOM) {
                androidx.compose.material3.OutlinedTextField(
                    value = viewModel.intervalDays,
                    onValueChange = { viewModel.intervalDays = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Every N days") },
                    singleLine = true,
                )
            }
            if (activeAccounts.isNotEmpty()) {
                PickerField("Account", activeAccounts.firstOrNull { it.id == viewModel.accountId }?.paymentLabel() ?: "Choose", activeAccounts, { it.paymentLabel() }) {
                    viewModel.accountId = it.id
                }
            }
            PickerField(
                "Category",
                categories.firstOrNull { it.id == viewModel.categoryId }?.let { CategoryTree.label(it.id, categories) } ?: "None",
                categories.filter { !it.archived },
                { CategoryTree.label(it.id, categories) },
            ) { viewModel.categoryId = it.id }
            DateField(viewModel.start) { viewModel.start = it }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Active", modifier = Modifier.weight(1f))
                Switch(checked = viewModel.active, onCheckedChange = { viewModel.active = it })
            }
            androidx.compose.material3.OutlinedTextField(
                value = viewModel.note,
                onValueChange = { viewModel.note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
            )
            Text("Mandatory and important items on liquid accounts reduce safe-to-spend. Optional and flexible items do not.")
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Save", enabled = activeAccounts.isNotEmpty(), onClick = viewModel::save)
            if (viewModel.isExisting) {
                TextButton(onClick = viewModel::delete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete schedule") }
            }
        }
    }
}
