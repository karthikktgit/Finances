package com.ledgerline.app.finance.debt

import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtStrategy
import java.time.LocalDate
import java.time.YearMonth

data class DebtPlan(
    val strategy: DebtStrategy,
    val months: Int,
    val interestMinor: Long,
    val payoffOrder: List<String>,
    val unfinished: Boolean,
    val assumption: String,
)

object DebtPlanner {
    const val ASSUMPTION =
        "Payoff order is an estimate. Interest uses a daily periodic rate, APR divided by 365, times the days in each month, " +
            "on the balance before that month's payment. Minimums are paid first. Extra money goes to one debt at a time. " +
            "This is not a lender quote."

    private const val MAX_MONTHS = 360

    fun plan(debts: List<Debt>, extraMonthlyMinor: Long, strategy: DebtStrategy): DebtPlan {
        val extra = extraMonthlyMinor.coerceAtLeast(0)
        val state = debts.filter { it.balanceMinor > 0 }.map {
            Working(
                it.id,
                it.name,
                it.balanceMinor,
                it.aprBasisPoints,
                it.minimumPaymentMinor.coerceAtLeast(0),
                it.aprKnown,
                it.dayCountBasis,
            )
        }.toMutableList()
        if (state.isEmpty()) {
            return DebtPlan(strategy, 0, 0, emptyList(), false, ASSUMPTION)
        }
        val order = ordered(state, strategy)
        var interest = 0L
        var month = 0
        val paid = mutableListOf<String>()
        var cursor = YearMonth.from(LocalDate.of(2026, 1, 1))
        while (state.any { it.balance > 0 } && month < MAX_MONTHS) {
            month += 1
            val days = cursor.lengthOfMonth()
            cursor = cursor.plusMonths(1)
            state.forEach { debt ->
                if (debt.balance <= 0 || !debt.aprKnown) return@forEach
                val charge = InterestMath.onBalance(debt.balance, debt.aprBasisPoints, days, debt.dayCountBasis)
                debt.balance += charge
                interest += charge
            }
            var pool = state.filter { it.balance > 0 }.sumOf { minOf(it.minimum, it.balance) } + extra
            val sequence = ordered(state.filter { it.balance > 0 }, strategy)
            sequence.forEach { debt ->
                if (pool <= 0 || debt.balance <= 0) return@forEach
                val minimum = minOf(debt.minimum, debt.balance, pool)
                debt.balance -= minimum
                pool -= minimum
            }
            val focus = ordered(state.filter { it.balance > 0 }, strategy)
            focus.forEach { debt ->
                if (pool <= 0 || debt.balance <= 0) return@forEach
                val payment = minOf(pool, debt.balance)
                debt.balance -= payment
                pool -= payment
                if (debt.balance == 0L && debt.name !in paid) paid += debt.name
            }
            state.filter { it.balance == 0L && it.name !in paid }.forEach { paid += it.name }
        }
        return DebtPlan(
            strategy = strategy,
            months = month,
            interestMinor = interest,
            payoffOrder = if (paid.isNotEmpty()) paid else order.map { it.name },
            unfinished = state.any { it.balance > 0 },
            assumption = ASSUMPTION,
        )
    }

    private fun ordered(debts: List<Working>, strategy: DebtStrategy): List<Working> = when (strategy) {
        DebtStrategy.AVALANCHE -> debts.sortedWith(compareByDescending<Working> { it.aprBasisPoints }.thenBy { it.balance })
        DebtStrategy.SNOWBALL -> debts.sortedWith(compareBy<Working> { it.balance }.thenByDescending { it.aprBasisPoints })
        DebtStrategy.CUSTOM -> debts
    }

    private class Working(
        val id: String,
        val name: String,
        var balance: Long,
        val aprBasisPoints: Int,
        val minimum: Long,
        val aprKnown: Boolean,
        val dayCountBasis: Int,
    )
}
