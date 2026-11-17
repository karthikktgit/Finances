package com.ledgerline.app.ui.calendar

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.TransactionType

enum class CalendarTone {
    OUTGOING,
    INCOME,
    TRANSFER,
}

data class CalendarAppearance(
    val tone: CalendarTone,
    val paid: Boolean,
    val overdue: Boolean,
) {
    fun label(): String {
        val base = when (tone) {
            CalendarTone.OUTGOING -> "Outgoing"
            CalendarTone.INCOME -> "Income"
            CalendarTone.TRANSFER -> "Transfer"
        }
        return when {
            paid -> "$base · Paid"
            overdue -> "$base · Overdue"
            else -> base
        }
    }
}

fun calendarAppearance(
    direction: CashFlowDirection,
    kind: ScheduledKind,
    state: OccurrenceState,
    overdue: Boolean,
): CalendarAppearance {
    val tone = when {
        kind == ScheduledKind.TRANSFER -> CalendarTone.TRANSFER
        direction == CashFlowDirection.INFLOW -> CalendarTone.INCOME
        else -> CalendarTone.OUTGOING
    }
    val paid = state == OccurrenceState.PAID
    return CalendarAppearance(
        tone = tone,
        paid = paid,
        overdue = overdue && !paid && tone == CalendarTone.OUTGOING,
    )
}

fun transactionTone(type: TransactionType): CalendarTone = when (type) {
    TransactionType.INCOME, TransactionType.REFUND -> CalendarTone.INCOME
    TransactionType.TRANSFER, TransactionType.PAYMENT -> CalendarTone.TRANSFER
    else -> CalendarTone.OUTGOING
}
