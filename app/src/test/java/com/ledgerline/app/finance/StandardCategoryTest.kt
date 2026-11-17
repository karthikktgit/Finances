package com.ledgerline.app.finance

import com.ledgerline.app.data.local.seed.CategoryCatalog
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.balance.DayLedger
import com.ledgerline.app.finance.category.CategoryTree
import com.ledgerline.app.finance.category.SpendingRules
import com.ledgerline.app.finance.category.StandardCategories
import com.ledgerline.app.finance.period.PayPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StandardCategoryTest {
    private val day = LocalDate.of(2026, 10, 22)

    private fun names(parentId: String) = CategoryCatalog.STANDARD.filter { it.parentId == parentId }.map { it.name }

    /** The category table of an install that predates the standard set. */
    private fun oldInstall(): List<Category> {
        fun c(id: String, parent: String?, name: String, order: Int, archived: Boolean = false) =
            Category(id, parent, name, Necessity.UNKNOWN, archived, order)
        return listOf(
            c("cat_food", null, "Food", 10), c("cat_restaurants", "cat_food", "Restaurants", 11),
            c("cat_groceries", null, "Groceries", 20),
            c("cat_transport", null, "Transportation", 30), c("cat_fuel", "cat_transport", "Fuel", 31),
            c("cat_bills", null, "Bills", 40), c("cat_rent", "cat_bills", "Rent", 41),
            c("cat_utilities", "cat_bills", "Utilities", 42), c("cat_phone", "cat_bills", "Phone", 43),
            c("cat_insurance", "cat_bills", "Insurance", 45), c("cat_internet", "cat_bills", "Internet", 44),
            c("cat_debt", null, "Debt", 100), c("cat_card_payment", "cat_debt", "Credit Card Payment", 101),
            c("cat_loan_payment", "cat_debt", "Loan Payment", 102),
            c("cat_income", null, "Income", 110), c("cat_paycheck", "cat_income", "Paycheck", 111),
            c("cat_other_income", "cat_income", "Other Income", 112),
            c("cat_transfer", null, "Transfer", 120), c("cat_other", null, "Other", 130),
            c("custom-1", null, "My own category", 1_000),
        )
    }

    private fun apply(existing: List<Category>, plan: StandardCategories.Plan): List<Category> {
        val reshaped = existing.map { row ->
            val seed = plan.reshape.firstOrNull { it.id == row.id }
            val base = if (seed == null) row else row.copy(name = seed.name, parentId = seed.parentId, sortOrder = seed.order)
            if (base.id in plan.archiveIds) base.copy(archived = true) else base
        }
        return reshaped + plan.insert.map { Category(it.id, it.parentId, it.name, it.necessity, it.archived, it.order) }
    }

    @Test
    fun standardSetHasTheRequestedStructure() {
        assertEquals(listOf("Groceries", "Gas / Fuel"), names("cat_groceries_gas"))
        assertEquals(listOf("Credit Card Payment", "Education Loan", "Other Debt"), names("cat_debt"))
        assertEquals(listOf("Salary", "Other Income"), names("cat_income"))
        assertEquals(listOf("Unplanned Spending"), names("cat_unplanned"))
        assertEquals(
            listOf("Rent", "Utilities", "Phone", "Insurance", "Subscriptions", "Car Wash", "Other Monthly Expenses"),
            names("cat_monthly"),
        )
        assertEquals(
            listOf("Groceries & Gas", "Debt", "Income", "Unplanned", "Monthly Expenditures"),
            CategoryCatalog.STANDARD.filter { it.parentId == null }.map { it.name },
        )
    }

    @Test
    fun seedIdsAreUniqueAndParentsComeFirst() {
        val ids = CategoryCatalog.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val seen = mutableSetOf<String>()
        CategoryCatalog.ALL.forEach { seed ->
            seed.parentId?.let { assertTrue("${seed.id} parent ${seed.parentId}", it in seen) }
            seen += seed.id
        }
        assertTrue(CategoryCatalog.LEGACY.all { it.archived })
        assertTrue(CategoryCatalog.STANDARD.none { it.archived })
    }

    @Test
    fun existingStandardCategoriesKeepTheirIdsSoHistoryStillMatches() {
        val plan = StandardCategories.plan(oldInstall())
        val reusedIds = listOf("cat_groceries", "cat_fuel", "cat_rent", "cat_utilities", "cat_phone", "cat_insurance", "cat_card_payment", "cat_paycheck")
        reusedIds.forEach { id -> assertFalse("$id must not be re-inserted", plan.insert.any { it.id == id }) }
        val after = apply(oldInstall(), plan)
        assertEquals("Groceries & Gas · Groceries", CategoryTree.label("cat_groceries", after))
        assertEquals("Groceries & Gas · Gas / Fuel", CategoryTree.label("cat_fuel", after))
        assertEquals("Income · Salary", CategoryTree.label("cat_paycheck", after))
        assertEquals("Monthly Expenditures · Rent", CategoryTree.label("cat_rent", after))
    }

    @Test
    fun oldCategoriesAreHiddenNotDeletedAndCustomOnesAreUntouched() {
        val before = oldInstall()
        val after = apply(before, StandardCategories.plan(before))
        assertTrue(before.map { it.id }.all { id -> after.any { it.id == id } })
        listOf("cat_food", "cat_restaurants", "cat_bills", "cat_internet", "cat_loan_payment", "cat_transfer", "cat_other").forEach { id ->
            assertTrue(id, after.first { it.id == id }.archived)
        }
        assertFalse(after.first { it.id == "custom-1" }.archived)
        assertEquals("Food · Restaurants", CategoryTree.label("cat_restaurants", after))
        // The standard set is never archived.
        assertTrue(CategoryCatalog.STANDARD.all { seed -> !after.first { it.id == seed.id }.archived })
    }

    @Test
    fun standardizingTwiceChangesNothing() {
        val once = apply(oldInstall(), StandardCategories.plan(oldInstall()))
        assertTrue(StandardCategories.plan(once).empty)
        assertTrue(StandardCategories.plan(CategoryCatalog.ALL.map { Category(it.id, it.parentId, it.name, it.necessity, it.archived, it.order) }).empty)
    }

    @Test
    fun pickerOffersIncomeOnlyForIncomeAndHidesArchived() {
        val after = apply(oldInstall(), StandardCategories.plan(oldInstall()))
        val income = StandardCategories.groupsFor(true, after)
        val spending = StandardCategories.groupsFor(false, after)
        assertEquals(listOf("Income"), income.map { it.first.name })
        assertFalse(spending.any { it.first.id == "cat_income" })
        assertTrue(spending.none { it.first.archived })
        assertTrue(spending.flatMap { it.second }.none { it.archived })
        assertEquals(listOf("Groceries & Gas", "Debt", "Unplanned", "Monthly Expenditures", "My own category"), spending.map { it.first.name })
    }

    @Test
    fun incomeIsNeverSpending() {
        assertFalse(SpendingRules.countsAsSpending(tx("i", TransactionType.INCOME, 1_000, "cat_paycheck")))
        assertFalse(SpendingRules.countsAsSpending(tx("i2", TransactionType.INCOME, 1_000, "cat_groceries")))
        assertEquals(0L, DayLedger.netSpending(listOf(tx("i", TransactionType.INCOME, 5_000, "cat_paycheck"))))
    }

    @Test
    fun transfersAndPaymentsAreNotSpending() {
        val rows = listOf(
            tx("t", TransactionType.TRANSFER, 3_000, null),
            tx("p", TransactionType.PAYMENT, 2_000, "cat_card_payment"),
            tx("a", TransactionType.ADJUSTMENT, 500, null),
        )
        assertEquals(0L, DayLedger.netSpending(rows))
    }

    @Test
    fun creditCardPurchaseAndPaymentAreCountedOnce() {
        val purchase = tx("buy", TransactionType.EXPENSE, 8_000, "cat_groceries", accountId = "card")
        val paymentAsPayment = tx("pay", TransactionType.PAYMENT, 8_000, "cat_card_payment")
        val paymentAsExpense = tx("pay2", TransactionType.EXPENSE, 8_000, "cat_card_payment")
        assertEquals(8_000L, DayLedger.netSpending(listOf(purchase, paymentAsPayment)))
        assertEquals(8_000L, DayLedger.netSpending(listOf(purchase, paymentAsExpense)))
        // The expense-typed card payment still moves the balance; it is only left out of spending.
        assertEquals(-8_000L, paymentAsExpense.balanceEffectMinor)
    }

    @Test
    fun debtPaymentsToLoansStillCountAsSpending() {
        assertTrue(SpendingRules.countsAsSpending(tx("loan", TransactionType.EXPENSE, 20_000, "cat_education_loan")))
    }

    @Test
    fun unplannedFlagIsIndependentOfCategory() {
        val flagged = tx("u1", TransactionType.EXPENSE, 1_500, "cat_groceries", unplanned = true)
        val categoryOnly = tx("u2", TransactionType.EXPENSE, 700, StandardCategories.UNPLANNED_SPENDING, unplanned = false)
        val accounts = listOf(account())
        val figures = DayLedger.cycleFigures(PayPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 19)), accounts, listOf(flagged, categoryOnly))
        assertEquals(2_200L, figures.spentMinor)
        // Only the flagged expense is "unplanned spending"; its normal category is unchanged.
        assertEquals(1_500L, figures.unplannedMinor)
        assertEquals("cat_groceries", flagged.categoryId)
    }

    private fun account() = Account(
        "a", "Checking", "", AccountType.CHECKING, null, 100_000, null, "USD", true, true, true, SyncStatus.MANUAL, null, null, 0,
    )

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Long,
        category: String?,
        accountId: String = "a",
        unplanned: Boolean = false,
    ) = LedgerTransaction(
        id, accountId, null, null, amount, if (type == TransactionType.INCOME || type == TransactionType.REFUND) amount else -amount,
        "USD", type, id, id, null, id, category, day, day, DetectionSource.MANUAL, 100, null, TransactionStatus.CONFIRMED,
        true, "", id, 0, 0, null, unplanned,
    )
}
