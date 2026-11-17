package com.ledgerline.app.finance.category

import com.ledgerline.app.data.local.seed.CategoryCatalog
import com.ledgerline.app.domain.model.Category

object StandardCategories {
    const val CARD_PAYMENT = "cat_card_payment"
    const val UNPLANNED_SPENDING = "cat_unplanned_spending"

    /** Present only after the standard set has been applied, so applying it again does nothing. */
    const val MARKER_ID = "cat_unplanned"

    data class Plan(
        val insert: List<CategoryCatalog.Seed>,
        val reshape: List<CategoryCatalog.Seed>,
        val archiveIds: List<String>,
    ) {
        val empty: Boolean get() = insert.isEmpty() && reshape.isEmpty() && archiveIds.isEmpty()
    }

    /**
     * Works out how to bring an existing category table to the standard set without deleting anything.
     * - Standard categories that are missing are added.
     * - Standard categories that already exist keep their id, so every transaction still points at them. Only the
     *   name, parent, and order change. Groceries is still Groceries.
     * - Old default categories that are not part of the standard set are archived. They keep their id and name,
     *   so old transactions still show their original category. Categories you created yourself are not touched.
     */
    fun plan(existing: List<Category>): Plan {
        if (existing.any { it.id == MARKER_ID }) return Plan(emptyList(), emptyList(), emptyList())
        val byId = existing.associateBy { it.id }
        val insert = CategoryCatalog.STANDARD.filter { it.id !in byId }
        val reshape = CategoryCatalog.STANDARD.filter { seed ->
            val row = byId[seed.id] ?: return@filter false
            row.name != seed.name || row.parentId != seed.parentId || row.sortOrder != seed.order
        }
        val archive = CategoryCatalog.LEGACY.map { it.id }.filter { id -> byId[id]?.archived == false }
        return Plan(insert, reshape, archive)
    }

    /** Standard top-level categories first, each followed by its subcategories. */
    fun groups(categories: List<Category>): List<Pair<Category, List<Category>>> {
        val live = categories.filter { !it.archived }
        return live.filter { it.parentId == null }.sortedBy { it.sortOrder }.map { parent ->
            parent to live.filter { it.parentId == parent.id }.sortedBy { it.sortOrder }
        }
    }

    /** Income categories apply to income; every other group applies to spending. */
    fun groupsFor(income: Boolean, categories: List<Category>): List<Pair<Category, List<Category>>> =
        groups(categories).filter { (parent, _) -> (parent.id == INCOME_ID) == income }

    const val INCOME_ID = "cat_income"
}
