package com.ledgerline.app.finance.policy

/**
 * Named thresholds for financial decisions. Change them here, not at call sites.
 */
object FinancePolicy {
    const val DUPLICATE_MERGE_SCORE = 95
    const val DUPLICATE_ASK_SCORE = 70
    const val DUPLICATE_WINDOW_DAYS = 3L
    const val CATEGORY_LEARN_CONFIRMATIONS = 2
    const val DEFAULT_BUFFER_MINOR = 50_000L
    const val DEFAULT_HORIZON_DAYS = 30
    const val MIN_HORIZON_DAYS = 7
    const val MAX_HORIZON_DAYS = 120
    const val DUE_SOON_DAYS = 3L
    const val PACE_WARNING_PERCENT = 15
    const val PACE_MIN_DAY_OF_MONTH = 3

    const val MANUAL_CONFIDENCE_PERCENT = 100
    const val AUTO_CONFIRM_CONFIDENCE = 80

    val committedPriorities = setOf(
        com.ledgerline.app.domain.model.PaymentPriority.MANDATORY,
        com.ledgerline.app.domain.model.PaymentPriority.IMPORTANT,
    )

    val reliableIncome = setOf(
        com.ledgerline.app.domain.model.IncomeConfidence.HIGH,
        com.ledgerline.app.domain.model.IncomeConfidence.MEDIUM,
    )

    val debtAccountTypes = setOf(
        com.ledgerline.app.domain.model.AccountType.CREDIT_CARD,
        com.ledgerline.app.domain.model.AccountType.LOAN,
    )

    val defaultLiquidTypes = setOf(
        com.ledgerline.app.domain.model.AccountType.CHECKING,
        com.ledgerline.app.domain.model.AccountType.CASH_WALLET,
        com.ledgerline.app.domain.model.AccountType.PREPAID,
    )
}
