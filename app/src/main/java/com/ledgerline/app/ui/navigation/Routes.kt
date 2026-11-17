package com.ledgerline.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object TransactionsRoute

@Serializable
data class UnplannedTransactionsRoute(val cycleStart: String, val cycleEnd: String)

@Serializable data object CalendarRoute

@Serializable data object InsightsRoute

@Serializable data object MoreRoute

@Serializable data object SafeToSpendRoute

@Serializable data object AccountsRoute

@Serializable data object BudgetsRoute

@Serializable data object CategoriesRoute

@Serializable data object SettingsRoute

@Serializable data object SalaryCycleRoute

@Serializable
data class TransactionEditorRoute(
    val transactionId: String = "",
    val presetType: String = "EXPENSE",
    val occurredOn: String = "",
    /** A captured spend to start from. The capture is marked added once the transaction is saved. */
    val captureId: String = "",
    /** Start with this amount and name, from the "before you buy" check. */
    val amountMinor: Long = 0,
    val merchant: String = "",
)

@Serializable
data class TransactionDetailRoute(val transactionId: String)

@Serializable
data class AccountEditorRoute(val accountId: String = "")

@Serializable
data class RecurringEditorRoute(
    val recurringId: String = "",
    val occurrenceOn: String = "",
    val startOn: String = "",
    /** Start a new item as expected income instead of a bill. */
    val income: Boolean = false,
    /** Start from a common bill such as "Rent" or "Netflix" (a name from BillPresets). */
    val preset: String = "",
)

@Serializable
data class BudgetEditorRoute(val budgetId: String = "")

@Serializable data object ReviewRoute

@Serializable data object ForecastRoute

@Serializable data object WhatIfRoute

@Serializable data object SubscriptionsRoute

@Serializable data object DebtRoute

@Serializable data object ExpenseNotesRoute

@Serializable data class ExpenseNoteEditorRoute(val noteId: String = "")

@Serializable data class DebtDetailRoute(val debtId: String)

@Serializable data object DetectionRoute

@Serializable data object BankRoute

@Serializable data object LockRoute

@Serializable data object PlanRoute

@Serializable data object CaptureInboxRoute

@Serializable data object RemindersRoute

@Serializable data class GoalEditorRoute(val goalId: String = "", val preset: String = "")

@Serializable data class GoalDetailRoute(val goalId: String)

/** The start-of-cycle checklist and plan. [offset] 0 is this cycle, 1 the next. */
@Serializable data class CycleSetupRoute(val offset: Int = 0)

/** The end-of-cycle review. [offset] 0 is this cycle, -1 the last. */
@Serializable data class CycleReviewRoute(val offset: Int = 0)
