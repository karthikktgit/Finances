package com.ledgerline.app.data.local.dao

import com.ledgerline.app.data.local.entity.CycleAmountEntity
import com.ledgerline.app.data.local.entity.CycleReviewEntity
import com.ledgerline.app.data.local.entity.HomeGroupEntity
import com.ledgerline.app.data.local.entity.HomeGroupItemEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ledgerline.app.data.local.entity.AccountEntity
import com.ledgerline.app.data.local.entity.BudgetEntity
import com.ledgerline.app.data.local.entity.CapturedSpendEntity
import com.ledgerline.app.data.local.entity.CategoryEntity
import com.ledgerline.app.data.local.entity.GoalContributionEntity
import com.ledgerline.app.data.local.entity.SavingsGoalEntity
import com.ledgerline.app.data.local.entity.DebtEntity
import com.ledgerline.app.data.local.entity.DebtMonthPlanEntity
import com.ledgerline.app.data.local.entity.DebtRateChangeEntity
import com.ledgerline.app.data.local.entity.DebtSettlementEntity
import com.ledgerline.app.data.local.entity.MerchantCategoryRuleEntity
import com.ledgerline.app.data.local.entity.MerchantEntity
import com.ledgerline.app.data.local.entity.NotificationSourceEntity
import com.ledgerline.app.data.local.entity.RecurringOccurrenceEntity
import com.ledgerline.app.data.local.entity.RecurringPaymentEntity
import com.ledgerline.app.data.local.entity.SyncRecordEntity
import com.ledgerline.app.data.local.entity.TransactionEntity
import com.ledgerline.app.data.local.entity.TransactionMatchEntity
import com.ledgerline.app.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    /** Accounts the user can still use. Deleted accounts are hidden from every active screen and write path. */
    @Query("SELECT * FROM accounts WHERE deletedAtEpochMillis IS NULL ORDER BY sortOrder, name")
    fun observe(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deletedAtEpochMillis IS NULL ORDER BY sortOrder, name")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id AND deletedAtEpochMillis IS NULL")
    suspend fun get(id: String): AccountEntity?

    /** Includes deleted accounts so history can still show the original name. */
    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    fun observeIncludingDeleted(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    suspend fun getAllIncludingDeleted(): List<AccountEntity>

    @Query("UPDATE accounts SET deletedAtEpochMillis = :at, active = 0, updatedAtEpochMillis = :at WHERE id = :id AND deletedAtEpochMillis IS NULL")
    suspend fun markDeleted(id: String, at: Long): Int

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

    /** Renames or moves a category without changing its id, so transactions keep pointing at it. */
    @Query("UPDATE categories SET name = :name, parentId = :parentId, necessity = :necessity, sortOrder = :sortOrder WHERE id = :id")
    suspend fun reshape(id: String, name: String, parentId: String?, necessity: String, sortOrder: Int)
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

    /** Transactions on accounts that still exist for the user. Used by balances, forecasts, and Home. */
    @Query(
        "SELECT t.* FROM transactions t LEFT JOIN accounts a ON a.id = t.accountId " +
            "WHERE a.deletedAtEpochMillis IS NULL ORDER BY t.occurredOn DESC, t.createdAtEpochMillis DESC",
    )
    fun observeLive(): Flow<List<TransactionEntity>>

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

    @Query(
        """
        SELECT * FROM transactions
        WHERE recurringPaymentId = :recurringId AND occurredOn = :date AND deletedAtEpochMillis IS NULL
        LIMIT 1
        """,
    )
    suspend fun forOccurrence(recurringId: String, date: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId
        AND deletedAtEpochMillis IS NULL
        AND status IN ('CONFIRMED', 'RECONCILED', 'MODIFIED')
        AND paid = 1
        """,
    )
    suspend fun activeForAccount(accountId: String): List<TransactionEntity>
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
interface RecurringOccurrenceDao {
    @Query("SELECT * FROM recurring_occurrences")
    fun observe(): Flow<List<RecurringOccurrenceEntity>>

    @Query(
        """
        SELECT * FROM recurring_occurrences
        WHERE recurringPaymentId = :recurringId AND occurrenceOn = :date
        LIMIT 1
        """,
    )
    suspend fun get(recurringId: String, date: String): RecurringOccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecurringOccurrenceEntity)
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
interface ExpenseNoteDao {
    @Query("SELECT * FROM expense_notes")
    fun observe(): Flow<List<com.ledgerline.app.data.local.entity.ExpenseNoteEntity>>

    @Query("SELECT * FROM expense_notes WHERE id = :id")
    suspend fun get(id: String): com.ledgerline.app.data.local.entity.ExpenseNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: com.ledgerline.app.data.local.entity.ExpenseNoteEntity)

    @Query("DELETE FROM expense_notes WHERE id = :id")
    suspend fun delete(id: String)
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
interface DebtMonthPlanDao {
    @Query("SELECT * FROM debt_month_plans")
    fun observe(): Flow<List<DebtMonthPlanEntity>>

    @Query("SELECT * FROM debt_month_plans WHERE debtId = :debtId AND yearMonth = :yearMonth")
    suspend fun forMonth(debtId: String, yearMonth: String): DebtMonthPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DebtMonthPlanEntity)

    @Query("DELETE FROM debt_month_plans WHERE debtId = :debtId AND yearMonth = :yearMonth")
    suspend fun deleteMonth(debtId: String, yearMonth: String)
}

@Dao
interface DebtRateChangeDao {
    @Query("SELECT * FROM debt_rate_changes ORDER BY effectiveOn")
    fun observe(): Flow<List<DebtRateChangeEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DebtRateChangeEntity)
}

@Dao
interface DebtSettlementDao {
    @Query("SELECT * FROM debt_settlements")
    fun observe(): Flow<List<DebtSettlementEntity>>

    @Query("SELECT * FROM debt_settlements WHERE debtId = :debtId AND yearMonth = :yearMonth")
    suspend fun forMonth(debtId: String, yearMonth: String): DebtSettlementEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DebtSettlementEntity)

    @Query("DELETE FROM debt_settlements WHERE id = :id")
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

@Dao
interface CapturedSpendDao {
    @Query("SELECT * FROM captured_spends WHERE status = 'PENDING' ORDER BY capturedAtEpochMillis DESC")
    fun observePending(): Flow<List<CapturedSpendEntity>>

    @Query("SELECT * FROM captured_spends ORDER BY capturedAtEpochMillis DESC LIMIT 30")
    fun observeRecent(): Flow<List<CapturedSpendEntity>>

    @Query("SELECT * FROM captured_spends WHERE id = :id")
    suspend fun get(id: String): CapturedSpendEntity?

    @Query("SELECT * FROM captured_spends WHERE amountMinor = :amountMinor AND direction = :direction AND packageName != :packageName AND capturedAtEpochMillis >= :since LIMIT 1")
    suspend fun similarFromOtherSince(amountMinor: Long, direction: String, packageName: String, since: Long): CapturedSpendEntity?

    @Query(
        "SELECT * FROM captured_spends WHERE amountMinor = :amountMinor AND direction = :direction " +
            "AND capturedAtEpochMillis >= :since LIMIT 1",
    )
    suspend fun similarSince(amountMinor: Long, direction: String, since: Long): CapturedSpendEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CapturedSpendEntity): Long

    @Query("UPDATE captured_spends SET status = :status, transactionId = :transactionId WHERE id = :id")
    suspend fun setStatus(id: String, status: String, transactionId: String?)

    @Query("DELETE FROM captured_spends WHERE status != 'PENDING' AND capturedAtEpochMillis < :before")
    suspend fun pruneHandled(before: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY archived, createdOn")
    fun observe(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM goal_contributions ORDER BY onDate DESC, createdAtEpochMillis DESC")
    fun observeContributions(): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun get(id: String): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertContribution(entity: GoalContributionEntity)

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContribution(id: String)
}

@Dao
interface HomeGroupDao {
    @Query("SELECT * FROM home_groups ORDER BY sortOrder, name")
    fun observe(): Flow<List<HomeGroupEntity>>

    @Query("SELECT * FROM home_group_items")
    fun observeItems(): Flow<List<HomeGroupItemEntity>>

    @Query("SELECT * FROM home_groups ORDER BY sortOrder, name")
    suspend fun observeOnce(): List<HomeGroupEntity>

    @Query("SELECT COUNT(*) FROM home_groups")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM home_groups")
    suspend fun maxOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HomeGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(entity: HomeGroupItemEntity)

    @Query("DELETE FROM home_group_items WHERE itemKey = :key")
    suspend fun deleteItem(key: String)

    @Query("DELETE FROM home_group_items WHERE groupId = :groupId")
    suspend fun deleteItemsOf(groupId: String)

    @Query("DELETE FROM home_groups WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CyclePlanDao {
    @Query("SELECT * FROM cycle_amounts")
    fun observeAmounts(): Flow<List<CycleAmountEntity>>

    @Query("SELECT * FROM cycle_reviews")
    fun observeReviews(): Flow<List<CycleReviewEntity>>

    @Query("SELECT * FROM cycle_reviews WHERE cycleStart = :cycleStart")
    suspend fun review(cycleStart: String): CycleReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAmount(entity: CycleAmountEntity)

    @Query("DELETE FROM cycle_amounts WHERE cycleStart = :cycleStart AND itemKey = :itemKey")
    suspend fun deleteAmount(cycleStart: String, itemKey: String)

    @Query("DELETE FROM cycle_amounts WHERE itemKey = :itemKey")
    suspend fun deleteAmountsFor(itemKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReview(entity: CycleReviewEntity)
}
