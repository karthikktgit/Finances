package com.ledgerline.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.CategorySpend
import com.ledgerline.app.domain.usecase.ObserveInsights
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.components.KeyValue
import com.ledgerline.app.ui.components.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(insights: ObserveInsights) : ViewModel() {
    val insights = insights.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun InsightsScreen(contentPadding: PaddingValues, onWhatIf: () -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Insights", style = MaterialTheme.typography.headlineSmall)
        val data = insights
        if (data == null) {
            Text("Loading spending…")
            return@Column
        }
        Text("This month ${MoneyFormat.format(data.thisMonthNetMinor, data.currency)}")
        Text("Last month ${MoneyFormat.format(data.lastMonthNetMinor, data.currency)}")
        Text(changeText(data.thisMonthNetMinor, data.lastMonthNetMinor))
        SectionLabel("By category")
        if (data.byCategory.isEmpty()) Text("No categorized spending in these two months.")
        data.byCategory.forEach { row ->
            CategoryRow(row, data.currency)
        }
        SectionLabel("By category label")
        Text("These labels come from the category you assigned. They are not a judgment of each purchase.")
        data.byNecessity.forEach { row ->
            KeyValue(row.name, MoneyFormat.format(row.amountMinor, data.currency))
        }
        SectionLabel("Merchants")
        data.topMerchants.forEach { row ->
            KeyValue(row.name, MoneyFormat.format(row.amountMinor, data.currency))
        }
        SectionLabel("Accounts")
        data.byAccount.forEach { row ->
            KeyValue(row.name, MoneyFormat.format(row.amountMinor, data.currency))
        }
        SectionLabel("Subscriptions")
        KeyValue("This month", MoneyFormat.format(data.subscriptionMonthMinor, data.currency))
        KeyValue("Annualized", MoneyFormat.format(data.subscriptionAnnualMinor, data.currency))
        SectionLabel("Money leaks")
        if (data.leaks.isEmpty()) Text("No leak pattern stands out in the current ledger.")
        data.leaks.forEach { note ->
            Text(note.title, style = MaterialTheme.typography.titleMedium)
            Text(note.detail)
        }
        SectionLabel("Unusual amounts")
        if (data.anomalies.isEmpty()) Text("No recent transaction is far outside its category's usual range.")
        data.anomalies.forEach { note ->
            Text(note.title, style = MaterialTheme.typography.titleMedium)
            Text(note.detail)
        }
        TextButton(onClick = onWhatIf, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try a what-if scenario") }
    }
}

@Composable
private fun CategoryRow(row: CategorySpend, currency: String) {
    val max = maxOf(row.thisMonthMinor, row.lastMonthMinor, 1L)
    Text(row.name, style = MaterialTheme.typography.titleMedium)
    LinearProgressIndicator(progress = { row.thisMonthMinor.toFloat() / max.toFloat() }, modifier = Modifier.fillMaxWidth())
    Text("This month ${MoneyFormat.format(row.thisMonthMinor, currency)} · Last month ${MoneyFormat.format(row.lastMonthMinor, currency)} · ${changeText(row.thisMonthMinor, row.lastMonthMinor)}")
}

private fun changeText(current: Long, previous: Long): String = when {
    previous == 0L && current == 0L -> "No spending in either month"
    previous == 0L -> "No spending last month to compare"
    else -> {
        val percent = ((current - previous) * 100) / previous
        when {
            percent > 0 -> "$percent% above last month"
            percent < 0 -> "${-percent}% below last month"
            else -> "Same as last month"
        }
    }
}
