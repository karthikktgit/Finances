package com.ledgerline.app.data.local.seed

import com.ledgerline.app.domain.model.Necessity

object CategoryCatalog {
    data class Seed(
        val id: String,
        val parentId: String?,
        val name: String,
        val necessity: Necessity,
        val order: Int,
        val archived: Boolean = false,
    )

    /** The categories the app offers. Parents are listed before their children. */
    val STANDARD: List<Seed> = listOf(
        Seed("cat_groceries_gas", null, "Groceries & Gas", Necessity.LIKELY_ESSENTIAL, 10),
        Seed("cat_groceries", "cat_groceries_gas", "Groceries", Necessity.LIKELY_ESSENTIAL, 11),
        Seed("cat_fuel", "cat_groceries_gas", "Gas / Fuel", Necessity.LIKELY_ESSENTIAL, 12),
        Seed("cat_debt", null, "Debt", Necessity.ESSENTIAL, 20),
        Seed("cat_card_payment", "cat_debt", "Credit Card Payment", Necessity.ESSENTIAL, 21),
        Seed("cat_education_loan", "cat_debt", "Education Loan", Necessity.ESSENTIAL, 22),
        Seed("cat_other_debt", "cat_debt", "Other Debt", Necessity.ESSENTIAL, 23),
        Seed("cat_income", null, "Income", Necessity.ESSENTIAL, 30),
        Seed("cat_paycheck", "cat_income", "Salary", Necessity.ESSENTIAL, 31),
        Seed("cat_other_income", "cat_income", "Other Income", Necessity.UNKNOWN, 32),
        Seed("cat_unplanned", null, "Unplanned", Necessity.UNKNOWN, 40),
        Seed("cat_unplanned_spending", "cat_unplanned", "Unplanned Spending", Necessity.UNKNOWN, 41),
        Seed("cat_monthly", null, "Monthly Expenditures", Necessity.ESSENTIAL, 50),
        Seed("cat_rent", "cat_monthly", "Rent", Necessity.ESSENTIAL, 51),
        Seed("cat_utilities", "cat_monthly", "Utilities", Necessity.ESSENTIAL, 52),
        Seed("cat_phone", "cat_monthly", "Phone", Necessity.ESSENTIAL, 53),
        Seed("cat_insurance", "cat_monthly", "Insurance", Necessity.ESSENTIAL, 54),
        Seed("cat_subscriptions", "cat_monthly", "Subscriptions", Necessity.DISCRETIONARY, 55),
        Seed("cat_car_wash", "cat_monthly", "Car Wash", Necessity.DISCRETIONARY, 56),
        Seed("cat_other_monthly", "cat_monthly", "Other Monthly Expenses", Necessity.UNKNOWN, 57),
    )

    /**
     * The earlier default categories that are not in the standard set. They stay in the database, hidden from
     * pickers, so transactions saved with them keep showing their original category.
     */
    val LEGACY: List<Seed> = listOf(
        Seed("cat_food", null, "Food", Necessity.DISCRETIONARY, 1_010, archived = true),
        Seed("cat_restaurants", "cat_food", "Restaurants", Necessity.DISCRETIONARY, 1_011, archived = true),
        Seed("cat_fast_food", "cat_food", "Fast Food", Necessity.DISCRETIONARY, 1_012, archived = true),
        Seed("cat_coffee", "cat_food", "Coffee", Necessity.DISCRETIONARY, 1_013, archived = true),
        Seed("cat_delivery", "cat_food", "Delivery", Necessity.DISCRETIONARY, 1_014, archived = true),
        Seed("cat_snacks", "cat_food", "Snacks", Necessity.DISCRETIONARY, 1_015, archived = true),
        Seed("cat_transport", null, "Transportation", Necessity.LIKELY_ESSENTIAL, 1_030, archived = true),
        Seed("cat_parking", "cat_transport", "Parking", Necessity.DISCRETIONARY, 1_032, archived = true),
        Seed("cat_transit", "cat_transport", "Public Transit", Necessity.LIKELY_ESSENTIAL, 1_033, archived = true),
        Seed("cat_rideshare", "cat_transport", "Rideshare", Necessity.DISCRETIONARY, 1_034, archived = true),
        Seed("cat_maintenance", "cat_transport", "Maintenance", Necessity.LIKELY_ESSENTIAL, 1_035, archived = true),
        Seed("cat_bills", null, "Bills", Necessity.ESSENTIAL, 1_040, archived = true),
        Seed("cat_internet", "cat_bills", "Internet", Necessity.ESSENTIAL, 1_044, archived = true),
        Seed("cat_shopping", null, "Shopping", Necessity.DISCRETIONARY, 1_050, archived = true),
        Seed("cat_electronics", "cat_shopping", "Electronics", Necessity.DISCRETIONARY, 1_051, archived = true),
        Seed("cat_clothing", "cat_shopping", "Clothing", Necessity.DISCRETIONARY, 1_052, archived = true),
        Seed("cat_household", "cat_shopping", "Household", Necessity.DISCRETIONARY, 1_053, archived = true),
        Seed("cat_shopping_other", "cat_shopping", "Other Shopping", Necessity.DISCRETIONARY, 1_054, archived = true),
        Seed("cat_entertainment", null, "Entertainment", Necessity.DISCRETIONARY, 1_060, archived = true),
        Seed("cat_healthcare", null, "Healthcare", Necessity.LIKELY_ESSENTIAL, 1_070, archived = true),
        Seed("cat_travel", null, "Travel", Necessity.DISCRETIONARY, 1_080, archived = true),
        Seed("cat_personal", null, "Personal", Necessity.DISCRETIONARY, 1_090, archived = true),
        Seed("cat_loan_payment", "cat_debt", "Loan Payment", Necessity.ESSENTIAL, 1_102, archived = true),
        Seed("cat_transfer", null, "Transfer", Necessity.UNKNOWN, 1_120, archived = true),
        Seed("cat_other", null, "Other", Necessity.UNKNOWN, 1_130, archived = true),
    )

    /** A new install gets the standard set, with the old defaults kept hidden for older records. */
    val ALL: List<Seed> = STANDARD + LEGACY

    /** Top-level groups shown first in the picker. */
    val quickPicks = STANDARD.filter { it.parentId == null }.map { it.id }
}
