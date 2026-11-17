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
    val deletedAtEpochMillis: Long? = null,
) {
    val deleted: Boolean get() = deletedAtEpochMillis != null
}

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
    val unplanned: Boolean = false,
    /** False for a planned spend not paid yet. It counts nowhere until it is paid. */
    val paid: Boolean = true,
    /** The day it was planned for, if it was planned first. */
    val plannedOn: LocalDate? = null,
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
    val maxOccurrences: Int? = null,
    /** Due dates on or after this day are marked paid automatically. Null means you mark them paid. */
    val autopaySince: LocalDate? = null,
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
    /** First day of the first cycle it is planned in; null means always. */
    val startOn: LocalDate? = null,
    /** First day of the last cycle it is planned in; null means it goes on. */
    val endOn: LocalDate? = null,
    /** How many cycles it runs for, when set that way. */
    val maxCycles: Int? = null,
) {
    /** Planned in the cycle starting [cycleStart]. */
    fun runsIn(cycleStart: LocalDate): Boolean =
        (startOn == null || !startOn.isAfter(cycleStart)) && (endOn == null || !cycleStart.isAfter(endOn))
}

data class UserSettings(
    val primaryCurrency: String,
    val minimumBufferMinor: Long,
    val horizonDays: Int,
    val onboardingCompleted: Boolean,
    val cycleType: CycleType = CycleType.MONTHLY,
    val cycleAnchor: java.time.LocalDate = java.time.LocalDate.of(2026, 1, 20),
    val cycleStartDay: Int = 20,
    val cycleSecondDay: Int = 5,
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
    val adjustmentDecreases: Boolean = false,
    val unplanned: Boolean = false,
    /** A spend you are planning, not one already paid. */
    val notPaidYet: Boolean = false,
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
    val endOn: LocalDate? = null,
    val maxOccurrences: Int? = null,
    val autopaySince: LocalDate? = null,
)

data class OccurrenceAdjustment(
    val recurringId: String,
    val occurrenceOn: LocalDate,
    val state: OccurrenceState,
    val amountMinor: Long?,
    val name: String?,
    val paidTransactionId: String?,
)

data class BudgetDraft(
    val existingId: String?,
    val name: String,
    val categoryId: String?,
    val period: BudgetPeriod,
    val amountMinor: Long,
    val reservesSafeToSpend: Boolean,
    val active: Boolean,
    /** The account this money is kept in. Only changed when [setAccount] is true, so older editors leave it alone. */
    val accountId: String? = null,
    val setAccount: Boolean = false,
    /** When it runs. Only changed when [setSchedule] is true. */
    val setSchedule: Boolean = false,
    val startOn: LocalDate? = null,
    val endOn: LocalDate? = null,
    val maxCycles: Int? = null,
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
