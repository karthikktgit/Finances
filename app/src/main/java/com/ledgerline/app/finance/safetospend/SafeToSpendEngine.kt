package com.ledgerline.app.finance.safetospend

import com.ledgerline.app.domain.model.SafeToSpendLine
import com.ledgerline.app.domain.model.SafeToSpendResult

/**
 * Safe to spend is not the account balance.
 *
 * ```
 * safeToSpend =
 *     liquid balance
 *     - committed payments inside the horizon
 *     - minimum buffer
 *     - planned transfers out of liquid accounts
 *     + expected income the user marked high or medium confidence
 *     - remaining discretionary budget reserved by the user
 * ```
 *
 * Amounts are minor currency units. Inputs that represent money leaving the
 * liquid pool are stored as positive numbers and subtracted here.
 * Low-confidence income is excluded. Flexible and optional payments are excluded.
 * Nothing in this result is a guarantee.
 */
object SafeToSpendEngine {
    const val DISCLAIMER =
        "Estimated from the accounts, scheduled items, budgets, and buffer stored on this device. " +
            "Based on current information. This is not a guarantee."

    data class Input(
        val liquidBalanceMinor: Long,
        val committedPaymentsMinor: Long,
        val minimumBufferMinor: Long,
        val plannedTransfersMinor: Long,
        val expectedIncomeMinor: Long,
        val plannedDiscretionaryMinor: Long,
    )

    fun calculate(input: Input): SafeToSpendResult {
        val lines = listOf(
            SafeToSpendLine("Current liquid balance", input.liquidBalanceMinor),
            SafeToSpendLine("Committed payments", -nonNegative(input.committedPaymentsMinor)),
            SafeToSpendLine("Minimum buffer", -nonNegative(input.minimumBufferMinor)),
            SafeToSpendLine("Planned transfers", -nonNegative(input.plannedTransfersMinor)),
            SafeToSpendLine("Expected income", nonNegative(input.expectedIncomeMinor)),
            SafeToSpendLine("Planned discretionary spending", -nonNegative(input.plannedDiscretionaryMinor)),
        )
        return SafeToSpendResult(
            amountMinor = lines.sumOf { it.minor },
            lines = lines,
            disclaimer = DISCLAIMER,
        )
    }

    private fun nonNegative(value: Long): Long = value.coerceAtLeast(0)
}
