package com.ledgerline.app.domain.model

import java.time.LocalDate

data class Account(
    val id: String,
    val name: String,
    val institution: String,
    val type: AccountType,
    val lastFour: String?,
    val openingBalanceMinor: Long,
    val creditLimitMinor: Long?,
    val currency: String,
    val active: Boolean,
    val includeInNetWorth: Boolean,
    val includeInSafeToSpend: Boolean,
    val syncStatus: SyncStatus,
    val lastSyncedAtEpochMillis: Long?,
    val syncError: String?,
    val sortOrder: Int,
)

data class Category(
    val id: String,
    val parentId: String?,
    val name: String,
    val necessity: Necessity,
    val archived: Boolean,
    val sortOrder: Int,
)

data class LedgerTransaction(
    val id: String,
    val accountId: String,
    val counterAccountId: String?,
    val linkId: String?,
    val amountMinor: Long,
    val balanceEffectMinor: Long,
    val currency: String,
    val type: TransactionType,
    val merchantRaw: String,
    val merchantNormalized: String,
    val merchantId: String?,
    val description: String,
    val categoryId: String?,
    val occurredOn: LocalDate,
    val postedOn: LocalDate?,
    val detectionSource: DetectionSource,
    val confidencePercent: Int,
    val recurringPaymentId: String?,
    val status: TransactionStatus,
    val userConfirmed: Boolean,
    val note: String,
    val fingerprint: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
)

data class RecurringPayment(
    val id: String,
    val name: String,
    val accountId: String,
    val categoryId: String?,
    val direction: CashFlowDirection,
    val kind: ScheduledKind,
    val amountMinor: Long,
    val currency: String,
    val frequency: RecurrenceFrequency,
    val intervalDays: Int?,
    val dayOfMonth: Int?,
    val dayOfWeek: Int?,
    val startOn: LocalDate,
    val endOn: LocalDate?,
    val priority: PaymentPriority,
    val incomeConfidence: IncomeConfidence,
    val active: Boolean,
    val note: String,
)

data class Budget(
    val id: String,
    val name: String,
    val categoryId: String?,
    val accountId: String?,
    val period: BudgetPeriod,
    val amountMinor: Long,
    val currency: String,
    val reservesSafeToSpend: Boolean,
    val active: Boolean,
)

data class UserSettings(
    val primaryCurrency: String,
    val minimumBufferMinor: Long,
    val horizonDays: Int,
    val onboardingCompleted: Boolean,
)

data class MerchantRecord(
    val id: String,
    val displayName: String,
    val normalizedName: String,
)

data class MerchantCategoryRule(
    val merchantId: String,
    val normalizedName: String,
    val categoryId: String,
    val confirmations: Int,
)

data class TransactionDraft(
    val existingId: String?,
    val existingLinkId: String?,
    val type: TransactionType,
    val amountMinor: Long,
    val merchant: String,
    val accountId: String,
    val counterAccountId: String?,
    val categoryId: String?,
    val occurredOn: LocalDate,
    val note: String,
    val acknowledgeDuplicate: Boolean,
)

data class AccountDraft(
    val existingId: String?,
    val name: String,
    val institution: String,
    val type: AccountType,
    val lastFour: String,
    val enteredBalanceMinor: Long,
    val applyBalance: Boolean,
    val creditLimitMinor: Long?,
    val active: Boolean,
    val includeInNetWorth: Boolean,
    val includeInSafeToSpend: Boolean,
)

data class RecurringDraft(
    val existingId: String?,
    val name: String,
    val accountId: String,
    val categoryId: String?,
    val direction: CashFlowDirection,
    val kind: ScheduledKind,
    val amountMinor: Long,
    val frequency: RecurrenceFrequency,
    val intervalDays: Int?,
    val dayOfMonth: Int?,
    val dayOfWeek: Int?,
    val startOn: LocalDate,
    val priority: PaymentPriority,
    val incomeConfidence: IncomeConfidence,
    val active: Boolean,
    val note: String,
)

data class BudgetDraft(
    val existingId: String?,
    val name: String,
    val categoryId: String?,
    val period: BudgetPeriod,
    val amountMinor: Long,
    val reservesSafeToSpend: Boolean,
    val active: Boolean,
)

sealed interface SaveResult {
    data object Saved : SaveResult

    data class Invalid(val message: String) : SaveResult

    data class PossibleDuplicate(
        val score: Int,
        val existingId: String,
        val summary: String,
    ) : SaveResult
}
