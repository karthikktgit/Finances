package com.ledgerline.app.domain.model

enum class AccountType {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    CASH_WALLET,
    PREPAID,
    LOAN,
    INVESTMENT,
    OTHER,
}

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
    REFUND,
    PAYMENT,
    ADJUSTMENT,
}

enum class DetectionSource {
    BANK_SYNC,
    CARD_SYNC,
    NOTIFICATION,
    SMS,
    EMAIL,
    MANUAL,
    IMPORT,
    RECEIPT,
    USER_CORRECTION,
}

enum class TransactionStatus {
    DETECTED,
    CONFIRMED,
    RECONCILED,
    MODIFIED,
    DUPLICATE,
    IGNORED,
}

enum class SyncStatus {
    MANUAL,
    NOT_CONNECTED,
    SYNCED,
    FAILED,
}

enum class PaymentPriority {
    MANDATORY,
    IMPORTANT,
    FLEXIBLE,
    OPTIONAL,
}

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY,
    CUSTOM,
}

enum class CashFlowDirection {
    OUTFLOW,
    INFLOW,
}

enum class ScheduledKind {
    INCOME,
    BILL,
    SUBSCRIPTION,
    DEBT,
    TRANSFER,
    OTHER,
}

enum class IncomeConfidence {
    HIGH,
    MEDIUM,
    LOW,
    NONE,
}

enum class WarningSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class Necessity {
    ESSENTIAL,
    LIKELY_ESSENTIAL,
    DISCRETIONARY,
    POTENTIALLY_UNNECESSARY,
    UNKNOWN,
}

enum class BudgetPeriod {
    WEEKLY,
    MONTHLY,
}

enum class BudgetPace {
    ON_TRACK,
    LIKELY_OVER,
    OVER,
}

enum class ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK,
}
