package com.ledgerline.app.finance.forecast

import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.period.PayPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Today's limit: an even share of what is left for the cycle, fixed at the start of the day. */
data class DailyLimit(
    /** What today could hold at the start of the day: what is left now plus what was already spent today, shared by the days left. */
    val limitMinor: Long,
    val spentTodayMinor: Long,
    val daysLeft: Long,
    /** If today goes over, tomorrow's even share of what is left. Null on the last day of the cycle. */
    val tomorrowLimitMinor: Long?,
) {
    val leftTodayMinor: Long get() = limitMinor - spentTodayMinor
    val over: Boolean get() = spentTodayMinor > limitMinor
}

enum class PaceVerdict { NO_SPENDING_YET, ON_TRACK, TIGHT, RUNS_OUT }

/** Where everyday spending is heading if it continues at the rate so far this cycle. */
data class PaceForecast(
    /** Everyday spending so far this cycle. Bills from the calendar are not part of it. */
    val spentMinor: Long,
    val daysElapsed: Long,
    val daysLeft: Long,
    val dailyAverageMinor: Long,
    /** What is left at the end of the cycle if spending keeps this pace. Negative means you run short. */
    val endMinor: Long,
    /** The day the money runs out at this pace, or null if it lasts the cycle. */
    val runsOutOn: LocalDate?,
    val verdict: PaceVerdict,
)

/**
 * Spending pace for people who want to catch overspending early. Built on Safe to spend, which already has bills
 * and the buffer taken out, so the only thing left to forecast is everyday spending.
 */
object SpendingPace {
    /** Everyday spending: counted expenses that are not a scheduled bill being paid. Refunds reduce it. */
    fun everyday(rows: List<LedgerTransaction>): Long {
        val counted = rows.filter { BalanceMath.isActive(it) && it.recurringPaymentId == null }
        val spent = counted.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
        val refunded = counted.filter { it.type == TransactionType.REFUND }.sumOf { it.amountMinor }
        return (spent - refunded).coerceAtLeast(0)
    }

    /**
     * @param safeNowMinor Safe to spend right now, which already reflects what was spent today
     * @param today the date the limit is for
     */
    fun dailyLimit(safeNowMinor: Long, spentTodayMinor: Long, today: LocalDate, cycleEnd: LocalDate): DailyLimit {
        val daysLeft = (ChronoUnit.DAYS.between(today, cycleEnd) + 1).coerceAtLeast(1)
        val startOfDay = (safeNowMinor + spentTodayMinor).coerceAtLeast(0)
        val limit = startOfDay / daysLeft
        val tomorrow = if (daysLeft > 1) safeNowMinor.coerceAtLeast(0) / (daysLeft - 1) else null
        return DailyLimit(limit, spentTodayMinor, daysLeft, tomorrow)
    }

    /**
     * @param spentMinor everyday spending from the start of the cycle through today
     * @param safeNowMinor Safe to spend right now
     */
    fun forecast(spentMinor: Long, safeNowMinor: Long, today: LocalDate, period: PayPeriod): PaceForecast {
        val daysElapsed = (ChronoUnit.DAYS.between(period.start, today) + 1).coerceIn(1, ChronoUnit.DAYS.between(period.start, period.end) + 1)
        // Days after today. Today's spending is already in the average and in Safe to spend.
        val daysLeft = ChronoUnit.DAYS.between(today, period.end).coerceAtLeast(0)
        val average = spentMinor / daysElapsed
        val end = safeNowMinor - average * daysLeft
        val runsOut = when {
            average <= 0 -> null
            safeNowMinor <= 0 -> today
            end >= 0 -> null
            else -> today.plusDays(safeNowMinor / average)
        }
        val verdict = when {
            spentMinor <= 0 -> PaceVerdict.NO_SPENDING_YET
            runsOut != null -> PaceVerdict.RUNS_OUT
            // Less than three days of spending at this pace left over: on track, but only just.
            end < average * 3 -> PaceVerdict.TIGHT
            else -> PaceVerdict.ON_TRACK
        }
        return PaceForecast(spentMinor, daysElapsed, daysLeft, average, end, runsOut, verdict)
    }

    /** What is left to spend at the end of [date] if everyday spending keeps its average pace. */
    fun leftOn(safeNowMinor: Long, averageMinor: Long, today: LocalDate, date: LocalDate): Long =
        safeNowMinor - averageMinor * ChronoUnit.DAYS.between(today, date).coerceAtLeast(0)

    /** Remaining money day by day from today to the cycle end: at your pace, and if you kept to the daily limit. */
    fun curve(safeNowMinor: Long, averageMinor: Long, daysLeft: Long): Pair<List<Long>, List<Long>> {
        val days = daysLeft.toInt().coerceAtLeast(0)
        val atPace = (0..days).map { safeNowMinor - averageMinor * it }
        val atLimit = (0..days).map { if (days == 0) safeNowMinor else safeNowMinor - safeNowMinor * it / days }
        return atPace to atLimit
    }
}

/** What a purchase you are thinking about would do to the rest of the cycle. */
data class PurchaseCheck(
    val priceMinor: Long,
    val limitBeforeMinor: Long,
    val limitAfterMinor: Long,
    /** How many days of today's limit the price is. */
    val daysOfLimit: Double,
    /** Safe to spend after buying it. Negative means it would eat into bills or the buffer. */
    val safeAfterMinor: Long,
) {
    val unaffordable: Boolean get() = safeAfterMinor < 0
}

object ImpulseCheck {
    fun check(priceMinor: Long, safeNowMinor: Long, today: LocalDate, cycleEnd: LocalDate): PurchaseCheck {
        val daysLeft = (ChronoUnit.DAYS.between(today, cycleEnd) + 1).coerceAtLeast(1)
        val before = safeNowMinor.coerceAtLeast(0) / daysLeft
        val after = (safeNowMinor - priceMinor).coerceAtLeast(0) / daysLeft
        val days = if (before > 0) priceMinor.toDouble() / before.toDouble() else Double.POSITIVE_INFINITY
        return PurchaseCheck(priceMinor, before, after, days, safeNowMinor - priceMinor)
    }
}
