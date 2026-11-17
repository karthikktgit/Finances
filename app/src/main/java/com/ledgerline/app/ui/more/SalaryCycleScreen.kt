package com.ledgerline.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.CycleType
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.ui.components.DateField
import com.ledgerline.app.ui.components.PickerField
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.format.withYear
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SalaryCycleViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {
    val settings = settingsRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    var type by mutableStateOf(CycleType.MONTHLY)
    var anchor by mutableStateOf(LocalDate.of(2026, 1, 20))
    var startDay by mutableIntStateOf(20)
    var secondDay by mutableIntStateOf(5)
    var message by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            val current = settingsRepository.observe().first()
            type = current.cycleType
            anchor = current.cycleAnchor
            startDay = current.cycleStartDay
            secondDay = current.cycleSecondDay
        }
    }

    fun save() {
        viewModelScope.launch {
            val current = settings.value ?: return@launch
            settingsRepository.update(
                current.copy(
                    cycleType = type,
                    cycleAnchor = anchor,
                    cycleStartDay = startDay.coerceIn(1, 31),
                    cycleSecondDay = secondDay.coerceIn(1, 31),
                ),
            )
            message = "Saved. Transaction dates were not moved."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryCycleScreen(onBack: () -> Unit, viewModel: SalaryCycleViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    LaunchedEffect(settings?.cycleType, settings?.cycleAnchor, settings?.cycleStartDay) {
        settings?.let {
            viewModel.type = it.cycleType
            viewModel.anchor = it.cycleAnchor
            viewModel.startDay = it.cycleStartDay
            viewModel.secondDay = it.cycleSecondDay
        }
    }
    val previewSettings = settings?.copy(
        cycleType = viewModel.type,
        cycleAnchor = viewModel.anchor,
        cycleStartDay = viewModel.startDay,
        cycleSecondDay = viewModel.secondDay,
    )
    val today = previewSettings?.let { CycleSchedule.containing(java.time.LocalDate.now(), it) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Salary and income cycle") },
            navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("This cycle drives Safe to Spend, upcoming outgoings, and the calendar cycle view. It does not change transaction dates.")
            PickerField("Cycle", viewModel.type.name.lowercase().replaceFirstChar { it.titlecase() }, CycleType.entries.toList(), {
                it.name.lowercase().replaceFirstChar { char -> char.titlecase() }
            }) { viewModel.type = it }
            when (viewModel.type) {
                CycleType.WEEKLY, CycleType.BIWEEKLY -> {
                    Text(if (viewModel.type == CycleType.WEEKLY) "Anchor date. Each cycle is 7 days from this weekday." else "Anchor date. Each cycle is 14 days from this date.")
                    DateField(viewModel.anchor) { viewModel.anchor = it }
                }
                CycleType.MONTHLY -> {
                    OutlinedTextField(
                        value = viewModel.startDay.toString(),
                        onValueChange = { viewModel.startDay = it.filter(Char::isDigit).take(2).toIntOrNull() ?: 1 },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Monthly start day") },
                        singleLine = true,
                    )
                    Text("A start day of 20 runs from the 20th through the 19th of the next month. Shorter months use the last day.")
                }
                CycleType.SEMIMONTHLY -> {
                    OutlinedTextField(
                        value = viewModel.startDay.toString(),
                        onValueChange = { viewModel.startDay = it.filter(Char::isDigit).take(2).toIntOrNull() ?: 1 },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("First start day") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = viewModel.secondDay.toString(),
                        onValueChange = { viewModel.secondDay = it.filter(Char::isDigit).take(2).toIntOrNull() ?: 15 },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Second start day") },
                        singleLine = true,
                    )
                }
            }
            if (today != null) {
                Text("Current cycle: ${today.start.withYear()} – ${today.end.withYear()}")
                val next = previewSettings?.let { CycleSchedule.next(today, it) }
                if (next != null) Text("Next cycle: ${next.start.withYear()} – ${next.end.withYear()}")
            }
            viewModel.message?.let { Text(it) }
            PrimaryButton("Save cycle", onClick = viewModel::save)
        }
    }
}
