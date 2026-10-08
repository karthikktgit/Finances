package com.ledgerline.app.ui.format

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.BudgetPace
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.model.WarningSeverity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun LocalDate.medium(): String = format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

fun LocalDate.withYear(): String = format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

fun LocalDate.weekday(): String = format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))

fun Account.paymentLabel(): String = lastFour?.let { "$name ····$it" } ?: name

fun AccountType.label(): String = when (this) {
    AccountType.CHECKING -> "Checking"
    AccountType.SAVINGS -> "Savings"
    AccountType.CREDIT_CARD -> "Credit card"
    AccountType.CASH_WALLET -> "Cash wallet"
    AccountType.PREPAID -> "Prepaid"
    AccountType.LOAN -> "Loan"
    AccountType.INVESTMENT -> "Investment"
    AccountType.OTHER -> "Other"
}

fun TransactionType.label(): String = when (this) {
    TransactionType.EXPENSE -> "Expense"
    TransactionType.INCOME -> "Income"
    TransactionType.TRANSFER -> "Transfer"
    TransactionType.REFUND -> "Refund"
    TransactionType.PAYMENT -> "Payment"
    TransactionType.ADJUSTMENT -> "Adjustment"
}

fun ScheduledKind.label(): String = when (this) {
    ScheduledKind.INCOME -> "Income"
    ScheduledKind.BILL -> "Bill"
    ScheduledKind.SUBSCRIPTION -> "Subscription"
    ScheduledKind.DEBT -> "Debt"
    ScheduledKind.TRANSFER -> "Transfer"
    ScheduledKind.OTHER -> "Other"
}

fun PaymentPriority.label(): String = name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }

fun RecurrenceFrequency.label(): String = when (this) {
    RecurrenceFrequency.BIWEEKLY -> "Every two weeks"
    else -> name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
}

fun DetectionSource.label(): String = when (this) {
    DetectionSource.BANK_SYNC -> "Bank sync"
    DetectionSource.CARD_SYNC -> "Card sync"
    DetectionSource.NOTIFICATION -> "Notification"
    DetectionSource.SMS -> "SMS"
    DetectionSource.EMAIL -> "Email"
    DetectionSource.MANUAL -> "Manual"
    DetectionSource.IMPORT -> "Import"
    DetectionSource.RECEIPT -> "Receipt"
    DetectionSource.USER_CORRECTION -> "Corrected by you"
}

fun WarningSeverity.label(): String = name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }

fun BudgetPeriod.label(): String = when (this) {
    BudgetPeriod.WEEKLY -> "Weekly"
    BudgetPeriod.MONTHLY -> "Monthly"
}

fun BudgetPace.label(): String = when (this) {
    BudgetPace.ON_TRACK -> "On track"
    BudgetPace.LIKELY_OVER -> "Likely over"
    BudgetPace.OVER -> "Over budget"
}

fun CashFlowDirection.signWord(): String = if (this == CashFlowDirection.INFLOW) "Expected in" else "Expected out"

fun dueLabel(today: LocalDate, date: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when (days) {
        0L -> "Due today"
        1L -> "Due tomorrow"
        else -> "Due in $days days"
    }
}
