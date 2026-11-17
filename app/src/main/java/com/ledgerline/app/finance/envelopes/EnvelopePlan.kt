package com.ledgerline.app.finance.envelopes

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.calendar.BillEntry
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.category.SpendingRules
import com.ledgerline.app.finance.goals.GoalProgress
import com.ledgerline.app.finance.period.PayPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class EnvelopeKind { BILLS, SPENDING, SAVINGS }

/** How an envelope is doing against the time gone in the pay cycle. */
enum class EnvelopeHealth { EMPTY_PLAN, ON_TRACK, FAST, OVER, DONE }

/** One bill inside the Bills envelope. */
data class EnvelopeBill(
    val key: String,
    val name: String,
    val date: LocalDate,
    val amountMinor: Long,
    val paid: Boolean,
    val overdue: Boolean,
    val accountId: String?,
    val recurringId: String?,
    val kind: com.ledgerline.app.domain.model.ScheduledKind = com.ledgerline.app.domain.model.ScheduledKind.BILL,
    val debtId: String? = null,
    /** What was actually paid, when it is paid and the payment is recorded. Can differ from [amountMinor]. */
    val paidMinor: Long? = null,
) {
    /** What this date has taken so far: the real payment if there is one, the bill's amount if paid without one. */
    val spentMinor: Long get() = if (paid) paidMinor ?: amountMinor else 0L
    /** Which Home item this bill belongs to: the schedule or debt, so every date of it shows as one item. */
    val itemKey: String get() = recurringId?.let { "bill:$it" } ?: debtId?.let { "debt:$it" } ?: key
}

/** Money given one job this pay cycle: what was put in, what has gone, and what is left. */
data class Envelope(
    val id: String,
    val name: String,
    val kind: EnvelopeKind,
    /** The account this money is kept in or paid from. Null means any account. */
    val accountId: String?,
    val categoryId: String?,
    val plannedMinor: Long,
    val spentMinor: Long,
    /** The part of [spentMinor] paid from an account other than the envelope's own. */
    val spentOtherMinor: Long = 0,
    /** What went out of each account, for rings coloured by account. */
    val spentByAccount: Map<String, Long> = emptyMap(),
    /** The transactions counted in this envelope this cycle. */
    val transactionIds: List<String> = emptyList(),
    val bills: List<EnvelopeBill> = emptyList(),
    /** For a goal envelope, the goal it saves toward. */
    val goalId: String? = null,
    /** The budget this envelope is stored as, when it is a spending envelope. */
    val budgetId: String? = null,
    /** Where spending should be by today if it is spread evenly over the cycle. */
    val expectedByTodayMinor: Long = 0,
    val daysLeft: Long = 1,
) {
    /** Can be below zero when more went out than was put in. */
    val leftMinor: Long get() = plannedMinor - spentMinor
    val overMinor: Long get() = (spentMinor - plannedMinor).coerceAtLeast(0)
    val fraction: Float get() = if (plannedMinor > 0) (spentMinor.toFloat() / plannedMinor).coerceIn(0f, 1f) else 0f

    /** An even share of what is left for each remaining day, for spending envelopes. */
    val perDayMinor: Long get() = if (leftMinor <= 0 || daysLeft <= 0) 0 else leftMinor / daysLeft

    val health: EnvelopeHealth
        get() = when {
            plannedMinor <= 0 -> if (spentMinor > 0) EnvelopeHealth.OVER else EnvelopeHealth.EMPTY_PLAN
            spentMinor > plannedMinor -> EnvelopeHealth.OVER
            kind != EnvelopeKind.SPENDING && spentMinor >= plannedMinor -> EnvelopeHealth.DONE
            // Spending ahead of the calendar by more than a tenth of the envelope is going too fast.
            kind == EnvelopeKind.SPENDING && spentMinor > expectedByTodayMinor + plannedMinor / 10 -> EnvelopeHealth.FAST
            else -> EnvelopeHealth.ON_TRACK
        }
}

/** One account seen through the plan: what it holds, what is still to come out of it, and what is truly free. */
data class AccountPlan(
    val account: Account,
    val balanceMinor: Long,
    /** Unpaid bills paid from this account this cycle. */
    val billsDueMinor: Long,
    /** Money still left in envelopes kept in this account. */
    val envelopesLeftMinor: Long,
    /** Goal savings still to put aside from this account this cycle. */
    val savingsDueMinor: Long,
    val envelopes: List<Envelope>,
    /** The first unpaid bill from this account, so a shortfall can say when it matters. */
    val nextDue: EnvelopeBill?,
    /**
     * Counted in the spending totals (a Safe to spend account, or one holding a spending item). Other accounts, such
     * as a credit card, still show as a ring but do not add to "free until payday".
     */
    val counted: Boolean = true,
    /** Money already saved for goals kept in this account: it is still here, held for them. */
    val savedGoals: List<Pair<String, Long>> = emptyList(),
) {
    val goalsSavedMinor: Long get() = savedGoals.sumOf { it.second }

    val spokenForMinor: Long get() = billsDueMinor + envelopesLeftMinor.coerceAtLeast(0) + savingsDueMinor + goalsSavedMinor

    /** Not given any job. Below zero means the account is short for what was planned out of it. */
    val freeMinor: Long get() = balanceMinor - spokenForMinor
}

/** The whole pay cycle as envelopes. */
data class PayPlan(
    val cycle: PayPeriod,
    val today: LocalDate,
    /** The pay this plan splits: your own figure, or income on the calendar for the cycle. */
    val incomeMinor: Long,
    val incomeFromCalendar: Boolean,
    val incomeReceivedMinor: Long,
    /** Income on the calendar for this cycle, or what came in if that is more. What the pay is when you set none. */
    val calendarIncomeMinor: Long = 0,
    val envelopes: List<Envelope>,
    /** Everyday spending this cycle that does not belong to any envelope. */
    val outsideMinor: Long,
    val outsideCount: Int,
    val accounts: List<AccountPlan>,
    /** Bills and spending envelopes arranged in your Home groups. */
    val sections: List<HomeSection> = emptyList(),
    /** The spends that are in no envelope. */
    val outsideIds: List<String> = emptyList(),
    /** Savings goals as rings. */
    val goalItems: List<HomeItem> = emptyList(),
) {
    val savings: List<Envelope> get() = envelopes.filter { it.kind == EnvelopeKind.SAVINGS }
    val bills: Envelope? get() = envelopes.firstOrNull { it.kind == EnvelopeKind.BILLS }
    val plannedMinor: Long get() = envelopes.sumOf { it.plannedMinor }

    /** Pay not given a job yet. Below zero means more is planned than comes in. */
    val unassignedMinor: Long get() = incomeMinor - plannedMinor
    /** Everyday spending this cycle, in envelopes or not. Bills and savings are counted separately. */
    val spentMinor: Long get() = envelopes.filter { it.kind == EnvelopeKind.SPENDING }.sumOf { it.spentMinor } + outsideMinor
    val stillToPayMinor: Long get() = bills?.bills?.filter { !it.paid }?.sumOf { it.amountMinor } ?: 0L

    /**
     * What is left of the pay for everyday spending until payday: the pay, less every bill and this cycle's savings,
     * less what has been spent. Envelopes only divide this up; they do not change it.
     */
    val leftOfPayMinor: Long get() = incomeMinor - billsCostMinor - envelopes.filter { it.kind == EnvelopeKind.SAVINGS }.sumOf { it.plannedMinor } - spentMinor

    /** Bills this cycle as they really cost: what was paid for paid ones, the amount due for the rest. */
    val billsCostMinor: Long get() = bills?.bills?.sumOf { if (it.paid) it.spentMinor else it.amountMinor } ?: 0L
    val hasEnvelopes: Boolean get() = envelopes.any { it.kind == EnvelopeKind.SPENDING }

    /** Money left in envelopes and goals not tied to one account. It still has to come from somewhere. */
    val anyAccountLeftMinor: Long get() = envelopes.filter { it.kind != EnvelopeKind.BILLS && it.accountId == null }.sumOf { it.leftMinor.coerceAtLeast(0) }

    /** Bills still to pay that have no account, or an account not shown in the account view. */
    val billsElsewhereMinor: Long get() = bills?.bills?.filter { bill -> !bill.paid && countedAccounts.none { it.account.id == bill.accountId } }?.sumOf { it.amountMinor } ?: 0L

    /** All the plan's accounts together: their balance less everything still planned out of them. */
    val freeOverallMinor: Long get() = countedAccounts.sumOf { it.freeMinor } - anyAccountLeftMinor

    /** The accounts in the spending totals; [accounts] also has the rest (cards, loans, savings) to show. */
    val countedAccounts: List<AccountPlan> get() = accounts.filter { it.counted }
    val daysLeft: Long get() = (ChronoUnit.DAYS.between(today, cycle.end) + 1).coerceAtLeast(1)
    val cycleDays: Long get() = ChronoUnit.DAYS.between(cycle.start, cycle.end) + 1
}

object EnvelopePlan {
    /** What a budget puts in its envelope for one pay cycle. Monthly and weekly budgets are scaled to the cycle. */
    fun perCycle(budget: Budget, cycle: PayPeriod): Long {
        val days = ChronoUnit.DAYS.between(cycle.start, cycle.end) + 1
        return when (budget.period) {
            BudgetPeriod.PAY_CYCLE -> budget.amountMinor
            // A monthly cycle is a month, whatever its length.
            BudgetPeriod.MONTHLY -> if (days in 28..31) budget.amountMinor else (budget.amountMinor * days * 12 + 182) / 365
            BudgetPeriod.WEEKLY -> (budget.amountMinor * days + 3) / 7
        }
    }

    /** Net spending in a set of rows: expenses less refunds, never below zero. */
    private fun net(rows: List<LedgerTransaction>): Long =
        (rows.filter { SpendingRules.countsAsSpending(it) }.sumOf { it.amountMinor } - rows.filter { it.type == TransactionType.REFUND }.sumOf { it.amountMinor })
            .coerceAtLeast(0)

    /**
     * Which envelope each everyday spend belongs to, so nothing is counted twice. Category comes first: an envelope for
     * the spend's category and account, then one for its category from any account, then one for its category kept in
     * a different account. Only then an account's catch-all envelope, then one catch-all for everything. Spending no envelope claims is under the null key. Bill payments are never here: they
     * belong to the Bills envelope.
     */
    /**
     * The category a spending item counts: its own, or the one its name means ("Rent" counts Rent). Null only for a
     * catch-all ("Everyday spending") or a name that matches no category.
     */
    fun effectiveCategory(budget: Budget, categories: List<Category>): String? =
        budget.categoryId ?: com.ledgerline.app.finance.category.CategoryNames.resolve(budget.name, categories)?.id

    fun owners(budgets: List<Budget>, categories: List<Category>, rows: List<LedgerTransaction>): Map<String?, List<LedgerTransaction>> {
        val scopes = budgets.associate { it.id to effectiveCategory(it, categories)?.let { id -> CategoryTree.scopeIds(id, categories) } }
        // Only an item named as a catch-all takes spending outside every category. Anything else with no category
        // (a name that matches nothing) takes nothing on its own; record its payments from its ring.
        val catchAll = budgets.filter { scopes[it.id] == null && com.ledgerline.app.finance.category.CategoryNames.isCatchAll(it.name) }
        fun owner(tx: LedgerTransaction): String? {
            val inCategory = budgets.filter { scopes[it.id]?.contains(tx.categoryId) == true }
            return inCategory.firstOrNull { it.accountId == tx.accountId }?.id
                ?: inCategory.firstOrNull { it.accountId == null }?.id
                // Paid from another account, it is still that category's spending.
                ?: inCategory.firstOrNull()?.id
                ?: catchAll.firstOrNull { it.accountId == tx.accountId }?.id
                ?: catchAll.firstOrNull { it.accountId == null }?.id
        }
        return rows
            .filter { BalanceMath.isActive(it) && it.recurringPaymentId == null && (SpendingRules.countsAsSpending(it) || it.type == TransactionType.REFUND) }
            .groupBy { owner(it) }
    }

    /** Spending this cycle in each pay-cycle envelope, as Safe to spend counts it. */
    fun spentByBudget(budgets: List<Budget>, categories: List<Category>, transactions: List<LedgerTransaction>, cycle: PayPeriod): Map<String, Long> {
        val owned = owners(budgets, categories, transactions.filter { cycle.contains(it.occurredOn) })
        return budgets.associate { it.id to net(owned[it.id].orEmpty()) }
    }

    /**
     * What you spent last cycle on what an envelope would hold, as a realistic starting amount. A catch-all envelope
     * (no category) gets all everyday spending, so it is an upper bound.
     */
    fun lastCycleSpent(categoryId: String?, accountId: String?, transactions: List<LedgerTransaction>, categories: List<Category>, previous: PayPeriod): Long {
        val scope = categoryId?.let { CategoryTree.scopeIds(it, categories) }
        return net(
            transactions.filter { tx ->
                BalanceMath.isActive(tx) && previous.contains(tx.occurredOn) && tx.recurringPaymentId == null &&
                    (scope == null || tx.categoryId in scope) && (accountId == null || tx.accountId == accountId)
            },
        )
    }

    fun build(
        cycle: PayPeriod,
        today: LocalDate,
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        categories: List<Category>,
        budgets: List<Budget>,
        bills: List<BillEntry>,
        goals: List<GoalProgress>,
        incomeOverrideMinor: Long?,
        currency: String,
        groups: List<com.ledgerline.app.domain.model.HomeGroup> = emptyList(),
        placements: List<com.ledgerline.app.domain.model.HomeGroupItem> = emptyList(),
    ): PayPlan {
        val live = accounts.filter { it.active && !it.deleted }
        val liquid = live.filter { it.includeInSafeToSpend }.map { it.id }.toSet()
        val active = transactions.filter { BalanceMath.isActive(it) && it.currency == currency }
        val inCycle = active.filter { cycle.contains(it.occurredOn) }
        val total = ChronoUnit.DAYS.between(cycle.start, cycle.end) + 1
        val elapsed = (ChronoUnit.DAYS.between(cycle.start, today) + 1).coerceIn(0, total)
        val daysLeft = (ChronoUnit.DAYS.between(today, cycle.end) + 1).coerceIn(1, total)

        // This cycle's bills, plus any left unpaid from before it: they still have to come out of this pay.
        val cycleBills = bills.filter { !it.incoming && !it.transfer && (cycle.contains(it.date) || (it.overdue && !it.paid && it.date.isBefore(cycle.start))) }
        val billEnvelope = cycleBills.takeIf { it.isNotEmpty() }?.let { rows ->
            // The real payment for each paid date, so a bill paid at a different amount shows what was paid.
            val payments = active.filter { it.recurringPaymentId != null && it.balanceEffectMinor < 0 }.associateBy { it.recurringPaymentId to it.occurredOn }
            val items = rows.map {
                EnvelopeBill(
                    it.key, it.name, it.date, it.amountMinor ?: 0L, it.paid, it.overdue, it.accountId, it.recurringId, it.kind, it.debtId,
                    paidMinor = if (it.paid) payments[it.recurringId to it.date]?.amountMinor else null,
                )
            }.sortedWith(compareBy({ it.paid }, { it.date }))
            Envelope(
                id = "bills",
                name = "Bills",
                kind = EnvelopeKind.BILLS,
                accountId = null,
                categoryId = null,
                plannedMinor = items.sumOf { it.amountMinor },
                spentMinor = items.sumOf { it.spentMinor },
                bills = items,
                daysLeft = daysLeft,
            )
        }

        val envelopeBudgets = budgets.filter { it.active && it.currency == currency }
        val owned = owners(envelopeBudgets, categories, inCycle)
        val spending = envelopeBudgets.map { budget ->
            val planned = perCycle(budget, cycle)
            val rows = owned[budget.id].orEmpty()
            val spent = net(rows)
            val own = budget.accountId?.let { id -> net(rows.filter { it.accountId == id }) } ?: spent
            val byAccount = rows.groupBy { it.accountId }.mapValues { (_, list) -> net(list) }.filterValues { it > 0 }
            Envelope(
                id = "budget-${budget.id}",
                name = budget.name.ifBlank { budget.categoryId?.let { CategoryTree.label(it, categories) } ?: "Everyday spending" },
                kind = EnvelopeKind.SPENDING,
                accountId = budget.accountId?.takeIf { id -> live.any { it.id == id } },
                categoryId = effectiveCategory(budget, categories),
                plannedMinor = planned,
                spentMinor = spent,
                spentOtherMinor = (spent - own).coerceAtLeast(0),
                spentByAccount = byAccount,
                transactionIds = rows.map { it.id },
                budgetId = budget.id,
                expectedByTodayMinor = if (total > 0) planned * elapsed / total else 0,
                daysLeft = daysLeft,
            )
        }.sortedByDescending { it.plannedMinor }

        val saving = goals.filter { !it.reached && !it.goal.closed && (it.savedThisCycleMinor + it.setAsideNowMinor) > 0 }.map { goal ->
            Envelope(
                id = "goal-${goal.goal.id}",
                name = goal.goal.name,
                kind = EnvelopeKind.SAVINGS,
                accountId = goal.goal.accountId,
                categoryId = null,
                plannedMinor = goal.savedThisCycleMinor + goal.setAsideNowMinor,
                spentMinor = goal.savedThisCycleMinor,
                goalId = goal.goal.id,
                daysLeft = daysLeft,
            )
        }

        val outside = owned[null].orEmpty().filter { it.accountId in liquid && SpendingRules.countsAsSpending(it) }

        val calendarIncome = bills.filter { it.incoming && !it.transfer && cycle.contains(it.date) }.sumOf { it.amountMinor ?: 0L }
        val received = inCycle.filter { it.type == TransactionType.INCOME && it.accountId in live.map { a -> a.id } }.sumOf { it.amountMinor }
        val fromCalendar = maxOf(calendarIncome, received)
        val income = incomeOverrideMinor?.takeIf { it > 0 } ?: fromCalendar

        val envelopes = listOfNotNull(billEnvelope) + spending + saving
        // Every account shows on Home; only spending ones count toward what is free.
        val accountPlans = live.map { account ->
            val mine = spending.filter { it.accountId == account.id } + saving.filter { it.accountId == account.id }
            val due = cycleBills.filter { !it.paid && it.accountId == account.id }
            AccountPlan(
                account = account,
                balanceMinor = BalanceMath.currentBalanceMinor(account, active),
                billsDueMinor = due.sumOf { it.amountMinor ?: 0L },
                envelopesLeftMinor = mine.filter { it.kind == EnvelopeKind.SPENDING }.sumOf { it.leftMinor.coerceAtLeast(0) },
                savingsDueMinor = mine.filter { it.kind == EnvelopeKind.SAVINGS }.sumOf { it.leftMinor.coerceAtLeast(0) },
                envelopes = mine,
                nextDue = billEnvelope?.bills?.filter { !it.paid && it.accountId == account.id }?.minByOrNull { it.date },
                counted = account.id in liquid || envelopes.any { e -> e.accountId == account.id },
                // Saved for a goal and not yet spent on it: the account still holds that money.
                savedGoals = goals.filter { it.goal.accountId == account.id && !it.goal.closed && it.heldMinor > 0 }.map { it.goal.name to it.heldMinor },
            )
        }

        return PayPlan(
            cycle = cycle,
            today = today,
            incomeMinor = income,
            incomeFromCalendar = incomeOverrideMinor == null || incomeOverrideMinor <= 0,
            incomeReceivedMinor = received,
            calendarIncomeMinor = fromCalendar,
            envelopes = envelopes,
            outsideMinor = net(outside),
            outsideIds = outside.map { it.id },
            outsideCount = outside.size,
            accounts = accountPlans,
            sections = HomeSections.build(listOfNotNull(billEnvelope) + spending, groups, placements),
            // A reached goal leaves Home after the cycle it was reached in.
            goalItems = goals.filter { com.ledgerline.app.finance.goals.GoalTimeline.showsIn(it, cycle) }.map { HomeSections.goalItem(it) },
        )
    }
}

/** Starting points for envelopes, from the categories you actually use. */
object EnvelopePresets {
    data class Preset(val name: String, val categoryId: String?)

    /** Categories that are bills, income, or debt are handled elsewhere, so they are not offered as envelopes. */
    private val notEnvelopes = setOf("cat_income", "cat_debt", "cat_monthly")

    /**
     * Spending categories you could give money to, most used first, with a catch-all "Everyday spending" at the end.
     * Categories that already have an envelope are left out.
     */
    fun offer(categories: List<Category>, transactions: List<LedgerTransaction>, taken: Set<String?>): List<Preset> {
        val usage = transactions.filter { BalanceMath.isActive(it) && it.type == TransactionType.EXPENSE }.groupingBy { it.categoryId }.eachCount()
        val excluded = categories.filter { it.id in notEnvelopes || it.parentId in notEnvelopes }.map { it.id }.toSet()
        val parents = categories.mapNotNull { it.parentId }.toSet()
        val leaves = categories
            .filter { !it.archived && it.id !in excluded && it.id !in parents && it.id !in taken }
            .sortedWith(compareByDescending<Category> { usage[it.id] ?: 0 }.thenBy { it.sortOrder })
            .map { Preset(it.name.replace(" / ", " & "), it.id) }
        return if (null in taken) leaves else leaves + Preset("Everyday spending", null)
    }
}

enum class HomeItemKind { BILL, ENVELOPE, GOAL }

/** One ring on Home: a bill (all its dates this cycle together) or a spending envelope. */
data class HomeItem(
    /** "bill:<id>", "debt:<id>", or "budget:<id>". */
    val key: String,
    val name: String,
    val kind: HomeItemKind,
    val plannedMinor: Long,
    /** Paid so far for a bill; spent so far for an envelope. */
    val spentMinor: Long,
    /** The part of [spentMinor] paid from an account other than the item's own. */
    val spentOtherMinor: Long = 0,
    /** What went out of (or, for a goal, into) each account; the ring is drawn in these accounts' colours. */
    val spentByAccount: Map<String, Long> = emptyMap(),
    /** The transactions counted in it this cycle (spending items). Bills find theirs by [recurringId]. */
    val transactionIds: List<String> = emptyList(),
    val accountId: String? = null,
    val goalId: String? = null,
    /** For a goal: what this month's plan still needs. */
    val dueThisCycleMinor: Long = 0,
    val budgetId: String? = null,
    val recurringId: String? = null,
    val categoryId: String? = null,
    /** The first unpaid date of a bill. */
    val nextDue: LocalDate? = null,
    val overdue: Boolean = false,
    val scheduledKind: com.ledgerline.app.domain.model.ScheduledKind? = null,
    /** For a bill: every date of it this cycle is paid, whatever amount was paid. */
    val settled: Boolean = false,
) {
    val leftMinor: Long get() = plannedMinor - spentMinor
    val overMinor: Long get() = (spentMinor - plannedMinor).coerceAtLeast(0)
    val paid: Boolean get() = when (kind) {
        HomeItemKind.BILL -> settled && plannedMinor > 0
        HomeItemKind.GOAL -> settled || (plannedMinor > 0 && spentMinor >= plannedMinor)
        HomeItemKind.ENVELOPE -> false
    }

    /** Ring segments in order: each account's share of the planned amount, never more than a full ring. */
    val segments: List<Pair<String, Float>>
        get() {
            if (plannedMinor <= 0) return emptyList()
            var used = 0f
            return spentByAccount.entries.sortedByDescending { it.value }.mapNotNull { (account, minor) ->
                val share = (minor.toFloat() / plannedMinor).coerceAtMost(1f - used)
                used += share
                if (share > 0f) account to share else null
            }
        }

    /** How full the ring is from the item's own account, and from other accounts, as parts of the planned amount. */
    val ownFraction: Float get() = if (plannedMinor > 0) ((spentMinor - spentOtherMinor).toFloat() / plannedMinor).coerceIn(0f, 1f) else 0f
    val otherFraction: Float get() = if (plannedMinor > 0) (spentOtherMinor.toFloat() / plannedMinor).coerceIn(0f, 1f - ownFraction) else 0f
}

/** A Home group with its items and totals. */
data class HomeSection(val id: String, val name: String, val items: List<HomeItem>) {
    val plannedMinor: Long get() = items.sumOf { it.plannedMinor }
    val spentMinor: Long get() = items.sumOf { it.spentMinor }
    val leftMinor: Long get() = items.sumOf { it.leftMinor.coerceAtLeast(0) }
}

object HomeSections {
    const val MONTHLY = "grp_monthly"
    const val SUBSCRIPTIONS = "grp_subscriptions"
    const val MONTHLY_NAME = "Monthly expenses"

    /**
     * Puts each bill and spending envelope in its group. Something never placed goes where it most likely belongs:
     * subscriptions to "Subscriptions" when that group exists, everything else to the first group. With no groups at
     * all, everything is in one "Monthly" group. Empty groups still show, so you can add to them.
     */
    fun build(
        envelopes: List<Envelope>,
        groups: List<com.ledgerline.app.domain.model.HomeGroup>,
        placements: List<com.ledgerline.app.domain.model.HomeGroupItem>,
    ): List<HomeSection> {
        val ordered = groups.sortedWith(compareBy({ it.sortOrder }, { it.name })).ifEmpty { listOf(com.ledgerline.app.domain.model.HomeGroup(MONTHLY, MONTHLY_NAME, 0)) }
        val ids = ordered.map { it.id }.toSet()
        val placed = placements.filter { it.groupId in ids }.associate { it.itemKey to it.groupId }
        val items = items(envelopes)
        fun home(item: HomeItem): String = placed[item.key]
            ?: if (item.scheduledKind == com.ledgerline.app.domain.model.ScheduledKind.SUBSCRIPTION && SUBSCRIPTIONS in ids) SUBSCRIPTIONS else ordered.first().id
        val byGroup = items.groupBy { home(it) }
        return ordered.map { group ->
            HomeSection(group.id, group.name, byGroup[group.id].orEmpty().sortedByDescending { it.plannedMinor })
        }
    }

    /**
     * A goal as a ring. With a plan this cycle the ring is this month's saving (planned, put aside so far); with none it
     * is the whole goal, so every goal always has something to show.
     */
    fun goalItem(progress: com.ledgerline.app.finance.goals.GoalProgress): HomeItem {
        val goal = progress.goal
        val cyclePlan = progress.savedThisCycleMinor + progress.setAsideNowMinor
        val monthly = progress.planMonthlyMinor != null && cyclePlan > 0 && !progress.reached
        return HomeItem(
            key = "goal:${goal.id}",
            name = goal.name,
            kind = HomeItemKind.GOAL,
            plannedMinor = if (monthly) cyclePlan else goal.targetMinor,
            spentMinor = if (monthly) progress.savedThisCycleMinor else progress.savedMinor,
            spentByAccount = goal.accountId?.let { mapOf(it to if (monthly) progress.savedThisCycleMinor else progress.savedMinor) }.orEmpty().filterValues { it > 0 },
            accountId = goal.accountId,
            goalId = goal.id,
            dueThisCycleMinor = progress.setAsideNowMinor,
            // Reached (or finished): a tick with what was saved.
            settled = progress.reached || goal.closed,
        )
    }

    /** Bills become one item per schedule (all its dates this cycle); each spending envelope is one item. */
    fun items(envelopes: List<Envelope>): List<HomeItem> {
        val bills = envelopes.filter { it.kind == EnvelopeKind.BILLS }.flatMap { it.bills }.groupBy { it.itemKey }.map { (key, rows) ->
            val unpaid = rows.filter { !it.paid }
            HomeItem(
                key = key,
                name = rows.first().name,
                kind = HomeItemKind.BILL,
                plannedMinor = rows.sumOf { it.amountMinor },
                spentMinor = rows.sumOf { it.spentMinor },
                spentByAccount = rows.filter { it.paid && it.accountId != null }.groupBy { it.accountId!! }.mapValues { (_, l) -> l.sumOf { it.spentMinor } },
                settled = rows.all { it.paid },
                accountId = rows.first().accountId,
                recurringId = rows.first().recurringId,
                nextDue = unpaid.minOfOrNull { it.date },
                overdue = unpaid.any { it.overdue },
                scheduledKind = rows.first().kind,
            )
        }
        val spending = envelopes.filter { it.kind == EnvelopeKind.SPENDING }.map { envelope ->
            HomeItem(
                key = "budget:${envelope.budgetId}",
                name = envelope.name,
                kind = HomeItemKind.ENVELOPE,
                plannedMinor = envelope.plannedMinor,
                spentMinor = envelope.spentMinor,
                spentOtherMinor = envelope.spentOtherMinor,
                spentByAccount = envelope.spentByAccount,
                transactionIds = envelope.transactionIds,
                accountId = envelope.accountId,
                budgetId = envelope.budgetId,
                categoryId = envelope.categoryId,
            )
        }
        return bills + spending
    }
}
