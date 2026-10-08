package com.ledgerline.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object TransactionsRoute

@Serializable data object CalendarRoute

@Serializable data object InsightsRoute

@Serializable data object MoreRoute

@Serializable data object SafeToSpendRoute

@Serializable data object AccountsRoute

@Serializable data object BudgetsRoute

@Serializable data object CategoriesRoute

@Serializable data object SettingsRoute

@Serializable
data class TransactionEditorRoute(
    val transactionId: String = "",
    val presetType: String = "EXPENSE",
)

@Serializable
data class TransactionDetailRoute(val transactionId: String)

@Serializable
data class AccountEditorRoute(val accountId: String = "")

@Serializable
data class RecurringEditorRoute(val recurringId: String = "")

@Serializable
data class BudgetEditorRoute(val budgetId: String = "")

@Serializable data object ReviewRoute

@Serializable data object ForecastRoute

@Serializable data object WhatIfRoute

@Serializable data object SubscriptionsRoute

@Serializable data object DebtRoute

@Serializable data object DetectionRoute

@Serializable data object BankRoute

@Serializable data object LockRoute
