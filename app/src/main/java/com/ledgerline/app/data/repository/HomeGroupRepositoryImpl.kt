package com.ledgerline.app.data.repository

import androidx.room.withTransaction
import com.ledgerline.app.data.Ids
import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.entity.HomeGroupEntity
import com.ledgerline.app.data.local.entity.HomeGroupItemEntity
import com.ledgerline.app.domain.model.HomeGroup
import com.ledgerline.app.domain.model.HomeGroupItem
import com.ledgerline.app.domain.repository.HomeGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeGroupRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
) : HomeGroupRepository {
    private val dao get() = database.homeGroups()

    override fun observe(): Flow<List<HomeGroup>> = dao.observe().map { rows -> rows.map { HomeGroup(it.id, it.name, it.sortOrder) } }

    override fun observeItems(): Flow<List<HomeGroupItem>> = dao.observeItems().map { rows -> rows.map { HomeGroupItem(it.itemKey, it.groupId, it.sortOrder) } }

    override suspend fun ensureDefaults() {
        // The first group was called "Monthly"; it is now "Monthly expenses" unless you renamed it yourself.
        dao.observeOnce().firstOrNull { it.id == MONTHLY && it.name == "Monthly" }?.let { dao.upsert(it.copy(name = "Monthly expenses")) }
        if (dao.count() > 0) return
        dao.upsert(HomeGroupEntity(MONTHLY, "Monthly expenses", 0))
        dao.upsert(HomeGroupEntity(SUBSCRIPTIONS, "Subscriptions", 1))
    }

    override suspend fun create(name: String): String {
        val id = Ids.new()
        dao.upsert(HomeGroupEntity(id, name.trim(), dao.maxOrder() + 1))
        return id
    }

    override suspend fun rename(id: String, name: String) {
        val current = dao.observeOnce().firstOrNull { it.id == id } ?: return
        dao.upsert(current.copy(name = name.trim()))
    }

    override suspend fun delete(id: String) {
        database.withTransaction {
            dao.deleteItemsOf(id)
            dao.delete(id)
        }
    }

    override suspend fun place(itemKey: String, groupId: String) {
        dao.upsertItem(HomeGroupItemEntity(itemKey, groupId, 0))
    }

    override suspend fun forget(itemKey: String) {
        dao.deleteItem(itemKey)
    }

    companion object {
        const val MONTHLY = "grp_monthly"
        const val SUBSCRIPTIONS = "grp_subscriptions"
    }
}
