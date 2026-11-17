package com.ledgerline.app.finance.category

import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionType

/**
 * What counts as spending. Income, transfers, card payments, refunds, and adjustments are not spending.
 * A card purchase is spending when it is made. Paying the card later moves money between accounts, so a
 * row saved as an expense in the Credit Card Payment category is kept in history and balances but is not
 * spending a second time. The Unplanned flag is separate from the category and does not change this.
 */
object SpendingRules {
    val NON_SPENDING_CATEGORIES: Set<String> = setOf(StandardCategories.CARD_PAYMENT)

    fun countsAsSpending(tx: LedgerTransaction): Boolean =
        tx.type == TransactionType.EXPENSE && tx.categoryId !in NON_SPENDING_CATEGORIES
}
