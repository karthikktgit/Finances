package com.ledgerline.app.finance.account

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.finance.balance.BalanceMath

/** What deleting one account touches. Counts only; nothing is changed here. */
data class AccountDeletionImpact(
    val transactionCount: Int,
    val transferCount: Int,
    val scheduleCount: Int,
    val debtCount: Int,
    val budgetCount: Int,
) {
    val empty: Boolean get() = transactionCount + transferCount + scheduleCount + debtCount + budgetCount == 0

    fun lines(): List<String> = buildList {
        if (transactionCount > 0) add("$transactionCount transactions stay in history, labeled with the deleted account, and leave active balances.")
        if (transferCount > 0) add("$transferCount transfers or payments with another account keep that account's side unchanged.")
        if (scheduleCount > 0) add("$scheduleCount repeating items are paused. Unpaid dates are not marked paid. Choose a new account to resume them.")
        if (debtCount > 0) add("$debtCount debt records keep their balance, APR, minimum, and history, and are unlinked from this account for review.")
        if (budgetCount > 0) add("$budgetCount budgets limited to this account are turned off.")
    }
}

/** Ids to change when an account is deleted. The account row itself is kept as a deleted record. */
data class AccountDeletionPlan(
    val accountId: String,
    val pausedScheduleIds: Set<String>,
    val unlinkedDebtIds: Set<String>,
    val pausedBudgetIds: Set<String>,
)

/** The account references a debt record holds. */
data class DebtLink(val id: String, val accountId: String?, val paymentAccountId: String?) {
    fun touches(accountId: String) = this.accountId == accountId || paymentAccountId == accountId
}

fun Debt.link() = DebtLink(id, accountId, paymentAccountId)

object AccountDeletion {
    const val CONFIRMATION =
        "Delete this account? Its active account access will be removed. Existing transaction history will be preserved where possible, " +
            "and associated schedules or payments may need reassignment. This action cannot be undone automatically."

    fun impact(
        accountId: String,
        transactions: List<LedgerTransaction>,
        recurring: List<RecurringPayment>,
        debts: List<DebtLink>,
        budgets: List<Budget>,
    ): AccountDeletionImpact {
        val live = transactions.filter { BalanceMath.isActive(it) }
        return AccountDeletionImpact(
            transactionCount = live.count { it.accountId == accountId },
            transferCount = live.count { tx ->
                (tx.accountId == accountId && tx.counterAccountId != null && tx.counterAccountId != accountId) ||
                    (tx.counterAccountId == accountId && tx.accountId != accountId)
            },
            scheduleCount = recurring.count { it.accountId == accountId && it.active },
            debtCount = debts.count { it.touches(accountId) },
            budgetCount = budgets.count { it.accountId == accountId && it.active },
        )
    }

    fun plan(
        accountId: String,
        recurring: List<RecurringPayment>,
        debts: List<DebtLink>,
        budgets: List<Budget>,
    ): AccountDeletionPlan = AccountDeletionPlan(
        accountId = accountId,
        pausedScheduleIds = recurring.filter { it.accountId == accountId && it.active }.map { it.id }.toSet(),
        unlinkedDebtIds = debts.filter { it.touches(accountId) }.map { it.id }.toSet(),
        pausedBudgetIds = budgets.filter { it.accountId == accountId && it.active }.map { it.id }.toSet(),
    )

    fun reviewNote(existing: String, accountName: String, deletedOn: java.time.LocalDate): String {
        val line = "Account \"$accountName\" was deleted on $deletedOn. Choose a new account."
        return if (existing.isBlank()) line else "$existing\n$line"
    }

    /** Transactions that belong to active views. History lists keep every row. */
    fun live(transactions: List<LedgerTransaction>, deletedIds: Set<String>): List<LedgerTransaction> =
        if (deletedIds.isEmpty()) transactions else transactions.filter { it.accountId !in deletedIds }

    fun label(accountId: String?, accounts: List<Account>, display: (Account) -> String = { it.name }): String {
        if (accountId.isNullOrBlank()) return "Account not assigned"
        val account = accounts.firstOrNull { it.id == accountId } ?: return "Account not assigned"
        return if (account.deleted) "Deleted account: ${account.name}" else display(account)
    }

    fun needsAccount(recurring: List<RecurringPayment>, deletedIds: Set<String>): List<RecurringPayment> =
        recurring.filter { it.accountId in deletedIds }
}
