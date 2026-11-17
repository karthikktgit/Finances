package com.ledgerline.app.ui.more

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.TaskAlt
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import kotlinx.coroutines.flow.map
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.ThemeChoice
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.usecase.ExportLedger
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.validation.TransactionRules
import com.ledgerline.app.preferences.UiPreferences
import com.ledgerline.app.ui.components.AmountField
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    private val preferences: UiPreferences,
    accounts: com.ledgerline.app.domain.repository.AccountRepository,
    transactions: com.ledgerline.app.domain.repository.TransactionRepository,
    private val freshStart: com.ledgerline.app.domain.usecase.FreshStart,
    private val financialReset: com.ledgerline.app.data.repository.FinancialDataReset,
    private val homeGroups: com.ledgerline.app.domain.repository.HomeGroupRepository,
) : ViewModel() {
    /** Each account with what it holds now (or owes, for a card or loan), to start the fresh-cycle form. */
    val balances = kotlinx.coroutines.flow.combine(accounts.observe(), transactions.observeAll()) { rows, txs ->
        rows.filter { it.active }.map { account ->
            val balance = com.ledgerline.app.finance.balance.BalanceMath.currentBalanceMinor(account, txs)
            account to if (com.ledgerline.app.finance.balance.BalanceMath.isDebtType(account.type)) -balance else balance
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var message by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    /** A new pay cycle from today with these balances; history stays but no longer counts toward it. */
    fun startFresh(balances: Map<String, Long>, keepSchedules: Boolean) {
        viewModelScope.launch {
            freshStart.start(balances, keepSchedules)
            message = "A fresh cycle started today."
        }
    }

    /** Erases every financial record on this phone. Settings such as theme and reminders stay. */
    fun deleteAll() {
        viewModelScope.launch {
            financialReset.resetFinancialData()
            homeGroups.ensureDefaults()
            preferences.setPayIncome(null)
            preferences.setSampleData(false)
            message = "All data was deleted."
        }
    }

    val captureOn = preferences.notificationDetection.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val lockOn = preferences.appLockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val accountCount = accounts.observe().map { rows -> rows.count { it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

@Composable
fun MoreScreen(
    onBack: () -> Unit,
    onAccounts: () -> Unit,
    onPlan: () -> Unit = {},
    onCategories: () -> Unit,
    onReview: () -> Unit,
    onCaptureSetup: () -> Unit,
    onReminders: () -> Unit,
    onBank: () -> Unit,
    onLock: () -> Unit,
    onSettings: () -> Unit,
    onSalaryCycle: () -> Unit,
    onCycleSetup: (next: Boolean) -> Unit = {},
    onCycleReview: () -> Unit = {},
    viewModel: SettingsHubViewModel = hiltViewModel(),
) {
    val captureOn by viewModel.captureOn.collectAsStateWithLifecycle()
    val lockOn by viewModel.lockOn.collectAsStateWithLifecycle()
    val accountCount by viewModel.accountCount.collectAsStateWithLifecycle()
    com.ledgerline.app.ui.components.SubScreen("Settings", onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MoreHeading("Your money")
            com.ledgerline.app.ui.components.GroupCard {
                MoreRow(Icons.Filled.AccountBalance, "Accounts", if (accountCount == 1) "1 account" else "$accountCount accounts", onAccounts)
                MoreRow(Icons.Filled.TaskAlt, "Plan details", "Income, subscriptions, loans, spending notes", onPlan)
                MoreRow(Icons.Filled.Event, "Pay cycle", "The dates your month runs between", onSalaryCycle)
                MoreRow(Icons.Filled.Category, "Categories", "Add or hide categories", onCategories)
            }
            MoreHeading("Automation")
            com.ledgerline.app.ui.components.GroupCard {
                MoreRow(Icons.Filled.AutoAwesome, "Auto-capture spends", if (captureOn) "On · from texts, email, and wallets" else "Off", onCaptureSetup)
                MoreRow(Icons.Filled.Inbox, "Needs checking", "Detected transactions not yet counted", onReview)
                MoreRow(Icons.Filled.Notifications, "Reminders", "Bills due, low balance, budgets", onReminders)
            }
            MoreHeading("Each cycle")
            com.ledgerline.app.ui.components.GroupCard {
                MoreRow(Icons.Filled.EventAvailable, "Set up this cycle", "Statements, balances, and what this cycle starts with") { onCycleSetup(false) }
                MoreRow(Icons.Filled.EditCalendar, "Plan next cycle", "Set next cycle's amounts ahead of time") { onCycleSetup(true) }
                MoreRow(Icons.Filled.Insights, "Cycle review", "This cycle vs the last, and what carries on or ends next cycle") { onCycleReview() }
            }
            MoreHeading("Start over")
            var freshOpen by remember { mutableStateOf(false) }
            var confirmWipe by remember { mutableStateOf(false) }
            com.ledgerline.app.ui.components.GroupCard {
                MoreRow(Icons.Filled.Autorenew, "Start a fresh cycle", "From today, with your real balances. History is kept.") { freshOpen = true }
                MoreRow(Icons.Filled.DeleteForever, "Delete all data", "Erase everything and begin again. Cannot be undone.") { confirmWipe = true }
            }
            Text(
                "To delete one transaction, swipe it left in Activity or open it and tap the bin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            viewModel.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            if (freshOpen) {
                val rows by viewModel.balances.collectAsStateWithLifecycle()
                FreshStartSheet(rows, onDone = { balances, keep -> viewModel.startFresh(balances, keep); freshOpen = false }, onDismiss = { freshOpen = false })
            }
            if (confirmWipe) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmWipe = false },
                    icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Delete all data?") },
                    text = {
                        Text(
                            "This erases every account, transaction, bill, spending item, goal, and captured spend on this phone. It cannot be undone. " +
                                "Your settings (theme, reminders, lock) stay. To remove just one transaction, delete it in Activity instead.",
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmWipe = false; viewModel.deleteAll() }) {
                            Text("Delete everything", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
                )
            }
            MoreHeading("App")
            com.ledgerline.app.ui.components.GroupCard {
                MoreRow(Icons.Filled.Lock, "App lock", if (lockOn) "On" else "Off", onLock)
                MoreRow(Icons.Filled.Tune, "Preferences and export", "Currency, buffer, theme, Excel and CSV, reset", onSettings)
                if (com.ledgerline.app.BuildConfig.DEBUG) {
                    MoreRow(Icons.Filled.Science, "Bank fixture (developer)", "Imports test data; no real bank", onBank)
                }
            }
            Text(
                "Everything stays on this phone. Ledgerline ${com.ledgerline.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun MoreHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
    )
}

@Composable
private fun MoreRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    com.ledgerline.app.ui.components.ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
    ) {
        androidx.compose.material3.Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val preferences: UiPreferences,
    private val exportLedger: ExportLedger,
    private val readModel: com.ledgerline.app.domain.repository.LedgerReadModel,
    private val accountsRepository: com.ledgerline.app.domain.repository.AccountRepository,
    private val financialReset: com.ledgerline.app.data.repository.FinancialDataReset,
    private val clock: com.ledgerline.app.domain.time.AppClock,
) : ViewModel() {
    val settings = settingsRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val theme = preferences.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeChoice.SYSTEM)
    val screenshots = preferences.screenshotProtection.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val resetOnLaunch = preferences.resetOnLaunch.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val accounts = accountsRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _share = MutableSharedFlow<ShareFile>()
    val share = _share.asSharedFlow()
    private val _bytes = MutableSharedFlow<ShareBinary>()
    val bytes = _bytes.asSharedFlow()
    var message by mutableStateOf<String?>(null)

    fun save(currency: String, bufferMinor: Long, horizon: Int) {
        viewModelScope.launch {
            val current = settings.value ?: return@launch
            settingsRepository.update(
                current.copy(
                    primaryCurrency = currency,
                    minimumBufferMinor = bufferMinor,
                    horizonDays = TransactionRules.clampHorizon(horizon),
                ),
            )
            message = "Saved. Existing amounts stay in the currency they were recorded in."
        }
    }

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { preferences.setTheme(choice) }
    }

    fun setScreenshots(enabled: Boolean) {
        viewModelScope.launch { preferences.setScreenshotProtection(enabled) }
    }

    fun replaySetup() {
        viewModelScope.launch {
            val current = settings.value ?: return@launch
            settingsRepository.update(current.copy(onboardingCompleted = false))
        }
    }

    fun export(json: Boolean) {
        viewModelScope.launch {
            val body = if (json) exportLedger.json() else exportLedger.csv()
            _share.emit(ShareFile(if (json) "ledgerline.json" else "ledgerline.csv", if (json) "application/json" else "text/csv", body))
        }
    }

    fun exportWorkbook(accountId: String?, allDates: Boolean) {
        viewModelScope.launch {
            val current = settings.value ?: return@launch
            val period = if (allDates) null else com.ledgerline.app.finance.period.CycleSchedule.containing(clock.today(), current)
            val accountName = accountId?.let { id -> readModel.accounts().firstOrNull { it.id == id }?.name } ?: "all"
            val accounts = readModel.accountsIncludingDeleted()
            val transactions = readModel.transactions()
            val recurring = readModel.recurring()
            val categories = readModel.categories()
            val bytes = withContext(Dispatchers.Default) {
                com.ledgerline.app.finance.export.XlsxReports.ledger(
                    today = clock.today(),
                    versionName = com.ledgerline.app.BuildConfig.VERSION_NAME,
                    accounts = accounts,
                    transactions = transactions,
                    recurring = recurring,
                    period = period,
                    accountId = accountId,
                    categories = categories,
                )
            }
            val range = if (allDates) "all-dates" else "pay-period"
            _bytes.emit(
                ShareBinary(
                    "ledgerline-$range-$accountName.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bytes,
                ),
            )
        }
    }

    fun setResetOnLaunch(enabled: Boolean) {
        viewModelScope.launch { preferences.setResetOnLaunch(enabled) }
    }

    fun resetFinancialData() {
        viewModelScope.launch {
            financialReset.resetFinancialData()
            preferences.setSampleData(false)
            message = "Financial records were deleted. Settings were kept."
        }
    }
}

data class ShareFile(val name: String, val mime: String, val body: String)

data class ShareBinary(val name: String, val mime: String, val bytes: ByteArray)

@Composable
fun SettingsScreen(contentPadding: PaddingValues, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val screenshots by viewModel.screenshots.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val resetOnLaunch by viewModel.resetOnLaunch.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var exportAllDates by remember { mutableStateOf(false) }
    var exportAccountId by remember { mutableStateOf<String?>(null) }
    var pendingWorkbook by remember { mutableStateOf<ShareBinary?>(null) }
    val createWorkbook = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        val file = pendingWorkbook
        pendingWorkbook = null
        if (uri == null || file == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) }
    }
    var currency by remember { mutableStateOf("USD") }
    var buffer by remember { mutableStateOf(0L) }
    var horizon by remember { mutableStateOf("30") }
    LaunchedEffect(settings?.primaryCurrency, settings?.minimumBufferMinor, settings?.horizonDays) {
        settings?.let {
            currency = it.primaryCurrency
            buffer = it.minimumBufferMinor
            horizon = it.horizonDays.toString()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.share.collect { file ->
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val out = File(dir, file.name)
            out.writeText(file.body)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", out)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = file.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export ledger"))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.bytes.collect { file ->
            pendingWorkbook = file
            createWorkbook.launch(file.name)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PickerField("Currency", currency, com.ledgerline.app.ui.onboarding.OnboardingCurrencies.list, { it }) { currency = it }
        Text("Changing currency does not convert existing records.")
        AmountField(buffer, currency) { buffer = it }
        Text("Minimum buffer kept out of safe-to-spend. Currently ${MoneyFormat.format(buffer, currency)}.")
        OutlinedTextField(
            value = horizon,
            onValueChange = { horizon = it.filter(Char::isDigit).take(3) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Horizon in days (7–120)") },
            singleLine = true,
        )
        PrimaryButton("Save financial settings") {
            viewModel.save(currency, buffer, horizon.toIntOrNull() ?: 30)
        }
        PickerField("Theme", theme.name.lowercase().replaceFirstChar { it.titlecase() }, ThemeChoice.entries.toList(), { it.name.lowercase().replaceFirstChar { c -> c.titlecase() } }) {
            viewModel.setTheme(it)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Block screenshots")
                Text("Hides Ledgerline from screenshots and the app switcher.", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = screenshots, onCheckedChange = viewModel::setScreenshots)
        }
        TextButton(onClick = { viewModel.export(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Export CSV") }
        TextButton(onClick = { viewModel.export(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Export JSON") }
        Text("Excel workbook", style = MaterialTheme.typography.titleMedium)
        PickerField(
            "Report dates",
            if (exportAllDates) "All dates" else "Current pay period",
            listOf(false, true),
            { if (it) "All dates" else "Current pay period" },
        ) { exportAllDates = it }
        PickerField(
            "Accounts",
            accounts.firstOrNull { it.id == exportAccountId }?.name ?: "All accounts",
            listOf<String?>(null) + accounts.map { it.id },
            { id -> accounts.firstOrNull { it.id == id }?.name ?: "All accounts" },
        ) { exportAccountId = it }
        TextButton(
            onClick = { viewModel.exportWorkbook(exportAccountId, exportAllDates) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                when {
                    exportAccountId == null && !exportAllDates -> "Export current pay period, all accounts"
                    exportAccountId == null && exportAllDates -> "Export all dates, all accounts"
                    !exportAllDates -> "Export current pay period, selected account"
                    else -> "Export all dates, selected account"
                },
            )
        }
        Text("About", style = MaterialTheme.typography.titleMedium)
        com.ledgerline.app.ui.components.BrandMark()
        Text("Ledgerline ${com.ledgerline.app.BuildConfig.VERSION_NAME} (${com.ledgerline.app.BuildConfig.VERSION_CODE})")
        Text("Version history is in CHANGELOG.md. A normal update keeps your accounts and transactions.")
        Text("Data management", style = MaterialTheme.typography.titleMedium)
        Text("Reset deletes accounts, transactions, schedules, budgets, and debts on this device. Settings stay.")
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reset all financial data") }
        if (com.ledgerline.app.BuildConfig.DEBUG) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Reset financial data on next launch", modifier = Modifier.weight(1f))
                Switch(checked = resetOnLaunch, onCheckedChange = viewModel::setResetOnLaunch)
            }
            Text("Debug only, and off unless you turn it on. Release builds ignore it.")
        }
        TextButton(onClick = viewModel::replaySetup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Run setup again") }
        viewModel.message?.let { Text(it) }
        Text(
            "Exports and calculations stay on this device. The financial assistant is not part of this version. Nothing here uploads transactions.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirmReset) {
        com.ledgerline.app.ui.components.ConfirmDialog(
            title = "Delete all financial records?",
            body = "Accounts, transactions, schedules, budgets, and debts on this device will be removed. Settings stay. Export a workbook first if you want a copy.",
            confirm = "Delete records",
            onConfirm = {
                confirmReset = false
                viewModel.resetFinancialData()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@HiltViewModel
class CategoryManagerViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {
    val categories = repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var name by mutableStateOf("")
    var parentId by mutableStateOf<String?>(null)
    var necessity by mutableStateOf(Necessity.DISCRETIONARY)

    fun add() {
        viewModelScope.launch {
            repository.addCustom(name, parentId, necessity)
            name = ""
        }
    }

    fun archive(id: String, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(id, archived) }
    }
}

@Composable
fun CategoriesScreen(contentPadding: PaddingValues, viewModel: CategoryManagerViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(viewModel.name, { viewModel.name = it }, Modifier.fillMaxWidth(), label = { Text("New category") }, singleLine = true)
        PickerField(
            "Parent",
            categories.firstOrNull { it.id == viewModel.parentId }?.name ?: "Top level",
            listOf<String?>(null) + categories.filter { it.parentId == null }.map { it.id },
            { id -> categories.firstOrNull { it.id == id }?.name ?: "Top level" },
        ) { viewModel.parentId = it }
        PickerField("Label", viewModel.necessity.name, Necessity.entries.toList(), { it.name }) { viewModel.necessity = it }
        PrimaryButton("Add category", onClick = viewModel::add)
        categories.sortedBy { it.sortOrder }.forEach { category ->
            TextButton(onClick = { viewModel.archive(category.id, !category.archived) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(
                    "${CategoryTree.label(category.id, categories)} · ${category.necessity.name.lowercase()}${if (category.archived) " · archived" else ""}",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Start a fresh cycle in three short steps: what happens, your balances now, and what to do with scheduled payments.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FreshStartSheet(
    rows: List<Pair<com.ledgerline.app.domain.model.Account, Long>>,
    onDone: (Map<String, Long>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    val texts = remember(rows.size) { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { rows.forEach { (a, b) -> put(a.id, com.ledgerline.app.finance.money.AmountInput.fromMinor(b.coerceAtLeast(0), 2).ifEmpty { "0" }) } } }
    var keep by remember { mutableStateOf(true) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Start a fresh cycle", style = MaterialTheme.typography.titleLarge)
            Text("Step ${step + 1} of 3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            when (step) {
                0 -> {
                    Text("A new pay cycle starts today with a clean slate.", style = MaterialTheme.typography.bodyLarge)
                    Text("• Your past transactions stay in Activity, but no longer count toward this cycle's spending.", style = MaterialTheme.typography.bodyMedium)
                    Text("• You set each account to what it really holds now.", style = MaterialTheme.typography.bodyMedium)
                    Text("• You choose to keep or remove your scheduled payments.", style = MaterialTheme.typography.bodyMedium)
                }
                1 -> {
                    Text("How much is in each account now?", style = MaterialTheme.typography.bodyLarge)
                    rows.forEach { (account, _) ->
                        val debt = com.ledgerline.app.finance.balance.BalanceMath.isDebtType(account.type)
                        androidx.compose.material3.OutlinedTextField(
                            value = texts[account.id].orEmpty(),
                            onValueChange = { texts[account.id] = com.ledgerline.app.finance.money.AmountInput.clean(it, 2) },
                            label = { Text(if (debt) "${account.name} · owed" else account.name) },
                            prefix = { Text("$") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> {
                    Text("Your scheduled payments", style = MaterialTheme.typography.bodyLarge)
                    ChoiceCard("Keep them", "Bills and income carry on as set up. Any still unpaid from before today are skipped, so nothing old carries over.", keep) { keep = true }
                    ChoiceCard("Remove them all", "Start completely fresh: every scheduled bill, income, and planned spend is removed. Add new ones as you go.", !keep) { keep = false }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 0) androidx.compose.material3.OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Back") }
                androidx.compose.material3.Button(
                    onClick = {
                        if (step < 2) {
                            step++
                        } else {
                            onDone(rows.associate { (a, _) -> a.id to com.ledgerline.app.finance.money.AmountInput.toMinor(texts[a.id].orEmpty(), 2) }, keep)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (step < 2) "Next" else "Start fresh cycle") }
            }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
