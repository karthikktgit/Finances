package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.finance.schedule.RecurringSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The subscriptions as entered on the phone (2026-11-10): each falls on its day and stops where it should. */
class SubscriptionDatesTest {
    private fun sub(name: String, day: Int, start: LocalDate, end: LocalDate? = null, times: Int? = null) = RecurringPayment(
        id = name, name = name, accountId = "a", categoryId = null, direction = CashFlowDirection.OUTFLOW, kind = ScheduledKind.SUBSCRIPTION,
        amountMinor = 1_000, currency = "USD", frequency = RecurrenceFrequency.MONTHLY, intervalDays = null, dayOfMonth = day,
        dayOfWeek = null, startOn = start, endOn = end, priority = PaymentPriority.IMPORTANT, incomeConfidence = IncomeConfidence.NONE,
        active = true, note = "", maxOccurrences = times,
    )

    private fun dates(item: RecurringPayment) = RecurringSchedule.occurrencesBetween(item, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31))

    @Test
    fun endsOnADate() {
        // Progressive: Oct 22 to Dec 22.
        assertEquals(listOf("2026-10-22", "2026-11-22", "2026-12-22"), dates(sub("Progressive", 22, LocalDate.of(2026, 10, 22), LocalDate.of(2026, 12, 22))).map { it.toString() })
        // AAA: Nov 9 to Feb 9.
        assertEquals(4, dates(sub("AAA", 9, LocalDate.of(2026, 11, 9), LocalDate.of(2027, 2, 9))).size)
        // Gym: Oct 25 to Sep 25 next year: twelve payments.
        assertEquals(12, dates(sub("Gym", 25, LocalDate.of(2026, 10, 25), LocalDate.of(2027, 9, 25))).size)
    }

    @Test
    fun endsAfterANumberOfTimes() {
        val claude = dates(sub("Claude", 22, LocalDate.of(2026, 10, 22), times = 6))
        assertEquals(6, claude.size)
        assertEquals(LocalDate.of(2027, 3, 22), claude.last())
        // A day the month lacks falls on its last day.
        assertEquals(LocalDate.of(2027, 2, 28), dates(sub("Rent", 31, LocalDate.of(2027, 1, 31), times = 2)).last())
    }
}
