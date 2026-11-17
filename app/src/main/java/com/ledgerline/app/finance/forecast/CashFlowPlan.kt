package com.ledgerline.app.finance.forecast

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import java.time.LocalDate

/** One money movement on the cash-flow timeline. */
data class FlowItem(
    val name: String,
    /** Positive comes in, negative goes out. */
    val signedMinor: Long,
    val transfer: Boolean,
    val accountName: String,
    /** A bill or income from the calendar, rather than a transaction already recorded for a future date. */
    val scheduled: Boolean,
    /** Its due date has passed and it is still unpaid, so it is counted today. */
    val overdue: Boolean = false,
)

/** A day on which money moves, and the cash left at its end. */
data class FlowDay(val date: LocalDate, val items: List<FlowItem>, val endMinor: Long)

/** The few facts the cash-flow screen leads with. */
data class FlowSummary(
    /** Cash at the end of today. */
    val todayMinor: Long,
    val lowestMinor: Long,
    val lowestOn: LocalDate,
    val endMinor: Long,
    val endOn: LocalDate,
    /** First day the cash goes below zero, if it does. */
    val runsOutOn: LocalDate?,
    /** First day the cash goes below your safety buffer, if it does. */
    val belowBufferOn: LocalDate?,
    /** Next payday after today, from income on the calendar. */
    val nextPayday: LocalDate?,
    /** Cash at the end of the day before that payday: what has to last until the money comes in. */
    val beforePaydayMinor: Long?,
)

enum class FlowRisk { SHORT, TIGHT, FINE }

/**
 * Turns the day-by-day projection into what a person wants to know: the lowest point, whether the money runs out
 * before payday, and which days money actually moves.
 */
object CashFlowPlan {
    fun paydays(events: List<ScheduledOccurrence>, selectedIds: Set<String>, today: LocalDate): List<LocalDate> =
        events
            .filter { it.direction == CashFlowDirection.INFLOW && it.kind == ScheduledKind.INCOME && it.accountId in selectedIds && it.date.isAfter(today) }
            .map { it.date }
            .distinct()
            .sorted()

    fun summarize(days: List<DayBalance>, paydays: List<LocalDate>, bufferMinor: Long): FlowSummary? {
        if (days.isEmpty()) return null
        val lowest = days.minWith(compareBy<DayBalance> { it.endMinor }.thenBy { it.date })
        val next = paydays.firstOrNull { payday -> days.any { it.date == payday } }
        return FlowSummary(
            todayMinor = days.first().endMinor,
            lowestMinor = lowest.endMinor,
            lowestOn = lowest.date,
            endMinor = days.last().endMinor,
            endOn = days.last().date,
            runsOutOn = days.firstOrNull { it.endMinor < 0 }?.date,
            belowBufferOn = if (bufferMinor > 0) days.firstOrNull { it.endMinor < bufferMinor }?.date else null,
            nextPayday = next,
            beforePaydayMinor = next?.let { payday -> days.lastOrNull { it.date.isBefore(payday) }?.endMinor },
        )
    }

    fun risk(summary: FlowSummary): FlowRisk = when {
        summary.runsOutOn != null -> FlowRisk.SHORT
        summary.belowBufferOn != null -> FlowRisk.TIGHT
        else -> FlowRisk.FINE
    }

    /**
     * Days with a bill, income, or future-dated transaction on the chosen accounts, in date order, with the cash at the
     * end of each. Calendar items already paid are left out, because their transaction is counted instead.
     */
    fun timeline(
        days: List<DayBalance>,
        events: List<ScheduledOccurrence>,
        transactions: List<LedgerTransaction>,
        selectedIds: Set<String>,
        accountNames: Map<String, String>,
    ): List<FlowDay> {
        if (days.isEmpty()) return emptyList()
        val first = days.first().date
        val last = days.last().date
        val settled = transactions.mapNotNull { tx ->
            val recurringId = tx.recurringPaymentId ?: return@mapNotNull null
            if (!BalanceMath.isActive(tx)) return@mapNotNull null
            recurringId to tx.occurredOn
        }.toSet()
        val scheduled = events
            .filter { it.accountId in selectedIds && !it.date.isBefore(first) && !it.date.isAfter(last) && (it.recurringId to it.date) !in settled }
            .distinctBy { Triple(it.recurringId, it.date, it.accountId) }
            .map { event ->
                event.date to FlowItem(
                    name = event.name,
                    signedMinor = if (event.direction == CashFlowDirection.INFLOW) event.amountMinor else -event.amountMinor,
                    transfer = event.kind == ScheduledKind.TRANSFER,
                    accountName = accountNames[event.accountId].orEmpty(),
                    scheduled = true,
                    overdue = event.overdue,
                )
            }
        // Spending already recorded today is in today's opening cash; only later dates are listed.
        val posted = transactions
            .filter { BalanceMath.isActive(it) && it.accountId in selectedIds && it.occurredOn.isAfter(first) && !it.occurredOn.isAfter(last) }
            .map { tx ->
                tx.occurredOn to FlowItem(
                    name = tx.merchantRaw.ifBlank { tx.type.name.lowercase().replaceFirstChar { it.titlecase() } },
                    signedMinor = tx.balanceEffectMinor,
                    transfer = tx.type == TransactionType.TRANSFER,
                    accountName = accountNames[tx.accountId].orEmpty(),
                    scheduled = false,
                )
            }
        val endByDate = days.associate { it.date to it.endMinor }
        return (scheduled + posted)
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .mapNotNull { (date, items) ->
                val end = endByDate[date] ?: return@mapNotNull null
                FlowDay(date, items.sortedByDescending { it.signedMinor }, end)
            }
    }
}
