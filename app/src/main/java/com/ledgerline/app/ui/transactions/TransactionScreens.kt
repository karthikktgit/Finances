package com.ledgerline.app.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ledgerline.app.data.local.seed.CategoryCatalog
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.TransactionDraft
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.MerchantRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.merchant.MerchantNormalizer
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.policy.FinancePolicy
import com.ledgerline.app.ui.components.AmountField
import com.ledgerline.app.ui.components.ConfirmDialog
import com.ledgerline.app.ui.components.ConfirmDialog
import com.ledgerline.app.ui.components.DateField
import com.ledgerline.app.ui.components.EmptyPane
import com.ledgerline.app.ui.components.KeyValue
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.format.paymentLabel
import com.ledgerline.app.ui.navigation.TransactionDetailRoute
import com.ledgerline.app.ui.navigation.TransactionEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class TransactionFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val accountId: String? = null,
    val range: DateRange = DateRange.ALL,
)

enum class DateRange { ALL, THIS_MONTH, LAST_MONTH }

fun LedgerTransaction.matches(filter: TransactionFilter, today: LocalDate): Boolean {
    if (deletedAtEpochMillis != null) return false
    if (filter.type != null && type != filter.type) return false
    if (filter.accountId != null && accountId != filter.accountId && counterAccountId != filter.accountId) return false
    val month = when (filter.range) {
        DateRange.ALL -> null
        DateRange.THIS_MONTH -> YearMonth.from(today)
        DateRange.LAST_MONTH -> YearMonth.from(today).minusMonths(1)
    }
    if (month != null && (occurredOn.isBefore(month.atDay(1)) || occurredOn.isAfter(month.atEndOfMonth()))) return false
    if (filter.query.isBlank()) return true
    val haystack = "$merchantRaw $merchantNormalized $note $description".lowercase()
    return haystack.contains(filter.query.trim().lowercase())
}

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val clock: AppClock,
) : ViewModel() {
    var filter by mutableStateOf(TransactionFilter())
        private set

    val rows = combine(transactions.observeAll(), accounts.observe(), categories.observe()) { txs, accts, cats ->
        Triple(txs, accts, cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(emptyList(), emptyList(), emptyList()))

    fun setQuery(value: String) { filter = filter.copy(query = value) }
    fun setType(value: TransactionType?) { filter = filter.copy(type = value) }
    fun setAccount(value: String?) { filter = filter.copy(accountId = value) }
    fun setRange(value: DateRange) { filter = filter.copy(range = value) }
    fun today(): LocalDate = clock.today()
}

@Composable
fun TransactionsScreen(
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    val data by viewModel.rows.collectAsStateWithLifecycle()
    val today = viewModel.today()
    val visible = data.first.filter { it.matches(viewModel.filter, today) }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        OutlinedTextField(
            value = viewModel.filter.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            label = { Text("Search merchant or note") },
            singleLine = true,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(viewModel.filter.range == DateRange.ALL, { viewModel.setRange(DateRange.ALL) }, { Text("All dates") })
            FilterChip(viewModel.filter.range == DateRange.THIS_MONTH, { viewModel.setRange(DateRange.THIS_MONTH) }, { Text("This month") })
            FilterChip(viewModel.filter.range == DateRange.LAST_MONTH, { viewModel.setRange(DateRange.LAST_MONTH) }, { Text("Last month") })
            FilterChip(viewModel.filter.type == null, { viewModel.setType(null) }, { Text("All types") })
            TransactionType.entries.forEach { type ->
                FilterChip(viewModel.filter.type == type, { viewModel.setType(type) }, { Text(type.label()) })
            }
        }
        val accounts = data.second.filter { it.active }
        if (accounts.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(viewModel.filter.accountId == null, { viewModel.setAccount(null) }, { Text("All accounts") })
                accounts.forEach { account ->
                    FilterChip(viewModel.filter.accountId == account.id, { viewModel.setAccount(account.id) }, { Text(account.name) })
                }
            }
        }
        TextButton(onClick = onCreate, modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp)) {
            Text("Add transaction")
        }
        if (visible.isEmpty()) {
            EmptyPane(
                "Nothing matches",
                "Try another search, or add an expense. Cash spending is never detected for you.",
                Modifier.padding(horizontal = 20.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(visible, key = { it.id }) { tx ->
                    val account = data.second.firstOrNull { it.id == tx.accountId }
                    val category = CategoryTree.label(tx.categoryId, data.third)
                    TextButton(onClick = { onOpen(tx.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Text(tx.merchantRaw.ifBlank { tx.type.label() }, style = MaterialTheme.typography.titleMedium)
                            Text("$category · ${account?.paymentLabel() ?: "Account"} · ${tx.occurredOn.medium()}")
                            Text("${tx.type.label()} ${MoneyFormat.signedLabel(tx.balanceEffectMinor, tx.currency)}")
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val merchants: MerchantRepository,
    clock: AppClock,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<TransactionEditorRoute>()
    val accountOptions = accounts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = categories.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var type by mutableStateOf(runCatching { TransactionType.valueOf(args.presetType) }.getOrDefault(TransactionType.EXPENSE))
    var amountMinor by mutableStateOf(0L)
    var merchant by mutableStateOf("")
    var accountId by mutableStateOf("")
    var counterAccountId by mutableStateOf("")
    var categoryId by mutableStateOf<String?>(null)
    var date by mutableStateOf(clock.today())
    var note by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var duplicate by mutableStateOf<SaveResult.PossibleDuplicate?>(null)
    private var categoryTouched = false
    private var existingLink: String? = null
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()

    init {
        if (args.transactionId.isNotBlank()) {
            viewModelScope.launch {
                val source = transactions.editingSource(args.transactionId) ?: return@launch
                type = source.type
                amountMinor = source.amountMinor
                merchant = source.merchantRaw
                accountId = source.accountId
                counterAccountId = source.counterAccountId.orEmpty()
                categoryId = source.categoryId
                date = source.occurredOn
                note = source.note
                existingLink = source.linkId
                categoryTouched = true
            }
        }
    }

    fun onMerchant(value: String) {
        merchant = value
        if (!categoryTouched) {
            viewModelScope.launch {
                val suggested = merchants.suggestion(
                    MerchantNormalizer.normalize(value),
                    FinancePolicy.CATEGORY_LEARN_CONFIRMATIONS,
                )
                if (!categoryTouched && suggested != null) categoryId = suggested
            }
        }
    }

    fun onCategory(id: String?) {
        categoryTouched = true
        categoryId = id
    }

    fun save(acknowledgeDuplicate: Boolean) {
        viewModelScope.launch {
            val result = transactions.save(
                TransactionDraft(
                    existingId = args.transactionId.ifBlank { null },
                    existingLinkId = existingLink,
                    type = type,
                    amountMinor = amountMinor,
                    merchant = merchant,
                    accountId = accountId,
                    counterAccountId = counterAccountId.ifBlank { null },
                    categoryId = categoryId,
                    occurredOn = date,
                    note = note,
                    acknowledgeDuplicate = acknowledgeDuplicate,
                ),
            )
            when (result) {
                SaveResult.Saved -> _done.emit(Unit)
                is SaveResult.Invalid -> error = result.message
                is SaveResult.PossibleDuplicate -> duplicate = result
            }
        }
    }

    fun dismissDuplicate() { duplicate = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(onDone: () -> Unit, viewModel: TransactionEditorViewModel = hiltViewModel()) {
    val accounts by viewModel.accountOptions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    val active = accounts.filter { it.active }
    val currency = active.firstOrNull { it.id == viewModel.accountId }?.currency ?: active.firstOrNull()?.currency ?: "USD"
    LaunchedEffect(active) {
        if (viewModel.accountId.isBlank() && active.isNotEmpty()) viewModel.accountId = active.first().id
    }
    val needsCounter = viewModel.type == TransactionType.TRANSFER || viewModel.type == TransactionType.PAYMENT
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.type == TransactionType.EXPENSE) "Expense" else viewModel.type.label()) },
                navigationIcon = { TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PickerField(
                label = "Type",
                value = viewModel.type.label(),
                options = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.REFUND, TransactionType.TRANSFER, TransactionType.PAYMENT),
                optionLabel = { it.label() },
                onSelect = { viewModel.type = it },
            )
            AmountField(viewModel.amountMinor, currency) { viewModel.amountMinor = it }
            if (!needsCounter) {
                OutlinedTextField(
                    value = viewModel.merchant,
                    onValueChange = viewModel::onMerchant,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Merchant") },
                    singleLine = true,
                )
            }
            if (active.isEmpty()) {
                Text("Add an account before recording a transaction.")
            } else {
                PickerField("Account", active.firstOrNull { it.id == viewModel.accountId }?.paymentLabel() ?: "Choose", active, { it.paymentLabel() }) {
                    viewModel.accountId = it.id
                }
            }
            if (needsCounter) {
                PickerField(
                    "Other account",
                    active.firstOrNull { it.id == viewModel.counterAccountId }?.paymentLabel() ?: "Choose",
                    active.filter { it.id != viewModel.accountId },
                    { it.paymentLabel() },
                ) { viewModel.counterAccountId = it.id }
            }
            val picks = categories.filter { it.id in CategoryCatalog.quickPicks && !it.archived }
            if (picks.isNotEmpty() && !needsCounter) {
                Text("Category")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    picks.forEach { category ->
                        FilterChip(viewModel.categoryId == category.id, { viewModel.onCategory(category.id) }, { Text(category.name) })
                    }
                }
                PickerField(
                    "More categories",
                    categories.firstOrNull { it.id == viewModel.categoryId }?.let { CategoryTree.label(it.id, categories) } ?: "None",
                    categories.filter { !it.archived },
                    { CategoryTree.label(it.id, categories) },
                ) { viewModel.onCategory(it.id) }
            }
            DateField(viewModel.date) { viewModel.date = it }
            OutlinedTextField(
                value = viewModel.note,
                onValueChange = { viewModel.note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
            )
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Save", enabled = active.isNotEmpty()) { viewModel.save(acknowledgeDuplicate = false) }
        }
    }
    viewModel.duplicate?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(if (prompt.score >= FinancePolicy.DUPLICATE_MERGE_SCORE) "This matches an existing transaction" else "Possible duplicate") },
            text = { Text("${prompt.summary} Saving another record would count it twice. Nothing is deleted.") },
            confirmButton = { TextButton(onClick = viewModel::dismissDuplicate) { Text("Keep existing") } },
            dismissButton = {
                TextButton(onClick = { viewModel.save(acknowledgeDuplicate = true) }) { Text("Save anyway") }
            },
        )
    }
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<TransactionDetailRoute>()
    val state = combine(transactions.observeAll(), accounts.observe(), categories.observe()) { txs, accts, cats ->
        Triple(txs.firstOrNull { it.id == args.transactionId }, accts, cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(null, emptyList(), emptyList()))

    private val _deleted = MutableSharedFlow<String>()
    val deleted = _deleted.asSharedFlow()

    fun delete() {
        viewModelScope.launch {
            transactions.softDelete(args.transactionId)
            _deleted.emit(args.transactionId)
        }
    }

    fun restore(id: String) {
        viewModelScope.launch { transactions.restore(id) }
    }

    fun setStatus(status: TransactionStatus) {
        viewModelScope.launch { transactions.setStatus(args.transactionId, status) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.deleted.collect { id ->
            val result = snackbar.showSnackbar("Transaction removed from balances", actionLabel = "Undo")
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.restore(id)
            onBack()
        }
    }
    val tx = state.first
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction") },
                navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tx == null || tx.deletedAtEpochMillis != null) {
                Text("This transaction is not in the active ledger.")
            } else {
                Text(tx.merchantRaw.ifBlank { tx.type.label() }, style = MaterialTheme.typography.headlineSmall)
                Text(MoneyFormat.signedLabel(tx.balanceEffectMinor, tx.currency), style = MaterialTheme.typography.displaySmall)
                KeyValue("Type", tx.type.label())
                KeyValue("Category", CategoryTree.label(tx.categoryId, state.third))
                KeyValue("Account", state.second.firstOrNull { it.id == tx.accountId }?.paymentLabel() ?: "Unknown")
                tx.counterAccountId?.let { counter ->
                    KeyValue("Other account", state.second.firstOrNull { it.id == counter }?.paymentLabel() ?: "Unknown")
                }
                KeyValue("Date", tx.occurredOn.medium())
                KeyValue("Detection", tx.detectionSource.label())
                KeyValue("Confidence", "${tx.confidencePercent}%")
                KeyValue("Status", tx.status.name.lowercase())
                if (tx.note.isNotBlank()) KeyValue("Notes", tx.note)
                TextButton(onClick = { onEdit(tx.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
                if (tx.status == TransactionStatus.DETECTED) {
                    TextButton(onClick = { viewModel.setStatus(TransactionStatus.CONFIRMED) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Count this transaction") }
                }
                TextButton(onClick = { viewModel.setStatus(TransactionStatus.DUPLICATE) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Mark duplicate") }
                TextButton(onClick = { viewModel.setStatus(TransactionStatus.IGNORED) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ignore") }
                TextButton(onClick = { confirm = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove from balances") }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Remove this transaction?",
            body = "Balances will update. The record stays on this device and you can undo from the notice that follows.",
            confirm = "Remove",
            onConfirm = {
                confirm = false
                viewModel.delete()
            },
            onDismiss = { confirm = false },
        )
    }
}
