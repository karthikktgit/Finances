package com.ledgerline.app.finance.schedule

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.TransactionType
import java.time.LocalDate

data class ProjectedOccurrence(
    val recurringId: String,
    val name: String,
    val date: LocalDate,
    val amountMinor: Long,
    val direction: CashFlowDirection,
    val kind: ScheduledKind,
    val priority: PaymentPriority,
    val accountId: String,
    val incomeConfidence: IncomeConfidence,
    val state: OccurrenceState,
    val overdue: Boolean,
)

object OccurrenceMaterializer {
    fun project(
        items: List<RecurringPayment>,
        adjustments: List<OccurrenceAdjustment>,
        settled: Set<Pair<String, LocalDate>>,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
    ): List<ProjectedOccurrence> {
        if (start > end) return emptyList()
        val byKey = adjustments.associateBy { it.recurringId to it.occurrenceOn }
        return items.flatMap { item ->
            RecurringSchedule.occurrencesBetween(item, start, end).mapNotNull { date ->
                val adjustment = byKey[item.id to date]
                if (adjustment?.state == OccurrenceState.SKIPPED) return@mapNotNull null
                val paid = adjustment?.state == OccurrenceState.PAID || (item.id to date) in settled
                val amount = adjustment?.amountMinor?.takeIf { it > 0 } ?: item.amountMinor
                val name = adjustment?.name?.takeIf { it.isNotBlank() } ?: item.name
                ProjectedOccurrence(
                    recurringId = item.id,
                    name = name,
                    date = date,
                    amountMinor = amount,
                    direction = item.direction,
                    kind = item.kind,
                    priority = item.priority,
                    accountId = item.accountId,
                    incomeConfidence = item.incomeConfidence,
                    state = if (paid) OccurrenceState.PAID else OccurrenceState.SCHEDULED,
                    overdue = !paid && date.isBefore(today) && item.direction == CashFlowDirection.OUTFLOW && item.kind != ScheduledKind.TRANSFER,
                )
            }
        }.distinctBy { it.recurringId to it.date }
            .sortedWith(compareBy<ProjectedOccurrence> { it.date }.thenBy { it.name })
    }

    /**
     * A posted bill is an expense. Income is income. A transfer or debt payment
     * is a payment on the scheduled account, so it is not also counted as spending.
     */
    fun postedTransactionType(item: RecurringPayment): TransactionType = when {
        item.direction == CashFlowDirection.INFLOW -> TransactionType.INCOME
        item.kind == ScheduledKind.TRANSFER || item.kind == ScheduledKind.DEBT -> TransactionType.PAYMENT
        else -> TransactionType.EXPENSE
    }
}
