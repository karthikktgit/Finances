package com.ledgerline.app.finance.calendar

import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.OccurrenceState
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.schedule.ProjectedOccurrence
import com.ledgerline.app.finance.schedule.RecurringSchedule
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.Locale

/** One bill, income, or loan payment on one date, from a schedule or a tracked debt. */
data class BillEntry(
    val key: String,
    val name: String,
    val date: LocalDate,
    /** Null when a debt has no planned amount yet. */
    val amountMinor: Long?,
    val incoming: Boolean,
    val kind: ScheduledKind,
    val paid: Boolean,
    val overdue: Boolean,
    val accountId: String?,
    /** Set for schedules; edits open the bill editor on this date. */
    val recurringId: String?,
    /** Set for tracked debts; edits open the debt screen. */
    val debtId: String?,
) {
    val transfer: Boolean get() = kind == ScheduledKind.TRANSFER
}

/** What a calendar cell shows. */
data class BillDay(
    val date: LocalDate,
    /** Bills still to pay that day. */
    val dueMinor: Long,
    /** Income expected or received that day. */
    val incomeMinor: Long,
    val billCount: Int,
    val allPaid: Boolean,
    val overdue: Boolean,
    /** A transaction you recorded by hand or from a capture, not from a schedule. */
    val hasSpending: Boolean,
    /** Everyday spending recorded that day. */
    val spentMinor: Long = 0,
    /** Bills marked paid that day. */
    val paidCount: Int = 0,
    /** Income that has come in that day, scheduled or not. */
    val incomeReceived: Boolean = false,
    /** Bills marked paid that day, by amount. */
    val paidMinor: Long = 0,
    /** Income recorded that day, by amount. */
    val receivedMinor: Long = 0,
    /** Spends planned for that day and not paid yet. */
    val plannedMinor: Long = 0,
) {
    /** Everything still to pay that day: unpaid bills and planned spends. */
    val toPayMinor: Long get() = dueMinor + plannedMinor

    /** All the money that went out that day: everyday spending and bills paid. */
    val outMinor: Long get() = spentMinor + paidMinor

    /** What happened or is due that day, one dot each, in a fixed order so the calendar reads the same everywhere. */
    val marks: List<DayMark>
        get() = buildList {
            if (incomeMinor > 0 || incomeReceived) add(DayMark.INCOME)
            if (overdue) add(DayMark.OVERDUE) else if (dueMinor > 0 || plannedMinor > 0) add(DayMark.DUE)
            if (paidCount > 0) add(DayMark.PAID)
            if (spentMinor > 0 || hasSpending) add(DayMark.SPENT)
        }
}

/** The kinds of dot a calendar day can show. */
enum class DayMark { INCOME, DUE, OVERDUE, PAID, SPENT }

data class BillMonthSummary(
    val billsMinor: Long,
    val paidMinor: Long,
    val billCount: Int,
    val paidCount: Int,
    val incomeMinor: Long,
    val overdueCount: Int,
) {
    val leftMinor: Long get() = billsMinor - paidMinor
}

object BillMonth {
    fun entries(occurrences: List<ProjectedOccurrence>, debts: List<DebtCalendarItem>, linkedRecurringIds: Set<String>): List<BillEntry> {
        val scheduled = occurrences
            .filter { it.recurringId !in linkedRecurringIds }
            .map { event ->
                BillEntry(
                    key = "r-${event.recurringId}-${event.date}",
                    name = event.name,
                    date = event.date,
                    amountMinor = event.amountMinor,
                    incoming = event.direction == CashFlowDirection.INFLOW,
                    kind = event.kind,
                    paid = event.state == OccurrenceState.PAID,
                    overdue = event.overdue && event.state != OccurrenceState.PAID,
                    accountId = event.accountId,
                    recurringId = event.recurringId,
                    debtId = null,
                )
            }
        val debtEntries = debts.map { item ->
            BillEntry(
                key = "d-${item.debtId}-${item.yearMonth}",
                name = item.name,
                date = item.date,
                amountMinor = item.amountMinor,
                incoming = false,
                kind = ScheduledKind.DEBT,
                paid = item.paid,
                overdue = item.overdue && !item.paid,
                accountId = item.paymentAccountId ?: item.accountId,
                recurringId = null,
                debtId = item.debtId,
            )
        }
        return (scheduled + debtEntries).sortedWith(compareBy({ it.date }, { it.incoming }, { it.name.lowercase() }))
    }

    /** Totals for one month. Transfers between your own accounts are neither bills nor income. */
    fun summary(entries: List<BillEntry>, month: YearMonth): BillMonthSummary {
        val inMonth = entries.filter { YearMonth.from(it.date) == month && !it.transfer }
        val bills = inMonth.filter { !it.incoming }
        return BillMonthSummary(
            billsMinor = bills.sumOf { it.amountMinor ?: 0L },
            paidMinor = bills.filter { it.paid }.sumOf { it.amountMinor ?: 0L },
            billCount = bills.size,
            paidCount = bills.count { it.paid },
            incomeMinor = inMonth.filter { it.incoming }.sumOf { it.amountMinor ?: 0L },
            overdueCount = bills.count { it.overdue },
        )
    }

    fun days(entries: List<BillEntry>, transactions: List<LedgerTransaction>, dates: List<LocalDate>): Map<LocalDate, BillDay> {
        val byDate = entries.filter { !it.transfer }.groupBy { it.date }
        val active = transactions.filter { BalanceMath.isActive(it) }
        val spentByDate = active
            .filter { it.recurringPaymentId == null && it.type == TransactionType.EXPENSE }
            .groupBy({ it.occurredOn }, { it.amountMinor })
            .mapValues { (_, amounts) -> amounts.sum() }
        val plannedByDate = transactions.filter { BalanceMath.isPlannedUnpaid(it) }.groupBy({ it.occurredOn }, { it.amountMinor }).mapValues { (_, l) -> l.sum() }
        val incomeByDate = active.filter { it.type == TransactionType.INCOME }.groupBy({ it.occurredOn }, { it.amountMinor }).mapValues { (_, l) -> l.sum() }
        val incomeDates = incomeByDate.keys
        val paidByDate = active.filter { it.recurringPaymentId != null && it.balanceEffectMinor < 0 && it.type != TransactionType.TRANSFER }
            .groupBy({ it.occurredOn }, { -it.balanceEffectMinor }).mapValues { (_, l) -> l.sum() }
        return dates.associateWith { date ->
            val rows = byDate[date].orEmpty()
            val bills = rows.filter { !it.incoming }
            BillDay(
                date = date,
                dueMinor = bills.filter { !it.paid }.sumOf { it.amountMinor ?: 0L },
                incomeMinor = rows.filter { it.incoming }.sumOf { it.amountMinor ?: 0L },
                billCount = bills.size,
                allPaid = bills.isNotEmpty() && bills.all { it.paid },
                overdue = bills.any { it.overdue },
                hasSpending = date in spentByDate,
                spentMinor = spentByDate[date] ?: 0L,
                paidCount = bills.count { it.paid },
                incomeReceived = date in incomeDates || rows.any { it.incoming && it.paid },
                paidMinor = paidByDate[date] ?: 0L,
                receivedMinor = incomeByDate[date] ?: 0L,
                plannedMinor = plannedByDate[date] ?: 0L,
            )
        }
    }

    /**
     * A short amount that fits a calendar cell: "$22", "$480", "$1.2k", "₹12k", "$2.5M". Whole units only, so the
     * cell stays readable; the exact figure is in the list below the calendar.
     */
    fun compact(minor: Long, currency: String, locale: Locale = Locale.getDefault()): String {
        // The sign goes before the currency symbol: -$355, not $-355.
        if (minor < 0) return "-" + compact(-minor, currency, locale)
        val symbol = runCatching { Currency.getInstance(currency).getSymbol(locale) }.getOrDefault(currency)
        val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0) }.getOrDefault(2)
        val major = BigDecimal.valueOf(minor).movePointLeft(digits)
        fun short(value: BigDecimal, suffix: String): String {
            val scaled = if (value < BigDecimal.TEN) value.setScale(1, RoundingMode.HALF_UP) else value.setScale(0, RoundingMode.HALF_UP)
            return scaled.stripTrailingZeros().toPlainString() + suffix
        }
        val body = when {
            major >= BigDecimal(1_000_000) -> short(major.divide(BigDecimal(1_000_000)), "M")
            major >= BigDecimal(1_000) -> short(major.divide(BigDecimal(1_000)), "k")
            else -> major.setScale(0, RoundingMode.HALF_UP).toPlainString()
        }
        return symbol + body
    }
}

/** One-tap starting points for the bills people add most. */
object BillPresets {
    data class Preset(
        val name: String,
        val kind: ScheduledKind,
        /** A word to find a matching category, such as "Rent" or "Subscriptions". */
        val categoryHint: String,
        val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    ) {
        val incoming: Boolean get() = kind == ScheduledKind.INCOME
    }

    val bills = listOf(
        Preset("Rent", ScheduledKind.BILL, "Rent"),
        Preset("Electricity", ScheduledKind.BILL, "Utilities"),
        Preset("Water", ScheduledKind.BILL, "Utilities"),
        Preset("Internet", ScheduledKind.BILL, "Internet"),
        Preset("Phone", ScheduledKind.BILL, "Phone"),
        Preset("Insurance", ScheduledKind.BILL, "Insurance"),
    )
    val subscriptions = listOf(
        Preset("Netflix", ScheduledKind.SUBSCRIPTION, "Subscriptions"),
        Preset("Spotify", ScheduledKind.SUBSCRIPTION, "Subscriptions"),
        Preset("YouTube Premium", ScheduledKind.SUBSCRIPTION, "Subscriptions"),
        Preset("Amazon Prime", ScheduledKind.SUBSCRIPTION, "Subscriptions", RecurrenceFrequency.YEARLY),
        Preset("iCloud", ScheduledKind.SUBSCRIPTION, "Subscriptions"),
        Preset("Gym", ScheduledKind.SUBSCRIPTION, "Subscriptions"),
    )
    val income = listOf(
        Preset("Salary", ScheduledKind.INCOME, "Salary"),
        Preset("Freelance", ScheduledKind.INCOME, "Other Income"),
    )
    val loans = listOf(
        Preset("Credit card", ScheduledKind.DEBT, "Credit Card Payment"),
        Preset("Car loan", ScheduledKind.DEBT, "Loan"),
        Preset("Student loan", ScheduledKind.DEBT, "Education Loan"),
    )

    fun forKind(kind: ScheduledKind): List<Preset> = when (kind) {
        ScheduledKind.SUBSCRIPTION -> subscriptions
        ScheduledKind.INCOME -> income
        ScheduledKind.DEBT -> loans
        ScheduledKind.TRANSFER -> emptyList()
        else -> bills
    }

    /** The live category that best matches [hint]: an exact name first, then a name that contains it. */
    fun category(hint: String, categories: List<Category>): Category? {
        val live = categories.filter { !it.archived }
        return live.firstOrNull { it.name.equals(hint, ignoreCase = true) }
            ?: live.filter { it.parentId != null }.firstOrNull { it.name.contains(hint, ignoreCase = true) }
            ?: live.firstOrNull { it.name.contains(hint, ignoreCase = true) }
    }
}

/** The next dates a bill will be due, shown while you set it up. */
object BillPreview {
    fun nextDates(
        frequency: RecurrenceFrequency,
        start: LocalDate,
        from: LocalDate,
        count: Int = 3,
        intervalDays: Int? = null,
        endOn: LocalDate? = null,
        maxOccurrences: Int? = null,
    ): List<LocalDate> {
        val draft = RecurringPayment(
            id = "preview", name = "", accountId = "", categoryId = null, direction = CashFlowDirection.OUTFLOW,
            // Any positive amount: the schedule skips empty items, and the dates do not depend on the amount.
            kind = ScheduledKind.BILL, amountMinor = 1, currency = "USD", frequency = frequency,
            intervalDays = intervalDays, dayOfMonth = start.dayOfMonth, dayOfWeek = start.dayOfWeek.value, startOn = start,
            endOn = endOn, priority = PaymentPriority.MANDATORY, incomeConfidence = IncomeConfidence.HIGH, active = true,
            note = "", maxOccurrences = maxOccurrences,
        )
        val begin = if (from.isBefore(start)) start else from
        return RecurringSchedule.occurrencesBetween(draft, begin, begin.plusYears(3)).take(count)
    }

    /** "Every month on the 5th", "Every 2 weeks on Friday", "Once, on Nov 5". */
    fun describe(frequency: RecurrenceFrequency, start: LocalDate, intervalDays: Int? = null, locale: Locale = Locale.getDefault()): String {
        val weekday = start.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
        val monthDay = start.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", locale))
        return when (frequency) {
            RecurrenceFrequency.ONCE -> "Once, on $monthDay"
            RecurrenceFrequency.DAILY -> "Every day"
            RecurrenceFrequency.WEEKLY -> "Every week on $weekday"
            RecurrenceFrequency.BIWEEKLY -> "Every 2 weeks on $weekday"
            RecurrenceFrequency.MONTHLY -> "Every month on the ${ordinal(start.dayOfMonth)}"
            RecurrenceFrequency.QUARTERLY -> "Every 3 months on the ${ordinal(start.dayOfMonth)}"
            RecurrenceFrequency.YEARLY -> "Every year on $monthDay"
            RecurrenceFrequency.CUSTOM -> "Every ${intervalDays ?: 1} days"
        }
    }

    fun ordinal(day: Int): String {
        val suffix = if (day % 100 in 11..13) "th" else when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }
}
