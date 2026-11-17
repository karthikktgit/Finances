package com.ledgerline.app.ui.notes

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
import com.ledgerline.app.domain.model.ExpenseNote
import com.ledgerline.app.domain.model.ExpenseNoteDraft
import com.ledgerline.app.domain.model.ExpenseNoteOrder
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.repository.ExpenseNoteRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.ui.components.AmountField
import com.ledgerline.app.ui.components.DateField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.navigation.ExpenseNoteEditorRoute
import com.ledgerline.app.ui.theme.LocalLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ExpenseNotesViewModel @Inject constructor(
    notes: ExpenseNoteRepository,
    settings: SettingsRepository,
    private val repository: ExpenseNoteRepository,
    private val clock: AppClock,
) : ViewModel() {
    val rows = notes.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val currency = settings.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = clock.today()

    fun setCompleted(id: String, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(id, completed) }
    }
}

@Composable
fun ExpenseNotesScreen(
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    contentPadding: PaddingValues,
    viewModel: ExpenseNotesViewModel = hiltViewModel(),
) {
    val notes by viewModel.rows.collectAsStateWithLifecycle()
    val settings by viewModel.currency.collectAsStateWithLifecycle()
    val currency = settings?.primaryCurrency ?: "USD"
    val today = viewModel.today()
    val open = ExpenseNoteOrder.open(notes, today)
    val done = ExpenseNoteOrder.completed(notes)
    val colors = LocalLedgerColors.current
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add expense note") }
        if (notes.isEmpty()) Text("No expense notes")
        open.forEach { note ->
            NoteRow(note, currency, ExpenseNoteOrder.isOverdue(note, today), colors.negative, onEdit, viewModel::setCompleted)
        }
        if (done.isNotEmpty()) {
            Text("Completed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            done.forEach { note ->
                NoteRow(note, currency, overdue = false, overdueColor = colors.negative, onEdit, viewModel::setCompleted)
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: ExpenseNote,
    currency: String,
    overdue: Boolean,
    overdueColor: androidx.compose.ui.graphics.Color,
    onEdit: (String) -> Unit,
    onCompleted: (String, Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onEdit(note.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text(note.name, style = MaterialTheme.typography.titleMedium, softWrap = true)
                val amount = note.amountMinor?.let { MoneyFormat.format(it, currency) } ?: "No amount"
                val due = note.dueOn?.medium() ?: "No date"
                Text(
                    "$amount · $due",
                    color = if (overdue) overdueColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true,
                )
            }
        }
        TextButton(onClick = { onCompleted(note.id, !note.completed) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (note.completed) "Reopen" else "Done")
        }
    }
}

@HiltViewModel
class ExpenseNoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notes: ExpenseNoteRepository,
    settings: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val noteId = savedStateHandle.toRoute<ExpenseNoteEditorRoute>().noteId
    val currency = settings.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val rows = notes.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var ready by mutableStateOf(noteId.isBlank())
    var name by mutableStateOf("")
    var hasAmount by mutableStateOf(false)
    var amount by mutableStateOf(0L)
    var hasDate by mutableStateOf(false)
    var due by mutableStateOf(clock.today())
    var note by mutableStateOf("")
    var completed by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun load(item: ExpenseNote) {
        if (ready) return
        name = item.name
        hasAmount = item.amountMinor != null
        amount = item.amountMinor ?: 0L
        hasDate = item.dueOn != null
        due = item.dueOn ?: clock.today()
        note = item.note
        completed = item.completed
        ready = true
    }

    fun current(): ExpenseNote? = rows.value.firstOrNull { it.id == noteId }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val result = notes.save(
                ExpenseNoteDraft(
                    existingId = noteId.ifBlank { null },
                    name = name,
                    amountMinor = if (hasAmount) amount else null,
                    dueOn = if (hasDate) due else null,
                    note = note,
                    completed = completed,
                ),
            )
            if (result is SaveResult.Invalid) message = result.message else onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (noteId.isNotBlank()) notes.delete(noteId)
            onDone()
        }
    }
}

@Composable
fun ExpenseNoteEditorScreen(onDone: () -> Unit, viewModel: ExpenseNoteEditorViewModel = hiltViewModel()) {
    val existing = viewModel.current()
    LaunchedEffect(existing?.id) { existing?.let(viewModel::load) }
    val settings by viewModel.currency.collectAsStateWithLifecycle()
    val currency = settings?.primaryCurrency ?: "USD"
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (viewModel.current() == null && viewModel.ready) "Add expense note" else "Edit expense note", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(viewModel.name, { viewModel.name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Expected amount", modifier = Modifier.weight(1f))
            Switch(viewModel.hasAmount, { viewModel.hasAmount = it })
        }
        if (viewModel.hasAmount) AmountField(viewModel.amount, currency) { viewModel.amount = it }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Due date", modifier = Modifier.weight(1f))
            Switch(viewModel.hasDate, { viewModel.hasDate = it })
        }
        if (viewModel.hasDate) DateField(viewModel.due) { viewModel.due = it }
        OutlinedTextField(viewModel.note, { viewModel.note = it }, Modifier.fillMaxWidth(), label = { Text("Notes") })
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Completed", modifier = Modifier.weight(1f))
            Switch(viewModel.completed, { viewModel.completed = it })
        }
        viewModel.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton("Save") { viewModel.save(onDone) }
        if (existing != null) {
            TextButton(onClick = { viewModel.delete(onDone) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete") }
        }
    }
}
