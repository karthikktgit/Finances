package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.insights.InsightAverages
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Average balance kept, spending a day and a month, and each category's usual month. */
class InsightAveragesTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val chk = Account("chk", "Checking", "", AccountType.CHECKING, null, 100_000, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0)
    private val cats = listOf(
        Category("food", null, "Food", Necessity.DISCRETIONARY, false, 1),
        Category("coffee", "food", "Coffee", Necessity.DISCRETIONARY, false, 2),
        Category("rent", null, "Rent", Necessity.ESSENTIAL, false, 3),
    )

    @Test
    fun averagesCoverOnlyTheDaysSinceTheFirstRecord() {
        // Records start on Nov 1: 10 days. Spend 100 on the 1st (coffee), 200 on the 6th (rent), refund 20 on the 6th.
        val txs = listOf(
            tx(LocalDate.of(2026, 11, 1), 10_000, "coffee"),
            tx(LocalDate.of(2026, 11, 6), 20_000, "rent"),
            tx(LocalDate.of(2026, 11, 6), 2_000, "rent", TransactionType.REFUND),
        )
        val a = InsightAverages.compute(today, listOf(chk), txs, cats)
        assertEquals(10, a.days)
        // Balance: 900 for 5 days (1st-5th), then 720 for 5 days (6th-10th): average 810.
        assertEquals(81_000L, a.balanceMinor)
        // 280 net over 10 days.
        assertEquals(2_800L, a.perDayMinor)
        // Categories roll up: Coffee counts as Food. This month equals the whole window here.
        assertEquals(listOf("Rent", "Food"), a.categories.map { it.name })
        assertEquals(18_000L, a.categories.first().thisMonthMinor)
        assertEquals(10_000L, a.categories.last().thisMonthMinor)
    }

    private fun tx(on: LocalDate, amount: Long, category: String, type: TransactionType = TransactionType.EXPENSE) = LedgerTransaction(
        id = "$on-$amount-$type", accountId = "chk", counterAccountId = null, linkId = null, amountMinor = amount,
        balanceEffectMinor = if (type == TransactionType.REFUND) amount else -amount, currency = "USD", type = type, merchantRaw = "x",
        merchantNormalized = "x", merchantId = null, description = "", categoryId = category, occurredOn = on, postedOn = null,
        detectionSource = DetectionSource.MANUAL, confidencePercent = 100, recurringPaymentId = null, status = TransactionStatus.CONFIRMED,
        userConfirmed = true, note = "", fingerprint = "", createdAtEpochMillis = 0, updatedAtEpochMillis = 0, deletedAtEpochMillis = null,
    )
}
