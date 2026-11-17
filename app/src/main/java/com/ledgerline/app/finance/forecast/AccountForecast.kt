package com.ledgerline.app.finance.forecast

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.ScheduledOccurrence
import com.ledgerline.app.finance.balance.BalanceMath

data class AccountSeries(
    val accountId: String,
    val name: String,
    val debt: Boolean,
    val forecast: HorizonForecast,
)

data class SelectionForecast(
    val selectedIds: List<String>,
    val accounts: List<AccountSeries>,
    val combined: HorizonForecast?,
    val limitation: String,
)

object ForecastAccounts {
    fun reconcile(touched: Boolean, selected: Set<String>, activeIds: Set<String>): Set<String> =
        if (!touched) activeIds else selected.intersect(activeIds)
}

data class ForecastSession(
    val days: Int = DEFAULT_DAYS,
    val touched: Boolean = false,
    val chosen: Set<String> = emptySet(),
) {
    fun selection(activeIds: Set<String>): Set<String> = ForecastAccounts.reconcile(touched, chosen, activeIds)

    /** Only the supported periods are accepted. Anything else keeps the current period. */
    fun chooseDays(value: Int): ForecastSession = if (value in PERIODS) copy(days = value) else this

    fun toggle(id: String, activeIds: Set<String>): ForecastSession {
        val current = selection(activeIds)
        val next = if (id in current) current - id else current + id
        return copy(touched = true, chosen = next)
    }

    fun selectAll(activeIds: Set<String>): ForecastSession = copy(touched = true, chosen = activeIds)

    fun clear(): ForecastSession = copy(touched = true, chosen = emptySet())

    companion object {
        const val DEFAULT_DAYS = 30

        /** The forecast periods, in days. The schedule is built far enough ahead for the longest one. */
        val PERIODS: List<Int> = listOf(30, 60, 90, 120, 180, 360)
        val MAX_DAYS: Int = PERIODS.max()
    }
}

object AccountForecast {
    const val LIMITATION =
        "The combined figure adds each selected account's signed balance. Credit cards and loans count as amounts owed. " +
            "A scheduled transfer is stored on one account, so the other account does not gain a matching inflow unless it has its own scheduled item. " +
            "A card payment reduces the paying account in this projection and does not also reduce the card unless that card has its own scheduled entry. " +
            "Estimated daily spending is applied once to the combined projection. This is not a guarantee."

    fun signedDelta(event: ScheduledOccurrence): Long =
        if (event.direction == CashFlowDirection.INFLOW) event.amountMinor else -event.amountMinor

    fun project(
        today: java.time.LocalDate,
        monthCount: Int,
        accounts: List<Account>,
        selectedIds: Set<String>,
        openingById: Map<String, Long>,
        events: List<ScheduledOccurrence>,
        dailyDiscretionaryMinor: Long,
    ): SelectionForecast {
        val chosen = accounts.filter { it.id in selectedIds && it.active }
        if (chosen.isEmpty()) {
            return SelectionForecast(emptyList(), emptyList(), null, LIMITATION)
        }
        val series = chosen.map { account ->
            val mine = events
                .filter { it.accountId == account.id }
                .map { it.copy(liquidDeltaMinor = signedDelta(it)) }
            AccountSeries(
                accountId = account.id,
                name = account.name,
                debt = BalanceMath.isDebtType(account.type),
                forecast = MonthlyForecast.project(
                    today = today,
                    monthCount = monthCount,
                    openingMinor = openingById[account.id] ?: account.openingBalanceMinor,
                    events = mine,
                    dailyDiscretionaryMinor = 0,
                ),
            )
        }
        val combinedEvents = series.flatMap { row ->
            events.filter { it.accountId == row.accountId }.map { it.copy(liquidDeltaMinor = signedDelta(it)) }
        }
        val combined = MonthlyForecast.project(
            today = today,
            monthCount = monthCount,
            openingMinor = series.sumOf { it.forecast.openingMinor },
            events = combinedEvents,
            dailyDiscretionaryMinor = dailyDiscretionaryMinor,
        )
        return SelectionForecast(
            selectedIds = series.map { it.accountId },
            accounts = series,
            combined = combined,
            limitation = LIMITATION,
        )
    }
}
