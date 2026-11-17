package com.ledgerline.app.finance.calendar

import com.ledgerline.app.domain.model.GoalContribution
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.goals.GoalProgress
import com.ledgerline.app.finance.period.CycleSchedule
import java.time.LocalDate

enum class GoalDayKind {
    /** The first day of a pay cycle: what the goal asks you to put aside that cycle. */
    SET_ASIDE,

    /** Money you put toward the goal that day. */
    SAVED,

    /** The date you want to have the goal by. */
    TARGET,
}

data class GoalDay(
    val goalId: String,
    val name: String,
    val kind: GoalDayKind,
    val amountMinor: Long,
    val savedMinor: Long,
    val targetMinor: Long,
)

/** Savings goals on the calendar: each cycle's set-aside on its first day, money saved, and target dates. */
object GoalCalendar {
    fun days(
        goals: List<GoalProgress>,
        contributions: List<GoalContribution>,
        settings: UserSettings,
        from: LocalDate,
        to: LocalDate,
        /** Each cycle's own saving by cycle start and goal id, where one was set; otherwise the goal's usual plan. */
        cyclePlan: (LocalDate, String) -> Long? = { _, _ -> null },
    ): Map<LocalDate, List<GoalDay>> {
        if (from > to) return emptyMap()
        val out = mutableMapOf<LocalDate, MutableList<GoalDay>>()
        fun add(date: LocalDate, day: GoalDay) = out.getOrPut(date) { mutableListOf() }.add(day)
        val open = goals.filter { !it.goal.archived && !it.goal.closed }
        // Every cycle start in the range, while the goal is still being saved for.
        var cycle = CycleSchedule.containing(from, settings)
        while (!cycle.start.isAfter(to)) {
            if (!cycle.start.isBefore(from)) {
                open.forEach { row ->
                    val beforeTarget = row.goal.targetDate?.let { !cycle.start.isAfter(it) } ?: true
                    val amount = cyclePlan(cycle.start, row.goal.id) ?: row.planMonthlyMinor
                    if (!row.reached && beforeTarget && amount != null && amount > 0) {
                        add(cycle.start, GoalDay(row.goal.id, row.goal.name, GoalDayKind.SET_ASIDE, amount, row.savedMinor, row.goal.targetMinor))
                    }
                }
            }
            cycle = CycleSchedule.next(cycle, settings)
        }
        val byId = open.associateBy { it.goal.id }
        contributions.filter { it.amountMinor != 0L && !it.onDate.isBefore(from) && !it.onDate.isAfter(to) }.forEach { c ->
            val row = byId[c.goalId] ?: return@forEach
            add(c.onDate, GoalDay(row.goal.id, row.goal.name, GoalDayKind.SAVED, c.amountMinor, row.savedMinor, row.goal.targetMinor))
        }
        open.forEach { row ->
            val date = row.goal.targetDate ?: return@forEach
            if (!date.isBefore(from) && !date.isAfter(to)) {
                add(date, GoalDay(row.goal.id, row.goal.name, GoalDayKind.TARGET, row.remainingMinor, row.savedMinor, row.goal.targetMinor))
            }
        }
        return out
    }
}
