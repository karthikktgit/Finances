package com.ledgerline.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.ui.accounts.AccountEditorScreen
import com.ledgerline.app.ui.accounts.AccountsScreen
import com.ledgerline.app.ui.budgets.BudgetEditorScreen
import com.ledgerline.app.ui.budgets.BudgetsScreen
import com.ledgerline.app.ui.calendar.CalendarScreen
import com.ledgerline.app.ui.calendar.RecurringEditorScreen
import com.ledgerline.app.ui.features.BankScreen
import com.ledgerline.app.ui.features.DebtScreen
import com.ledgerline.app.ui.features.DetectionScreen
import com.ledgerline.app.ui.features.ForecastScreen
import com.ledgerline.app.ui.features.ReviewScreen
import com.ledgerline.app.ui.features.SubscriptionsScreen
import com.ledgerline.app.ui.features.WhatIfScreen
import com.ledgerline.app.ui.dashboard.DashboardScreen
import com.ledgerline.app.ui.dashboard.SafeToSpendScreen
import com.ledgerline.app.ui.insights.InsightsScreen
import com.ledgerline.app.ui.more.CategoriesScreen
import com.ledgerline.app.ui.more.MoreScreen
import com.ledgerline.app.ui.more.SettingsScreen
import com.ledgerline.app.ui.security.LockSetupScreen
import com.ledgerline.app.ui.transactions.TransactionDetailScreen
import com.ledgerline.app.ui.transactions.TransactionEditorScreen
import com.ledgerline.app.ui.transactions.TransactionsScreen

private data class Tab(val label: String, val icon: ImageVector, val route: Any)

@Composable
fun LedgerScaffold() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = destination?.let { dest ->
        dest.hasRoute<HomeRoute>() || dest.hasRoute<TransactionsRoute>() || dest.hasRoute<CalendarRoute>() ||
            dest.hasRoute<InsightsRoute>() || dest.hasRoute<MoreRoute>()
    } == true
    val tabs = listOf(
        Tab("Home", Icons.Filled.Home, HomeRoute),
        Tab("Transactions", Icons.Filled.ReceiptLong, TransactionsRoute),
        Tab("Calendar", Icons.Filled.CalendarMonth, CalendarRoute),
        Tab("Insights", Icons.Filled.Insights, InsightsRoute),
        Tab("More", Icons.Filled.AccountBalance, MoreRoute),
    )
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = when (tab.route) {
                            HomeRoute -> destination?.hasRoute<HomeRoute>() == true
                            TransactionsRoute -> destination?.hasRoute<TransactionsRoute>() == true
                            CalendarRoute -> destination?.hasRoute<CalendarRoute>() == true
                            InsightsRoute -> destination?.hasRoute<InsightsRoute>() == true
                            else -> destination?.hasRoute<MoreRoute>() == true
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                DashboardScreen(
                    onSafeToSpend = { navController.navigate(SafeToSpendRoute) },
                    onAddExpense = { navController.navigate(TransactionEditorRoute(presetType = TransactionType.EXPENSE.name)) },
                    onAddIncome = { navController.navigate(TransactionEditorRoute(presetType = TransactionType.INCOME.name)) },
                    onAddPayment = { navController.navigate(TransactionEditorRoute(presetType = TransactionType.PAYMENT.name)) },
                    onTransaction = { id -> navController.navigate(TransactionDetailRoute(id)) },
                    onAccounts = { navController.navigate(AccountsRoute) },
                    onReview = { navController.navigate(ReviewRoute) },
                    onForecast = { navController.navigate(ForecastRoute) },
                    contentPadding = padding,
                )
            }
            composable<TransactionsRoute> {
                TransactionsScreen(
                    onOpen = { id -> navController.navigate(TransactionDetailRoute(id)) },
                    onCreate = { navController.navigate(TransactionEditorRoute()) },
                    contentPadding = padding,
                )
            }
            composable<CalendarRoute> {
                CalendarScreen(
                    onEdit = { id -> navController.navigate(RecurringEditorRoute(id)) },
                    onCreate = { navController.navigate(RecurringEditorRoute()) },
                    contentPadding = padding,
                )
            }
            composable<InsightsRoute> { InsightsScreen(padding, onWhatIf = { navController.navigate(WhatIfRoute) }) }
            composable<MoreRoute> {
                MoreScreen(
                    onAccounts = { navController.navigate(AccountsRoute) },
                    onBudgets = { navController.navigate(BudgetsRoute) },
                    onCategories = { navController.navigate(CategoriesRoute) },
                    onReview = { navController.navigate(ReviewRoute) },
                    onForecast = { navController.navigate(ForecastRoute) },
                    onWhatIf = { navController.navigate(WhatIfRoute) },
                    onSubscriptions = { navController.navigate(SubscriptionsRoute) },
                    onDebt = { navController.navigate(DebtRoute) },
                    onDetection = { navController.navigate(DetectionRoute) },
                    onBank = { navController.navigate(BankRoute) },
                    onLock = { navController.navigate(LockRoute) },
                    onSettings = { navController.navigate(SettingsRoute) },
                    contentPadding = padding,
                )
            }
            composable<SafeToSpendRoute> { SafeToSpendScreen(onBack = { navController.popBackStack() }, contentPadding = padding) }
            composable<TransactionEditorRoute> { TransactionEditorScreen(onDone = { navController.popBackStack() }) }
            composable<TransactionDetailRoute> {
                TransactionDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(TransactionEditorRoute(transactionId = id)) },
                )
            }
            composable<AccountsRoute> {
                AccountsScreen(
                    onEdit = { id -> navController.navigate(AccountEditorRoute(id)) },
                    onCreate = { navController.navigate(AccountEditorRoute()) },
                    contentPadding = padding,
                )
            }
            composable<AccountEditorRoute> { AccountEditorScreen(onDone = { navController.popBackStack() }) }
            composable<RecurringEditorRoute> { RecurringEditorScreen(onDone = { navController.popBackStack() }) }
            composable<BudgetsRoute> {
                BudgetsScreen(
                    onEdit = { id -> navController.navigate(BudgetEditorRoute(id)) },
                    onCreate = { navController.navigate(BudgetEditorRoute()) },
                    contentPadding = padding,
                )
            }
            composable<BudgetEditorRoute> { BudgetEditorScreen(onDone = { navController.popBackStack() }) }
            composable<CategoriesRoute> { CategoriesScreen(padding) }
            composable<SettingsRoute> { SettingsScreen(padding) }
            composable<ReviewRoute> { ReviewScreen(onOpen = { id -> navController.navigate(TransactionDetailRoute(id)) }, contentPadding = padding) }
            composable<ForecastRoute> { ForecastScreen(padding) }
            composable<WhatIfRoute> { WhatIfScreen(padding) }
            composable<SubscriptionsRoute> { SubscriptionsScreen(onEdit = { id -> navController.navigate(RecurringEditorRoute(id)) }, contentPadding = padding) }
            composable<DebtRoute> { DebtScreen(padding) }
            composable<DetectionRoute> { DetectionScreen(padding) }
            composable<BankRoute> { BankScreen(padding) }
            composable<LockRoute> { LockSetupScreen() }
        }
    }
}

private fun NavHostController.navigateTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
