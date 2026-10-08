package com.ledgerline.app.finance

import com.ledgerline.app.detection.DetectionDisposition
import com.ledgerline.app.detection.TransactionDetectionEngine
import com.ledgerline.app.detection.parser.GenericNotificationParser
import com.ledgerline.app.detection.parser.NotificationData
import com.ledgerline.app.detection.parser.ParserRegistry
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtStrategy
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.anomaly.AnomalyDetector
import com.ledgerline.app.finance.debt.DebtPlanner
import com.ledgerline.app.finance.forecast.CashFlowForecastEngine
import com.ledgerline.app.finance.leak.MoneyLeakEngine
import com.ledgerline.app.finance.recurring.RecurringDetector
import com.ledgerline.app.finance.subscription.SubscriptionRollup
import com.ledgerline.app.finance.whatif.WhatIfEngine
import com.ledgerline.app.finance.whatif.WhatIfRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalysisEngineTest {
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun forecast_appliesScheduleThenDailyDiscretionary() {
        val days = CashFlowForecastEngine.project(
            start = today,
            days = 3,
            openingMinor = 100_000,
            events = listOf(CashFlowForecastEngine.DatedAmount(today.plusDays(1), "bill", "Bill", -4_000)),
            dailyDiscretionaryMinor = 100,
        )
        assertEquals(99_900, days[0].balanceAfterMinor)
        assertEquals(95_800, days[1].balanceAfterMinor)
        assertEquals(95_700, days[2].balanceAfterMinor)
        assertTrue(CashFlowForecastEngine.DISCLAIMER.contains("not a guarantee"))
    }

    @Test
    fun whatIf_expenseLowersTheProjectionWithoutWritingData() {
        val events = emptyList<CashFlowForecastEngine.DatedAmount>()
        val result = WhatIfEngine.evaluate(
            start = today,
            days = 5,
            openingMinor = 10_000,
            events = events,
            dailyDiscretionaryMinor = 0,
            bufferMinor = 1_000,
            request = WhatIfRequest(oneTimeExpenseMinor = 500, oneTimeDate = today),
        )
        assertEquals(10_000, result.baselineEndMinor)
        assertEquals(9_500, result.scenarioEndMinor)
        assertTrue(!result.crossesBuffer)
        val tight = WhatIfEngine.evaluate(
            start = today,
            days = 5,
            openingMinor = 10_000,
            events = events,
            dailyDiscretionaryMinor = 0,
            bufferMinor = 1_000,
            request = WhatIfRequest(oneTimeExpenseMinor = 9_500, oneTimeDate = today),
        )
        assertTrue(tight.crossesBuffer)
        assertTrue(tight.summary.contains("minimum buffer"))
    }

    @Test
    fun parser_extractsThePurchaseAlertAndLeavesUnknownFieldsEmpty() {
        val parsed = GenericNotificationParser().parse(
            NotificationData("com.example.bank", "Purchase alert: $43.29 at Amazon using Visa ending 1234"),
        )
        assertEquals(4_329L, parsed?.amountMinor)
        assertEquals("Amazon", parsed?.merchant)
        assertEquals("1234", parsed?.lastFour)
        assertEquals(TransactionType.EXPENSE, parsed?.type)
        val partial = GenericNotificationParser().parse(
            NotificationData("com.example.bank", "Your debit card was used for $87.20"),
        )
        assertEquals(8_720L, partial?.amountMinor)
        assertNull(partial?.merchant)
        assertNull(partial?.lastFour)
        assertNull(ParserRegistry.parse(NotificationData("com.example.bank", "Your statement is ready")))
    }

    @Test
    fun detection_linksAHighConfidenceDuplicateAndRejectsAMissingAccount() {
        val parsed = GenericNotificationParser().parse(
            NotificationData("com.chase.sig.android", "Purchase alert: $43.29 at Amazon using Visa ending 1234"),
        )!!
        val account = account("card", "1234")
        val linked = TransactionDetectionEngine.decide(parsed, account, duplicateScore = 96)
        assertEquals(DetectionDisposition.LINK, linked.disposition)
        val missing = TransactionDetectionEngine.decide(parsed, null, null)
        assertEquals(DetectionDisposition.REJECT, missing.disposition)
        assertNull(TransactionDetectionEngine.matchAccount(listOf(account, account("other", "1234")), "1234", "USD"))
    }

    @Test
    fun recurringDetector_findsAMonthlyMerchant() {
        val rows = listOf(
            expense("a", LocalDate.of(2026, 1, 5), 1_549),
            expense("b", LocalDate.of(2026, 2, 5), 1_549),
            expense("c", LocalDate.of(2026, 3, 5), 1_549),
        )
        val found = RecurringDetector.detect(rows, today)
        assertEquals(1, found.size)
        assertEquals(RecurrenceFrequency.MONTHLY, found.first().frequency)
        assertEquals(1_549L, found.first().typicalAmountMinor)
    }

    @Test
    fun anomaly_flagsARestaurantChargeFarAboveTheUsualRange() {
        val usual = listOf(1_500L, 2_000L, 1_800L, 2_200L, 1_600L).mapIndexed { index, amount ->
            expense("u$index", today.minusDays(30L + index), amount, categoryId = "restaurants")
        }
        val large = expense("big", today.minusDays(1), 28_000, categoryId = "restaurants", merchant = "Bistro")
        val notes = AnomalyDetector.analyze(today, "USD", listOf(category("restaurants", "Restaurants")), usual + large)
        assertTrue(notes.any { it.detail.contains("pattern comparison") })
    }

    @Test
    fun moneyLeak_countsSmallPurchases() {
        val rows = (1..8).map { expense("s$it", today.minusDays(1), 1_000) }
        val notes = MoneyLeakEngine.analyze(today, "USD", emptyList(), rows, emptyList())
        assertTrue(notes.any { it.detail.contains("8 purchases") })
    }

    @Test
    fun debtPlan_avalancheAndSnowballOrderTheDebtsDifferently() {
        val highApr = debt("A", "Card", 50_000, 2_000, 0)
        val small = debt("B", "Loan", 10_000, 500, 0)
        val avalanche = DebtPlanner.plan(listOf(small, highApr), 1_000, DebtStrategy.AVALANCHE)
        val snowball = DebtPlanner.plan(listOf(small, highApr), 1_000, DebtStrategy.SNOWBALL)
        assertEquals("Card", avalanche.payoffOrder.first())
        assertEquals("Loan", snowball.payoffOrder.first())
        assertTrue(avalanche.assumption.contains("estimate"))
    }

    @Test
    fun subscriptions_annualizeAMonthlyCharge() {
        val netflix = RecurringPayment(
            id = "n",
            name = "Netflix",
            accountId = "card",
            categoryId = null,
            direction = CashFlowDirection.OUTFLOW,
            kind = ScheduledKind.SUBSCRIPTION,
            amountMinor = 1_549,
            currency = "USD",
            frequency = RecurrenceFrequency.MONTHLY,
            intervalDays = null,
            dayOfMonth = 10,
            dayOfWeek = null,
            startOn = today,
            endOn = null,
            priority = PaymentPriority.OPTIONAL,
            incomeConfidence = IncomeConfidence.NONE,
            active = true,
            note = "",
        )
        assertEquals(1_549L, SubscriptionRollup.monthlyMinor(listOf(netflix)))
        assertEquals(1_549L * 12, SubscriptionRollup.annualizedMinor(1_549))
    }

    private fun account(id: String, lastFour: String) = Account(
        id, id, "", AccountType.CREDIT_CARD, lastFour, 0, 300_000, "USD", true, true, false,
        SyncStatus.MANUAL, null, null, 0,
    )

    private fun category(id: String, name: String) = Category(id, null, name, Necessity.DISCRETIONARY, false, 1)

    private fun expense(
        id: String,
        date: LocalDate,
        amount: Long,
        categoryId: String? = null,
        merchant: String = "Netflix",
    ) = LedgerTransaction(
        id = id,
        accountId = "card",
        counterAccountId = null,
        linkId = null,
        amountMinor = amount,
        balanceEffectMinor = -amount,
        currency = "USD",
        type = TransactionType.EXPENSE,
        merchantRaw = merchant,
        merchantNormalized = merchant,
        merchantId = null,
        description = merchant,
        categoryId = categoryId,
        occurredOn = date,
        postedOn = date,
        detectionSource = DetectionSource.MANUAL,
        confidencePercent = 100,
        recurringPaymentId = null,
        status = TransactionStatus.CONFIRMED,
        userConfirmed = true,
        note = "",
        fingerprint = id,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
        deletedAtEpochMillis = null,
    )

    private fun debt(id: String, name: String, balance: Long, apr: Int, minimum: Long) = Debt(
        id, name, null, balance, balance, apr, minimum, 15, RecurrenceFrequency.MONTHLY, PaymentPriority.MANDATORY, "USD",
    )
}
