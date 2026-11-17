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
    ONCE,
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY,
    CUSTOM,
}

enum class OccurrenceState {
    SCHEDULED,
    PAID,
    SKIPPED,
}

enum class SeriesScope {
    THIS_OCCURRENCE,
    THIS_AND_FUTURE,
    ENTIRE_SERIES,
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

    /** From one payday to the next, so an envelope matches the pay it is split from. */
    PAY_CYCLE,
}

enum class BudgetPace {
    ON_TRACK,
    LIKELY_OVER,
    OVER,
}

enum class CycleType {
    WEEKLY,
    BIWEEKLY,
    SEMIMONTHLY,
    MONTHLY,
}

enum class ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class DebtType {
    CREDIT_CARD,
    EDUCATION_LOAN,
    OTHER,
}

enum class RateType {
    FIXED,
    VARIABLE,
    UNKNOWN,
}

enum class PayChoice {
    MINIMUM,
    FIXED,
    MINIMUM_PLUS,
    SAVED_PLAN,
}
