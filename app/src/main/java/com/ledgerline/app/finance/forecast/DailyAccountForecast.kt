package com.ledgerline.app.finance.forecast

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import java.time.LocalDate

data class DayBalance(
    val date: LocalDate,
    val endMinor: Long,
    val incomingMinor: Long,
    val outgoingMinor: Long,
    val negative: Boolean,
    val unreliable: Boolean,
)

data class AccountDaily(
    val accountId: String,
    val name: String,
    val debt: Boolean,
    val days: List<DayBalance>,
)

data class DailySelection(
    val selectedIds: List<String>,
    val accounts: List<AccountDaily>,
    val combined: List<DayBalance>?,
    val limitation: String,
)

object DailyAccountForecast {
    const val LIMITATION =
        "Each row is the projected balance at the end of that day. Credit cards and loans stay as amounts owed. " +
            "A transfer moves the accounts it is saved on and is not income or spending. " +
            "Estimated daily spending is included once in the combined balance. " +
            "A completed transaction is applied on its own date and is not added again as a scheduled item. This is not a guarantee."

    fun dates(today: LocalDate, dayCount: Int): List<LocalDate> {
        if (dayCount <= 0) return emptyList()
        return (0 until dayCount).map { today.plusDays(it.toLong()) }
    }

    fun project(
        today: LocalDate,
        dayCount: Int,
        accounts: List<Account>,
        selectedIds: Set<String>,
        transactions: List<LedgerTransaction>,
        events: List<ScheduledOccurrence>,
        dailyDiscretionaryMinor: Long,
    ): DailySelection {
        // An account listed twice is still one account.
        val chosen = accounts.distinctBy { it.id }.filter { it.id in selectedIds && it.active }
        if (chosen.isEmpty() || dayCount <= 0) {
            return DailySelection(emptyList(), emptyList(), null, LIMITATION)
        }
        val window = dates(today, dayCount)
        val end = window.last()
        val settled = transactions.mapNotNull { transaction ->
            val recurringId = transaction.recurringPaymentId ?: return@mapNotNull null
            if (!BalanceMath.isActive(transaction)) return@mapNotNull null
            recurringId to transaction.occurredOn
        }.toSet()
        val unreliable = (
            events.filter { it.accountId.isBlank() && !it.date.isBefore(today) && !it.date.isAfter(end) }.map { it.date } +
                transactions.filter { BalanceMath.isActive(it) && it.accountId.isBlank() && !it.occurredOn.isBefore(today) && !it.occurredOn.isAfter(end) }
                    .map { it.occurredOn }
            ).toSet()
        val series = chosen.map { account ->
            val mineEvents = events.filter { it.accountId == account.id && (it.recurringId to it.date) !in settled }
            AccountDaily(
                accountId = account.id,
                name = account.name,
                debt = BalanceMath.isDebtType(account.type),
                days = walk(today, dayCount, account, transactions, mineEvents, unreliable, discretionary = 0),
            )
        }
        val combinedEvents = series.flatMap { row ->
            events.filter { it.accountId == row.accountId && (it.recurringId to it.date) !in settled }
        }
        val combinedOpening = chosen.sumOf { BalanceMath.balanceAsOf(it, transactions, today.minusDays(1)) }
        val combinedDeltas = chosen.flatMap { deltas(it.id, transactions, events.filter { event -> event.accountId == it.id && (event.recurringId to event.date) !in settled }, today, end) }
        val walked = CashFlowForecastEngine.project(today, dayCount, combinedOpening, combinedDeltas, dailyDiscretionaryMinor)
        val flow = cashByDate(chosen.map { it.id }.toSet(), transactions, combinedEvents, today, end)
        val combined = walked.map { day ->
            val pair = flow[day.date] ?: (0L to 0L)
            DayBalance(
                date = day.date,
                endMinor = day.balanceAfterMinor,
                incomingMinor = pair.first,
                outgoingMinor = pair.second,
                negative = day.balanceAfterMinor < 0,
                unreliable = day.date in unreliable,
            )
        }
        return DailySelection(series.map { it.accountId }, series, combined, LIMITATION)
    }

    private fun walk(
        today: LocalDate,
        dayCount: Int,
        account: Account,
        transactions: List<LedgerTransaction>,
        events: List<ScheduledOccurrence>,
        unreliable: Set<LocalDate>,
        discretionary: Long,
    ): List<DayBalance> {
        val end = today.plusDays((dayCount - 1).toLong())
        val opening = BalanceMath.balanceAsOf(account, transactions, today.minusDays(1))
        val walked = CashFlowForecastEngine.project(
            today,
            dayCount,
            opening,
            deltas(account.id, transactions, events, today, end),
            discretionary,
        )
        val flow = cashByDate(setOf(account.id), transactions, events, today, end)
        return walked.map { day ->
            val pair = flow[day.date] ?: (0L to 0L)
            DayBalance(
                date = day.date,
                endMinor = day.balanceAfterMinor,
                incomingMinor = pair.first,
                outgoingMinor = pair.second,
                negative = day.balanceAfterMinor < 0,
                unreliable = day.date in unreliable,
            )
        }
    }

    private fun deltas(
        accountId: String,
        transactions: List<LedgerTransaction>,
        events: List<ScheduledOccurrence>,
        today: LocalDate,
        end: LocalDate,
    ): List<CashFlowForecastEngine.DatedAmount> {
        val posted = transactions.filter {
            BalanceMath.isActive(it) && it.accountId == accountId && !it.occurredOn.isBefore(today) && !it.occurredOn.isAfter(end)
        }.map { CashFlowForecastEngine.DatedAmount(it.occurredOn, it.id, it.merchantRaw, it.balanceEffectMinor) }
        val scheduled = events.filter { it.accountId == accountId && !it.date.isBefore(today) && !it.date.isAfter(end) }
            .map { CashFlowForecastEngine.DatedAmount(it.date, it.recurringId, it.name, AccountForecast.signedDelta(it)) }
        return posted + scheduled
    }

    private fun cashByDate(
        accountIds: Set<String>,
        transactions: List<LedgerTransaction>,
        events: List<ScheduledOccurrence>,
        today: LocalDate,
        end: LocalDate,
    ): Map<LocalDate, Pair<Long, Long>> {
        val dates = mutableSetOf<LocalDate>()
        transactions.filter { BalanceMath.isActive(it) && it.accountId in accountIds && !it.occurredOn.isBefore(today) && !it.occurredOn.isAfter(end) }
            .forEach { dates += it.occurredOn }
        events.filter { it.accountId in accountIds && !it.date.isBefore(today) && !it.date.isAfter(end) }
            .forEach { dates += it.date }
        return dates.associateWith { date ->
            val txs = transactions.filter { BalanceMath.isActive(it) && it.accountId in accountIds && it.occurredOn == date }
            val postedIn = txs.filter { it.type == TransactionType.INCOME || it.type == TransactionType.REFUND }.sumOf { it.amountMinor }
            val postedOut = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
            val scheduled = events.filter { it.accountId in accountIds && it.date == date && it.kind != ScheduledKind.TRANSFER }
            val scheduledIn = scheduled.filter { it.direction == CashFlowDirection.INFLOW }.sumOf { it.amountMinor }
            val scheduledOut = scheduled.filter { it.direction == CashFlowDirection.OUTFLOW }.sumOf { it.amountMinor }
            (postedIn + scheduledIn) to (postedOut + scheduledOut)
        }
    }
}
