package com.ledgerline.app.finance.debt

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtMonthPlan
import com.ledgerline.app.domain.model.DebtSettlement
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import java.time.LocalDate
import java.time.YearMonth

object DebtCashFlow {
    fun unpaid(
        debts: List<Debt>,
        plans: List<DebtMonthPlan>,
        settlements: List<DebtSettlement>,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
    ): List<ProjectedOccurrence> {
        if (start.isAfter(end)) return emptyList()
        val planBy = plans.associateBy { it.debtId to it.yearMonth }
        val settled = settlements.map { it.debtId to it.yearMonth }.toSet()
        return monthsBetween(start, end).flatMap { month ->
            debts.mapNotNull { debt ->
                if ((debt.id to month) in settled) return@mapNotNull null
                val plan = planBy[debt.id to month]
                val due = DebtDates.dueOn(month, debt.dueDayOfMonth, plan?.dueOn)
                if (due.isBefore(start) || due.isAfter(end)) return@mapNotNull null
                val source = debt.paymentAccountId ?: return@mapNotNull null
                val amount = plan?.plannedPaymentMinor ?: debt.minimumPaymentMinor.takeIf { debt.minimumKnown }
                    ?: return@mapNotNull null
                ProjectedOccurrence(
                    recurringId = eventId(debt.id, month),
                    name = debt.name,
                    date = due,
                    amountMinor = amount,
                    direction = CashFlowDirection.OUTFLOW,
                    kind = ScheduledKind.DEBT,
                    priority = debt.priority,
                    accountId = source,
                    incomeConfidence = IncomeConfidence.NONE,
                    state = OccurrenceState.SCHEDULED,
                    overdue = due.isBefore(today),
                )
            }
        }
    }

    /**
     * A debt event replaces the linked repeating payment for that month.
     * A recorded settlement removes both, because the account balance already moved.
     */
    fun withoutCoveredSeries(
        events: List<ProjectedOccurrence>,
        debts: List<Debt>,
        debtEvents: List<ProjectedOccurrence>,
        settlements: List<DebtSettlement>,
    ): List<ProjectedOccurrence> {
        val covered = coveredMonths(debts, debtEvents, settlements)
        if (covered.isEmpty()) return events
        return events.filter { (it.recurringId to YearMonth.from(it.date)) !in covered }
    }

    fun eventId(debtId: String, month: YearMonth): String = "debt:$debtId:$month"

    fun debtIdFrom(eventId: String): String? {
        if (!eventId.startsWith("debt:")) return null
        val body = eventId.removePrefix("debt:")
        val monthStart = body.lastIndexOf(':')
        if (monthStart <= 0) return null
        return body.substring(0, monthStart)
    }

    private fun coveredMonths(
        debts: List<Debt>,
        debtEvents: List<ProjectedOccurrence>,
        settlements: List<DebtSettlement>,
    ): Set<Pair<String, YearMonth>> {
        val byId = debts.associateBy { it.id }
        val fromEvents = debtEvents.mapNotNull { event ->
            val debtId = debtIdFrom(event.recurringId) ?: return@mapNotNull null
            val recurringId = byId[debtId]?.recurringPaymentId ?: return@mapNotNull null
            recurringId to YearMonth.from(event.date)
        }
        val fromSettlements = settlements.mapNotNull { settlement ->
            val recurringId = byId[settlement.debtId]?.recurringPaymentId ?: return@mapNotNull null
            recurringId to settlement.yearMonth
        }
        return (fromEvents + fromSettlements).toSet()
    }

    private fun monthsBetween(start: LocalDate, end: LocalDate): List<YearMonth> {
        val months = mutableListOf<YearMonth>()
        var cursor = YearMonth.from(start)
        val last = YearMonth.from(end)
        while (!cursor.isAfter(last)) {
            months += cursor
            cursor = cursor.plusMonths(1)
        }
        return months
    }
}
