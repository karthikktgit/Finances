package com.ledgerline.app.finance

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetPace
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Category
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
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.budget.BudgetMath
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.dashboard.DashboardAssembler
import com.ledgerline.app.finance.duplicate.DuplicateCandidate
import com.ledgerline.app.finance.duplicate.DuplicateDetector
import com.ledgerline.app.finance.export.LedgerExport
import com.ledgerline.app.finance.fingerprint.TransactionFingerprint
import com.ledgerline.app.finance.merchant.MerchantNormalizer
import com.ledgerline.app.finance.policy.FinancePolicy
import com.ledgerline.app.finance.safetospend.SafeToSpendEngine
import com.ledgerline.app.finance.schedule.RecurringSchedule
import com.ledgerline.app.finance.schedule.ScheduledBalancePreview
import com.ledgerline.app.finance.schedule.PreviewEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceEngineTest {
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun safeToSpend_usesTheDocumentedFormula() {
        val result = SafeToSpendEngine.calculate(
            SafeToSpendEngine.Input(
                liquidBalanceMinor = 324_000,
                committedPaymentsMinor = 118_000,
                minimumBufferMinor = 50_000,
                plannedTransfersMinor = 0,
                expectedIncomeMinor = 150_000,
                plannedDiscretionaryMinor = 60_000,
            ),
        )
        assertEquals(324_000 - 118_000 - 50_000 + 150_000 - 60_000, result.amountMinor)
        assertEquals(result.amountMinor, result.lines.sumOf { it.minor })
        assertTrue(result.disclaimer.contains("not a guarantee"))
    }

    @Test
    fun safeToSpend_ignoresNegativeInputsThatWouldIncreaseTheResult() {
        val result = SafeToSpendEngine.calculate(
            SafeToSpendEngine.Input(
                liquidBalanceMinor = 10_000,
                committedPaymentsMinor = -5_000,
                minimumBufferMinor = 1_000,
                plannedTransfersMinor = 0,
                expectedIncomeMinor = -9_000,
                plannedDiscretionaryMinor = 0,
            ),
        )
        assertEquals(9_000, result.amountMinor)
    }

    @Test
    fun duplicate_sameAmountAccountDateAndMerchant_isAutomatic() {
        val match = DuplicateDetector.score(candidate(), candidate(id = "other"))
        assertTrue(match.score >= FinancePolicy.DUPLICATE_MERGE_SCORE)
    }

    @Test
    fun duplicate_amountOnly_staysSeparate() {
        val match = DuplicateDetector.score(
            candidate(),
            candidate(id = "other", accountId = "other", date = today.plusDays(10), merchant = "Cafe"),
        )
        assertTrue(match.score < FinancePolicy.DUPLICATE_ASK_SCORE)
    }

    @Test
    fun duplicate_samePurchaseDifferentAccount_asks() {
        val match = DuplicateDetector.bestMatch(
            candidate(lastFour = "1234"),
            listOf(candidate(id = "bank", accountId = "bank", lastFour = "9999", merchant = "Amazon Marketplace")),
        )
        assertTrue(match != null && match.score in FinancePolicy.DUPLICATE_ASK_SCORE until FinancePolicy.DUPLICATE_MERGE_SCORE)
    }

    @Test
    fun merchantNormalization_collapsesKnownAliasesWithoutInventingOthers() {
        assertEquals("Amazon", MerchantNormalizer.normalize("AMZN MKTP"))
        assertEquals("Amazon", MerchantNormalizer.normalize("AMAZON.COM"))
        assertEquals("Uber Eats", MerchantNormalizer.normalize("UBER EATS"))
        assertEquals("", MerchantNormalizer.normalize("   "))
        assertEquals("Corner Cafe", MerchantNormalizer.normalize("CORNER CAFE"))
    }

    @Test
    fun fingerprint_isStableAndIndependentOfRawCasing() {
        val first = TransactionFingerprint.of("usd", 4329, "acct", today, TransactionType.EXPENSE, "Amazon")
        val second = TransactionFingerprint.of("USD", -4329, "acct", today, TransactionType.EXPENSE, " amazon ")
        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun recurringMonthly_clampsShortMonthsAndRespectsTheWindow() {
        val item = recurring(day = 31, start = LocalDate.of(2026, 1, 31))
        val dates = RecurringSchedule.occurrencesBetween(item, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30))
        assertEquals(listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), dates)
    }

    @Test
    fun budgetPace_marksLikelyOverBeforeTheLimitIsCrossed() {
        val result = BudgetMath.evaluate(spentMinor = 8_000, budgetMinor = 20_000, elapsedDays = 8, periodDays = 31)
        assertEquals(12_000, result.remainingMinor)
        assertEquals(BudgetPace.LIKELY_OVER, result.pace)
        assertTrue(result.projectedMinor > 20_000)
    }

    @Test
    fun balance_transfersDoNotChangeNetWorthAndExpensesReduceIt() {
        val checking = account("checking", AccountType.CHECKING, 10_000, liquid = true)
        val savings = account("savings", AccountType.SAVINGS, 5_000, liquid = false)
        val expense = tx("e", "checking", TransactionType.EXPENSE, 2_000, BalanceMath.effectFor(TransactionType.EXPENSE, 2_000))
        val out = tx("out", "checking", TransactionType.TRANSFER, 1_000, -1_000, counter = "savings", link = "l")
        val into = tx("in", "savings", TransactionType.TRANSFER, 1_000, 1_000, counter = "checking", link = "l")
        assertEquals(7_000, BalanceMath.currentBalanceMinor(checking, listOf(expense, out, into)))
        assertEquals(6_000, BalanceMath.currentBalanceMinor(savings, listOf(expense, out, into)))
    }

    @Test
    fun creditAvailable_isLimitMinusAmountOwed() {
        val card = account("card", AccountType.CREDIT_CARD, -6_300, liquid = false).copy(creditLimitMinor = 300_000)
        assertEquals(293_700L, BalanceMath.availableCreditMinor(card, -6_300))
    }

    @Test
    fun dashboard_excludesOptionalItemsAndLowConfidenceIncome() {
        val checking = account("checking", AccountType.CHECKING, 300_000, liquid = true)
        val settings = UserSettings("USD", 50_000, 30, true)
        val rent = recurring(name = "Rent", amount = 100_000, day = 20, start = today.plusDays(12), priority = PaymentPriority.MANDATORY)
        val optional = recurring(
            name = "Stream",
            amount = 1_500,
            day = today.plusDays(3).dayOfMonth,
            start = today.plusDays(3),
            priority = PaymentPriority.OPTIONAL,
            kind = ScheduledKind.SUBSCRIPTION,
        )
        val shaky = recurring(
            name = "Maybe",
            amount = 80_000,
            day = today.plusDays(5).dayOfMonth,
            start = today.plusDays(5),
            direction = CashFlowDirection.INFLOW,
            confidence = IncomeConfidence.LOW,
            kind = ScheduledKind.INCOME,
            priority = PaymentPriority.FLEXIBLE,
        )
        val dashboard = DashboardAssembler.assemble(
            today = today,
            settings = settings,
            accounts = listOf(checking),
            categories = emptyList(),
            transactions = emptyList(),
            recurring = listOf(rent, optional, shaky),
            budgets = emptyList(),
        )
        assertEquals(300_000 - 100_000 - 50_000, dashboard.safeToSpend.amountMinor)
        assertEquals(3, dashboard.upcoming.size)
        assertTrue(dashboard.upcoming.single { it.name == "Stream" }.countedInSafeToSpend.not())
    }

    @Test
    fun categoryScope_includesChildren() {
        val categories = listOf(
            Category("food", null, "Food", Necessity.DISCRETIONARY, false, 1),
            Category("coffee", "food", "Coffee", Necessity.DISCRETIONARY, false, 2),
        )
        assertEquals(setOf("food", "coffee"), CategoryTree.scopeIds("food", categories))
        assertEquals("Food · Coffee", CategoryTree.label("coffee", categories))
    }

    @Test
    fun scheduledPreview_appliesDeltasInDateOrder() {
        val points = ScheduledBalancePreview.project(
            1_000,
            listOf(
                PreviewEvent(today.plusDays(2), "Bill", -400),
                PreviewEvent(today.plusDays(1), "Pay", 200),
            ),
        )
        assertEquals(1_200, points[0].balanceAfterMinor)
        assertEquals(800, points[1].balanceAfterMinor)
    }

    @Test
    fun csvEscapesFormulaInjection() {
        assertTrue(LedgerExport.csvCell("=cmd").startsWith("\"'="))
        val csv = LedgerExport.csv(listOf(tx("1", "checking", TransactionType.EXPENSE, 100, -100, merchant = "=evil")), emptyList(), emptyList())
        assertTrue(csv.contains("'=evil"))
    }

    @Test
    fun deletedTransaction_doesNotMatchAsDuplicate() {
        val match = DuplicateDetector.bestMatch(candidate(), listOf(candidate(id = "gone", deleted = true)))
        assertNull(match)
    }

    private fun candidate(
        id: String = "a",
        accountId: String = "checking",
        date: LocalDate = today,
        merchant: String = "Amazon",
        lastFour: String? = "1234",
        deleted: Boolean = false,
    ) = DuplicateCandidate(
        id = id,
        accountId = accountId,
        counterAccountId = null,
        linkId = null,
        amountMinor = 4_329,
        currency = "USD",
        type = TransactionType.EXPENSE,
        occurredOn = date,
        merchantNormalized = MerchantNormalizer.normalize(merchant),
        lastFour = lastFour,
        deleted = deleted,
    )

    private fun account(id: String, type: AccountType, opening: Long, liquid: Boolean) = Account(
        id = id,
        name = id,
        institution = "",
        type = type,
        lastFour = null,
        openingBalanceMinor = opening,
        creditLimitMinor = null,
        currency = "USD",
        active = true,
        includeInNetWorth = true,
        includeInSafeToSpend = liquid,
        syncStatus = SyncStatus.MANUAL,
        lastSyncedAtEpochMillis = null,
        syncError = null,
        sortOrder = 0,
    )

    private fun tx(
        id: String,
        accountId: String,
        type: TransactionType,
        amount: Long,
        effect: Long,
        counter: String? = null,
        link: String? = null,
        merchant: String = "Shop",
    ) = LedgerTransaction(
        id = id,
        accountId = accountId,
        counterAccountId = counter,
        linkId = link,
        amountMinor = amount,
        balanceEffectMinor = effect,
        currency = "USD",
        type = type,
        merchantRaw = merchant,
        merchantNormalized = merchant,
        merchantId = null,
        description = merchant,
        categoryId = null,
        occurredOn = today,
        postedOn = today,
        detectionSource = DetectionSource.MANUAL,
        confidencePercent = 100,
        recurringPaymentId = null,
        status = TransactionStatus.CONFIRMED,
        userConfirmed = true,
        note = "",
        fingerprint = "fp",
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
        deletedAtEpochMillis = null,
    )

    private fun recurring(
        name: String = "Item",
        amount: Long = 100,
        day: Int = 15,
        start: LocalDate = LocalDate.of(2026, 1, 31),
        priority: PaymentPriority = PaymentPriority.MANDATORY,
        direction: CashFlowDirection = CashFlowDirection.OUTFLOW,
        confidence: IncomeConfidence = IncomeConfidence.NONE,
        kind: ScheduledKind = ScheduledKind.BILL,
    ) = RecurringPayment(
        id = name,
        name = name,
        accountId = "checking",
        categoryId = null,
        direction = direction,
        kind = kind,
        amountMinor = amount,
        currency = "USD",
        frequency = RecurrenceFrequency.MONTHLY,
        intervalDays = null,
        dayOfMonth = day,
        dayOfWeek = null,
        startOn = start,
        endOn = null,
        priority = priority,
        incomeConfidence = confidence,
        active = true,
        note = "",
    )

    @Suppress("unused")
    private fun unusedBudget(category: Category) = Budget(
        "b", "B", category.id, null, BudgetPeriod.MONTHLY, 1, "USD", false, true,
    )
}
