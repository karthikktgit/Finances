package com.ledgerline.app.finance.validation

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountDraft
import com.ledgerline.app.domain.model.BudgetDraft
import com.ledgerline.app.domain.model.RecurringDraft
import com.ledgerline.app.domain.model.TransactionDraft
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.policy.FinancePolicy

object TransactionRules {
    fun validate(draft: TransactionDraft, account: Account?, counter: Account?): String? {
        if (draft.amountMinor <= 0) return "Enter an amount greater than zero."
        if (account == null) return "Choose an account."
        if (!account.active) return "That account is inactive. Choose an active account or reactivate it."
        if (draft.merchant.isBlank() && draft.type != TransactionType.TRANSFER && draft.type != TransactionType.PAYMENT) {
            return "Enter a merchant or description."
        }
        if (draft.type == TransactionType.TRANSFER || draft.type == TransactionType.PAYMENT) {
            if (counter == null) return "Choose the other account."
            if (counter.id == account.id) return "Choose two different accounts."
            if (!counter.active) return "The other account is inactive."
            if (!account.currency.equals(counter.currency, ignoreCase = true)) {
                return "These accounts use ${account.currency} and ${counter.currency}. Ledgerline does not convert currencies, so this ${draft.type.name.lowercase()} stays blocked."
            }
        }
        return null
    }

    fun validate(draft: AccountDraft): String? {
        if (draft.name.isBlank()) return "Name the account."
        if (draft.lastFour.isNotBlank() && (draft.lastFour.length != 4 || draft.lastFour.any { !it.isDigit() })) {
            return "Last four digits must be four numbers, or leave the field empty."
        }
        if (draft.enteredBalanceMinor < 0) return "Enter the balance as a positive amount. Debt account types are stored as money owed."
        val limit = draft.creditLimitMinor
        if (limit != null && limit < 0) return "Credit limit cannot be negative."
        return null
    }

    fun validate(draft: RecurringDraft, account: Account?): String? {
        if (draft.name.isBlank()) return "Name the payment or income."
        if (draft.amountMinor <= 0) return "Enter an amount greater than zero."
        if (account == null) return "Choose an account."
        if (draft.frequency == com.ledgerline.app.domain.model.RecurrenceFrequency.CUSTOM &&
            (draft.intervalDays == null || draft.intervalDays < 1)
        ) {
            return "Custom schedules need an interval of at least one day."
        }
        if (draft.endOn != null && draft.endOn.isBefore(draft.startOn)) {
            return "The end date cannot be before the first due date."
        }
        if (draft.maxOccurrences != null && draft.maxOccurrences < 1) {
            return "The number of payments must be at least 1."
        }
        return null
    }

    fun validate(draft: BudgetDraft): String? {
        if (draft.name.isBlank()) return "Name the budget."
        if (draft.amountMinor <= 0) return "Enter a budget amount greater than zero."
        return null
    }

    fun clampHorizon(days: Int): Int = days.coerceIn(FinancePolicy.MIN_HORIZON_DAYS, FinancePolicy.MAX_HORIZON_DAYS)
}
