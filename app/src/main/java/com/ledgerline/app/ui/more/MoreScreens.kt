package com.ledgerline.app.ui.more

import android.content.Intent
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
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@Composable
fun MoreScreen(
    onAccounts: () -> Unit,
    onBudgets: () -> Unit,
    onCategories: () -> Unit,
    onReview: () -> Unit,
    onForecast: () -> Unit,
    onWhatIf: () -> Unit,
    onSubscriptions: () -> Unit,
    onDebt: () -> Unit,
    onDetection: () -> Unit,
    onBank: () -> Unit,
    onLock: () -> Unit,
    onSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("More", style = MaterialTheme.typography.headlineSmall)
        Text("Records stay on this device. Bank passwords are never requested.")
        listOf(
            "Accounts" to onAccounts,
            "Budgets" to onBudgets,
            "Categories" to onCategories,
            "Needs review" to onReview,
            "Cash-flow forecast" to onForecast,
            "What if" to onWhatIf,
            "Subscriptions" to onSubscriptions,
            "Debt" to onDebt,
            "Notification detection" to onDetection,
            "Bank connection" to onBank,
            "App lock" to onLock,
            "Settings and export" to onSettings,
        ).forEach { (label, action) ->
            TextButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(label, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val preferences: UiPreferences,
    private val exportLedger: ExportLedger,
) : ViewModel() {
    val settings = settingsRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val theme = preferences.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeChoice.SYSTEM)
    val screenshots = preferences.screenshotProtection.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _share = MutableSharedFlow<ShareFile>()
    val share = _share.asSharedFlow()
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
}

data class ShareFile(val name: String, val mime: String, val body: String)

@Composable
fun SettingsScreen(contentPadding: PaddingValues, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val screenshots by viewModel.screenshots.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        PickerField("Currency", currency, listOf("USD", "EUR", "GBP", "INR", "CAD"), { it }) { currency = it }
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
        TextButton(onClick = viewModel::replaySetup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Run setup again") }
        viewModel.message?.let { Text(it) }
        Text(
            "Exports and calculations stay on this device. The financial assistant is not part of this version. Nothing here uploads transactions.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text("Categories", style = MaterialTheme.typography.headlineSmall)
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
