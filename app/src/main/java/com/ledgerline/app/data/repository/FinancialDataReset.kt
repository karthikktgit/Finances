package com.ledgerline.app.data.repository

import androidx.room.withTransaction
import com.ledgerline.app.data.local.database.LedgerDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinancialDataReset @Inject constructor(
    private val database: LedgerDatabase,
) {
    suspend fun resetFinancialData() {
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            listOf(
                "cycle_amounts",
                "cycle_reviews",
                "captured_spends",
                "expense_notes",
                "sync_records",
                "home_group_items",
                "home_groups",
                "goal_contributions",
                "savings_goals",
                "transaction_matches",
                "transactions",
                "recurring_occurrences",
                "recurring_payments",
                "budgets",
                "debt_settlements",
                "debt_month_plans",
                "debt_rate_changes",
                "debts",
                "merchant_category_rules",
                "merchants",
                "accounts",
            ).forEach { table ->
                db.execSQL("DELETE FROM $table")
            }
        }
    }
}
