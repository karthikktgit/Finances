package com.ledgerline.app.finance.debt

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Daily periodic rate: APR / day-count basis.
 * Interest for a span is balance × APR × days / (basis × 10,000), rounded half-up
 * to minor units. An average daily balance is the same formula on the sum of the
 * daily balances. This is not a statement amount unless those daily balances came
 * from the issuer's cycle.
 */
object InterestMath {
    fun onBalance(balanceMinor: Long, aprBasisPoints: Int, days: Int, dayCountBasis: Int = 365): Long {
        if (balanceMinor <= 0 || aprBasisPoints <= 0 || days <= 0 || dayCountBasis <= 0) return 0L
        return BigDecimal.valueOf(balanceMinor)
            .multiply(BigDecimal.valueOf(aprBasisPoints.toLong()))
            .multiply(BigDecimal.valueOf(days.toLong()))
            .divide(
                BigDecimal.valueOf(dayCountBasis.toLong()).multiply(BigDecimal.valueOf(10_000L)),
                0,
                RoundingMode.HALF_UP,
            )
            .longValueExact()
    }

    fun onDailyBalances(dailyBalances: List<Long>, aprBasisPoints: Int, dayCountBasis: Int = 365): Long {
        if (dailyBalances.isEmpty() || aprBasisPoints <= 0 || dayCountBasis <= 0) return 0L
        var sum = 0L
        dailyBalances.forEach { balance ->
            val owed = balance.coerceAtLeast(0)
            if (sum > Long.MAX_VALUE - owed) return Long.MAX_VALUE
            sum += owed
        }
        return onBalance(sum, aprBasisPoints, 1, dayCountBasis)
    }
}

data class BucketInterest(
    val interestMinor: Long?,
    val estimate: Boolean,
    val missing: List<String>,
    val graceApplied: Boolean,
)

object CardInterest {
    fun buckets(
        purchaseBalances: List<Long>,
        purchaseAprBasisPoints: Int?,
        transferBalances: List<Long>,
        transferAprBasisPoints: Int?,
        cashBalances: List<Long>,
        cashAprBasisPoints: Int?,
        dayCountBasis: Int = 365,
        graceOnPurchases: Boolean = false,
        previousStatementPaidInFull: Boolean? = null,
    ): BucketInterest {
        val missing = mutableListOf<String>()
        if (purchaseBalances.isEmpty() && transferBalances.isEmpty() && cashBalances.isEmpty()) {
            missing += "Daily balances"
        }
        val grace = graceOnPurchases && previousStatementPaidInFull == true
        if (graceOnPurchases && previousStatementPaidInFull == null && purchaseBalances.any { it > 0 }) {
            missing += "Whether the previous statement was paid in full"
        }
        val purchase = portion(purchaseBalances, purchaseAprBasisPoints, "Purchase APR", dayCountBasis, zeroBecauseGrace = grace, missing = missing)
        val transfer = portion(transferBalances, transferAprBasisPoints, "Balance-transfer APR", dayCountBasis, zeroBecauseGrace = false, missing = missing)
        val cash = portion(cashBalances, cashAprBasisPoints, "Cash-advance APR", dayCountBasis, zeroBecauseGrace = false, missing = missing)
        if (missing.isNotEmpty()) {
            return BucketInterest(null, estimate = true, missing = missing.distinct(), graceApplied = grace)
        }
        return BucketInterest(
            interestMinor = (purchase ?: 0L) + (transfer ?: 0L) + (cash ?: 0L),
            estimate = false,
            missing = emptyList(),
            graceApplied = grace,
        )
    }

    private fun portion(
        balances: List<Long>,
        aprBasisPoints: Int?,
        label: String,
        dayCountBasis: Int,
        zeroBecauseGrace: Boolean,
        missing: MutableList<String>,
    ): Long? {
        if (balances.none { it > 0 }) return 0L
        if (zeroBecauseGrace) return 0L
        if (aprBasisPoints == null) {
            missing += label
            return null
        }
        return InterestMath.onDailyBalances(balances, aprBasisPoints, dayCountBasis)
    }
}
