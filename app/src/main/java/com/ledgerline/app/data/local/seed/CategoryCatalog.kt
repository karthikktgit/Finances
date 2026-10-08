package com.ledgerline.app.data.local.seed

import com.ledgerline.app.domain.model.Necessity

object CategoryCatalog {
    data class Seed(
        val id: String,
        val parentId: String?,
        val name: String,
        val necessity: Necessity,
        val order: Int,
    )

    val ALL: List<Seed> = listOf(
        Seed("cat_food", null, "Food", Necessity.DISCRETIONARY, 10),
        Seed("cat_restaurants", "cat_food", "Restaurants", Necessity.DISCRETIONARY, 11),
        Seed("cat_fast_food", "cat_food", "Fast Food", Necessity.DISCRETIONARY, 12),
        Seed("cat_coffee", "cat_food", "Coffee", Necessity.DISCRETIONARY, 13),
        Seed("cat_delivery", "cat_food", "Delivery", Necessity.DISCRETIONARY, 14),
        Seed("cat_snacks", "cat_food", "Snacks", Necessity.DISCRETIONARY, 15),
        Seed("cat_groceries", null, "Groceries", Necessity.LIKELY_ESSENTIAL, 20),
        Seed("cat_transport", null, "Transportation", Necessity.LIKELY_ESSENTIAL, 30),
        Seed("cat_fuel", "cat_transport", "Fuel", Necessity.LIKELY_ESSENTIAL, 31),
        Seed("cat_parking", "cat_transport", "Parking", Necessity.DISCRETIONARY, 32),
        Seed("cat_transit", "cat_transport", "Public Transit", Necessity.LIKELY_ESSENTIAL, 33),
        Seed("cat_rideshare", "cat_transport", "Rideshare", Necessity.DISCRETIONARY, 34),
        Seed("cat_maintenance", "cat_transport", "Maintenance", Necessity.LIKELY_ESSENTIAL, 35),
        Seed("cat_bills", null, "Bills", Necessity.ESSENTIAL, 40),
        Seed("cat_rent", "cat_bills", "Rent", Necessity.ESSENTIAL, 41),
        Seed("cat_utilities", "cat_bills", "Utilities", Necessity.ESSENTIAL, 42),
        Seed("cat_phone", "cat_bills", "Phone", Necessity.ESSENTIAL, 43),
        Seed("cat_internet", "cat_bills", "Internet", Necessity.ESSENTIAL, 44),
        Seed("cat_insurance", "cat_bills", "Insurance", Necessity.ESSENTIAL, 45),
        Seed("cat_shopping", null, "Shopping", Necessity.DISCRETIONARY, 50),
        Seed("cat_electronics", "cat_shopping", "Electronics", Necessity.DISCRETIONARY, 51),
        Seed("cat_clothing", "cat_shopping", "Clothing", Necessity.DISCRETIONARY, 52),
        Seed("cat_household", "cat_shopping", "Household", Necessity.DISCRETIONARY, 53),
        Seed("cat_shopping_other", "cat_shopping", "Other Shopping", Necessity.DISCRETIONARY, 54),
        Seed("cat_entertainment", null, "Entertainment", Necessity.DISCRETIONARY, 60),
        Seed("cat_healthcare", null, "Healthcare", Necessity.LIKELY_ESSENTIAL, 70),
        Seed("cat_travel", null, "Travel", Necessity.DISCRETIONARY, 80),
        Seed("cat_personal", null, "Personal", Necessity.DISCRETIONARY, 90),
        Seed("cat_debt", null, "Debt", Necessity.ESSENTIAL, 100),
        Seed("cat_card_payment", "cat_debt", "Credit Card Payment", Necessity.ESSENTIAL, 101),
        Seed("cat_loan_payment", "cat_debt", "Loan Payment", Necessity.ESSENTIAL, 102),
        Seed("cat_income", null, "Income", Necessity.ESSENTIAL, 110),
        Seed("cat_paycheck", "cat_income", "Paycheck", Necessity.ESSENTIAL, 111),
        Seed("cat_other_income", "cat_income", "Other Income", Necessity.UNKNOWN, 112),
        Seed("cat_transfer", null, "Transfer", Necessity.UNKNOWN, 120),
        Seed("cat_other", null, "Other", Necessity.UNKNOWN, 130),
    )

    val quickPicks = listOf(
        "cat_food",
        "cat_groceries",
        "cat_fuel",
        "cat_transport",
        "cat_shopping",
        "cat_bills",
        "cat_entertainment",
        "cat_healthcare",
        "cat_travel",
        "cat_personal",
        "cat_debt",
        "cat_other",
    )
}
