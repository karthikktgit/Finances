package com.ledgerline.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerline.app.domain.model.BudgetDraft
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringDraft
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.BudgetRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.HomeGroupRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.envelopes.HomeItem
import com.ledgerline.app.finance.envelopes.HomeItemKind
import com.ledgerline.app.finance.envelopes.HomeSections
import com.ledgerline.app.finance.category.CategoryNames
import com.ledgerline.app.finance.money.AmountInput
import com.ledgerline.app.finance.period.CycleSchedule
import com.ledgerline.app.finance.period.PayPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** A bill or spending item as you edit it on Home. */
data class ItemEdit(
    val key: String?,
    val kind: HomeItemKind,
    val name: String,
    val amountText: String,
    val accountId: String?,
    val groupId: String,
    /** Day of the month a new bill is due. */
    val day: Int,
    val budgetId: String? = null,
    val recurringId: String? = null,
    val categoryId: String? = null,
    /** The first unpaid date of an existing bill, so it can be marked paid from here. */
    val nextDue: LocalDate? = null,
    /** For a new bill: how often it repeats and when it stops. */
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val end: com.ledgerline.app.finance.schedule.ScheduleEnd = com.ledgerline.app.finance.schedule.ScheduleEnd(),
    /** For a new bill: its first due date, picked on a calendar. It repeats from there. */
    val firstDue: LocalDate? = null,
    /** The cycle Home is showing, which "this cycle only" means. */
    val cycleStart: LocalDate? = null,
    /** A changed amount is for this cycle only (true) or from now on in every cycle (false). */
    val thisCycleOnly: Boolean = true,
    /** The amount it usually has, before any change for one cycle. */
    val usualMinor: Long? = null,
) {
    val isNew: Boolean get() = key == null
}

/** Names offered with one tap when adding to a group. */
object ItemPresets {
    val bills = listOf("Rent", "Electricity", "Internet", "Phone", "Insurance", "Car payment")
    val subscriptions = listOf("Gym", "Netflix", "Spotify", "Car wash", "iCloud", "YouTube")
    val spending = listOf("Groceries", "Gas", "Eating out", "Shopping", "Coffee", "Fun")
}

/**
 * Adding, changing, and grouping bills and spending items straight from Home. Bills are saved as monthly bills on the
 * calendar; spending items as pay-cycle budgets. Both are tied to a category, found by name or made for you, so
 * spending you add later lands in the right ring.
 */
@HiltViewModel
class HomeEditViewModel @Inject constructor(
    private val groups: HomeGroupRepository,
    private val budgets: BudgetRepository,
    private val recurring: RecurringRepository,
    private val categories: CategoryRepository,
    accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val preferences: com.ledgerline.app.preferences.UiPreferences,
    private val cyclePlans: com.ledgerline.app.domain.repository.CyclePlanRepository,
) : ViewModel() {
    /** The pay figure being edited, as typed; null when the dialog is closed. */
    var paying by mutableStateOf<String?>(null)
        private set
    private var calendarPay = 0L

    fun startPay(current: Long, fromCalendar: Long) {
        calendarPay = fromCalendar
        paying = AmountInput.fromMinor(current, fractionDigits)
    }

    fun onPayText(text: String) {
        paying = AmountInput.clean(text, fractionDigits)
    }

    fun useCalendarPay() {
        paying = AmountInput.fromMinor(calendarPay, fractionDigits)
    }

    fun dismissPay() {
        paying = null
    }

    /** Saves the pay. Matching what the calendar says clears your own figure, so it follows the calendar again. */
    fun savePay() {
        val minor = AmountInput.toMinor(paying.orEmpty(), fractionDigits)
        viewModelScope.launch {
            preferences.setPayIncome(if (minor <= 0 || minor == calendarPay) null else minor)
            paying = null
        }
    }

    val accounts = accounts.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groupList = groups.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var editing by mutableStateOf<ItemEdit?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private suspend fun digits(): Int {
        val currency = settings.observe().first().primaryCurrency
        return runCatching { java.util.Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
    }

    fun startNew(groupId: String, cycleStart: LocalDate? = null) {
        error = null
        val defaultAccount = accounts.value.firstOrNull { it.active && !it.deleted && it.includeInSafeToSpend }?.id
        editing = ItemEdit(
            key = null,
            kind = if (groupId == HomeSections.SUBSCRIPTIONS) HomeItemKind.BILL else HomeItemKind.ENVELOPE,
            name = "",
            amountText = "",
            accountId = defaultAccount,
            groupId = groupId,
            day = clock.today().dayOfMonth,
            firstDue = clock.today(),
            cycleStart = cycleStart,
        )
    }

    fun startEdit(item: HomeItem, groupId: String, cycleStart: LocalDate? = null) {
        error = null
        viewModelScope.launch {
            val budget = item.budgetId?.let { budgets.get(it) }
            val series = item.recurringId?.let { recurring.get(it) }
            // How it ends now, so the sheet opens on the current choice.
            val end = when {
                budget?.maxCycles != null -> com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.AFTER_COUNT, budget.maxCycles)
                budget?.endOn != null -> com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.ON_DATE, on = budget.endOn)
                series?.maxOccurrences != null -> com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.AFTER_COUNT, series.maxOccurrences)
                series?.endOn != null -> com.ledgerline.app.finance.schedule.ScheduleEnd(com.ledgerline.app.finance.schedule.EndMode.ON_DATE, on = series.endOn)
                else -> com.ledgerline.app.finance.schedule.ScheduleEnd()
            }
            editing = ItemEdit(
                end = end,
                cycleStart = cycleStart,
                usualMinor = budget?.amountMinor ?: series?.amountMinor,
                key = item.key,
                kind = item.kind,
                name = item.name,
                amountText = AmountInput.fromMinor(
                    if (item.kind == HomeItemKind.BILL) item.recurringId?.let { recurring.get(it)?.amountMinor } ?: item.plannedMinor else item.plannedMinor,
                    digits(),
                ),
                accountId = item.accountId,
                groupId = groupId,
                day = item.nextDue?.dayOfMonth ?: clock.today().dayOfMonth,
                budgetId = item.budgetId,
                recurringId = item.recurringId,
                categoryId = item.categoryId,
                nextDue = item.nextDue,
            )
        }
    }

    fun change(update: (ItemEdit) -> ItemEdit) {
        editing = editing?.let(update)
    }

    /** Decimal places of your currency, read once, so typing updates the field at once and never drops a key. */
    private var fractionDigits = 2

    init {
        viewModelScope.launch { fractionDigits = digits() }
    }

    fun onAmount(text: String) {
        editing = editing?.copy(amountText = AmountInput.clean(text, fractionDigits))
    }

    fun dismiss() {
        editing = null
        error = null
    }

    /**
     * The category a name means (see [CategoryNames]); a hidden match is brought back so you can pick it when adding a
     * spend. With no match, a new category of that name is made. A catch-all name ("Everyday spending") has none.
     */
    private suspend fun categoryFor(name: String, spending: Boolean): String? {
        val wanted = name.trim()
        if (spending && CategoryNames.isCatchAll(wanted)) return null
        val all = categories.observe().first()
        CategoryNames.resolve(wanted, all)?.let { match ->
            if (match.archived) categories.setArchived(match.id, false)
            return match.id
        }
        categories.addCustom(wanted, null, if (spending) Necessity.DISCRETIONARY else Necessity.ESSENTIAL)
        return categories.observe().first().firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.id
    }

    fun save() {
        val edit = editing ?: return
        viewModelScope.launch {
            error = null
            val amount = AmountInput.toMinor(edit.amountText, digits())
            when {
                edit.name.isBlank() -> error = "Give it a name."
                amount <= 0 -> error = "Enter an amount."
                edit.kind == HomeItemKind.BILL && edit.accountId == null -> error = "Choose the account it is paid from."
                else -> {
                    val key = if (edit.kind == HomeItemKind.BILL) saveBill(edit, amount) else saveEnvelope(edit, amount)
                    if (key != null) {
                        groups.place(key, edit.groupId)
                        editing = null
                    }
                }
            }
        }
    }

    private suspend fun saveEnvelope(edit: ItemEdit, amount: Long): String? {
        val before = budgets.observe().first().map { it.id }.toSet()
        val category = edit.categoryId ?: categoryFor(edit.name, spending = true)
        val existing = edit.budgetId?.let { budgets.get(it) }
        val userSettings = settings.observe().first()
        val shown = edit.cycleStart ?: CycleSchedule.containing(clock.today(), userSettings).start
        // A new item starts in the cycle you are looking at; an existing one keeps its start.
        val startOn = if (existing == null) shown else existing.startOn
        val lastCycle = com.ledgerline.app.finance.cycle.CycleAmounts.lastCycleStart(startOn ?: shown, edit.end, userSettings)
        // "This cycle only" leaves the usual amount alone and gives just this cycle its own.
        val oneCycle = existing != null && edit.thisCycleOnly
        val result = budgets.save(
            BudgetDraft(
                existingId = edit.budgetId,
                name = edit.name.trim(),
                categoryId = category,
                period = if (oneCycle) existing!!.period else BudgetPeriod.PAY_CYCLE,
                amountMinor = if (oneCycle) existing!!.amountMinor else amount,
                reservesSafeToSpend = true,
                active = true,
                accountId = edit.accountId,
                setAccount = true,
                setSchedule = true,
                startOn = startOn,
                endOn = lastCycle,
                maxCycles = edit.end.maxOccurrences(),
            ),
        )
        if (result is SaveResult.Invalid) {
            error = result.message
            return null
        }
        val id = edit.budgetId ?: budgets.observe().first().firstOrNull { it.id !in before }?.id ?: return null
        val key = com.ledgerline.app.finance.cycle.CycleAmounts.budgetKey(id)
        if (oneCycle) {
            cyclePlans.setAmount(shown, key, amount.takeIf { it != existing!!.amountMinor })
        } else if (existing != null) {
            // From now on: this cycle follows the new usual amount too.
            cyclePlans.setAmount(shown, key, null)
        }
        return "budget:$id"
    }

    private suspend fun saveBill(edit: ItemEdit, amount: Long): String? {
        val existing = edit.recurringId?.let { recurring.get(it) }
        val subscription = edit.groupId == HomeSections.SUBSCRIPTIONS
        val draft = if (existing != null) {
            RecurringDraft(
                existingId = existing.id,
                name = edit.name.trim(),
                accountId = edit.accountId ?: existing.accountId,
                categoryId = existing.categoryId,
                direction = existing.direction,
                kind = existing.kind,
                // "This cycle only" keeps the usual amount; the one date gets its own below.
                amountMinor = if (edit.thisCycleOnly) existing.amountMinor else amount,
                frequency = existing.frequency,
                intervalDays = existing.intervalDays,
                dayOfMonth = existing.dayOfMonth,
                dayOfWeek = existing.dayOfWeek,
                startOn = existing.startOn,
                priority = existing.priority,
                incomeConfidence = existing.incomeConfidence,
                active = existing.active,
                note = existing.note,
                endOn = edit.end.endOn(existing.startOn),
                maxOccurrences = edit.end.maxOccurrences(),
                autopaySince = existing.autopaySince,
            )
        } else {
            val first = edit.firstDue ?: BillStart.firstDue(edit.day, clock.today(), CycleSchedule.containing(clock.today(), settings.observe().first()))
            val day = first.dayOfMonth
            RecurringDraft(
                existingId = null,
                name = edit.name.trim(),
                accountId = edit.accountId ?: return null,
                categoryId = categoryFor(edit.name, spending = false),
                direction = CashFlowDirection.OUTFLOW,
                kind = if (subscription) ScheduledKind.SUBSCRIPTION else ScheduledKind.BILL,
                amountMinor = amount,
                frequency = edit.frequency,
                intervalDays = null,
                dayOfMonth = day,
                dayOfWeek = first.dayOfWeek.value,
                startOn = first,
                endOn = edit.end.endOn(first),
                maxOccurrences = edit.end.maxOccurrences(),
                priority = if (subscription) PaymentPriority.IMPORTANT else PaymentPriority.MANDATORY,
                incomeConfidence = IncomeConfidence.NONE,
                active = true,
                note = "",
            )
        }
        val before = recurring.observe().first().map { it.id }.toSet()
        val result = recurring.save(draft)
        if (result is SaveResult.Invalid) {
            error = result.message
            return null
        }
        val id = existing?.id ?: recurring.observe().first().firstOrNull { it.id !in before }?.id ?: return null
        if (existing != null && edit.thisCycleOnly && amount != existing.amountMinor) {
            val date = edit.nextDue
            if (date == null) {
                error = "This cycle's payment is already paid. Choose All cycles to change the amount from now on."
                return null
            }
            recurring.setOccurrence(id, date, amount, skipped = false)
        }
        return "bill:$id"
    }

    fun markPaid() {
        val edit = editing ?: return
        val id = edit.recurringId ?: return
        val date = edit.nextDue ?: return
        viewModelScope.launch {
            (recurring.markPaid(id, date) as? SaveResult.Invalid)?.let { error = it.message } ?: run { editing = null }
        }
    }

    /** Removes the item: a bill comes off the calendar, a spending item stops being planned. Past spending stays. */
    fun delete() {
        val edit = editing ?: return
        viewModelScope.launch {
            edit.budgetId?.let { budgets.delete(it) }
            edit.recurringId?.let { recurring.delete(it) }
            edit.key?.let { groups.forget(it) }
            editing = null
        }
    }

    fun createGroup(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { groups.create(name) }
    }

    fun renameGroup(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            // A built-in group shown before any group was saved is created on first rename.
            if (groupList.value.none { it.id == id }) groups.ensureDefaults()
            groups.rename(id, name)
        }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch { groups.delete(id) }
    }
}

/** When a new monthly bill is first due. */
object BillStart {
    /**
     * The date of [day] inside the current pay cycle, even if it has already passed, so a bill added today counts in
     * this pay (tap Paid if it is already paid). If the cycle has no such date (a short cycle), the next one from today.
     */
    fun firstDue(day: Int, today: LocalDate, cycle: PayPeriod): LocalDate {
        val d = day.coerceIn(1, 31)
        fun inMonth(month: LocalDate) = month.withDayOfMonth(minOf(d, month.lengthOfMonth()))
        listOf(inMonth(cycle.start), inMonth(cycle.end)).firstOrNull { cycle.contains(it) }?.let { return it }
        val thisMonth = inMonth(today)
        return if (thisMonth.isBefore(today)) inMonth(today.plusMonths(1)) else thisMonth
    }
}
