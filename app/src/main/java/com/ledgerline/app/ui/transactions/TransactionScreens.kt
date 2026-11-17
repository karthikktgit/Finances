package com.ledgerline.app.ui.transactions

import androidx.compose.material3.Button
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import com.ledgerline.app.domain.model.RecurrenceFrequency
import androidx.compose.foundation.layout.size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import com.ledgerline.app.ui.components.AmountKeypad
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ledgerline.app.detection.capture.CaptureAccounts
import com.ledgerline.app.detection.capture.CaptureActions
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.TransactionDraft
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.CaptureRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.MerchantRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.account.AccountDeletion
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.category.StandardCategories
import com.ledgerline.app.finance.merchant.MerchantNormalizer
import com.ledgerline.app.finance.money.AmountInput
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.finance.policy.FinancePolicy
import com.ledgerline.app.finance.transactions.FeedRow
import com.ledgerline.app.finance.transactions.PaymentFilter
import com.ledgerline.app.finance.transactions.TransactionFeed
import com.ledgerline.app.ui.components.BannerCard
import com.ledgerline.app.ui.components.CategoryVisuals
import com.ledgerline.app.ui.components.ConfirmDialog
import com.ledgerline.app.ui.components.DateField
import com.ledgerline.app.ui.components.EmptyPane
import com.ledgerline.app.ui.components.GroupCard
import com.ledgerline.app.ui.components.KeyValue
import com.ledgerline.app.ui.components.ListRow
import com.ledgerline.app.ui.components.PrimaryButton
import com.ledgerline.app.ui.components.ScreenHeader
import com.ledgerline.app.ui.components.SegmentedChoice
import com.ledgerline.app.ui.components.StatusPill
import com.ledgerline.app.ui.components.amountColor
import com.ledgerline.app.ui.components.rowIcon
import com.ledgerline.app.ui.format.label
import com.ledgerline.app.ui.format.medium
import com.ledgerline.app.ui.format.paymentLabel
import com.ledgerline.app.ui.navigation.TransactionDetailRoute
import com.ledgerline.app.ui.navigation.TransactionEditorRoute
import com.ledgerline.app.ui.theme.LocalLedgerColors
import com.ledgerline.app.ui.undo.showBriefUndo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class TransactionFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val accountId: String? = null,
    val range: DateRange = DateRange.ALL,
    val unplannedOnly: Boolean = false,
    val from: LocalDate? = null,
    val until: LocalDate? = null,
    val status: PaymentFilter = PaymentFilter.ALL,
    /** Ids of the chosen category and its subcategories. Null means every category. */
    val categoryScope: Set<String>? = null,
    val categoryRootId: String? = null,
)

enum class DateRange { ALL, THIS_MONTH, LAST_MONTH }

fun LedgerTransaction.matches(filter: TransactionFilter, today: LocalDate): Boolean {
    if (deletedAtEpochMillis != null) return false
    if (filter.type != null && type != filter.type) return false
    if (!TransactionFeed.matchesStatus(this, filter.status)) return false
    if (filter.categoryScope != null && categoryId !in filter.categoryScope) return false
    if (filter.accountId != null && accountId != filter.accountId && counterAccountId != filter.accountId) return false
    if (filter.unplannedOnly && (!unplanned || (type != TransactionType.EXPENSE && type != TransactionType.REFUND))) return false
    if (filter.from != null && occurredOn.isBefore(filter.from)) return false
    if (filter.until != null && occurredOn.isAfter(filter.until)) return false
    val month = when (filter.range) {
        DateRange.ALL -> null
        DateRange.THIS_MONTH -> YearMonth.from(today)
        DateRange.LAST_MONTH -> YearMonth.from(today).minusMonths(1)
    }
    if (month != null && (occurredOn.isBefore(month.atDay(1)) || occurredOn.isAfter(month.atEndOfMonth()))) return false
    if (filter.query.isBlank()) return true
    val haystack = "$merchantRaw $merchantNormalized $note $description".lowercase()
    return haystack.contains(filter.query.trim().lowercase())
}

/** Same filters as [matches], applied to a scheduled item that has not been paid. */
fun ScheduledOccurrence.matches(filter: TransactionFilter, today: LocalDate): Boolean {
    if (!TransactionFeed.matchesStatus(filter.status)) return false
    // Scheduled items carry no category and are never flagged unplanned.
    if (filter.categoryScope != null || filter.unplannedOnly) return false
    if (filter.type != null && TransactionFeed.typeOf(this) != filter.type) return false
    if (filter.accountId != null && accountId != filter.accountId) return false
    if (filter.from != null && date.isBefore(filter.from)) return false
    if (filter.until != null && date.isAfter(filter.until)) return false
    val month = when (filter.range) {
        DateRange.ALL -> null
        DateRange.THIS_MONTH -> YearMonth.from(today)
        DateRange.LAST_MONTH -> YearMonth.from(today).minusMonths(1)
    }
    if (month != null && (date.isBefore(month.atDay(1)) || date.isAfter(month.atEndOfMonth()))) return false
    if (filter.query.isBlank()) return true
    return name.lowercase().contains(filter.query.trim().lowercase())
}

/** The four quick views at the top of Activity. Anything finer lives in the filter sheet. */
enum class ActivityView { ALL, SPENDING, INCOME, UPCOMING }

fun TransactionFilter.view(): ActivityView? = when {
    status == PaymentFilter.UNPAID && type == null -> ActivityView.UPCOMING
    status != PaymentFilter.ALL -> null
    type == null -> ActivityView.ALL
    type == TransactionType.EXPENSE -> ActivityView.SPENDING
    type == TransactionType.INCOME -> ActivityView.INCOME
    else -> null
}

fun TransactionFilter.withView(view: ActivityView): TransactionFilter = when (view) {
    ActivityView.ALL -> copy(type = null, status = PaymentFilter.ALL)
    ActivityView.SPENDING -> copy(type = TransactionType.EXPENSE, status = PaymentFilter.ALL)
    ActivityView.INCOME -> copy(type = TransactionType.INCOME, status = PaymentFilter.ALL)
    ActivityView.UPCOMING -> copy(type = null, status = PaymentFilter.UNPAID)
}

/** Filters set from the sheet, beyond the quick view. Shown as removable chips. */
fun TransactionFilter.extraCount(): Int = listOf(
    range != DateRange.ALL,
    accountId != null,
    categoryRootId != null,
    view() == null,
).count { it }

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    captures: CaptureRepository,
    observeDashboard: com.ledgerline.app.domain.usecase.ObserveDashboard,
    private val clock: AppClock,
) : ViewModel() {
    var filter by mutableStateOf(TransactionFilter())
        private set

    /** Deletes one transaction. It leaves every list and total at once; Undo brings it back. */
    fun delete(id: String) {
        viewModelScope.launch { transactions.softDelete(id) }
    }

    /** Marks a planned spend paid today. From now it counts in balances, rings, and charts. */
    fun markPaid(id: String) {
        viewModelScope.launch { transactions.markSpendPaid(id, clock.today()) }
    }

    /** Unpaid scheduled items. They are shown as not paid and are never turned into transactions by viewing them. */
    val scheduled = observeDashboard.observe()
        .map { TransactionFeed.scheduledWindow(it.asOf, it.forecastSchedule) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ScheduledOccurrence>())
    private var allCategories: List<Category> = emptyList()

    val rows = combine(transactions.observeHistory(), accounts.observeIncludingDeleted(), categories.observe()) { txs, accts, cats ->
        Triple(txs, accts, cats)
    }.onEach { allCategories = it.third }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(emptyList(), emptyList(), emptyList()))

    val pendingCaptures = captures.observePending().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setStatus(value: PaymentFilter) { filter = filter.copy(status = value) }
    fun setCategory(rootId: String?) {
        filter = if (rootId == null) {
            filter.copy(categoryRootId = null, categoryScope = null)
        } else {
            filter.copy(categoryRootId = rootId, categoryScope = CategoryTree.scopeIds(rootId, allCategories))
        }
    }
    fun setQuery(value: String) { filter = filter.copy(query = value) }
    fun setType(value: TransactionType?) { filter = filter.copy(type = value) }
    fun setAccount(value: String?) { filter = filter.copy(accountId = value) }
    fun setRange(value: DateRange) { filter = filter.copy(range = value) }
    fun setView(view: ActivityView) { filter = filter.withView(view) }
    fun clearFilters() { filter = TransactionFilter(query = filter.query) }
    fun showUnplanned(from: LocalDate, until: LocalDate) {
        filter = filter.copy(unplannedOnly = true, from = from, until = until, type = null, range = DateRange.ALL, status = PaymentFilter.ALL)
    }
    fun today(): LocalDate = clock.today()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
    unplannedFrom: LocalDate? = null,
    unplannedUntil: LocalDate? = null,
    onOpenSchedule: (String, String) -> Unit = { _, _ -> },
    onCaptures: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    LaunchedEffect(unplannedFrom, unplannedUntil) {
        if (unplannedFrom != null && unplannedUntil != null) viewModel.showUnplanned(unplannedFrom, unplannedUntil)
    }
    val data by viewModel.rows.collectAsStateWithLifecycle()
    val dueItems by viewModel.scheduled.collectAsStateWithLifecycle()
    val pendingCaptures by viewModel.pendingCaptures.collectAsStateWithLifecycle()
    val today = viewModel.today()
    val filter = viewModel.filter
    var searching by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val matching = remember(data, filter, today) {
        data.first.filter { it.matches(filter, today) }
            .sortedWith(compareByDescending<LedgerTransaction> { it.occurredOn }.thenByDescending { it.createdAtEpochMillis })
    }
    // Spends planned but not paid yet sit with the other unpaid items, each with a Paid button.
    val plannedSpends = matching.filter { com.ledgerline.app.finance.balance.BalanceMath.isPlannedUnpaid(it) }.sortedBy { it.occurredOn }
    val posted = matching.filter { !com.ledgerline.app.finance.balance.BalanceMath.isPlannedUnpaid(it) }
    // Scheduled items read soonest first; in All they are limited to two weeks so the history stays visible.
    val upcoming = remember(dueItems, filter, today) {
        dueItems.filter { it.matches(filter, today) }
            .filter { filter.view() == ActivityView.UPCOMING || it.overdue || !it.date.isAfter(today.plusDays(14)) }
            .sortedWith(compareBy({ !it.overdue }, { it.date }, { it.name }))
    }
    val currency = data.second.firstOrNull { !it.deleted }?.currency ?: "USD"
    val accounts = data.second
    val categories = data.third

    LazyColumn(
        Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                title = if (filter.unplannedOnly) "Unplanned spending" else "Activity",
                subtitle = if (filter.unplannedOnly && filter.from != null && filter.until != null) {
                    com.ledgerline.app.ui.format.cycleRange(filter.from, filter.until)
                } else {
                    null
                },
            ) {
                if (onBack != null) {
                    TextButton(onClick = onBack) { Text("Done") }
                }
                IconButton(onClick = { searching = !searching }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                IconButton(onClick = { showFilters = true }) {
                    BadgedBox(badge = { if (filter.extraCount() > 0) Badge { Text("${filter.extraCount()}") } }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Filters")
                    }
                }
            }
        }
        if (searching || filter.query.isNotBlank()) {
            item {
                TextField(
                    value = filter.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search merchant or note") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filter.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
            }
        }
        if (!filter.unplannedOnly) {
            item {
                SegmentedChoice(
                    options = listOf(
                        ActivityView.ALL to "All",
                        ActivityView.SPENDING to "Spending",
                        ActivityView.INCOME to "Income",
                        ActivityView.UPCOMING to "Upcoming",
                    ),
                    selected = filter.view(),
                    onSelect = { view -> view?.let(viewModel::setView) },
                )
            }
        }
        if (filter.extraCount() > 0) {
            item { ActiveFilterChips(filter, accounts, categories, viewModel) }
        }
        if (pendingCaptures > 0 && !filter.unplannedOnly) {
            item {
                BannerCard(
                    title = "$pendingCaptures captured from notifications",
                    body = "Not in your balances yet. Add or dismiss them.",
                    icon = Icons.Filled.Inbox,
                    action = "Review",
                    onClick = onCaptures,
                )
            }
        }
        if (posted.isEmpty() && upcoming.isEmpty()) {
            item {
                EmptyPane(
                    if (filter.unplannedOnly) "No unplanned spending" else "Nothing here yet",
                    if (filter.unplannedOnly) {
                        "Spending you mark as unplanned in this cycle shows up here."
                    } else if (filter == TransactionFilter()) {
                        "Tap Add to note a spend. Turn on auto-capture in Settings to pick up payment alerts from your messages."
                    } else {
                        "Nothing matches these filters."
                    },
                )
            }
        }
        if (posted.isNotEmpty() || plannedSpends.isNotEmpty()) {
            item(key = "swipe-hint") {
                Text("Swipe a transaction left to delete it.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (upcoming.isNotEmpty() || plannedSpends.isNotEmpty()) {
            item { DayHeader(if (filter.view() == ActivityView.UPCOMING) "Not paid yet" else "Coming up · not paid yet", null, currency) }
            item {
                GroupCard {
                    // Planned spends and scheduled bills as one list by date (overdue first), not one list after the other.
                    val merged = plannedSpends.map { Triple(it.occurredOn, it.occurredOn.isBefore(today), it as Any) } +
                        upcoming.map { Triple(it.date, it.overdue, it as Any) }
                    merged.sortedWith(compareBy({ !it.second }, { it.first })).forEach { (_, _, entry) ->
                        when (entry) {
                            is LedgerTransaction -> SwipeToDelete(entry.id, onDelete = { viewModel.delete(entry.id) }) {
                                TransactionRow(entry, accounts, categories, onPaid = { viewModel.markPaid(entry.id) }) { onOpen(entry.id) }
                            }
                            else -> {
                                val item = upcoming.first { it === entry }
                                ScheduledRow(item, currency, today) { onOpenSchedule(item.recurringId, item.date.toString()) }
                            }
                        }
                    }
                }
            }
        }
        val days = posted.groupBy { it.occurredOn }
        days.forEach { (date, rows) ->
            val spent = com.ledgerline.app.finance.dashboard.HomeMath.daySpending(rows)
            item(key = "day-$date") { DayHeader(dayLabel(date, today), spent.takeIf { it > 0 }, rows.first().currency) }
            item(key = "rows-$date") {
                GroupCard {
                    rows.forEach { tx ->
                        SwipeToDelete(tx.id, onDelete = { viewModel.delete(tx.id) }) {
                            TransactionRow(tx, accounts, categories) { onOpen(tx.id) }
                        }
                    }
                }
            }
        }
    }
    if (showFilters) {
        FilterSheet(filter, accounts.filter { it.active && !it.deleted }, categories, viewModel) { showFilters = false }
    }
}

@Composable
private fun DayHeader(title: String, spentMinor: Long?, currency: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (spentMinor != null) {
            Text("Spent ${MoneyFormat.format(spentMinor, currency)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(
        DateTimeFormatter.ofPattern(if (date.year == today.year) "EEEE, MMM d" else "EEE, MMM d, yyyy", Locale.getDefault()),
    )
}

/** One recorded transaction: category icon, merchant, category and account, signed amount. */
@Composable
fun TransactionRow(tx: LedgerTransaction, accounts: List<Account>, categories: List<Category>, onPaid: (() -> Unit)? = null, onClick: () -> Unit) {
    val colors = LocalLedgerColors.current
    val accountLabel = AccountDeletion.label(tx.accountId, accounts) { it.name }
    val category = if (tx.categoryId == null) tx.type.label() else categories.firstOrNull { it.id == tx.categoryId }?.name ?: tx.type.label()
    val counted = com.ledgerline.app.finance.balance.BalanceMath.isActive(tx)
    val tint = when (tx.type) {
        TransactionType.INCOME, TransactionType.REFUND -> colors.positive
        TransactionType.TRANSFER, TransactionType.PAYMENT -> colors.transfer
        else -> MaterialTheme.colorScheme.primary
    }
    ListRow(
        title = tx.merchantRaw.ifBlank { tx.type.label() },
        subtitle = if (!tx.paid) "${dayLabel(tx.occurredOn, java.time.LocalDate.now())} · $category · $accountLabel" else "$category · $accountLabel",
        icon = rowIcon(tx.type, tx.categoryId, categories),
        iconTint = tint,
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                MoneyFormat.signedLabel(tx.balanceEffectMinor, tx.currency),
                style = MaterialTheme.typography.titleSmall,
                color = if (counted) amountColor(tx.type, tx.balanceEffectMinor) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            when {
                !tx.paid && onPaid != null -> androidx.compose.material3.FilledTonalButton(
                    onClick = onPaid,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(34.dp),
                ) { Text("Paid", style = MaterialTheme.typography.labelLarge) }
                !tx.paid -> StatusPill("Not paid", colors.caution)
                tx.status == TransactionStatus.DETECTED -> StatusPill("Check", colors.caution)
                tx.status == TransactionStatus.IGNORED -> StatusPill("Ignored", MaterialTheme.colorScheme.onSurfaceVariant)
                tx.status == TransactionStatus.DUPLICATE -> StatusPill("Duplicate", MaterialTheme.colorScheme.onSurfaceVariant)
                tx.unplanned -> StatusPill("Unplanned", colors.caution)
            }
        }
    }
}

@Composable
fun ScheduledRow(item: ScheduledOccurrence, currency: String, today: LocalDate, trailingAction: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val colors = LocalLedgerColors.current
    val incoming = item.direction == com.ledgerline.app.domain.model.CashFlowDirection.INFLOW
    val when_ = if (item.overdue) "Overdue" else com.ledgerline.app.ui.format.dueLabel(today, item.date).removePrefix("Due ").replaceFirstChar { it.titlecase() }
    ListRow(
        title = item.name,
        subtitle = "$when_ · ${item.accountName.ifBlank { "No account" }}",
        icon = if (incoming) CategoryVisuals.forType(TransactionType.INCOME) else Icons.Filled.Event,
        iconTint = when {
            item.overdue -> colors.overdue
            incoming -> colors.positive
            else -> colors.scheduled
        },
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (incoming) "+" else "−") + MoneyFormat.format(item.amountMinor, currency),
                style = MaterialTheme.typography.titleSmall,
                color = if (item.overdue) colors.overdue else if (incoming) colors.positive else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (trailingAction == null) StatusPill(if (item.overdue) "Overdue" else "Not paid", if (item.overdue) colors.overdue else colors.scheduled)
        }
        trailingAction?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterChips(filter: TransactionFilter, accounts: List<Account>, categories: List<Category>, viewModel: TransactionListViewModel) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (filter.range != DateRange.ALL) {
            RemovableChip(if (filter.range == DateRange.THIS_MONTH) "This month" else "Last month") { viewModel.setRange(DateRange.ALL) }
        }
        filter.accountId?.let { id ->
            RemovableChip(accounts.firstOrNull { it.id == id }?.name ?: "Account") { viewModel.setAccount(null) }
        }
        filter.categoryRootId?.let { id ->
            RemovableChip(categories.firstOrNull { it.id == id }?.name ?: "Category") { viewModel.setCategory(null) }
        }
        if (filter.view() == null) {
            val label = filter.type?.label() ?: when (filter.status) {
                PaymentFilter.PAID -> "Paid only"
                else -> "Not paid only"
            }
            RemovableChip(label) { viewModel.setView(ActivityView.ALL) }
        }
        TextButton(onClick = viewModel::clearFilters) { Text("Clear all") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $label filter") },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filter: TransactionFilter,
    accounts: List<Account>,
    categories: List<Category>,
    viewModel: TransactionListViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            SheetLabel("When")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DateRange.ALL to "Any time", DateRange.THIS_MONTH to "This month", DateRange.LAST_MONTH to "Last month").forEach { (range, label) ->
                    FilterChip(filter.range == range, { viewModel.setRange(range) }, { Text(label) })
                }
            }
            SheetLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter.type == null, { viewModel.setType(null) }, { Text("Any type") })
                TransactionType.entries.forEach { type ->
                    FilterChip(filter.type == type, { viewModel.setType(type) }, { Text(type.label()) })
                }
            }
            SheetLabel("Status")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaymentFilter.ALL to "Everything", PaymentFilter.PAID to "Paid", PaymentFilter.UNPAID to "Not paid yet").forEach { (status, label) ->
                    FilterChip(filter.status == status, { viewModel.setStatus(status) }, { Text(label) })
                }
            }
            if (accounts.isNotEmpty()) {
                SheetLabel("Account")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter.accountId == null, { viewModel.setAccount(null) }, { Text("All accounts") })
                    accounts.forEach { account ->
                        FilterChip(filter.accountId == account.id, { viewModel.setAccount(account.id) }, { Text(account.name) })
                    }
                }
            }
            val groups = StandardCategories.groups(categories)
            if (groups.isNotEmpty()) {
                SheetLabel("Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter.categoryRootId == null, { viewModel.setCategory(null) }, { Text("All categories") })
                    groups.forEach { (parent, _) ->
                        FilterChip(filter.categoryRootId == parent.id, { viewModel.setCategory(parent.id) }, { Text(parent.name) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::clearFilters, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Clear all") }
                PrimaryButton("Show results", modifier = Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val merchants: MerchantRepository,
    private val captures: CaptureRepository,
    private val captureActions: CaptureActions,
    private val clock: AppClock,
    payPlan: com.ledgerline.app.domain.usecase.ObservePayPlan,
    private val recurring: com.ledgerline.app.domain.repository.RecurringRepository,
    private val homeGroups: com.ledgerline.app.domain.repository.HomeGroupRepository,
) : ViewModel() {
    /** Saving makes this a scheduled transaction (a bill, or regular income) instead of a single one. */
    var repeats by mutableStateOf(false)

    /** True: every month on the chosen date's day. False: a custom pattern from [repeatEvery]. Both start on the date. */
    var monthlyOnDay by mutableStateOf(true)
    var repeatEvery by mutableStateOf(RecurrenceFrequency.MONTHLY)

    /** When the schedule stops: never, after a number of times, or on a date. */
    var repeatEnd by mutableStateOf(com.ledgerline.app.finance.schedule.ScheduleEnd())

    /** Only new spends or income can repeat; a spend paying an existing bill already belongs to a schedule. */
    fun canRepeat(): Boolean =
        !isExisting && !fromCapture && (type == TransactionType.EXPENSE || type == TransactionType.INCOME) && billChoice == null

    /**
     * The first date of the schedule: always the date chosen above. (It used to take a separate day-of-month too,
     * which could quietly move the start to another month when the two disagreed.)
     */
    fun firstRepeatDate(): LocalDate = date

    private suspend fun saveRepeating() {
        val income = type == TransactionType.INCOME
        val start = firstRepeatDate()
        val chosenSection = itemKey?.let { key -> sections.value.firstOrNull { section -> section.items.any { it.key == key } } }
        val name = merchant.trim().ifBlank {
            sections.value.flatMap { it.items }.firstOrNull { it.key == itemKey }?.name
                ?: categoryId?.let { id -> categories.value.firstOrNull { it.id == id }?.name }
                ?: if (income) "Income" else "Payment"
        }
        val frequency = if (monthlyOnDay) RecurrenceFrequency.MONTHLY else repeatEvery
        val before = recurring.observe().first().map { it.id }.toSet()
        val result = recurring.save(
            com.ledgerline.app.domain.model.RecurringDraft(
                existingId = null,
                name = name,
                accountId = accountId,
                categoryId = categoryId,
                direction = if (income) com.ledgerline.app.domain.model.CashFlowDirection.INFLOW else com.ledgerline.app.domain.model.CashFlowDirection.OUTFLOW,
                kind = when {
                    income -> com.ledgerline.app.domain.model.ScheduledKind.INCOME
                    chosenSection?.id == com.ledgerline.app.finance.envelopes.HomeSections.SUBSCRIPTIONS -> com.ledgerline.app.domain.model.ScheduledKind.SUBSCRIPTION
                    else -> com.ledgerline.app.domain.model.ScheduledKind.BILL
                },
                amountMinor = amountMinor,
                frequency = frequency,
                intervalDays = null,
                dayOfMonth = start.dayOfMonth,
                dayOfWeek = start.dayOfWeek.value,
                startOn = start,
                priority = if (income) com.ledgerline.app.domain.model.PaymentPriority.IMPORTANT else com.ledgerline.app.domain.model.PaymentPriority.MANDATORY,
                incomeConfidence = if (income) com.ledgerline.app.domain.model.IncomeConfidence.HIGH else com.ledgerline.app.domain.model.IncomeConfidence.NONE,
                active = true,
                note = note.trim(),
                endOn = repeatEnd.endOn(start),
                maxOccurrences = repeatEnd.maxOccurrences(),
            ),
        )
        if (result is SaveResult.Invalid) {
            error = result.message
            return
        }
        val id = recurring.observe().first().firstOrNull { it.id !in before }?.id
        if (id != null) {
            // It shows in the Home group of the item you picked, if any.
            chosenSection?.let { homeGroups.place("bill:$id", it.id) }
            // A first date that has already come is a payment that happened: record it.
            if (!start.isAfter(clock.today())) recurring.markPaid(id, start)
        }
        _done.emit(Unit)
    }
    private val args = savedStateHandle.toRoute<TransactionEditorRoute>()

    /** Your Home groups and their items, offered as what a spend is for. */
    val sections = payPlan.observe()
        .map { it.sections.filter { section -> section.items.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The Home item this spend is for, by key; null means un-marked. */
    var itemKey by mutableStateOf<String?>(null)
        private set

    /** A bill picked as what this spend is for: saving marks that bill paid on its due date. */
    private var billChoice: Pair<String, LocalDate>? = null
    private var existingRecurring: String? = null

    /** Picks (or, tapped again, clears) the Home item a spend is for. A bill also fills in what is left to pay. */
    fun chooseItem(item: com.ledgerline.app.finance.envelopes.HomeItem?) {
        categoryTouched = true
        if (item == null || item.key == itemKey) {
            itemKey = null
            billChoice = null
            categoryId = null
            return
        }
        itemKey = item.key
        item.accountId?.let { accountId = it }
        if (item.kind == com.ledgerline.app.finance.envelopes.HomeItemKind.BILL && item.recurringId != null && item.nextDue != null) {
            billChoice = item.recurringId to item.nextDue
            categoryId = null
            if (amountMinor == 0L && item.leftMinor > 0) run {
                val currency = accountOptions.value.firstOrNull { it.id == accountId }?.currency ?: "USD"
                val digits = runCatching { java.util.Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
                onAmountText(com.ledgerline.app.finance.money.AmountInput.fromMinor(item.leftMinor, digits), currency)
            }
        } else {
            billChoice = null
            categoryId = item.categoryId
        }
    }

    /** The item shown as chosen: the one picked, or the one an existing spend already belongs to. */
    fun selectedKey(sections: List<com.ledgerline.app.finance.envelopes.HomeSection>): String? =
        itemKey ?: sections.flatMap { it.items }.firstOrNull { item ->
            (existingRecurring != null && item.recurringId == existingRecurring) ||
                (item.kind == com.ledgerline.app.finance.envelopes.HomeItemKind.ENVELOPE && categoryId != null && item.categoryId == categoryId)
        }?.key
    val isExisting = args.transactionId.isNotBlank()
    val fromCapture = args.captureId.isNotBlank()
    val accountOptions = accounts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = categories.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var type by mutableStateOf(runCatching { TransactionType.valueOf(args.presetType) }.getOrDefault(TransactionType.EXPENSE))
    var amountMinor by mutableStateOf(0L)

    /** What the person typed in the amount box, kept as text so "12." and "12.5" stay as typed. */
    var amountText by mutableStateOf("")
        private set
    var merchant by mutableStateOf("")
    var accountId by mutableStateOf("")
    var counterAccountId by mutableStateOf("")
    var categoryId by mutableStateOf<String?>(null)
    var date by mutableStateOf(
        args.occurredOn.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: clock.today(),
    )
    var note by mutableStateOf("")
    var adjustmentDecreases by mutableStateOf(false)
    var unplanned by mutableStateOf(false)

    /** A spend you are planning: saved now, counted only once you tap Paid. */
    var notPaidYet by mutableStateOf(false)
    private var paidTouched = false

    fun choosePaid(paid: Boolean) {
        paidTouched = true
        notPaidYet = !paid
    }

    /** Until you choose, a new spend dated later than today starts as not paid yet, and one today or earlier as paid. */
    fun suggestPaid() {
        if (!paidTouched && !isExisting) notPaidYet = date.isAfter(clock.today())
    }
    var error by mutableStateOf<String?>(null)
    var duplicate by mutableStateOf<SaveResult.PossibleDuplicate?>(null)
    var captureSource by mutableStateOf<String?>(null)
        private set
    private var categoryTouched = false
    private var existingLink: String? = null
    private val _done = MutableSharedFlow<Unit>()
    val done = _done.asSharedFlow()

    fun today(): LocalDate = clock.today()

    init {
        if (isExisting) {
            viewModelScope.launch {
                val source = transactions.editingSource(args.transactionId) ?: return@launch
                type = source.type
                setAmountMinor(source.amountMinor, source.currency)
                merchant = source.merchantRaw
                accountId = source.accountId
                counterAccountId = source.counterAccountId.orEmpty()
                categoryId = source.categoryId
                date = source.occurredOn
                note = source.note
                existingLink = source.linkId
                unplanned = source.unplanned
                existingRecurring = source.recurringPaymentId
                notPaidYet = !source.paid
                paidTouched = true
                categoryTouched = true
            }
        } else if (fromCapture) {
            viewModelScope.launch {
                val capture = captures.get(args.captureId) ?: return@launch
                type = capture.transactionType
                setAmountMinor(capture.amountMinor, capture.currency)
                merchant = capture.merchant
                date = capture.occurredOn
                note = "From ${capture.sourceLabel}"
                captureSource = capture.sourceLabel
                CaptureAccounts.suggested(capture, accountOptions.first { it.isNotEmpty() })?.let { accountId = it.id }
                captureActions.suggestedCategory(capture.merchant)?.let { categoryId = it }
            }
        } else {
            if (args.amountMinor > 0) setAmountMinor(args.amountMinor, "USD")
            if (args.merchant.isNotBlank()) merchant = args.merchant
            // New entries start on your main spending account so a quick spend needs only an amount.
            viewModelScope.launch {
                val live = accountOptions.first { it.isNotEmpty() }.filter { it.active }
                if (accountId.isBlank()) {
                    accountId = (live.firstOrNull { it.includeInSafeToSpend } ?: live.firstOrNull())?.id.orEmpty()
                }
            }
        }
    }

    fun onAmountText(raw: String, currency: String) {
        val digits = MoneyFormat.fractionDigits(currency)
        amountText = AmountInput.clean(raw, digits)
        amountMinor = AmountInput.toMinor(amountText, digits)
    }

    private fun setAmountMinor(minor: Long, currency: String) {
        amountMinor = minor
        amountText = AmountInput.fromMinor(minor, MoneyFormat.fractionDigits(currency))
    }

    fun onMerchant(value: String) {
        merchant = value
        if (!categoryTouched) {
            viewModelScope.launch {
                val suggested = merchants.suggestion(
                    MerchantNormalizer.normalize(value),
                    FinancePolicy.CATEGORY_LEARN_CONFIRMATIONS,
                )
                val usable = suggested != null && categories.value.firstOrNull { it.id == suggested }?.archived == false
                if (!categoryTouched && usable) categoryId = suggested
            }
        }
    }

    fun onCategory(id: String?) {
        categoryTouched = true
        categoryId = id
    }

    fun save(acknowledgeDuplicate: Boolean) {
        viewModelScope.launch {
            if (repeats && canRepeat()) {
                if (amountMinor <= 0) {
                    error = "Enter an amount."
                } else if (accountId.isBlank()) {
                    error = "Choose an account."
                } else {
                    saveRepeating()
                }
                return@launch
            }
            // A new spend for a bill is that bill's payment: mark it paid so its ring and the calendar fill.
            val bill = billChoice
            if (bill != null && !isExisting && type == TransactionType.EXPENSE) {
                when (val result = recurring.markPaid(bill.first, bill.second)) {
                    is SaveResult.Invalid -> error = result.message
                    else -> {
                        // Keep what you typed: if the amount or account differs from the bill's, correct the payment.
                        val posted = transactions.observeAll().first().firstOrNull {
                            it.recurringPaymentId == bill.first && it.occurredOn == bill.second && com.ledgerline.app.finance.balance.BalanceMath.isActive(it)
                        }
                        if (posted != null && amountMinor > 0 && (posted.amountMinor != amountMinor || (accountId.isNotBlank() && posted.accountId != accountId))) {
                            transactions.save(
                                TransactionDraft(
                                    existingId = posted.id,
                                    existingLinkId = posted.linkId,
                                    type = posted.type,
                                    amountMinor = amountMinor,
                                    merchant = posted.merchantRaw.ifBlank { merchant.ifBlank { "Bill" } },
                                    accountId = accountId.ifBlank { posted.accountId },
                                    counterAccountId = posted.counterAccountId,
                                    categoryId = posted.categoryId,
                                    occurredOn = posted.occurredOn,
                                    note = note.ifBlank { posted.note },
                                    acknowledgeDuplicate = true,
                                ),
                            )
                        }
                        _done.emit(Unit)
                    }
                }
                return@launch
            }
            val result = transactions.save(
                TransactionDraft(
                    existingId = args.transactionId.ifBlank { null },
                    existingLinkId = existingLink,
                    type = type,
                    amountMinor = amountMinor,
                    // The name is optional: a spend with none is called by its category, so Save never silently fails.
                    merchant = merchant.ifBlank {
                        categoryId?.let { id -> categories.value.firstOrNull { it.id == id }?.name }
                            ?: when (type) {
                                TransactionType.INCOME -> "Income"
                                TransactionType.REFUND -> "Refund"
                                TransactionType.ADJUSTMENT -> "Balance correction"
                                else -> "Spend"
                            }
                    },
                    accountId = accountId,
                    counterAccountId = counterAccountId.ifBlank { null },
                    categoryId = categoryId,
                    occurredOn = date,
                    note = note,
                    acknowledgeDuplicate = acknowledgeDuplicate,
                    adjustmentDecreases = adjustmentDecreases,
                    unplanned = unplanned && (type == TransactionType.EXPENSE || type == TransactionType.REFUND),
                    notPaidYet = notPaidYet && type == TransactionType.EXPENSE,
                ),
            )
            when (result) {
                SaveResult.Saved -> {
                    if (fromCapture) captureActions.markAdded(args.captureId)
                    _done.emit(Unit)
                }
                is SaveResult.Invalid -> error = result.message
                is SaveResult.PossibleDuplicate -> duplicate = result
            }
        }
    }

    /** The capture was already recorded: keep the existing transaction and clear it from the inbox. */
    fun keepExisting() {
        duplicate = null
        if (fromCapture) {
            viewModelScope.launch {
                captureActions.markAdded(args.captureId)
                _done.emit(Unit)
            }
        }
    }

    fun dismissDuplicate() { duplicate = null }
}

private enum class EntryKind(val label: String) { SPENT("Spent"), RECEIVED("Received"), MOVED("Transfer"), OTHER("Other") }

private fun TransactionType.entryKind(): EntryKind = when (this) {
    TransactionType.EXPENSE -> EntryKind.SPENT
    TransactionType.INCOME -> EntryKind.RECEIVED
    TransactionType.TRANSFER -> EntryKind.MOVED
    else -> EntryKind.OTHER
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorScreen(onDone: () -> Unit, viewModel: TransactionEditorViewModel = hiltViewModel()) {
    val accounts by viewModel.accountOptions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    val active = accounts.filter { it.active }
    val currency = active.firstOrNull { it.id == viewModel.accountId }?.currency ?: active.firstOrNull()?.currency ?: "USD"
    val type = viewModel.type
    val needsCounter = type == TransactionType.TRANSFER || type == TransactionType.PAYMENT
    val focus = remember { FocusRequester() }
    var showMore by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!viewModel.isExisting && !viewModel.fromCapture) runCatching { focus.requestFocus() }
    }
    val title = when {
        viewModel.isExisting -> "Edit"
        viewModel.fromCapture -> "Add captured spend"
        else -> "Add transaction"
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close without saving") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding().padding(16.dp)) {
                PrimaryButton(
                    if (viewModel.isExisting) "Save changes" else "Save",
                    enabled = active.isNotEmpty() && viewModel.amountMinor > 0,
                ) { viewModel.save(acknowledgeDuplicate = false) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            viewModel.captureSource?.let { source ->
                Text(
                    "Captured from ${if (source == "Text message") "a text message" else source}. Check the details, then save.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SegmentedChoice(
                options = EntryKind.entries.map { it to it.label },
                selected = type.entryKind(),
                onSelect = { kind ->
                    viewModel.type = when (kind) {
                        EntryKind.SPENT -> TransactionType.EXPENSE
                        EntryKind.RECEIVED -> TransactionType.INCOME
                        EntryKind.MOVED -> TransactionType.TRANSFER
                        EntryKind.OTHER -> if (type.entryKind() == EntryKind.OTHER) type else TransactionType.REFUND
                    }
                },
            )
            if (type.entryKind() == EntryKind.OTHER) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        TransactionType.REFUND to "Refund",
                        TransactionType.PAYMENT to "Card or loan payment",
                        TransactionType.ADJUSTMENT to "Balance correction",
                    ).forEach { (option, label) ->
                        FilterChip(type == option, { viewModel.type = option }, { Text(label) })
                    }
                }
            }
            BigAmountField(
                text = viewModel.amountText,
                currency = currency,
                color = when (type) {
                    TransactionType.INCOME, TransactionType.REFUND -> LocalLedgerColors.current.positive
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.focusRequester(focus),
            ) { viewModel.onAmountText(it, currency) }
            if (!needsCounter) {
                OutlinedTextField(
                    value = viewModel.merchant,
                    onValueChange = viewModel::onMerchant,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (type == TransactionType.INCOME) "From (optional)" else "What was it? (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
            val homeSections by viewModel.sections.collectAsStateWithLifecycle()
            if (type == TransactionType.EXPENSE && homeSections.isNotEmpty()) {
                HomeItemChooser(homeSections, viewModel.selectedKey(homeSections), viewModel::chooseItem)
            } else if (!needsCounter && type != TransactionType.ADJUSTMENT) {
                CategoryChooser(
                    categories = categories,
                    selectedId = viewModel.categoryId,
                    income = type == TransactionType.INCOME,
                    onSelect = viewModel::onCategory,
                )
            }
            if (active.isEmpty()) {
                Text("Add an account first: Settings › Accounts.", color = MaterialTheme.colorScheme.error)
            } else {
                AccountChooser(
                    label = when (type) {
                        TransactionType.INCOME, TransactionType.REFUND -> "Into"
                        TransactionType.TRANSFER -> "From"
                        TransactionType.ADJUSTMENT -> "Account to correct"
                        else -> "Paid with"
                    },
                    accounts = active,
                    selectedId = viewModel.accountId,
                ) { viewModel.accountId = it }
                if (needsCounter) {
                    AccountChooser(
                        label = if (type == TransactionType.PAYMENT) "Paying off" else "To",
                        accounts = active.filter { it.id != viewModel.accountId },
                        selectedId = viewModel.counterAccountId,
                    ) { viewModel.counterAccountId = it }
                }
            }
            DateChooser(viewModel.date, viewModel.today()) { viewModel.date = it }
            if (viewModel.canRepeat()) RepeatPicker(viewModel)
            if (type == TransactionType.EXPENSE || type == TransactionType.REFUND) {
                SwitchRow(
                    title = "Unplanned",
                    body = "A spend you did not plan for. Home totals these so you can see where the plan slipped.",
                    checked = viewModel.unplanned,
                ) { viewModel.unplanned = it }
            }
            if (type == TransactionType.EXPENSE && !viewModel.repeats) {
                // Paid now, or saved to pay later. Unpaid ones show on the planned line and in the forecast, and wait in
                // Activity (and on their page) with a Paid button; only paid ones count as spent.
                LaunchedEffect(viewModel.date) { viewModel.suggestPaid() }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !viewModel.notPaidYet,
                            onClick = { viewModel.choosePaid(true) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("Paid") }
                        SegmentedButton(
                            selected = viewModel.notPaidYet,
                            onClick = { viewModel.choosePaid(false) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("Not paid yet") }
                    }
                    Text(
                        if (viewModel.notPaidYet) "Saved as planned. Tap Paid on it when you pay; until then it is only in the forecast."
                        else "Counts as spent now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (type == TransactionType.ADJUSTMENT) {
                SwitchRow(
                    title = "This lowers the balance",
                    body = "Leave off to raise the balance.",
                    checked = viewModel.adjustmentDecreases,
                ) { viewModel.adjustmentDecreases = it }
            }
            if (showMore || viewModel.note.isNotBlank()) {
                OutlinedTextField(
                    value = viewModel.note,
                    onValueChange = { viewModel.note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                    shape = RoundedCornerShape(16.dp),
                )
            } else {
                TextButton(onClick = { showMore = true }) { Text("Add a note") }
            }
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
        }
    }
    viewModel.duplicate?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(if (prompt.score >= FinancePolicy.DUPLICATE_MERGE_SCORE) "Already recorded?" else "Possible duplicate") },
            text = { Text("${prompt.summary} Saving again would count it twice. Nothing is deleted.") },
            confirmButton = { TextButton(onClick = viewModel::keepExisting) { Text("Keep existing") } },
            dismissButton = { TextButton(onClick = { viewModel.save(acknowledgeDuplicate = true) }) { Text("Save anyway") } },
        )
    }
}

/**
 * A large amount with the currency symbol, centred together as one figure. Tapping it opens a small keypad right
 * under it instead of the phone keyboard. It starts open when there is no amount yet. Typing "12.5" means 12.50.
 */
@Composable
internal fun BigAmountField(text: String, currency: String, color: Color, modifier: Modifier = Modifier, onText: (String) -> Unit) {
    val symbol = remember(currency) {
        runCatching { java.util.Currency.getInstance(currency).getSymbol(Locale.getDefault()) }.getOrDefault(currency)
    }
    val style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold, color = color)
    var open by rememberSaveable { mutableStateOf(text.isEmpty()) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .focusable()
                .onFocusChanged { if (it.isFocused) keyboard?.hide() }
                .clickable {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    open = !open
                }
                .padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text(
                    symbol,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline().padding(end = 4.dp),
                )
                Text(
                    text.ifEmpty { "0" },
                    style = if (text.isEmpty()) style.copy(color = MaterialTheme.colorScheme.outline) else style,
                    modifier = Modifier.alignByBaseline(),
                    maxLines = 1,
                )
            }
            Text(if (open) "Amount" else "Amount · tap to change", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) AmountKeypad(text, onText, onDone = { open = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountChooser(label: String, accounts: List<Account>, selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { account ->
                FilterChip(
                    selected = account.id == selectedId,
                    onClick = { onSelect(account.id) },
                    label = { Text(account.paymentLabel()) },
                    leadingIcon = { Icon(CategoryVisuals.forAccount(account.type), contentDescription = null, modifier = Modifier.padding(0.dp)) },
                )
            }
        }
        if (selectedId.isBlank()) {
            Text("Choose an account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateChooser(date: LocalDate, today: LocalDate, onDate: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("When", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(date == today, { onDate(today) }, { Text("Today") })
            FilterChip(date == today.minusDays(1), { onDate(today.minusDays(1)) }, { Text("Yesterday") })
            val other = date != today && date != today.minusDays(1)
            FilterChip(other, { picking = true }, { Text(if (other) date.medium() else "Pick a date") }, leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) })
        }
    }
    if (picking) {
        DatePickerDialogOnly(date, onDismiss = { picking = false }) {
            onDate(it)
            picking = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerDialogOnly(date: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = com.ledgerline.app.finance.time.FinancialDate.toPickerMillis(date),
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(com.ledgerline.app.finance.time.FinancialDate.fromPickerMillis(it)) } ?: onDismiss()
            }) { Text("Use this date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { androidx.compose.material3.DatePicker(state = state) }
}

@Composable
fun SwitchRow(title: String, body: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    // The whole row is the switch: a bigger target, and screen readers announce it with its title.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .toggleable(value = checked, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Main categories as chips with icons, then the chosen group's subcategories. Archived categories are never offered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChooser(
    categories: List<Category>,
    selectedId: String?,
    income: Boolean,
    onSelect: (String?) -> Unit,
) {
    val groups = StandardCategories.groupsFor(income, categories)
    if (groups.isEmpty()) return
    val selectedRoot = CategoryTree.root(selectedId, categories)?.id
    var tapped by remember { mutableStateOf<String?>(null) }
    val groupId = (tapped ?: selectedRoot)?.takeIf { id -> groups.any { it.first.id == id } } ?: groups.singleOrNull()?.first?.id
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { (parent, children) ->
                FilterChip(
                    selected = groupId == parent.id,
                    onClick = {
                        tapped = parent.id
                        // A group with no subcategories is itself the choice.
                        if (children.isEmpty()) onSelect(parent.id)
                    },
                    label = { Text(parent.name) },
                    leadingIcon = { Icon(CategoryVisuals.forName(parent.name), contentDescription = null) },
                )
            }
        }
        val subs = groups.firstOrNull { it.first.id == groupId }?.second.orEmpty()
        if (subs.isNotEmpty()) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                subs.forEach { sub ->
                    FilterChip(selectedId == sub.id, { onSelect(if (selectedId == sub.id) null else sub.id) }, { Text(sub.name) })
                }
            }
        }
        if (selectedId != null && groups.none { (parent, children) -> parent.id == selectedId || children.any { it.id == selectedId } }) {
            Text("Saved category: ${CategoryTree.label(selectedId, categories)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val clock: AppClock,
) : ViewModel() {
    /** Marks this planned spend paid today. From now it counts as spent. */
    fun markPaid() {
        viewModelScope.launch { transactions.markSpendPaid(args.transactionId, clock.today()) }
    }

    private val args = savedStateHandle.toRoute<TransactionDetailRoute>()
    val state = combine(transactions.observeHistory(), accounts.observeIncludingDeleted(), categories.observe()) { txs, accts, cats ->
        Triple(txs.firstOrNull { it.id == args.transactionId }, accts, cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(null, emptyList(), emptyList()))

    private val _deleted = MutableSharedFlow<String>()
    val deleted = _deleted.asSharedFlow()

    fun delete() {
        viewModelScope.launch {
            transactions.softDelete(args.transactionId)
            _deleted.emit(args.transactionId)
        }
    }

    fun restore(id: String) {
        viewModelScope.launch { transactions.restore(id) }
    }

    fun setStatus(status: TransactionStatus) {
        viewModelScope.launch { transactions.setStatus(args.transactionId, status) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.deleted.collect { id ->
            snackbar.showBriefUndo("Transaction removed from balances") { viewModel.restore(id) }
            onBack()
        }
    }
    val tx = state.first
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (tx != null && tx.deletedAtEpochMillis == null) {
                        IconButton(onClick = { onEdit(tx.id) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tx == null || tx.deletedAtEpochMillis != null) {
                Text("This transaction is not in the active ledger.")
            } else {
                val categories = state.third
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.ledgerline.app.ui.components.IconBubble(rowIcon(tx.type, tx.categoryId, categories), MaterialTheme.colorScheme.primary, size = 56.dp)
                    Text(tx.merchantRaw.ifBlank { tx.type.label() }, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(
                        MoneyFormat.signedLabel(tx.balanceEffectMinor, tx.currency),
                        style = MaterialTheme.typography.displaySmall,
                        color = amountColor(tx.type, tx.balanceEffectMinor),
                    )
                    Text(tx.occurredOn.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!tx.paid) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LocalLedgerColors.current.caution.copy(alpha = 0.12f)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Not paid yet", style = MaterialTheme.typography.titleMedium, color = LocalLedgerColors.current.caution)
                        Text(
                            "Planned for ${(tx.plannedOn ?: tx.occurredOn).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}. It is in your forecast but not counted as spent until you pay.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = viewModel::markPaid, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Mark as paid", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                if (tx.status == TransactionStatus.DETECTED) {
                    BannerCard(
                        title = "Not counted yet",
                        body = "This was picked up automatically. Count it to include it in your balances.",
                        icon = Icons.Filled.Inbox,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        action = "Count it",
                        onClick = { viewModel.setStatus(TransactionStatus.CONFIRMED) },
                    )
                }
                com.ledgerline.app.ui.components.LedgerCard {
                    KeyValue("Type", tx.type.label())
                    KeyValue("Category", CategoryTree.label(tx.categoryId, categories))
                    KeyValue("Account", AccountDeletion.label(tx.accountId, state.second) { it.paymentLabel() })
                    tx.counterAccountId?.let { counter ->
                        KeyValue("Other account", AccountDeletion.label(counter, state.second) { it.paymentLabel() })
                    }
                    if (tx.unplanned) KeyValue("Unplanned", "Yes")
                    KeyValue("Paid", if (tx.paid) "Yes" else "Not yet")
                    tx.plannedOn?.takeIf { tx.paid && it != tx.occurredOn }?.let { KeyValue("Planned for", it.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))) }
                    KeyValue("Recorded by", tx.detectionSource.label())
                    KeyValue("Status", tx.status.name.lowercase().replaceFirstChar { it.titlecase() })
                    if (tx.note.isNotBlank()) KeyValue("Note", tx.note)
                }
                if (state.second.any { it.deleted && it.id == tx.accountId }) {
                    Text(
                        "This account was deleted. The transaction stays in history and is not counted in active balances. Edit it to choose another account.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.setStatus(TransactionStatus.DUPLICATE) }, modifier = Modifier.weight(1f)) { Text("Duplicate") }
                    OutlinedButton(onClick = { viewModel.setStatus(TransactionStatus.IGNORED) }, modifier = Modifier.weight(1f)) { Text("Ignore") }
                }
                Text(
                    "Duplicate and Ignore keep the record but stop it from counting in balances.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Remove this transaction?",
            body = "Balances will update. The record stays on this device and you can undo from the notice that follows.",
            confirm = "Remove",
            onConfirm = {
                confirm = false
                viewModel.delete()
            },
            onDismiss = { confirm = false },
        )
    }
}

/**
 * What a spend is for, picked from your Home groups: each group's name as a faint label with a thin line, then its
 * items. Nothing picked means un-marked, and Home lists it under Un-marked spending. Picking a bill pays that bill.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeItemChooser(
    sections: List<com.ledgerline.app.finance.envelopes.HomeSection>,
    selected: String?,
    onChoose: (com.ledgerline.app.finance.envelopes.HomeItem?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("For", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (selected != null) TextButton(onClick = { onChoose(null) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Leave un-marked") }
        }
        sections.forEach { section ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(section.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                Box(Modifier.padding(start = 8.dp).weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                section.items.forEach { item ->
                    FilterChip(
                        selected = item.key == selected,
                        onClick = { onChoose(item) },
                        label = { Text(item.name) },
                        leadingIcon = if (item.kind == com.ledgerline.app.finance.envelopes.HomeItemKind.BILL) {
                            { Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (selected == null) {
            Text("Not picked: it will show under Un-marked spending on Home.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 1st, 2nd, 3rd, 4th ... 21st, 22nd, 23rd ... 31st. */
internal fun ordinal(day: Int): String = day.toString() + when {
    day % 100 in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

/**
 * Repeats: turn a transaction into a schedule. Either the same day each month, or a custom pattern (every week, two
 * weeks, month, three months, or year) starting on the date chosen above. It then shows on the calendar and on
 * Home, and you are reminded the day before.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepeatPicker(viewModel: TransactionEditorViewModel) {
    SwitchRow(
        title = "Repeats",
        body = "Make it a scheduled transaction. It shows on the calendar and Home, and you get a reminder the day before.",
        checked = viewModel.repeats,
    ) { viewModel.repeats = it }
    if (!viewModel.repeats) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val day = viewModel.date.dayOfMonth
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(viewModel.monthlyOnDay, { viewModel.monthlyOnDay = true }, { Text("Every month on the ${ordinal(day)}") })
            FilterChip(!viewModel.monthlyOnDay, { viewModel.monthlyOnDay = false }, { Text("Other") })
        }
        if (!viewModel.monthlyOnDay) {
            com.ledgerline.app.ui.components.RepeatEveryPicker(viewModel.repeatEvery) { viewModel.repeatEvery = it }
        }
        val first = viewModel.firstRepeatDate()
        com.ledgerline.app.ui.components.ScheduleEndPicker(
            viewModel.repeatEnd,
            first,
            if (viewModel.monthlyOnDay) RecurrenceFrequency.MONTHLY else viewModel.repeatEvery,
        ) { viewModel.repeatEnd = it }
        Text(
            "Starts ${first.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))} (change the date above)" +
                if (!first.isAfter(viewModel.today())) " · recorded as paid now" else "",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Swipe a row left to delete it: a red band with a bin shows under it as it slides. The "Transaction deleted" message
 * that follows has Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDelete(key: String, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    androidx.compose.runtime.key(key) {
        androidx.compose.material3.SwipeToDismissBox(
            state = state,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Row(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(LocalLedgerColors.current.negative).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text("Delete", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White)
                }
            },
        ) {
            Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
        }
    }
}
