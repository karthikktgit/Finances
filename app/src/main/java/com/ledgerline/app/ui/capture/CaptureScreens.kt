package com.ledgerline.app.ui.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.detection.capture.CaptureAccounts
import com.ledgerline.app.detection.capture.CaptureActions
import com.ledgerline.app.detection.capture.CaptureAddResult
import com.ledgerline.app.detection.capture.CaptureChannel
import com.ledgerline.app.detection.capture.CaptureSources
import com.ledgerline.app.detection.capture.MoneyDirection
import com.ledgerline.app.detection.capture.SpendTextParser
import com.ledgerline.app.domain.model.CapturedSpend
import com.ledgerline.app.domain.model.NotificationSource
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.CaptureRepository
import com.ledgerline.app.domain.repository.NotificationSourceRepository
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.preferences.UiPreferences
import com.ledgerline.app.ui.components.BannerCard
import com.ledgerline.app.ui.components.EmptyPane
import com.ledgerline.app.ui.components.GroupCard
import com.ledgerline.app.ui.components.IconBubble
import com.ledgerline.app.ui.components.LedgerCard
import com.ledgerline.app.ui.components.SectionHeader
import com.ledgerline.app.ui.components.SubScreen
import com.ledgerline.app.ui.theme.LocalLedgerColors
import com.ledgerline.app.ui.transactions.SwitchRow
import com.ledgerline.app.ui.transactions.dayLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private fun channelIcon(channel: CaptureChannel): ImageVector = when (channel) {
    CaptureChannel.SMS -> Icons.Filled.Sms
    CaptureChannel.EMAIL -> Icons.Filled.Email
    CaptureChannel.WALLET -> Icons.Filled.AccountBalanceWallet
    CaptureChannel.BANK -> Icons.Filled.AccountBalance
}

@HiltViewModel
class CaptureInboxViewModel @Inject constructor(
    captures: CaptureRepository,
    accounts: AccountRepository,
    private val actions: CaptureActions,
    preferences: UiPreferences,
) : ViewModel() {
    val pending = captures.observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val accounts = accounts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val captureOn = preferences.notificationDetection.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Captures that look like something already recorded, with the match described. */
    val duplicates = mutableStateMapOf<String, String>()
    var message by mutableStateOf<String?>(null)
        private set

    fun add(capture: CapturedSpend, acknowledgeDuplicate: Boolean = false) {
        viewModelScope.launch {
            when (val result = actions.add(capture, acknowledgeDuplicate = acknowledgeDuplicate)) {
                CaptureAddResult.Added -> {
                    duplicates.remove(capture.id)
                    message = "Added ${capture.merchant.ifBlank { "payment" }}"
                }
                is CaptureAddResult.PossibleDuplicate -> duplicates[capture.id] = result.summary
                CaptureAddResult.NoAccount -> message = "Add an account first, in Settings › Accounts."
                is CaptureAddResult.Invalid -> message = result.message
            }
        }
    }

    fun dismiss(id: String) {
        duplicates.remove(id)
        viewModelScope.launch { actions.dismiss(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { actions.restore(id) }
    }

    fun consumeMessage() {
        message = null
    }
}

@Composable
fun CaptureInboxScreen(
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    onSetup: () -> Unit,
    viewModel: CaptureInboxViewModel = hiltViewModel(),
) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val captureOn by viewModel.captureOn.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            viewModel.consumeMessage()
            snackbar.showSnackbar(it)
        }
    }
    SubScreen(
        "Captured spends",
        onBack,
        actions = { TextButton(onClick = onSetup) { Text("Settings") } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Read from your payment notifications. They are not in your balances until you add them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!captureOn) {
                item {
                    BannerCard(
                        title = "Auto-capture is off",
                        body = "Turn it on to pick up spends from texts, email, and wallet apps.",
                        icon = Icons.Filled.Inbox,
                        action = "Set up",
                        onClick = onSetup,
                    )
                }
            }
            val rows = pending
            if (rows != null && rows.isEmpty()) {
                item {
                    EmptyPane(
                        "All caught up",
                        if (captureOn) "New payments from your notifications will wait here." else "Nothing captured yet.",
                    )
                }
            }
            items(rows.orEmpty(), key = { it.id }) { capture ->
                CaptureCard(
                    capture = capture,
                    accountName = CaptureAccounts.suggested(capture, accounts)?.name,
                    matched = CaptureAccounts.matched(capture, accounts) != null,
                    duplicate = viewModel.duplicates[capture.id],
                    onAdd = { viewModel.add(capture) },
                    onAddAnyway = { viewModel.add(capture, acknowledgeDuplicate = true) },
                    onReview = { onReview(capture.id) },
                    onDismiss = {
                        viewModel.dismiss(capture.id)
                        scope.launch {
                            val result = snackbar.showSnackbar("Dismissed", actionLabel = "Undo", withDismissAction = true)
                            if (result == SnackbarResult.ActionPerformed) viewModel.restore(capture.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CaptureCard(
    capture: CapturedSpend,
    accountName: String?,
    matched: Boolean,
    duplicate: String?,
    onAdd: () -> Unit,
    onAddAnyway: () -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalLedgerColors.current
    val out = capture.direction == MoneyDirection.OUT
    val time = Instant.ofEpochMilli(capture.capturedAtEpochMillis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    LedgerCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBubble(channelIcon(capture.channel), MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    capture.merchant.ifBlank { if (out) "Payment" else "Money received" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${capture.sourceLabel} · ${dayLabel(time.toLocalDate(), today)} ${time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                (if (out) "−" else "+") + MoneyFormat.format(capture.amountMinor, capture.currency),
                style = MaterialTheme.typography.titleMedium,
                color = if (out) colors.negative else colors.positive,
            )
        }
        if (capture.preview.isNotBlank()) {
            Text(
                "“${capture.preview}”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(10.dp),
            )
        }
        Text(
            when {
                accountName == null -> "No account yet"
                matched -> "Goes to $accountName (card ending ${capture.lastFour})"
                else -> "Goes to $accountName · tap Review to change"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (duplicate != null) {
            Text(
                "Looks already recorded: $duplicate",
                style = MaterialTheme.typography.bodySmall,
                color = colors.caution,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("It's the same") }
                Button(onClick = onAddAnyway, modifier = Modifier.weight(1f)) { Text("Add anyway") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onReview) { Text("Review") }
                }
                Button(onClick = onAdd, enabled = accountName != null) { Text("Add") }
            }
        }
    }
}

@HiltViewModel
class CaptureSetupViewModel @Inject constructor(
    private val preferences: UiPreferences,
    private val sources: NotificationSourceRepository,
) : ViewModel() {
    val enabled = preferences.notificationDetection.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val autoAdd = preferences.captureAutoAdd.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val notify = preferences.captureNotify.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val apps = sources.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val smsDirect = preferences.captureSmsDirect.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val hidden = preferences.hiddenNotices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setSmsDirect(value: Boolean) {
        viewModelScope.launch { preferences.setCaptureSmsDirect(value) }
    }

    init {
        viewModelScope.launch { sources.ensureCatalog() }
    }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { preferences.setNotificationDetection(value) }
    }

    fun setApp(id: String, value: Boolean) {
        viewModelScope.launch { sources.setEnabled(id, value) }
    }

    fun setAutoAdd(value: Boolean) {
        viewModelScope.launch { preferences.setCaptureAutoAdd(value) }
    }

    fun setNotify(value: Boolean) {
        viewModelScope.launch { preferences.setCaptureNotify(value) }
    }
}

private fun listenerGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun postGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun smsGranted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

private fun installed(context: Context, packageName: String): Boolean =
    runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

@Composable
fun CaptureSetupScreen(onBack: () -> Unit, viewModel: CaptureSetupViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val autoAdd by viewModel.autoAdd.collectAsStateWithLifecycle()
    val notify by viewModel.notify.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val smsDirect by viewModel.smsDirect.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canReadSms by remember { mutableStateOf(smsGranted(context)) }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canReadSms = granted
        viewModel.setSmsDirect(granted)
    }
    var access by remember { mutableStateOf(listenerGranted(context)) }
    var canPost by remember { mutableStateOf(postGranted(context)) }
    LifecycleResumeEffect(Unit) {
        // The person may have just come back from Android's settings screen.
        access = listenerGranted(context)
        canPost = postGranted(context)
        canReadSms = smsGranted(context)
        onPauseOrDispose { }
    }
    val askPost = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canPost = it }
    val installedPackages = remember(apps) { apps.filter { installed(context, it.packageName) }.map { it.packageName }.toSet() }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val anyApp = apps.any { it.enabled } || (smsDirect && canReadSms)

    SubScreen("Auto-capture spends", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Your bank already texts or emails you when money moves. Ledgerline reads those alerts as they arrive and " +
                        "lines them up for you to add with one tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionHeader("Set up") }
            item {
                GroupCard {
                    StepRow(
                        done = access,
                        title = "1. Allow notification access",
                        body = if (access) "Allowed." else "Android asks you to allow this in Settings. Find Ledgerline and turn it on.",
                        action = if (access) null else "Open settings",
                    ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    StepRow(
                        done = enabled,
                        title = "2. Turn on auto-capture",
                        body = "You can pause it here at any time.",
                        toggle = viewModel::setEnabled,
                        trailing = { Switch(checked = enabled, onCheckedChange = null) },
                    )
                    StepRow(
                        done = anyApp,
                        title = "3. Pick the apps to read",
                        body = if (anyApp) "Done. Change it below at any time." else "Choose below what to read. Nothing else is read.",
                    )
                }
            }
            if (enabled && !access) {
                item {
                    BannerCard(
                        title = "Waiting for notification access",
                        body = "Nothing can be captured until step 1 is done.",
                        icon = Icons.Filled.Lock,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            if (hidden > 0 && !(smsDirect && canReadSms)) {
                item {
                    BannerCard(
                        title = "Android is hiding message text",
                        body = "$hidden ${if (hidden == 1L) "message" else "messages"} from your chosen apps arrived with the text hidden, " +
                            "so they could not be read. Turn on \"Read new texts directly\" below.",
                        icon = Icons.Filled.Lock,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            item { SectionHeader("Text messages") }
            item {
                SwitchRow(
                    title = "Read new texts directly",
                    body = "Recommended. Newer Android versions can hide message notifications from Ledgerline; this reads each " +
                        "new text as it arrives instead. Only new texts, never your message history, and only bank alerts are kept.",
                    checked = smsDirect && canReadSms,
                ) { on ->
                    if (on && !canReadSms) askSms.launch(Manifest.permission.RECEIVE_SMS) else viewModel.setSmsDirect(on)
                }
            }
            val visible = apps.filter { showAll || it.packageName in installedPackages || it.enabled }
            CaptureChannel.entries.forEach { channel ->
                val group = visible.filter { CaptureSources.channel(it.packageName) == channel }
                if (group.isNotEmpty()) {
                    // Text-message apps sit under the direct-reading switch above.
                    if (channel != CaptureChannel.SMS) item { SectionHeader(CaptureSources.channelLabel(channel)) }
                    item {
                        GroupCard {
                            group.sortedBy { it.label }.forEach { app -> AppRow(app, channel, app.packageName in installedPackages, viewModel::setApp) }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show only apps on this phone" else "Show all supported apps")
                }
            }
            item { SectionHeader("When a spend is captured") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwitchRow(
                        title = "Tell me with a notification",
                        body = if (canPost) "Tap it to add the spend." else "Android needs your permission to show these.",
                        checked = notify && canPost,
                    ) { on ->
                        if (on && !canPost && Build.VERSION.SDK_INT >= 33) askPost.launch(Manifest.permission.POST_NOTIFICATIONS)
                        viewModel.setNotify(on)
                    }
                    SwitchRow(
                        title = "Add automatically when the card matches",
                        body = "If the alert names a card or account ending that matches exactly one of your accounts, it is added " +
                            "straight away. Otherwise it waits for you.",
                        checked = autoAdd,
                        onChange = viewModel::setAutoAdd,
                    )
                }
            }
            item { SectionHeader("Try a message") }
            item { TryParser() }
            item {
                Text(
                    "Privacy: notifications are read on this phone only. Ledgerline keeps the amount, merchant, card ending, and a " +
                        "short excerpt with long numbers masked. Messages that are not payments are ignored and not stored. " +
                        "One-time passcodes, bill reminders, offers, and declined payments are skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    done: Boolean,
    title: String,
    body: String,
    action: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    toggle: ((Boolean) -> Unit)? = null,
    onAction: () -> Unit = {},
) {
    val colors = LocalLedgerColors.current
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
    Row(
        (if (toggle != null) base.toggleable(value = done, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = toggle) else base)
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (done) "Done" else "Not done",
            tint = if (done) colors.positive else MaterialTheme.colorScheme.outline,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) Button(onClick = onAction) { Text(action) }
        trailing?.invoke()
    }
}

@Composable
private fun AppRow(app: NotificationSource, channel: CaptureChannel, onPhone: Boolean, onToggle: (String, Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = app.enabled, role = androidx.compose.ui.semantics.Role.Switch) { onToggle(app.id, it) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBubble(channelIcon(channel), MaterialTheme.colorScheme.primary, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleSmall)
            if (!onPhone) Text("Not on this phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = app.enabled, onCheckedChange = null)
    }
}

/** Paste any alert to see what would be captured. Nothing is saved. */
@Composable
private fun TryParser() {
    var text by rememberSaveable { mutableStateOf("") }
    val signal = remember(text) { if (text.isBlank()) null else SpendTextParser.parse(text, requireBankWording = true) }
    LedgerCard {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Paste a bank text or email") },
            placeholder = { Text("You made a \$12.40 transaction with STARBUCKS on card ending 1234") },
            minLines = 2,
            shape = RoundedCornerShape(14.dp),
        )
        if (text.isNotBlank()) {
            if (signal == null) {
                Text(
                    "Not a payment. This would be ignored.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val verb = if (signal.direction == MoneyDirection.OUT) "Spent" else if (signal.refund) "Refund" else "Received"
                Text(
                    "$verb ${MoneyFormat.format(signal.amountMinor, signal.currency ?: "USD")}" +
                        (signal.merchant?.let { " · $it" } ?: "") +
                        (signal.lastFour?.let { " · card ending $it" } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
