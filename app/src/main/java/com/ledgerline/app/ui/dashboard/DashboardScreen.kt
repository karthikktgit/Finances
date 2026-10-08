package com.ledgerline.app.ui.dashboard

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.Dashboard
import com.ledgerline.app.domain.usecase.ObserveDashboard
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.preferences.UiPreferences
import com.ledgerline.app.ui.components.EmptyPane
import com.ledgerline.app.ui.components.KeyValue
import com.ledgerline.app.ui.components.LedgerCard
import com.ledgerline.app.ui.components.MoneyText
import com.ledgerline.app.ui.components.SectionLabel
import com.ledgerline.app.ui.format.dueLabel
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.format.paymentLabel
import com.ledgerline.app.ui.format.weekday
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeDashboard: ObserveDashboard,
    preferences: UiPreferences,
) : ViewModel() {
    val dashboard: StateFlow<Result<Dashboard>?> = observeDashboard.observe()
        .map { Result.success(it) }
        .catch { error -> emit(Result.failure(error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sampleData = preferences.sampleData.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

@Composable
fun DashboardScreen(
    onSafeToSpend: () -> Unit,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onAddPayment: () -> Unit,
    onTransaction: (String) -> Unit,
    onAccounts: () -> Unit,
    onReview: () -> Unit,
    onForecast: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.dashboard.collectAsStateWithLifecycle()
    val sample by viewModel.sampleData.collectAsStateWithLifecycle()
    when (val result = state) {
        null -> Text("Loading your ledger…", modifier = Modifier.padding(contentPadding).padding(20.dp))
        else -> result.fold(
            onSuccess = { dashboard ->
                DashboardBody(dashboard, sample, onSafeToSpend, onAddExpense, onAddIncome, onAddPayment, onTransaction, onAccounts, onReview, onForecast, contentPadding)
            },
            onFailure = {
                Text(
                    "The ledger could not be read. Your records were not changed.",
                    modifier = Modifier.padding(contentPadding).padding(20.dp),
                )
            },
        )
    }
}

@Composable
private fun DashboardBody(
    dashboard: Dashboard,
    sample: Boolean,
    onSafeToSpend: () -> Unit,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onAddPayment: () -> Unit,
    onTransaction: (String) -> Unit,
    onAccounts: () -> Unit,
    onReview: () -> Unit,
    onForecast: () -> Unit,
    contentPadding: PaddingValues,
) {
    val hour = LocalTime.now(ZoneId.systemDefault()).hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(greeting, style = MaterialTheme.typography.titleMedium)
            Text(dashboard.asOf.weekday(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sample) {
            item {
                LedgerCard {
                    Text("Sample finances", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "These balances are an example stored only on this device. Replace them with your own accounts when you are ready.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (dashboard.accounts.isEmpty()) {
            item {
                EmptyPane(
                    title = "No accounts yet",
                    body = "Add a checking account or cash wallet. Safe-to-spend stays empty until there is a balance to measure.",
                )
                Button(onClick = onAccounts, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Add an account")
                }
            }
        }
        item {
            Text("Available", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(dashboard.liquidMinor, dashboard.currency, large = true)
            Text(
                "Liquid accounts included in safe-to-spend. This is not the same as what you can spend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            LedgerCard {
                Text("Safe to spend", style = MaterialTheme.typography.labelMedium)
                Text("Estimated", color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(dashboard.safeToSpend.amountMinor, dashboard.currency, large = true, signed = dashboard.safeToSpend.amountMinor < 0)
                Text(dashboard.safeToSpend.disclaimer, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onSafeToSpend, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("How this was calculated")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerCard(Modifier.weight(1f)) {
                    Text("This month out", style = MaterialTheme.typography.labelMedium)
                    Text(MoneyFormat.format(dashboard.monthSpendingMinor, dashboard.currency), style = MaterialTheme.typography.titleLarge)
                    if (dashboard.monthRefundsMinor > 0) {
                        Text("Refunds ${MoneyFormat.format(dashboard.monthRefundsMinor, dashboard.currency)}")
                    }
                }
                LedgerCard(Modifier.weight(1f)) {
                    Text("This month in", style = MaterialTheme.typography.labelMedium)
                    Text(MoneyFormat.format(dashboard.monthIncomeMinor, dashboard.currency), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        if (dashboard.needsReviewCount > 0) {
            item {
                Button(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("${dashboard.needsReviewCount} transactions need review")
                }
            }
        }
        item {
            TextButton(onClick = onForecast, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open cash-flow forecast") }
        }
        if (dashboard.warnings.isNotEmpty()) {
            item { SectionLabel("Warnings") }
            items(dashboard.warnings.size) { index ->
                val warning = dashboard.warnings[index]
                LedgerCard(
                    Modifier.semantics {
                        contentDescription = "${warning.severity.label()}. ${warning.title}. ${warning.detail}"
                    },
                ) {
                    Text("${warning.severity.label()} · ${warning.title}", style = MaterialTheme.typography.titleMedium)
                    Text(warning.detail)
                }
            }
        }
        item { SectionLabel("Upcoming") }
        if (dashboard.upcoming.isEmpty()) {
            item { Text("No scheduled income or payments in the next ${dashboard.horizonDays} days.") }
        } else {
            item {
                val next = dashboard.upcoming.first()
                LedgerCard {
                    Text("Next: ${next.name}", style = MaterialTheme.typography.titleMedium)
                    Text("${next.kind.label()} · ${next.priority.label()} · ${dueLabel(dashboard.asOf, next.date)}")
                    Text(
                        "${if (next.direction.name == "INFLOW") "+" else "−"}${MoneyFormat.format(next.amountMinor, dashboard.currency)} · ${next.date.medium()} · ${next.accountName}",
                    )
                    if (!next.countedInSafeToSpend) {
                        Text("Not subtracted from safe-to-spend.")
                    }
                }
            }
            item {
                KeyValue("Committed payments", MoneyFormat.format(dashboard.committedPaymentsMinor, dashboard.currency))
                KeyValue("Expected income", MoneyFormat.format(dashboard.expectedIncomeMinor, dashboard.currency))
            }
        }
        if (dashboard.preview.isNotEmpty()) {
            item { SectionLabel("If only the schedule posts") }
            items(dashboard.preview.size) { index ->
                val point = dashboard.preview[index]
                KeyValue(
                    "${point.date.medium()} ${point.label}",
                    MoneyFormat.format(point.balanceAfterMinor, dashboard.currency),
                )
            }
            item {
                Text(
                    "Estimated liquid balance after each counted item. Everyday spending is not in this list.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (dashboard.budgets.isNotEmpty()) {
            item { SectionLabel("Budgets") }
            items(dashboard.budgets.size) { index ->
                val budget = dashboard.budgets[index]
                KeyValue(
                    budget.budget.name,
                    "${MoneyFormat.format(budget.remainingMinor, dashboard.currency)} left · ${budget.pace.label()}",
                )
            }
        }
        item { SectionLabel("Recent") }
        if (dashboard.recent.isEmpty()) {
            item { Text("No transactions yet.") }
        } else {
            items(dashboard.recent.size) { index ->
                val tx = dashboard.recent[index]
                val account = dashboard.accounts.firstOrNull { it.account.id == tx.accountId }?.account
                TextButton(
                    onClick = { onTransaction(tx.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(tx.merchantRaw.ifBlank { tx.type.label() }, style = MaterialTheme.typography.titleMedium)
                        Text("${tx.occurredOn.medium()} · ${account?.paymentLabel() ?: "Account"} · ${tx.type.label()}")
                        Text(MoneyFormat.signedLabel(tx.balanceEffectMinor, tx.currency))
                    }
                }
            }
        }
        item {
            SectionLabel("Quick actions")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddExpense, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add expense") }
                OutlinedButton(onClick = onAddIncome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add income") }
                OutlinedButton(onClick = onAddPayment, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add payment") }
            }
        }
    }
}

@Composable
fun SafeToSpendScreen(onBack: () -> Unit, contentPadding: PaddingValues, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.dashboard.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
        Text("Safe to spend", style = MaterialTheme.typography.headlineSmall)
        val dashboard = state?.getOrNull()
        if (dashboard == null) {
            Text("Loading the estimate…")
        } else {
            MoneyText(dashboard.safeToSpend.amountMinor, dashboard.currency, large = true, signed = true)
            dashboard.safeToSpend.lines.forEach { line ->
                KeyValue(line.label, MoneyFormat.signedLabel(line.minor, dashboard.currency))
            }
            Text(dashboard.safeToSpend.disclaimer)
            Text(
                "Committed payments are mandatory and important items on liquid accounts. " +
                    "Optional and flexible items stay on the calendar and are not subtracted. " +
                    "Low-confidence income is not added.",
            )
        }
    }
}
