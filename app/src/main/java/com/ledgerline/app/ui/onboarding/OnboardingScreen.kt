package com.ledgerline.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.AccountDraft
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.DemoSeeder
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.finance.policy.FinancePolicy
import com.ledgerline.app.ui.components.AmountField
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.format.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val accounts: AccountRepository,
    private val demoSeeder: DemoSeeder,
) : ViewModel() {
    var error by mutableStateOf<String?>(null)

    fun skip() {
        viewModelScope.launch {
            val current = settingsRepository.observe().first()
            settingsRepository.update(current.copy(onboardingCompleted = true))
        }
    }

    fun loadSample() {
        viewModelScope.launch { demoSeeder.seedIfEmpty() }
    }

    fun finish(currency: String, bufferMinor: Long, name: String, type: AccountType, balanceMinor: Long) {
        viewModelScope.launch {
            val current = settingsRepository.observe().first()
            settingsRepository.update(
                current.copy(primaryCurrency = currency, minimumBufferMinor = bufferMinor, onboardingCompleted = true),
            )
            if (name.isBlank()) return@launch
            val result = accounts.save(
                AccountDraft(
                    existingId = null,
                    name = name,
                    institution = "",
                    type = type,
                    lastFour = "",
                    enteredBalanceMinor = balanceMinor,
                    applyBalance = true,
                    creditLimitMinor = null,
                    active = true,
                    includeInNetWorth = true,
                    includeInSafeToSpend = type in FinancePolicy.defaultLiquidTypes,
                ),
            )
            if (result is SaveResult.Invalid) error = result.message
        }
    }
}

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var currency by rememberSaveable { mutableStateOf("USD") }
    var buffer by rememberSaveable { mutableStateOf(FinancePolicy.DEFAULT_BUFFER_MINOR) }
    var name by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf(AccountType.CHECKING.name) }
    var balance by rememberSaveable { mutableStateOf(0L) }
    val type = runCatching { AccountType.valueOf(typeName) }.getOrDefault(AccountType.CHECKING)
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            0 -> {
                Text("Ledgerline", style = MaterialTheme.typography.headlineMedium)
                Text("See what is left to spend after money you already owe, bills that are scheduled, and income you expect.")
                Text("Estimates stay on this device. They are not a promise that the money will be there.")
                PrimaryButton("Continue") { step = 1 }
                TextButton(onClick = viewModel::skip, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip and set up later") }
                TextButton(onClick = viewModel::loadSample, modifier = Modifier.heightIn(min = 48.dp)) { Text("Explore with sample finances") }
            }
            1 -> {
                Text("Currency and buffer", style = MaterialTheme.typography.headlineSmall)
                PickerField("Currency", currency, listOf("USD", "EUR", "GBP", "INR", "CAD"), { it }) { currency = it }
                AmountField(buffer, currency) { buffer = it }
                Text("The buffer stays out of the safe-to-spend estimate.")
                PrimaryButton("Continue") { step = 2 }
            }
            else -> {
                Text("First account", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Account name") }, singleLine = true)
                PickerField("Type", type.label(), AccountType.entries.toList(), { it.label() }) { typeName = it.name }
                Text(if (type == AccountType.CREDIT_CARD || type == AccountType.LOAN) "Amount owed" else "Current balance")
                AmountField(balance, currency) { balance = it }
                viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                PrimaryButton("Finish") { viewModel.finish(currency, buffer, name, type, balance) }
                TextButton(onClick = viewModel::skip, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip account for now") }
            }
        }
    }
}
