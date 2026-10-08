package com.ledgerline.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ledgerline.app.data.local.entity.AccountEntity
import com.ledgerline.app.data.local.entity.BudgetEntity
import com.ledgerline.app.data.local.entity.CategoryEntity
import com.ledgerline.app.data.local.entity.DebtEntity
import com.ledgerline.app.data.local.entity.MerchantCategoryRuleEntity
import com.ledgerline.app.data.local.entity.MerchantEntity
import com.ledgerline.app.data.local.entity.NotificationSourceEntity
import com.ledgerline.app.data.local.entity.RecurringPaymentEntity
import com.ledgerline.app.data.local.entity.SyncRecordEntity
import com.ledgerline.app.data.local.entity.TransactionEntity
import com.ledgerline.app.data.local.entity.TransactionMatchEntity
import com.ledgerline.app.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    fun observe(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AccountEntity)

    @Update
    suspend fun update(entity: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM accounts")
    suspend fun maxSortOrder(): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observe(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity)

    @Query("UPDATE categories SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)
}

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchants WHERE normalizedName = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): MerchantEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MerchantEntity)

    @Query("SELECT * FROM merchant_category_rules WHERE merchantId = :merchantId")
    suspend fun ruleForMerchant(merchantId: String): MerchantCategoryRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(entity: MerchantCategoryRuleEntity)

    @Query(
        """
        SELECT rules.categoryId FROM merchant_category_rules AS rules
        INNER JOIN merchants ON merchants.id = rules.merchantId
        WHERE merchants.normalizedName = :normalized AND rules.confirmations >= :minimum
        LIMIT 1
        """,
    )
    suspend fun suggestedCategory(normalized: String, minimum: Int): String?
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredOn DESC, createdAtEpochMillis DESC")
    fun observe(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE linkId = :linkId")
    suspend fun byLink(linkId: String): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAtEpochMillis IS NULL
        AND occurredOn BETWEEN :start AND :end
        """,
    )
    suspend fun activeBetween(start: String, end: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionEntity)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE accountId = :accountId OR counterAccountId = :accountId
        """,
    )
    suspend fun countForAccount(accountId: String): Int
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_payments ORDER BY name")
    fun observe(): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT * FROM recurring_payments")
    suspend fun getAll(): List<RecurringPaymentEntity>

    @Query("SELECT * FROM recurring_payments WHERE id = :id")
    suspend fun get(id: String): RecurringPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: RecurringPaymentEntity)

    @Update
    suspend fun update(entity: RecurringPaymentEntity)

    @Query("DELETE FROM recurring_payments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM recurring_payments WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: String): Int
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY name")
    fun observe(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: BudgetEntity)

    @Update
    suspend fun update(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM budgets WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: String): Int
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun observe(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun get(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserSettingsEntity)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY name")
    fun observe(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts")
    suspend fun getAll(): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun get(id: String): DebtEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DebtEntity)

    @Update
    suspend fun update(entity: DebtEntity)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface NotificationSourceDao {
    @Query("SELECT * FROM notification_sources ORDER BY label")
    fun observe(): Flow<List<NotificationSourceEntity>>

    @Query("SELECT * FROM notification_sources WHERE packageName = :packageName")
    suspend fun find(packageName: String): NotificationSourceEntity?

    @Query("SELECT * FROM notification_sources WHERE id = :id")
    suspend fun findById(id: String): NotificationSourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NotificationSourceEntity)

    @Update
    suspend fun update(entity: NotificationSourceEntity)
}

@Dao
interface TransactionMatchDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionMatchEntity)

    @Query("SELECT * FROM transaction_matches WHERE incomingId = :incomingId")
    suspend fun forIncoming(incomingId: String): TransactionMatchEntity?
}

@Dao
interface SyncRecordDao {
    @Query("SELECT * FROM sync_records ORDER BY createdAtEpochMillis DESC LIMIT 20")
    fun observe(): Flow<List<SyncRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SyncRecordEntity)
}
