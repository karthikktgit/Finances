package com.ledgerline.app.ui.security

import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.preferences.UiPreferences
import com.ledgerline.app.security.AppLock
import com.ledgerline.app.security.PinStore
import com.ledgerline.app.ui.components.PrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val appLock: AppLock,
    private val pins: PinStore,
    private val preferences: UiPreferences,
) : ViewModel() {
    val locked = appLock.locked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val enabled = preferences.appLockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    var pin by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    fun onBackground() = appLock.onBackground(System.currentTimeMillis())

    fun onForeground() {
        viewModelScope.launch { appLock.onForeground(System.currentTimeMillis()) }
    }

    fun unlockWithPin() {
        viewModelScope.launch {
            if (pins.verify(pin)) {
                pin = ""
                message = null
                appLock.unlock()
            } else {
                message = "That PIN does not match."
            }
        }
    }

    fun unlock() = appLock.unlock()

    fun savePin() {
        viewModelScope.launch {
            if (pin.length !in 4..8 || pin.any { !it.isDigit() }) {
                message = "Use 4 to 8 digits."
                return@launch
            }
            pins.setPin(pin)
            preferences.setAppLock(true)
            pin = ""
            message = "App lock is on."
            appLock.unlock()
        }
    }

    fun disable() {
        viewModelScope.launch {
            if (!pins.hasPin() || pins.verify(pin)) {
                preferences.setAppLock(false)
                pins.clear()
                pin = ""
                message = "App lock is off."
                appLock.unlock()
            } else {
                message = "Enter the current PIN to turn app lock off."
            }
        }
    }
}

@Composable
fun LockGate(viewModel: LockViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    androidx.compose.runtime.DisposableEffect(context) {
        val activity = context as? ComponentActivity
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.onBackground()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) viewModel.onForeground()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    if (locked) {
        LockPanel(viewModel, gate = true)
    } else {
        content()
    }
}

@Composable
fun LockSetupScreen(viewModel: LockViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (enabled) "A PIN is required after you leave Ledgerline." else "App lock is off. Set a PIN to require it.")
        Text("The PIN is checked with a key that stays in Android Keystore. Ledgerline does not store the PIN itself.")
        LockPanel(viewModel, gate = false)
    }
}

@Composable
private fun LockPanel(viewModel: LockViewModel, gate: Boolean) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (gate) {
            Text("Ledgerline is locked", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = {
                val activity = context as? ComponentActivity ?: return@TextButton
                showBiometric(activity, viewModel::unlock)
            }) { Text("Use biometrics") }
        }
        OutlinedTextField(
            value = viewModel.pin,
            onValueChange = { viewModel.pin = it.filter(Char::isDigit).take(8) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("PIN") },
            singleLine = true,
        )
        if (gate) {
            PrimaryButton("Unlock", onClick = viewModel::unlockWithPin)
        } else {
            PrimaryButton("Save PIN and turn on", onClick = viewModel::savePin)
            TextButton(onClick = viewModel::disable) { Text("Turn off") }
        }
        viewModel.message?.let { Text(it) }
    }
}

private fun showBiometric(activity: ComponentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt.Builder(activity)
        .setTitle("Unlock Ledgerline")
        .setDescription("Biometrics unlocks the app. The PIN remains available.")
        .setNegativeButton("Use PIN", activity.mainExecutor) { _, _ -> }
        .build()
    prompt.authenticate(
        CancellationSignal(),
        activity.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                onSuccess()
            }
        },
    )
}
