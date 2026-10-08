package com.ledgerline.app.domain.model

import java.time.LocalDate

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
)

data class DebtDraft(
    val existingId: String?,
    val name: String,
    val balanceMinor: Long,
    val aprBasisPoints: Int,
    val minimumPaymentMinor: Long,
    val dueDayOfMonth: Int,
    val priority: PaymentPriority,
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
