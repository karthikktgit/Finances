package com.ledgerline.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ledgerline.app.domain.model.BudgetDraft
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.repository.BudgetRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.usecase.ObserveDashboard
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.components.AmountField
import com.ledgerline.app.ui.components.BudgetMeter
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.navigation.BudgetEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BudgetsViewModel @Inject constructor(dashboard: ObserveDashboard) : ViewModel() {
    val dashboard = dashboard.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun BudgetsScreen(onEdit: (String) -> Unit, onCreate: () -> Unit, contentPadding: PaddingValues, viewModel: BudgetsViewModel = hiltViewModel()) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Budgets", style = MaterialTheme.typography.headlineSmall)
        Text("Projected means the current pace continued through the period. It is an estimate.")
        TextButton(onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add budget") }
        val budgets = dashboard?.budgets.orEmpty()
        if (budgets.isEmpty()) Text("No budgets yet.")
        budgets.forEach { snapshot ->
            TextButton(onClick = { onEdit(snapshot.budget.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text("${snapshot.budget.name} · ${snapshot.pace.label()}", style = MaterialTheme.typography.titleMedium)
                    BudgetMeter(snapshot.spentMinor, snapshot.budget.amountMinor, snapshot.budget.currency)
                    Text("Remaining ${MoneyFormat.format(snapshot.remainingMinor, snapshot.budget.currency)} · Projected ${MoneyFormat.format(snapshot.projectedMinor, snapshot.budget.currency)}")
                }
            }
        }
    }
}

@HiltViewModel
class BudgetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<BudgetEditorRoute>()
    val isExisting = args.budgetId.isNotBlank()
    val categories = categories.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var name by mutableStateOf("")
    var categoryId by mutableStateOf<String?>(null)
    var period by mutableStateOf(BudgetPeriod.MONTHLY)
    var amountMinor by mutableStateOf(0L)
    var reserve by mutableStateOf(true)
    var active by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()

    init {
        if (isExisting) {
            viewModelScope.launch {
                val budget = budgets.get(args.budgetId)
                if (budget != null) {
                    name = budget.name
                    categoryId = budget.categoryId
                    period = budget.period
                    amountMinor = budget.amountMinor
                    reserve = budget.reservesSafeToSpend
                    active = budget.active
                }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            when (val result = budgets.save(BudgetDraft(args.budgetId.ifBlank { null }, name, categoryId, period, amountMinor, reserve, active))) {
                SaveResult.Saved -> _done.emit(Unit)
                is SaveResult.Invalid -> error = result.message
                is SaveResult.PossibleDuplicate -> Unit
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            budgets.delete(args.budgetId)
            _done.emit(Unit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditorScreen(onDone: () -> Unit, viewModel: BudgetEditorViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Budget") }, navigationIcon = { TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedTextField(viewModel.name, { viewModel.name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            AmountField(viewModel.amountMinor, "USD") { viewModel.amountMinor = it }
            PickerField("Period", viewModel.period.label(), BudgetPeriod.entries.toList(), { it.label() }) { viewModel.period = it }
            PickerField(
                "Category",
                categories.firstOrNull { it.id == viewModel.categoryId }?.let { CategoryTree.label(it.id, categories) } ?: "All spending",
                categories.filter { !it.archived },
                { CategoryTree.label(it.id, categories) },
            ) { viewModel.categoryId = it.id }
            Text("Reserve remaining amount from safe-to-spend")
            Switch(viewModel.reserve, { viewModel.reserve = it })
            Text("Turn this off for bills you already scheduled, or the same money is counted twice.")
            Text("Active")
            Switch(viewModel.active, { viewModel.active = it })
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Save budget", onClick = viewModel::save)
            if (viewModel.isExisting) TextButton(onClick = viewModel::delete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete budget") }
        }
    }
}
