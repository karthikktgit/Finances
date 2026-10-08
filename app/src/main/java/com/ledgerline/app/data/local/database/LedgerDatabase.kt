package com.ledgerline.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ledgerline.app.data.local.dao.AccountDao
import com.ledgerline.app.data.local.dao.BudgetDao
import com.ledgerline.app.data.local.dao.CategoryDao
import com.ledgerline.app.data.local.dao.DebtDao
import com.ledgerline.app.data.local.dao.MerchantDao
import com.ledgerline.app.data.local.dao.NotificationSourceDao
import com.ledgerline.app.data.local.dao.RecurringDao
import com.ledgerline.app.data.local.dao.SettingsDao
import com.ledgerline.app.data.local.dao.SyncRecordDao
import com.ledgerline.app.data.local.dao.TransactionDao
import com.ledgerline.app.data.local.dao.TransactionMatchDao
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

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        MerchantCategoryRuleEntity::class,
        TransactionEntity::class,
        RecurringPaymentEntity::class,
        BudgetEntity::class,
        DebtEntity::class,
        NotificationSourceEntity::class,
        TransactionMatchEntity::class,
        SyncRecordEntity::class,
        UserSettingsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao

    abstract fun categories(): CategoryDao

    abstract fun merchants(): MerchantDao

    abstract fun transactions(): TransactionDao

    abstract fun recurring(): RecurringDao

    abstract fun budgets(): BudgetDao

    abstract fun debts(): DebtDao

    abstract fun notificationSources(): NotificationSourceDao

    abstract fun matches(): TransactionMatchDao

    abstract fun syncRecords(): SyncRecordDao

    abstract fun settings(): SettingsDao
}
