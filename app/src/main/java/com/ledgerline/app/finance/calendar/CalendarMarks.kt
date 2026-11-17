package com.ledgerline.app.finance.calendar

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtMonthPlan
import com.ledgerline.app.domain.model.DebtSettlement
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.debt.DebtDates
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarDot {
    SCHEDULED,
    MANUAL,
    INCOME,
    DEBT,
    TRANSFER,
}

data class DayMarks(val dots: List<CalendarDot>, val extraCount: Int, val recordCount: Int)

data class DebtCalendarItem(
    val debtId: String,
    val name: String,
    val date: LocalDate,
    val yearMonth: YearMonth,
    val amountMinor: Long?,
    val paid: Boolean,
    val overdue: Boolean,
    val accountId: String?,
    val paymentAccountId: String?,
)

object DebtCalendar {
    fun items(
        month: YearMonth,
        debts: List<Debt>,
        plans: List<DebtMonthPlan>,
        settlements: List<DebtSettlement>,
        today: LocalDate,
        respectVisibility: Boolean,
    ): List<DebtCalendarItem> {
        val planBy = plans.associateBy { it.debtId to it.yearMonth }
        val settlementBy = settlements.associateBy { it.debtId to it.yearMonth }
        return debts.mapNotNull { debt ->
            if (respectVisibility && !debt.showInCalendar) return@mapNotNull null
            val plan = planBy[debt.id to month]
            val settlement = settlementBy[debt.id to month]
            val due = DebtDates.dueOn(month, debt.dueDayOfMonth, plan?.dueOn)
            if (YearMonth.from(due) != month) return@mapNotNull null
            DebtCalendarItem(
                debtId = debt.id,
                name = debt.name,
                date = due,
                yearMonth = month,
                amountMinor = plan?.plannedPaymentMinor ?: debt.minimumPaymentMinor.takeIf { debt.minimumKnown },
                paid = settlement != null,
                overdue = settlement == null && due.isBefore(today),
                accountId = debt.accountId,
                paymentAccountId = debt.paymentAccountId,
            )
        }
    }
}

object CalendarMarks {
    private val order = listOf(
        CalendarDot.SCHEDULED,
        CalendarDot.MANUAL,
        CalendarDot.INCOME,
        CalendarDot.DEBT,
        CalendarDot.TRANSFER,
    )

    fun forDate(
        date: LocalDate,
        transactions: List<LedgerTransaction>,
        occurrences: List<ProjectedOccurrence>,
        debtItems: List<DebtCalendarItem>,
        settlementLinkIds: Set<String>,
        linkedRecurringIds: Set<String>,
    ): DayMarks {
        val categories = mutableListOf<CalendarDot>()
        val seenLinks = mutableSetOf<String>()
        transactions.filter { BalanceMath.isActive(it) && it.occurredOn == date }.forEach { transaction ->
            val link = transaction.linkId
            if (link != null && !seenLinks.add(link)) return@forEach
            categories += if (link != null && link in settlementLinkIds) {
                CalendarDot.DEBT
            } else {
                dotForTransaction(transaction)
            }
        }
        occurrences.filter { it.date == date && it.recurringId !in linkedRecurringIds }.forEach { event ->
            val alreadyPosted = event.state == OccurrenceState.PAID
            if (alreadyPosted) return@forEach
            categories += dotForOccurrence(event)
        }
        debtItems.filter { it.date == date && !it.paid }.forEach { _ ->
            categories += CalendarDot.DEBT
        }
        val distinct = order.filter { it in categories }
        val dots = distinct.take(3)
        val extra = if (categories.size > dots.size && (categories.size > 3 || distinct.size > dots.size)) {
            categories.size - dots.size
        } else {
            0
        }
        return DayMarks(dots, extra, categories.size)
    }

    private fun dotForTransaction(transaction: LedgerTransaction): CalendarDot = when (transaction.type) {
        TransactionType.INCOME, TransactionType.REFUND -> CalendarDot.INCOME
        TransactionType.TRANSFER, TransactionType.PAYMENT -> CalendarDot.TRANSFER
        else -> if (transaction.recurringPaymentId != null) CalendarDot.SCHEDULED else CalendarDot.MANUAL
    }

    private fun dotForOccurrence(event: ProjectedOccurrence): CalendarDot = when {
        event.kind == ScheduledKind.DEBT -> CalendarDot.DEBT
        event.kind == ScheduledKind.TRANSFER -> CalendarDot.TRANSFER
        event.direction == CashFlowDirection.INFLOW -> CalendarDot.INCOME
        else -> CalendarDot.SCHEDULED
    }
}
