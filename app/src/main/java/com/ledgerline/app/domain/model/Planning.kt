package com.ledgerline.app.domain.model

import java.time.LocalDate
import java.time.YearMonth

data class Debt(
    val id: String,
    val name: String,
    val accountId: String?,
    val principalMinor: Long,
    val balanceMinor: Long,
    val aprBasisPoints: Int,
    val minimumPaymentMinor: Long,
    val dueDayOfMonth: Int,
    val frequency: RecurrenceFrequency,
    val priority: PaymentPriority,
    val currency: String,
    val debtType: DebtType = DebtType.OTHER,
    val rateType: RateType = RateType.UNKNOWN,
    val paymentAccountId: String? = null,
    val statementCloseDay: Int? = null,
    val originationOn: LocalDate? = null,
    val maturityOn: LocalDate? = null,
    val remainingTermMonths: Int? = null,
    val feeMinor: Long = 0,
    val rateUpdatedOn: LocalDate? = null,
    val accruedInterestMinor: Long? = null,
    val showInCalendar: Boolean = true,
    val aprKnown: Boolean = true,
    val minimumKnown: Boolean = true,
    val purchaseAprBasisPoints: Int? = null,
    val balanceTransferAprBasisPoints: Int? = null,
    val cashAdvanceAprBasisPoints: Int? = null,
    val graceOnPurchases: Boolean = false,
    val dayCountBasis: Int = 365,
    val recurringPaymentId: String? = null,
    val note: String = "",
)

data class DebtDraft(
    val existingId: String?,
    val name: String,
    val balanceMinor: Long,
    val aprBasisPoints: Int?,
    val aprKnown: Boolean,
    val minimumPaymentMinor: Long?,
    val minimumKnown: Boolean,
    val dueDayOfMonth: Int,
    val priority: PaymentPriority,
    val debtType: DebtType,
    val rateType: RateType,
    val accountId: String?,
    val paymentAccountId: String?,
    val statementCloseDay: Int?,
    val originationOn: LocalDate?,
    val maturityOn: LocalDate?,
    val remainingTermMonths: Int?,
    val feeMinor: Long,
    val rateUpdatedOn: LocalDate?,
    val accruedInterestMinor: Long?,
    val showInCalendar: Boolean,
    val purchaseAprBasisPoints: Int?,
    val balanceTransferAprBasisPoints: Int?,
    val cashAdvanceAprBasisPoints: Int?,
    val graceOnPurchases: Boolean,
    val dayCountBasis: Int,
    val recurringPaymentId: String?,
    val note: String,
)

data class DebtMonthPlan(
    val id: String,
    val debtId: String,
    val yearMonth: YearMonth,
    val plannedPaymentMinor: Long,
    val dueOn: LocalDate?,
)

data class DebtRateChange(
    val id: String,
    val debtId: String,
    val effectiveOn: LocalDate,
    val aprBasisPoints: Int,
    val note: String,
)

data class DebtSettlement(
    val id: String,
    val debtId: String,
    val yearMonth: YearMonth,
    val paidOn: LocalDate,
    val amountMinor: Long,
    val transactionId: String,
    val linkId: String?,
    val interestMinor: Long,
    val principalMinor: Long,
    val feeMinor: Long,
    val splitKnown: Boolean,
)

data class DetectedDraft(
    val accountId: String,
    val amountMinor: Long,
    val merchant: String,
    val type: TransactionType,
    val occurredOn: LocalDate,
    val source: DetectionSource,
    val confidencePercent: Int,
)

sealed interface IngestResult {
    data class Saved(val id: String, val status: TransactionStatus) : IngestResult

    data class NeedsReview(val id: String, val duplicateScore: Int) : IngestResult

    data class Linked(val survivorId: String, val score: Int) : IngestResult

    data class Rejected(val message: String) : IngestResult
}

data class NotificationSource(
    val id: String,
    val packageName: String,
    val label: String,
    val enabled: Boolean,
)

data class SyncRecord(
    val id: String,
    val providerId: String,
    val status: String,
    val message: String,
    val createdAtEpochMillis: Long,
)

enum class DebtStrategy {
    AVALANCHE,
    SNOWBALL,
    CUSTOM,
}
