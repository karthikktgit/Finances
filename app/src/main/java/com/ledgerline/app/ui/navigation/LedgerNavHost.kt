package com.ledgerline.app.ui.navigation

import androidx.compose.foundation.background
import com.ledgerline.app.ui.theme.iconPattern
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ledgerline.app.ui.accounts.AccountEditorScreen
import com.ledgerline.app.ui.accounts.AccountsScreen
import com.ledgerline.app.ui.budgets.BudgetEditorScreen
import com.ledgerline.app.ui.budgets.BudgetsScreen
import com.ledgerline.app.ui.calendar.CalendarScreen
import com.ledgerline.app.ui.calendar.RecurringEditorScreen
import com.ledgerline.app.ui.capture.CaptureInboxScreen
import com.ledgerline.app.ui.capture.CaptureSetupScreen
import com.ledgerline.app.ui.components.SubScreen
import com.ledgerline.app.ui.dashboard.DashboardScreen
import com.ledgerline.app.ui.dashboard.SafeToSpendScreen
import com.ledgerline.app.ui.debt.DebtDetailScreen
import com.ledgerline.app.ui.debt.DebtScreen
import com.ledgerline.app.ui.features.BankScreen
import com.ledgerline.app.ui.cashflow.CashFlowScreen
import com.ledgerline.app.ui.features.RemindersScreen
import com.ledgerline.app.ui.features.ReviewScreen
import com.ledgerline.app.ui.features.SubscriptionsScreen
import com.ledgerline.app.ui.features.WhatIfScreen
import com.ledgerline.app.ui.insights.InsightsScreen
import com.ledgerline.app.ui.more.CategoriesScreen
import com.ledgerline.app.ui.more.MoreScreen
import com.ledgerline.app.ui.more.SalaryCycleScreen
import com.ledgerline.app.ui.more.SettingsScreen
import com.ledgerline.app.ui.plan.PlanScreen
import com.ledgerline.app.ui.security.LockSetupScreen
import com.ledgerline.app.ui.transactions.TransactionDetailScreen
import com.ledgerline.app.ui.transactions.TransactionEditorScreen
import com.ledgerline.app.ui.transactions.TransactionsScreen
import com.ledgerline.app.ui.undo.UndoCenter
import com.ledgerline.app.ui.undo.showBriefUndo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class Tab(val label: String, val icon: ImageVector, val route: Any)

@HiltViewModel
class UndoViewModel @Inject constructor(
    private val undoCenter: UndoCenter,
) : ViewModel() {
    val messages = undoCenter.messages
    fun undo() {
        viewModelScope.launch { undoCenter.consume() }
    }
}

/**
 * Four tabs, one Add button. Home answers "how am I doing", Activity is the record, Plan is where you set what
 * comes in and goes out, Insights looks back. Everything else opens from those, with a back arrow.
 *
 * @param openCaptures changes each time a "tap to add" notification is opened, so the inbox opens on top.
 */
@Composable
fun LedgerScaffold(openCaptures: Int = 0, openCycle: Int = 0) {
    val navController = rememberNavController()
    val undoViewModel: UndoViewModel = hiltViewModel()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(undoViewModel) {
        undoViewModel.messages.collect { message ->
            snackbar.showBriefUndo(message) { undoViewModel.undo() }
        }
    }
    LaunchedEffect(openCaptures) {
        if (openCaptures > 0) navController.navigate(CaptureInboxRoute) { launchSingleTop = true }
    }
    LaunchedEffect(openCycle) {
        if (openCycle > 0) navController.navigate(CycleSetupRoute()) { launchSingleTop = true }
    }
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val tabs = listOf(
        Tab("Home", Icons.Filled.Home, HomeRoute),
        Tab("Activity", Icons.AutoMirrored.Filled.ReceiptLong, TransactionsRoute),
        Tab("Calendar", Icons.Filled.CalendarMonth, CalendarRoute),
        Tab("Insights", Icons.Filled.Insights, InsightsRoute),
    )
    val back: () -> Unit = { navController.popBackStack() }
    // The day picked on the Calendar tab, so its "Add bill" button starts on that date.
    val calendarDay = remember { androidx.compose.runtime.mutableStateOf(java.time.LocalDate.now()) }
    val addSpend: () -> Unit = { navController.navigate(TransactionEditorRoute()) }

    /**
     * The tab bar and Add button belong to each tab page rather than to the whole app, so they move with the page:
     * a back swipe from a detail screen reveals the tab underneath complete with its bar, instead of the bar
     * popping in when the gesture ends.
     */
    @Composable
    fun TabPage(route: Any, add: Pair<String, () -> Unit>? = null, content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit) {
        Scaffold(
            // Each page is opaque with its own faint icon pattern, so pages never show through each other mid-swipe.
            modifier = Modifier.background(MaterialTheme.colorScheme.background).iconPattern(),
            containerColor = Color.Transparent,
            floatingActionButton = {
                if (add != null) {
                    com.ledgerline.app.ui.components.CashButton(add.first, add.second)
                }
            },
            bottomBar = {
                androidx.compose.foundation.layout.Column {
                    // A flat bar on the page colour with a hairline above it: quiet, so the page content leads.
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = tab.route == route,
                                onClick = { navController.navigateTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
            content = content,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = { LedgerMotion.enter(initialState.destination, targetState.destination) },
            exitTransition = { LedgerMotion.exit(initialState.destination, targetState.destination) },
            popEnterTransition = { LedgerMotion.popEnter(initialState.destination, targetState.destination) },
            popExitTransition = { LedgerMotion.popExit(initialState.destination, targetState.destination) },
            // The back gesture plays the same animation as a back press, scrubbed by the finger. Using the library's
            // separate default (a shrink) and then switching to the slide on release is what made it stutter.
            predictivePopEnterTransition = { LedgerMotion.popEnter(initialState.destination, targetState.destination) },
            predictivePopExitTransition = { LedgerMotion.popExit(initialState.destination, targetState.destination) },
        ) {
            composable<HomeRoute> {
                TabPage(HomeRoute, "Add" to addSpend) { padding ->
                DashboardScreen(
                    onSafeToSpend = { navController.navigate(SafeToSpendRoute) },
                    onTransaction = { id -> navController.navigate(TransactionDetailRoute(id)) },
                    onAccounts = { navController.navigate(AccountsRoute) },
                    onReview = { navController.navigate(ReviewRoute) },
                    onCaptures = { navController.navigate(CaptureInboxRoute) },
                    onCaptureSetup = { navController.navigate(DetectionRoute) },
                    onEditSchedule = { id, date -> navController.navigate(RecurringEditorRoute(id, date)) },
                    onCalendar = { navController.navigateTab(CalendarRoute) },
                    onActivity = { navController.navigateTab(TransactionsRoute) },
                    onSettings = { navController.navigate(MoreRoute) },
                    onPlan = { navController.navigate(PlanRoute) },
                    onUnplanned = { start, end -> navController.navigate(UnplannedTransactionsRoute(start, end)) },
                    onBuy = { name, amount -> navController.navigate(TransactionEditorRoute(amountMinor = amount, merchant = name)) },
                    onCashFlow = { navController.navigate(ForecastRoute) },
                    onOpenGoal = { id -> navController.navigate(GoalDetailRoute(id)) },
                    onAddGoal = { navController.navigate(GoalEditorRoute()) },
                    onAddAccount = { navController.navigate(AccountEditorRoute()) },
                    onCycleSetup = { navController.navigate(CycleSetupRoute()) },
                    onCycleReview = { navController.navigate(CycleReviewRoute()) },
                    onEditAccount = { id -> navController.navigate(AccountEditorRoute(id)) },
                    contentPadding = padding,
                )
                }
            }
            composable<TransactionsRoute> {
                TabPage(TransactionsRoute, "Add" to addSpend) { padding ->
                TransactionsScreen(
                    onOpen = { id -> navController.navigate(TransactionDetailRoute(id)) },
                    onCreate = { navController.navigate(TransactionEditorRoute()) },
                    onOpenSchedule = { id, date -> navController.navigate(RecurringEditorRoute(id, date)) },
                    onCaptures = { navController.navigate(CaptureInboxRoute) },
                    contentPadding = padding,
                )
                }
            }
            composable<UnplannedTransactionsRoute> { entry ->
                val route = entry.toRoute<UnplannedTransactionsRoute>()
                TransactionsScreen(
                    onOpen = { id -> navController.navigate(TransactionDetailRoute(id)) },
                    onCreate = { navController.navigate(TransactionEditorRoute()) },
                    onOpenSchedule = { id, date -> navController.navigate(RecurringEditorRoute(id, date)) },
                    contentPadding = padding,
                    unplannedFrom = java.time.LocalDate.parse(route.cycleStart),
                    unplannedUntil = java.time.LocalDate.parse(route.cycleEnd),
                    onBack = back,
                )
            }
            composable<PlanRoute> {
                SubScreen("Plan", back) { padding ->
                PlanScreen(
                    contentPadding = padding,
                    onSettings = { navController.navigate(MoreRoute) },
                    onAddIncome = { navController.navigate(RecurringEditorRoute(income = true)) },
                    onAddBill = { navController.navigate(RecurringEditorRoute()) },
                    onEditRecurring = { id -> navController.navigate(RecurringEditorRoute(id)) },
                    onAddBudget = { navController.navigate(BudgetEditorRoute()) },
                    onEditBudget = { id -> navController.navigate(BudgetEditorRoute(id)) },
                    onNotes = { navController.navigate(ExpenseNotesRoute) },
                    onAddNote = { navController.navigate(ExpenseNoteEditorRoute()) },
                    onEditNote = { id -> navController.navigate(ExpenseNoteEditorRoute(id)) },
                    onCalendar = { navController.navigateTab(CalendarRoute) },
                    onForecast = { navController.navigate(ForecastRoute) },
                    onWhatIf = { navController.navigate(WhatIfRoute) },
                    onSubscriptions = { navController.navigate(SubscriptionsRoute) },
                    onDebt = { navController.navigate(DebtRoute) },
                    onPayCycle = { navController.navigate(SalaryCycleRoute) },
                    onAddGoal = { navController.navigate(GoalEditorRoute()) },
                    onOpenGoal = { id -> navController.navigate(GoalDetailRoute(id)) },
                )
                }
            }
            composable<InsightsRoute> {
                TabPage(InsightsRoute) { padding -> InsightsScreen(padding) }
            }
            composable<CalendarRoute> {
                // No floating button here: it would cover the Paid buttons. Add bill sits in the header and the day card.
                TabPage(CalendarRoute) { padding ->
                CalendarScreen(
                    onEdit = { id, date -> navController.navigate(RecurringEditorRoute(recurringId = id, occurrenceOn = date)) },
                    onAddPayment = { date -> navController.navigate(RecurringEditorRoute(startOn = date)) },
                    onAddTransaction = { date -> navController.navigate(TransactionEditorRoute(occurredOn = date)) },
                    onOpenTransaction = { id -> navController.navigate(TransactionDetailRoute(id)) },
                    onOpenDebt = { id -> navController.navigate(DebtDetailRoute(id)) },
                    onSelectedDay = { day -> calendarDay.value = day },
                    onPreset = { name, _ -> navController.navigate(RecurringEditorRoute(startOn = calendarDay.value.toString(), preset = name)) },
                    onOpenGoal = { id -> navController.navigate(GoalDetailRoute(id)) },
                    contentPadding = padding,
                )
                }
            }
            composable<MoreRoute> {
                MoreScreen(
                    onBack = back,
                    onAccounts = { navController.navigate(AccountsRoute) },
                    onPlan = { navController.navigate(PlanRoute) },
                    onCategories = { navController.navigate(CategoriesRoute) },
                    onReview = { navController.navigate(ReviewRoute) },
                    onCaptureSetup = { navController.navigate(DetectionRoute) },
                    onReminders = { navController.navigate(RemindersRoute) },
                    onBank = { navController.navigate(BankRoute) },
                    onLock = { navController.navigate(LockRoute) },
                    onSettings = { navController.navigate(SettingsRoute) },
                    onSalaryCycle = { navController.navigate(SalaryCycleRoute) },
                    onCycleSetup = { next -> navController.navigate(CycleSetupRoute(if (next) 1 else 0)) },
                    onCycleReview = { navController.navigate(CycleReviewRoute()) },
                )
            }
            composable<CycleSetupRoute> { com.ledgerline.app.ui.cycle.CycleSetupScreen(onBack = back) }
            composable<CycleReviewRoute> { com.ledgerline.app.ui.cycle.CycleReviewScreen(onBack = back) }
            composable<CaptureInboxRoute> {
                CaptureInboxScreen(
                    onBack = back,
                    onReview = { id -> navController.navigate(TransactionEditorRoute(captureId = id)) },
                    onSetup = { navController.navigate(DetectionRoute) },
                )
            }
            composable<DetectionRoute> { CaptureSetupScreen(onBack = back) }
            composable<RemindersRoute> { SubScreen("Reminders", back) { RemindersScreen(it) } }
            composable<SafeToSpendRoute> { SafeToSpendScreen(onBack = back, contentPadding = padding) }
            composable<TransactionEditorRoute> { TransactionEditorScreen(onDone = back) }
            composable<TransactionDetailRoute> {
                TransactionDetailScreen(
                    onBack = back,
                    onEdit = { id -> navController.navigate(TransactionEditorRoute(transactionId = id)) },
                )
            }
            composable<AccountsRoute> {
                SubScreen("Accounts", back) { inner ->
                    AccountsScreen(
                        onEdit = { id -> navController.navigate(AccountEditorRoute(id)) },
                        onCreate = { navController.navigate(AccountEditorRoute()) },
                        onEditSchedule = { id -> navController.navigate(RecurringEditorRoute(id)) },
                        contentPadding = inner,
                    )
                }
            }
            composable<AccountEditorRoute> { AccountEditorScreen(onDone = back) }
            composable<RecurringEditorRoute> { RecurringEditorScreen(onDone = back) }
            composable<BudgetsRoute> {
                SubScreen("Budgets", back) { inner ->
                    BudgetsScreen(
                        onEdit = { id -> navController.navigate(BudgetEditorRoute(id)) },
                        onCreate = { navController.navigate(BudgetEditorRoute()) },
                        contentPadding = inner,
                    )
                }
            }
            composable<BudgetEditorRoute> { BudgetEditorScreen(onDone = back) }
            composable<CategoriesRoute> { SubScreen("Categories", back) { CategoriesScreen(it) } }
            composable<SettingsRoute> { SubScreen("Preferences and export", back) { SettingsScreen(it) } }
            composable<SalaryCycleRoute> { SalaryCycleScreen(onBack = back) }
            composable<ReviewRoute> {
                SubScreen("Needs checking", back) { inner ->
                    ReviewScreen(onOpen = { id -> navController.navigate(TransactionDetailRoute(id)) }, contentPadding = inner)
                }
            }
            composable<GoalEditorRoute> { com.ledgerline.app.ui.goals.GoalEditorScreen(onDone = back) }
            composable<GoalDetailRoute> {
                com.ledgerline.app.ui.goals.GoalDetailScreen(
                    onBack = back,
                    onEdit = { id -> navController.navigate(GoalEditorRoute(goalId = id)) },
                    onTransfer = { navController.navigate(TransactionEditorRoute(presetType = "TRANSFER")) },
                    onRecordPurchase = { name, amount -> navController.navigate(TransactionEditorRoute(amountMinor = amount, merchant = name)) },
                )
            }
            composable<ForecastRoute> { SubScreen("Cash flow", back) { CashFlowScreen(it) } }
            composable<WhatIfRoute> { SubScreen("What if", back) { WhatIfScreen(it) } }
            composable<SubscriptionsRoute> {
                SubScreen("Subscriptions", back) { inner ->
                    SubscriptionsScreen(onEdit = { id -> navController.navigate(RecurringEditorRoute(id)) }, contentPadding = inner)
                }
            }
            composable<ExpenseNotesRoute> {
                SubScreen("Spending notes", back) { inner ->
                    com.ledgerline.app.ui.notes.ExpenseNotesScreen(
                        onAdd = { navController.navigate(ExpenseNoteEditorRoute()) },
                        onEdit = { id -> navController.navigate(ExpenseNoteEditorRoute(id)) },
                        contentPadding = inner,
                    )
                }
            }
            composable<ExpenseNoteEditorRoute> {
                SubScreen("Spending note", back) { inner ->
                    Box(Modifier.padding(inner)) { com.ledgerline.app.ui.notes.ExpenseNoteEditorScreen(onDone = back) }
                }
            }
            composable<DebtRoute> {
                SubScreen("Debt payoff", back) { inner ->
                    DebtScreen(inner, onOpen = { id -> navController.navigate(DebtDetailRoute(id)) })
                }
            }
            composable<DebtDetailRoute> { DebtDetailScreen(onBack = back) }
            composable<BankRoute> { SubScreen("Bank fixture", back) { BankScreen(it) } }
            composable<LockRoute> { SubScreen("App lock", back) { inner -> Box(Modifier.padding(inner)) { LockSetupScreen() } } }
        }
    }
}

private fun NavHostController.navigateTab(route: Any) {
    if (!TabNavigation.opensDestination(currentDestination.isTab(route))) return
    if (route == HomeRoute) {
        // Pop to the existing Home entry. Restoring here would put another tab back on screen.
        popBackStack(graph.findStartDestination().id, inclusive = false, saveState = true)
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = TabNavigation.restoreSavedState(targetIsHome = false)
    }
}

private fun NavDestination?.isTab(route: Any): Boolean {
    if (this == null) return false
    return when (route) {
        HomeRoute -> hasRoute<HomeRoute>()
        TransactionsRoute -> hasRoute<TransactionsRoute>()
        CalendarRoute -> hasRoute<CalendarRoute>()
        PlanRoute -> hasRoute<PlanRoute>()
        InsightsRoute -> hasRoute<InsightsRoute>()
        else -> false
    }
}

internal fun NavDestination?.isAnyTab(): Boolean =
    listOf(HomeRoute, TransactionsRoute, CalendarRoute, InsightsRoute).any { isTab(it) }
