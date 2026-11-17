package com.ledgerline.app.finance.debt

import com.ledgerline.app.domain.model.PayChoice
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class RatePoint(val effectiveOn: LocalDate, val aprBasisPoints: Int)

data class MonthLine(
    val yearMonth: YearMonth,
    val dueOn: LocalDate,
    val openingMinor: Long,
    val interestMinor: Long?,
    val paymentMinor: Long?,
    val interestPaidMinor: Long?,
    val principalPaidMinor: Long?,
    val feeMinor: Long,
    val unpaidInterestMinor: Long?,
    val unpaidFeeMinor: Long?,
    val closingMinor: Long?,
    val missing: List<String>,
)

data class SeriesProjection(
    val months: List<MonthLine>,
    val payoffMonth: YearMonth?,
    val totalInterestMinor: Long?,
    val estimate: Boolean,
    val missing: List<String>,
    val stoppedForShortfall: Boolean,
    val note: String,
)

data class ForecastInput(
    val openingMinor: Long,
    val start: YearMonth,
    val monthCount: Int,
    val dueDayOfMonth: Int,
    val aprKnown: Boolean,
    val aprBasisPoints: Int,
    val rateUpdatedOn: LocalDate?,
    val rateChanges: List<RatePoint>,
    val dayCountBasis: Int = 365,
    val minimumKnown: Boolean,
    val minimumMinor: Long,
    val feeMinor: Long,
    val accruedInterestMinor: Long?,
    val plans: Map<YearMonth, Long>,
    val dueOverrides: Map<YearMonth, LocalDate>,
    val choice: PayChoice,
    val fixedMinor: Long = 0,
    val extraMinor: Long = 0,
    val statementCloseDay: Int? = null,
)

data class ScenarioComparison(
    val baseline: SeriesProjection,
    val alternative: SeriesProjection,
    val interestDifferenceMinor: Long?,
)

object DebtDates {
    fun dueOn(month: YearMonth, dueDay: Int, override: LocalDate?): LocalDate {
        if (override != null && YearMonth.from(override) == month) return override
        val length = month.lengthOfMonth()
        val day = dueDay.coerceIn(1, length)
        return month.atDay(day)
    }
}

object DebtForecast {
    const val NOTE =
        "Estimate. Interest uses a daily periodic rate on the opening balance for each day in the calendar month, " +
            "then the payment is applied to interest, fees, and principal. " +
            "Daily purchases, credits, and the day a payment posts are not included. This is not a statement amount."

    private const val MAX_MONTHS = 360

    fun ratePoints(input: ForecastInput): List<RatePoint> {
        val points = input.rateChanges.toMutableList()
        if (input.aprKnown) {
            val on = input.rateUpdatedOn ?: LocalDate.MIN
            if (points.none { it.effectiveOn == on }) points += RatePoint(on, input.aprBasisPoints)
        }
        return points.sortedBy { it.effectiveOn }
    }

    fun rateOn(date: LocalDate, points: List<RatePoint>): Int? =
        points.filter { !it.effectiveOn.isAfter(date) }.maxByOrNull { it.effectiveOn }?.aprBasisPoints

    fun project(input: ForecastInput): SeriesProjection {
        val points = ratePoints(input)
        val lines = mutableListOf<MonthLine>()
        var opening = input.openingMinor.coerceAtLeast(0)
        var totalInterest = 0L
        var interestComplete = true
        var shortfall = false
        val missing = linkedMissing(input)
        val limit = input.monthCount.coerceIn(0, MAX_MONTHS)
        for (offset in 0 until limit) {
            if (opening == 0L && lines.isNotEmpty()) break
            val month = input.start.plusMonths(offset.toLong())
            val due = DebtDates.dueOn(month, input.dueDayOfMonth, input.dueOverrides[month])
            val monthMissing = mutableListOf<String>()
            val interest = interestFor(opening, month, points, input.dayCountBasis, if (offset == 0) input.accruedInterestMinor else null, monthMissing)
            val payment = paymentFor(month, input, monthMissing)
            if (interest == null || payment == null) {
                interestComplete = false
                lines += MonthLine(
                    yearMonth = month,
                    dueOn = due,
                    openingMinor = opening,
                    interestMinor = interest,
                    paymentMinor = payment,
                    interestPaidMinor = null,
                    principalPaidMinor = null,
                    feeMinor = input.feeMinor.coerceAtLeast(0),
                    unpaidInterestMinor = null,
                    unpaidFeeMinor = null,
                    closingMinor = null,
                    missing = monthMissing.distinct(),
                )
                missing += monthMissing
                break
            }
            val split = allocate(opening, interest, input.feeMinor.coerceAtLeast(0), payment)
            totalInterest += interest
            lines += MonthLine(
                yearMonth = month,
                dueOn = due,
                openingMinor = opening,
                interestMinor = interest,
                paymentMinor = payment,
                interestPaidMinor = split.interestPaid,
                principalPaidMinor = split.principal,
                feeMinor = input.feeMinor.coerceAtLeast(0),
                unpaidInterestMinor = split.unpaidInterest,
                unpaidFeeMinor = split.unpaidFee,
                closingMinor = split.closing,
                missing = emptyList(),
            )
            opening = split.closing
            if (split.unpaidInterest > 0 || split.unpaidFee > 0) {
                shortfall = true
                break
            }
            if (opening == 0L) break
        }
        val payoff = if (!shortfall && lines.lastOrNull()?.closingMinor == 0L) lines.last().yearMonth else null
        return SeriesProjection(
            months = lines,
            payoffMonth = payoff,
            totalInterestMinor = if (interestComplete) totalInterest else null,
            estimate = true,
            missing = missing.distinct(),
            stoppedForShortfall = shortfall,
            note = NOTE,
        )
    }

    fun compare(input: ForecastInput, alternative: PayChoice, fixedMinor: Long, extraMinor: Long): ScenarioComparison {
        val baseline = project(input.copy(choice = PayChoice.MINIMUM, monthCount = MAX_MONTHS))
        val other = project(
            input.copy(choice = alternative, fixedMinor = fixedMinor, extraMinor = extraMinor, monthCount = MAX_MONTHS),
        )
        val difference = if (baseline.totalInterestMinor != null && other.totalInterestMinor != null) {
            other.totalInterestMinor - baseline.totalInterestMinor
        } else {
            null
        }
        return ScenarioComparison(baseline, other, difference)
    }

    fun paymentToReach(input: ForecastInput, target: YearMonth): Long? {
        val months = ChronoUnit.MONTHS.between(input.start, target).toInt() + 1
        if (months <= 0 || months > MAX_MONTHS) return null
        var high = (input.openingMinor + input.feeMinor.coerceAtLeast(0) * months).coerceAtLeast(1)
        var found = false
        repeat(24) {
            val projection = project(input.copy(choice = PayChoice.FIXED, fixedMinor = high, monthCount = months))
            if (projection.payoffMonth != null && !projection.payoffMonth.isAfter(target)) {
                found = true
                return@repeat
            }
            if (high > Long.MAX_VALUE / 2) return null
            high *= 2
        }
        if (!found) return null
        var low = 0L
        while (low < high) {
            val mid = low + (high - low) / 2
            val projection = project(input.copy(choice = PayChoice.FIXED, fixedMinor = mid, monthCount = months))
            if (projection.payoffMonth != null && !projection.payoffMonth.isAfter(target)) high = mid else low = mid + 1
        }
        return high
    }

    private fun linkedMissing(input: ForecastInput): MutableList<String> {
        val missing = mutableListOf<String>()
        if (input.statementCloseDay == null) missing += "Statement closing date"
        missing += "Daily balances for the billing period"
        return missing
    }

    private fun interestFor(
        opening: Long,
        month: YearMonth,
        points: List<RatePoint>,
        dayCountBasis: Int,
        accrued: Long?,
        missing: MutableList<String>,
    ): Long? {
        if (opening <= 0) return accrued?.coerceAtLeast(0) ?: 0L
        var interest = accrued?.coerceAtLeast(0) ?: 0L
        var cursor = month.atDay(1)
        val end = month.atEndOfMonth()
        while (!cursor.isAfter(end)) {
            val rate = rateOn(cursor, points)
            val next = points.filter { it.effectiveOn.isAfter(cursor) }.minByOrNull { it.effectiveOn }?.effectiveOn
            val segmentEnd = if (next != null && next.minusDays(1) <= end) next.minusDays(1) else end
            if (rate == null) {
                missing += "APR for ${month}"
                return null
            }
            val days = ChronoUnit.DAYS.between(cursor, segmentEnd).toInt() + 1
            interest += InterestMath.onBalance(opening, rate, days, dayCountBasis)
            cursor = segmentEnd.plusDays(1)
        }
        return interest
    }

    private fun paymentFor(month: YearMonth, input: ForecastInput, missing: MutableList<String>): Long? {
        val amount = when (input.choice) {
            PayChoice.MINIMUM -> if (input.minimumKnown) input.minimumMinor.coerceAtLeast(0) else null
            PayChoice.FIXED -> input.fixedMinor.takeIf { it > 0 }
            PayChoice.MINIMUM_PLUS -> if (input.minimumKnown) input.minimumMinor.coerceAtLeast(0) + input.extraMinor.coerceAtLeast(0) else null
            PayChoice.SAVED_PLAN -> input.plans[month]
        }
        if (amount == null) {
            missing += when (input.choice) {
                PayChoice.SAVED_PLAN -> "Planned payment for ${month}"
                PayChoice.FIXED -> "Payment amount"
                else -> "Minimum payment from the statement"
            }
        }
        return amount
    }

    private data class Split(
        val interestPaid: Long,
        val principal: Long,
        val unpaidInterest: Long,
        val unpaidFee: Long,
        val closing: Long,
    )

    private fun allocate(opening: Long, interest: Long, fee: Long, payment: Long): Split {
        val paid = payment.coerceAtLeast(0)
        val interestPaid = minOf(paid, interest)
        val afterInterest = paid - interestPaid
        val feePaid = minOf(afterInterest, fee)
        val principal = minOf(afterInterest - feePaid, opening).coerceAtLeast(0)
        val unpaidInterest = interest - interestPaid
        val unpaidFee = fee - feePaid
        val closing = (opening - principal + unpaidInterest + unpaidFee).coerceAtLeast(0)
        return Split(interestPaid, principal, unpaidInterest, unpaidFee, closing)
    }
}
