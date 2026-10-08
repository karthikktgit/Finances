package com.ledgerline.app.finance.duplicate

import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.policy.FinancePolicy
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class DuplicateCandidate(
    val id: String,
    val accountId: String,
    val counterAccountId: String?,
    val linkId: String?,
    val amountMinor: Long,
    val currency: String,
    val type: TransactionType,
    val occurredOn: LocalDate,
    val merchantNormalized: String,
    val lastFour: String?,
    val deleted: Boolean,
)

data class DuplicateMatch(
    val existingId: String,
    val score: Int,
    val reasons: List<String>,
)

object DuplicateDetector {
    fun bestMatch(
        candidate: DuplicateCandidate,
        existing: List<DuplicateCandidate>,
        mergeScore: Int = FinancePolicy.DUPLICATE_MERGE_SCORE,
        askScore: Int = FinancePolicy.DUPLICATE_ASK_SCORE,
    ): DuplicateMatch? {
        val match = existing
            .asSequence()
            .filter { !it.deleted && it.id != candidate.id }
            .filter { candidate.linkId == null || it.linkId != candidate.linkId }
            .map { score(candidate, it) }
            .filter { it.score >= askScore }
            .maxByOrNull { it.score }
        return match?.takeIf { it.score >= askScore }?.let { found ->
            found.copy(score = found.score.coerceAtMost(100))
        }?.also {
            check(mergeScore >= askScore)
        }
    }

    fun score(left: DuplicateCandidate, right: DuplicateCandidate): DuplicateMatch {
        var points = 0
        val reasons = mutableListOf<String>()
        if (left.currency.equals(right.currency, ignoreCase = true)) {
            val leftAbs = abs(left.amountMinor)
            val rightAbs = abs(right.amountMinor)
            if (leftAbs == rightAbs) {
                points += 40
                reasons += "Same amount"
            } else if (withinOnePercent(leftAbs, rightAbs)) {
                points += 20
                reasons += "Amount within 1%"
            }
        }
        val sameAccount = left.accountId == right.accountId ||
            (left.counterAccountId != null && left.counterAccountId == right.accountId) ||
            (right.counterAccountId != null && right.counterAccountId == left.accountId)
        if (sameAccount) {
            points += 20
            reasons += "Same account"
        } else if (!left.lastFour.isNullOrBlank() && left.lastFour == right.lastFour) {
            points += 15
            reasons += "Same card ending"
        }
        val days = abs(ChronoUnit.DAYS.between(left.occurredOn, right.occurredOn))
        when (days) {
            0L -> {
                points += 20
                reasons += "Same date"
            }
            1L -> {
                points += 12
                reasons += "One day apart"
            }
            in 2L..FinancePolicy.DUPLICATE_WINDOW_DAYS -> {
                points += 6
                reasons += "Within three days"
            }
        }
        points += merchantPoints(left.merchantNormalized, right.merchantNormalized, reasons)
        if (left.type != right.type && !compatibleTypes(left.type, right.type)) {
            points -= 15
            reasons += "Different transaction type"
        }
        return DuplicateMatch(
            existingId = right.id,
            score = points.coerceIn(0, 100),
            reasons = reasons,
        )
    }

    private fun merchantPoints(left: String, right: String, reasons: MutableList<String>): Int {
        val a = left.trim().lowercase()
        val b = right.trim().lowercase()
        if (a.isEmpty() || b.isEmpty()) return 0
        if (a == b) {
            reasons += "Same merchant"
            return 20
        }
        if (a.contains(b) || b.contains(a)) {
            reasons += "Similar merchant"
            return 12
        }
        val leftTokens = a.split(" ").filter { it.length > 1 }.toSet()
        val rightTokens = b.split(" ").filter { it.length > 1 }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0
        val overlap = leftTokens.intersect(rightTokens).size.toDouble() / minOf(leftTokens.size, rightTokens.size)
        if (overlap >= 0.5) {
            reasons += "Overlapping merchant words"
            return 8
        }
        return 0
    }

    private fun withinOnePercent(left: Long, right: Long): Boolean {
        val base = maxOf(left, right).coerceAtLeast(1)
        return abs(left - right) * 100 <= base
    }

    private fun compatibleTypes(left: TransactionType, right: TransactionType): Boolean {
        val paymentFamily = setOf(TransactionType.PAYMENT, TransactionType.TRANSFER)
        return left in paymentFamily && right in paymentFamily
    }
}
