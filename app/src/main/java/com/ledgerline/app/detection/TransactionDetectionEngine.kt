package com.ledgerline.app.detection

import com.ledgerline.app.detection.parser.ParsedTransaction
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.finance.policy.FinancePolicy

enum class DetectionDisposition {
    CONFIRMED,
    REVIEW,
    LINK,
    REJECT,
}

data class DetectionDecision(
    val disposition: DetectionDisposition,
    val confidencePercent: Int,
    val status: TransactionStatus?,
    val message: String,
)

object TransactionDetectionEngine {
    fun matchAccount(accounts: List<Account>, lastFour: String?, currency: String): Account? {
        if (lastFour.isNullOrBlank()) return null
        val hits = accounts.filter { it.active && it.currency.equals(currency, ignoreCase = true) && it.lastFour == lastFour }
        return hits.singleOrNull()
    }

    fun confidence(parsed: ParsedTransaction, accountMatched: Boolean): Int {
        var score = 40
        if (!parsed.merchant.isNullOrBlank()) score += 25
        if (accountMatched) score += 20
        if (parsed.type != null) score += 10
        return score.coerceIn(0, 100)
    }

    fun decide(parsed: ParsedTransaction, account: Account?, duplicateScore: Int?): DetectionDecision {
        if (parsed.amountMinor == null || parsed.amountMinor <= 0) {
            return DetectionDecision(DetectionDisposition.REJECT, 0, null, "A notification was read, but the amount could not be identified.")
        }
        if (parsed.type == null) {
            return DetectionDecision(DetectionDisposition.REJECT, 0, null, "A notification was read, but the transaction type could not be identified.")
        }
        if (account == null) {
            return DetectionDecision(DetectionDisposition.REJECT, 0, null, "A notification was read, but no single account matched the card or account ending.")
        }
        val confidence = confidence(parsed, accountMatched = true)
        if (duplicateScore != null && duplicateScore >= FinancePolicy.DUPLICATE_MERGE_SCORE) {
            return DetectionDecision(
                DetectionDisposition.LINK,
                confidence,
                TransactionStatus.DUPLICATE,
                "Linked to an existing transaction at $duplicateScore% confidence. The original record was kept.",
            )
        }
        if (duplicateScore != null && duplicateScore >= FinancePolicy.DUPLICATE_ASK_SCORE) {
            return DetectionDecision(
                DetectionDisposition.REVIEW,
                confidence,
                TransactionStatus.DETECTED,
                "Possible duplicate at $duplicateScore%. It is not included in balances until you review it.",
            )
        }
        return if (confidence >= FinancePolicy.AUTO_CONFIRM_CONFIDENCE) {
            DetectionDecision(DetectionDisposition.CONFIRMED, confidence, TransactionStatus.CONFIRMED, "Recorded from the notification.")
        } else {
            DetectionDecision(DetectionDisposition.REVIEW, confidence, TransactionStatus.DETECTED, "Needs review before it changes balances.")
        }
    }
}
