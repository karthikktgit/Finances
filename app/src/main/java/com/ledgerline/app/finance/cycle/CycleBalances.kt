package com.ledgerline.app.finance.cycle

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import java.time.LocalDate

/**
 * Fresh balances for a cycle: each account starts the cycle at an amount you enter instead of carrying on from the
 * cycle before. It is kept as a "Cycle start balance" correction dated the cycle's first day, so earlier cycles (which
 * only count up to their own last day) stay exactly as they were, and everything after starts from the new figure.
 */
object CycleBalances {
    const val NAME = "Cycle start balance"

    fun isStartCorrection(tx: LedgerTransaction, cycleStart: LocalDate): Boolean =
        tx.type == TransactionType.ADJUSTMENT && tx.merchantRaw == NAME && tx.occurredOn == cycleStart && BalanceMath.isActive(tx)

    /** What the account would start the cycle with if nothing were reset: everything before the cycle's first day. */
    fun carriedMinor(account: Account, transactions: List<LedgerTransaction>, cycleStart: LocalDate): Long =
        BalanceMath.currentBalanceMinor(account, transactions.filter { BalanceMath.isActive(it) && it.occurredOn.isBefore(cycleStart) })

    /** The balance the cycle starts with: carried over, plus this cycle's start correction if it has one. */
    fun startMinor(account: Account, transactions: List<LedgerTransaction>, cycleStart: LocalDate): Long =
        carriedMinor(account, transactions, cycleStart) +
            transactions.filter { it.accountId == account.id && isStartCorrection(it, cycleStart) }.sumOf { it.balanceEffectMinor }

    /** The correction needed so the account starts at [targetMinor] (signed: owed is below zero). */
    fun correctionMinor(account: Account, transactions: List<LedgerTransaction>, cycleStart: LocalDate, targetMinor: Long): Long =
        targetMinor - carriedMinor(account, transactions, cycleStart)

    /** What you type: owed is entered as a positive amount for a card or loan. */
    fun signed(account: Account, enteredMinor: Long): Long = if (BalanceMath.isDebtType(account.type)) -enteredMinor else enteredMinor

    fun shown(account: Account, signedMinor: Long): Long = if (BalanceMath.isDebtType(account.type)) -signedMinor else signedMinor
}
