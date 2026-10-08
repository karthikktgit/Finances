package com.ledgerline.app.finance.category

import com.ledgerline.app.domain.model.Category

object CategoryTree {
    fun scopeIds(rootId: String, categories: List<Category>): Set<String> {
        val children = categories.groupBy { it.parentId }
        val ids = mutableSetOf<String>()
        fun walk(id: String) {
            if (!ids.add(id)) return
            children[id].orEmpty().forEach { walk(it.id) }
        }
        walk(rootId)
        return ids
    }

    fun root(categoryId: String?, categories: List<Category>): Category? {
        if (categoryId == null) return null
        var current = categories.firstOrNull { it.id == categoryId } ?: return null
        var guard = 0
        while (current.parentId != null && guard < 8) {
            current = categories.firstOrNull { it.id == current.parentId } ?: return current
            guard += 1
        }
        return current
    }

    fun label(categoryId: String?, categories: List<Category>): String {
        val category = categories.firstOrNull { it.id == categoryId } ?: return "Uncategorized"
        val parent = category.parentId?.let { parentId -> categories.firstOrNull { it.id == parentId } }
        return if (parent == null) category.name else "${parent.name} · ${category.name}"
    }
}
