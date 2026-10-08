package com.ledgerline.app.finance.balance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import kotlin.math.abs

object BalanceMath {
    fun effectFor(type: TransactionType, absoluteMinor: Long, adjustmentSigned: Long = 0): Long {
        val absolute = abs(absoluteMinor)
        return when (type) {
            TransactionType.EXPENSE -> -absolute
            TransactionType.INCOME -> absolute
            TransactionType.REFUND -> absolute
            TransactionType.TRANSFER -> -absolute
            TransactionType.PAYMENT -> -absolute
            TransactionType.ADJUSTMENT -> adjustmentSigned
        }
    }

    fun counterEffect(effect: Long): Long = -effect

    private val countingStatuses = setOf(
        TransactionStatus.CONFIRMED,
        TransactionStatus.RECONCILED,
        TransactionStatus.MODIFIED,
    )

    fun isActive(transaction: LedgerTransaction): Boolean =
        transaction.deletedAtEpochMillis == null && transaction.status in countingStatuses

    fun currentBalanceMinor(account: Account, transactions: List<LedgerTransaction>): Long {
        val movement = transactions
            .asSequence()
            .filter { isActive(it) && it.accountId == account.id }
            .sumOf { it.balanceEffectMinor }
        return account.openingBalanceMinor + movement
    }

    fun amountOwedMinor(balanceMinor: Long): Long = if (balanceMinor < 0) -balanceMinor else 0L

    fun availableCreditMinor(account: Account, balanceMinor: Long): Long? {
        if (account.type != AccountType.CREDIT_CARD) return null
        val limit = account.creditLimitMinor ?: return null
        return (limit - amountOwedMinor(balanceMinor)).coerceAtLeast(0)
    }

    fun isDebtType(type: AccountType): Boolean =
        type == AccountType.CREDIT_CARD || type == AccountType.LOAN
}
