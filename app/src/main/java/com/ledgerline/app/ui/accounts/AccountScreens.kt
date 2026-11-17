package com.ledgerline.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.ledgerline.app.domain.model.AccountDraft
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.account.AccountDeletion
import com.ledgerline.app.finance.account.AccountDeletionImpact
import com.ledgerline.app.finance.balance.AccountProjection
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.schedule.OccurrenceMaterializer
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.policy.FinancePolicy
import com.ledgerline.app.ui.components.AmountField
import com.ledgerline.app.ui.components.EmptyPane
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.format.paymentLabel
import com.ledgerline.app.ui.navigation.AccountEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accounts: AccountRepository,
    transactions: TransactionRepository,
    recurring: RecurringRepository,
    private val clock: AppClock,
) : ViewModel() {
    internal val state = combine(
        accounts.observe(),
        transactions.observeAll(),
        recurring.observe(),
        recurring.observeAdjustments(),
    ) { accts, txs, series, adjustments ->
        val today = clock.today()
        val settled = txs.mapNotNull { transaction ->
            val recurringId = transaction.recurringPaymentId ?: return@mapNotNull null
            if (!BalanceMath.isActive(transaction)) return@mapNotNull null
            recurringId to transaction.occurredOn
        }.toSet()
        val scheduled = OccurrenceMaterializer.project(
            items = series,
            adjustments = adjustments,
            settled = settled,
            start = today,
            end = today.plusYears(1),
            today = today,
        ).filter { it.state == OccurrenceState.SCHEDULED }
        accts.map { account ->
            val actual = BalanceMath.currentBalanceMinor(account, txs)
            AccountRow(account, actual, AccountProjection.projectedMinor(actual, account.id, scheduled))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Repeating items paused because their account was deleted, paired with the deleted account's name. */
    internal val needsAccount = combine(accounts.observeIncludingDeleted(), recurring.observe()) { all, series ->
        val deleted = all.filter { it.deleted }
        AccountDeletion.needsAccount(series, deleted.map { it.id }.toSet()).map { item ->
            item to AccountDeletion.label(item.accountId, all)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

internal data class AccountRow(
    val account: com.ledgerline.app.domain.model.Account,
    val actualMinor: Long,
    val projectedMinor: Long,
)

@Composable
fun AccountsScreen(
    onEdit: (String) -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
    onEditSchedule: (String) -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val rows by viewModel.state.collectAsStateWithLifecycle()
    val needsAccount by viewModel.needsAccount.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add account") }
        if (rows.isEmpty()) EmptyPane("No accounts", "A cash wallet is a real account. Add it if you spend cash.")
        Text("Actual is money already posted. Projected adds unpaid scheduled items on that account and is not cash you have spent.")
        rows.forEach { row ->
            val account = row.account
            TextButton(onClick = { onEdit(account.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(account.paymentLabel(), style = MaterialTheme.typography.titleMedium)
                    Text("${account.type.label()} · ${account.institution.ifBlank { "No institution" }} · ${if (account.active) "Active" else "Inactive"}")
                    Text("Actual ${MoneyFormat.format(row.actualMinor, account.currency)}")
                    Text("Projected ${MoneyFormat.format(row.projectedMinor, account.currency)}")
                    val credit = BalanceMath.availableCreditMinor(account, row.actualMinor)
                    if (credit != null) Text("Available credit ${MoneyFormat.format(credit, account.currency)}")
                }
            }
        }
        if (needsAccount.isNotEmpty()) {
            Text("Needs a new account", style = MaterialTheme.typography.titleMedium)
            Text("These repeating items were paused when their account was deleted. Open one to choose an account and turn it back on.")
            needsAccount.forEach { (item, accountLabel) ->
                TextButton(onClick = { onEditSchedule(item.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        Text("$accountLabel · ${MoneyFormat.format(item.amountMinor, item.currency)} · ${if (item.active) "On" else "Paused"}")
                    }
                }
            }
        }
    }
}

@HiltViewModel
class AccountEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accounts: AccountRepository,
    transactions: TransactionRepository,
    recurring: RecurringRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<AccountEditorRoute>()
    val accountId = args.accountId
    val isExisting = accountId.isNotBlank()
    val balances = combine(accounts.observe(), transactions.observeAll()) { accts, txs ->
        accts.associate { it.id to BalanceMath.currentBalanceMinor(it, txs) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val activity = combine(
        transactions.observeAll(),
        recurring.observe(),
        recurring.observeAdjustments(),
    ) { txs, series, adjustments ->
        if (accountId.isBlank()) {
            emptyList<LedgerTransaction>() to emptyList<com.ledgerline.app.finance.schedule.ProjectedOccurrence>()
        } else {
            val today = clock.today()
            val settled = txs.mapNotNull { transaction ->
                val recurringId = transaction.recurringPaymentId ?: return@mapNotNull null
                if (!BalanceMath.isActive(transaction)) return@mapNotNull null
                recurringId to transaction.occurredOn
            }.toSet()
            val scheduled = OccurrenceMaterializer.project(
                items = series,
                adjustments = adjustments,
                settled = settled,
                start = today.minusYears(1),
                end = today.plusYears(1),
                today = today,
            ).filter { it.accountId == accountId }
            txs.filter { BalanceMath.isActive(it) && (it.accountId == accountId || it.counterAccountId == accountId) } to scheduled
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<LedgerTransaction>() to emptyList())
    var name by mutableStateOf("")
    var institution by mutableStateOf("")
    var type by mutableStateOf(AccountType.CHECKING)
    var lastFour by mutableStateOf("")
    var balanceMinor by mutableStateOf(0L)
    var applyBalance by mutableStateOf(!isExisting)
    var limitMinor by mutableStateOf(0L)
    var hasLimit by mutableStateOf(false)
    var active by mutableStateOf(true)
    var netWorth by mutableStateOf(true)
    var safe by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var pendingDelete by mutableStateOf<AccountDeletionImpact?>(null)
        private set
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()

    init {
        if (isExisting) {
            viewModelScope.launch {
                val account = accounts.get(args.accountId) ?: return@launch
                name = account.name
                institution = account.institution
                type = account.type
                lastFour = account.lastFour.orEmpty()
                active = account.active
                netWorth = account.includeInNetWorth
                safe = account.includeInSafeToSpend
                hasLimit = account.creditLimitMinor != null
                limitMinor = account.creditLimitMinor ?: 0L
                val current = balances.first { it.containsKey(account.id) }[account.id] ?: 0L
                balanceMinor = abs(current)
                applyBalance = false
            }
        } else {
            safe = type in FinancePolicy.defaultLiquidTypes
        }
    }

    fun save() {
        viewModelScope.launch {
            val owed = BalanceMath.isDebtType(type)
            val entered = if (owed && !isExisting) balanceMinor else balanceMinor
            val result = accounts.save(
                AccountDraft(
                    existingId = args.accountId.ifBlank { null },
                    name = name,
                    institution = institution,
                    type = type,
                    lastFour = lastFour,
                    enteredBalanceMinor = entered,
                    applyBalance = applyBalance,
                    creditLimitMinor = if (hasLimit) limitMinor else null,
                    active = active,
                    includeInNetWorth = netWorth,
                    includeInSafeToSpend = safe,
                ),
            )
            when (result) {
                SaveResult.Saved -> _done.emit(Unit)
                is SaveResult.Invalid -> error = result.message
                is SaveResult.PossibleDuplicate -> Unit
            }
        }
    }

    /** Opens the confirmation. Nothing is changed until [confirmDelete]. */
    fun requestDelete() {
        if (!isExisting) return
        viewModelScope.launch { pendingDelete = accounts.deletionImpact(accountId) }
    }

    fun cancelDelete() {
        pendingDelete = null
    }

    fun confirmDelete() {
        pendingDelete = null
        viewModelScope.launch {
            when (val result = accounts.delete(accountId)) {
                SaveResult.Saved -> _done.emit(Unit)
                is SaveResult.Invalid -> error = result.message
                is SaveResult.PossibleDuplicate -> Unit
            }
        }
    }

    fun syncDisplayedBalance() {
        if (!isExisting || applyBalance) return
        val current = balances.value[accountId] ?: return
        balanceMinor = abs(current)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorScreen(onDone: () -> Unit, viewModel: AccountEditorViewModel = hiltViewModel()) {
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    val currency = "USD"
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (viewModel.isExisting) "Edit account" else "New account") },
            navigationIcon = { TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(viewModel.name, { viewModel.name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(viewModel.institution, { viewModel.institution = it }, Modifier.fillMaxWidth(), label = { Text("Institution") }, singleLine = true)
            PickerField("Type", viewModel.type.label(), AccountType.entries.toList(), { it.label() }) {
                viewModel.type = it
                if (!viewModel.isExisting) viewModel.safe = it in FinancePolicy.defaultLiquidTypes
            }
            OutlinedTextField(
                viewModel.lastFour,
                { viewModel.lastFour = it.filter(Char::isDigit).take(4) },
                Modifier.fillMaxWidth(),
                label = { Text("Last four digits") },
                singleLine = true,
            )
            Text(if (BalanceMath.isDebtType(viewModel.type)) "Amount owed" else "Current balance")
            AmountField(viewModel.balanceMinor, currency) {
                viewModel.balanceMinor = it
                if (viewModel.isExisting) viewModel.applyBalance = true
            }
            if (viewModel.isExisting) {
                val current = balances[viewModel.accountId]
                Text("Leave the amount unchanged to keep the ledger balance. Editing it records an adjustment, not a silent overwrite.")
                TextButton(onClick = viewModel::syncDisplayedBalance, modifier = Modifier.heightIn(min = 48.dp)) { Text("Load current balance into the field") }
                if (current != null) Text("Ledger balance now: ${MoneyFormat.format(current, currency)}")
            }
            if (viewModel.type == AccountType.CREDIT_CARD) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Set a credit limit", modifier = Modifier.weight(1f))
                    Switch(viewModel.hasLimit, { viewModel.hasLimit = it })
                }
                if (viewModel.hasLimit) AmountField(viewModel.limitMinor, currency) { viewModel.limitMinor = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active", modifier = Modifier.weight(1f))
                Switch(viewModel.active, { viewModel.active = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Include in net worth", modifier = Modifier.weight(1f))
                Switch(viewModel.netWorth, { viewModel.netWorth = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Include in safe-to-spend", modifier = Modifier.weight(1f))
                Switch(viewModel.safe, { viewModel.safe = it })
            }
            Text("Sync status: manual. Bank connections are not part of this version.")
            if (viewModel.isExisting) {
                Text("Transactions on this account", style = MaterialTheme.typography.titleMedium)
                if (activity.first.isEmpty()) Text("No posted transactions yet.")
                activity.first.sortedByDescending { it.occurredOn }.forEach { tx ->
                    Text("${tx.occurredOn} · ${tx.type.label()} · ${tx.merchantRaw.ifBlank { tx.type.label() }} · ${MoneyFormat.signedLabel(tx.balanceEffectMinor, tx.currency)}")
                }
                Text("Scheduled payments", style = MaterialTheme.typography.titleMedium)
                if (activity.second.isEmpty()) Text("No scheduled payments on this account.")
                activity.second.forEach { item ->
                    Text("${item.date} · ${item.name} · ${item.state.name} · ${MoneyFormat.format(item.amountMinor, "USD")}")
                }
            }
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Save account", onClick = viewModel::save)
            if (viewModel.isExisting) {
                TextButton(onClick = viewModel::requestDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Delete Account", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    viewModel.pendingDelete?.let { impact ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete ${viewModel.name.ifBlank { "this account" }}?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(AccountDeletion.CONFIRMATION)
                    if (impact.empty) {
                        Text("This account has no transactions, schedules, debts, or budgets.")
                    } else {
                        impact.lines().forEach { Text("• $it") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Delete Account", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
            },
        )
    }
}
