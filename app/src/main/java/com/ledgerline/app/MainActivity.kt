package com.ledgerline.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.ledgerline.app.detection.capture.CaptureNotifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.ThemeChoice
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.preferences.UiPreferences
import com.ledgerline.app.ui.navigation.LedgerScaffold
import com.ledgerline.app.ui.onboarding.OnboardingScreen
import com.ledgerline.app.ui.security.LockGate
import com.ledgerline.app.ui.theme.LedgerlineTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Bumped when a "tap to add" notification opens the app, so the captured-spend inbox opens. */
    private val openCaptures = mutableIntStateOf(0)

    /** Bumped when the start-of-cycle reminder opens the app, so the new-cycle checklist opens. */
    private val openCycle = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { LedgerlineRoot(openCaptures.intValue, openCycle.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getStringExtra(CaptureNotifier.OPEN_EXTRA) == CaptureNotifier.OPEN_CAPTURES) {
            openCaptures.intValue += 1
            intent.removeExtra(CaptureNotifier.OPEN_EXTRA)
        }
        if (intent?.getStringExtra(CaptureNotifier.OPEN_EXTRA) == com.ledgerline.app.worker.ReminderNotifier.OPEN_CYCLE) {
            openCycle.intValue += 1
            intent.removeExtra(CaptureNotifier.OPEN_EXTRA)
        }
    }
}

internal sealed interface BootState {
    data object Loading : BootState
    data object Failed : BootState
    data class Started(val onboardingCompleted: Boolean) : BootState
}

@HiltViewModel
class BootViewModel @Inject constructor(
    private val categories: CategoryRepository,
    private val homeGroups: com.ledgerline.app.domain.repository.HomeGroupRepository,
    private val settings: SettingsRepository,
    private val preferences: UiPreferences,
    private val financialReset: com.ledgerline.app.data.repository.FinancialDataReset,
    private val autoPay: com.ledgerline.app.finance.schedule.AutoPay,
) : ViewModel() {
    private val boot = MutableStateFlow<BootState>(BootState.Loading)
    internal val state = boot.asStateFlow()
    val theme = preferences.theme
    val screenshots = preferences.screenshotProtection

    init {
        viewModelScope.launch {
            try {
                categories.ensureDefaults()
                homeGroups.ensureDefaults()
                settings.ensureDefault()
                if (com.ledgerline.app.finance.dashboard.ResetPolicy.resetOnLaunch(
                        debugBuild = BuildConfig.DEBUG,
                        enabledByDeveloper = preferences.resetOnLaunch.first(),
                    )
                ) {
                    financialReset.resetFinancialData()
                }
                // Bills on autopay that came due since the app was last open are recorded before anything is shown.
                runCatching { autoPay.settleDue() }
                settings.observe().collect { value ->
                    boot.value = BootState.Started(value.onboardingCompleted)
                }
            } catch (_: Exception) {
                boot.value = BootState.Failed
            }
        }
    }
}

@Composable
private fun LedgerlineRoot(openCaptures: Int, openCycle: Int, viewModel: BootViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle(ThemeChoice.SYSTEM)
    val screenshots by viewModel.screenshots.collectAsStateWithLifecycle(false)
    val boot by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(screenshots) {
        val window = (context as? ComponentActivity)?.window ?: return@LaunchedEffect
        if (screenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    LedgerlineTheme(theme) {
        LockGate {
        when (val state = boot) {
            BootState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            BootState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ledgerline could not open the local database.", color = MaterialTheme.colorScheme.onBackground)
            }
            is BootState.Started -> if (state.onboardingCompleted) LedgerScaffold(openCaptures, openCycle) else OnboardingScreen()
        }
        }
    }
}
