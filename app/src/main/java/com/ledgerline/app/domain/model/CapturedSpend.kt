package com.ledgerline.app.domain.model

import com.ledgerline.app.detection.capture.CaptureChannel
import com.ledgerline.app.detection.capture.MoneyDirection
import java.time.LocalDate

enum class CaptureStatus { PENDING, ADDED, DISMISSED }

data class CapturedSpend(
    val id: String,
    val packageName: String,
    val sourceLabel: String,
    val channel: CaptureChannel,
    val amountMinor: Long,
    val currency: String,
    val merchant: String,
    val lastFour: String?,
    val direction: MoneyDirection,
    val refund: Boolean,
    val occurredOn: LocalDate,
    val capturedAtEpochMillis: Long,
    val preview: String,
    val status: CaptureStatus,
    val transactionId: String?,
) {
    /** The transaction type this capture becomes when you add it. */
    val transactionType: TransactionType
        get() = when {
            direction == MoneyDirection.OUT -> TransactionType.EXPENSE
            refund -> TransactionType.REFUND
            else -> TransactionType.INCOME
        }
}
