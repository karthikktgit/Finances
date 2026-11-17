package com.ledgerline.app.finance.dashboard

import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.finance.balance.BalanceMath
import java.time.LocalDate

object UpcomingWindow {
    fun inclusiveEnd(today: LocalDate, days: Int): LocalDate =
        today.plusDays((days - 1).coerceAtLeast(0).toLong())

    fun scheduled(today: LocalDate, days: Int, items: List<ScheduledOccurrence>): List<ScheduledOccurrence> {
        val end = inclusiveEnd(today, days)
        return items
            .asSequence()
            .filter { !it.overdue && !it.date.isBefore(today) && !it.date.isAfter(end) }
            .distinctBy { Triple(it.recurringId, it.date, it.accountId) }
            .sortedWith(compareBy({ it.date }, { it.name }))
            .toList()
    }

    fun posted(today: LocalDate, days: Int, transactions: List<LedgerTransaction>): List<LedgerTransaction> {
        val end = inclusiveEnd(today, days)
        return transactions
            .asSequence()
            .filter { BalanceMath.isActive(it) && !it.occurredOn.isBefore(today) && !it.occurredOn.isAfter(end) }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.occurredOn }, { it.merchantRaw }))
            .toList()
    }
}
