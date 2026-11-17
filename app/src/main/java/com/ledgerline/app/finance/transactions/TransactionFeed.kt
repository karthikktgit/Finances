package com.ledgerline.app.finance.transactions

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import java.time.LocalDate

/** Which transactions the list shows. Choosing a filter only changes the view; it never edits a record. */
enum class PaymentFilter { ALL, PAID, UNPAID }

/** A row in the transaction list: a recorded transaction, or a scheduled item that has not been paid. */
sealed interface FeedRow {
    val date: LocalDate
    val key: String

    data class Posted(val tx: LedgerTransaction) : FeedRow {
        override val date: LocalDate get() = tx.occurredOn
        override val key: String get() = "tx-${tx.id}"
    }

    data class Scheduled(val item: ScheduledOccurrence) : FeedRow {
        override val date: LocalDate get() = item.date
        override val key: String get() = "due-${item.recurringId}-${item.date}-${item.accountId}"
    }
}

object TransactionFeed {
    /** Unpaid scheduled items are listed through this many days from today. Older unpaid items stay listed as overdue. */
    const val SCHEDULE_DAYS = 60

    /**
     * Paid or completed: confirmed, reconciled, or modified transactions, which are the ones that move balances.
     * Not paid or pending: detected transactions waiting for confirmation, and scheduled items nobody marked paid.
     * Ignored and duplicate rows are in neither group; they appear only under All.
     */
    fun isPaid(tx: LedgerTransaction): Boolean = BalanceMath.isActive(tx)

    fun isPending(tx: LedgerTransaction): Boolean = tx.deletedAtEpochMillis == null && tx.status == TransactionStatus.DETECTED

    fun matchesStatus(tx: LedgerTransaction, filter: PaymentFilter): Boolean = when (filter) {
        PaymentFilter.ALL -> true
        PaymentFilter.PAID -> isPaid(tx)
        PaymentFilter.UNPAID -> isPending(tx)
    }

    /** A scheduled item is never paid until it has a matching transaction, so it belongs to All and Not paid only. */
    fun matchesStatus(filter: PaymentFilter): Boolean = filter != PaymentFilter.PAID

    /** The transaction type a scheduled item would become if it were marked paid. Used only for the type filter. */
    fun typeOf(item: ScheduledOccurrence): TransactionType = when {
        item.kind == ScheduledKind.TRANSFER -> TransactionType.TRANSFER
        item.direction == CashFlowDirection.INFLOW -> TransactionType.INCOME
        item.kind == ScheduledKind.DEBT -> TransactionType.PAYMENT
        else -> TransactionType.EXPENSE
    }

    /** Scheduled rows inside the list window, without duplicates. Same item on the same date and account appears once. */
    fun scheduledWindow(today: LocalDate, items: List<ScheduledOccurrence>): List<ScheduledOccurrence> {
        val end = today.plusDays(SCHEDULE_DAYS.toLong())
        return items
            .filter { !it.date.isAfter(end) }
            .distinctBy { Triple(it.recurringId, it.date, it.accountId) }
    }

    fun merge(posted: List<LedgerTransaction>, scheduled: List<ScheduledOccurrence>): List<FeedRow> =
        (posted.map { FeedRow.Posted(it) } + scheduled.map { FeedRow.Scheduled(it) })
            .sortedWith(compareByDescending<FeedRow> { it.date }.thenBy { it.key })
}
