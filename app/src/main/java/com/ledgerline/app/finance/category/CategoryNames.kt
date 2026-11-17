package com.ledgerline.app.finance.category

import com.ledgerline.app.domain.model.Category

/** Finds the category a typed name means, so "Rent", "Gas", or "Loan" count the spending you already tag that way. */
object CategoryNames {
    /** Words that mean an existing standard category. */
    val aliases = mapOf(
        "gas" to "Gas / Fuel",
        "fuel" to "Gas / Fuel",
        "electricity" to "Utilities",
        "water" to "Utilities",
        "power" to "Utilities",
        "netflix" to "Subscriptions",
        "spotify" to "Subscriptions",
        "icloud" to "Subscriptions",
        "youtube" to "Subscriptions",
        "eating out" to "Restaurants",
        "loan" to "Other Debt",
        "car payment" to "Other Debt",
        "credit card" to "Credit Card Payment",
    )

    /** Names that mean "everything no other item takes". */
    private val catchAll = setOf("everyday spending", "everything else", "other", "misc", "miscellaneous")

    fun isCatchAll(name: String): Boolean = name.trim().lowercase() in catchAll

    /** The category for [name]: an exact name first (visible ones before hidden ones), then a known alias. Null if none. */
    fun resolve(name: String, categories: List<Category>): Category? {
        val wanted = name.trim()
        if (wanted.isEmpty() || isCatchAll(wanted)) return null
        fun named(n: String) = categories.firstOrNull { !it.archived && it.name.equals(n, ignoreCase = true) }
            ?: categories.firstOrNull { it.name.equals(n, ignoreCase = true) }
        return named(wanted) ?: aliases[wanted.lowercase()]?.let(::named)
    }
}
