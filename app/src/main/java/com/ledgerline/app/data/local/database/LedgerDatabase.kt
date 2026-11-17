package com.ledgerline.app.data.local.database

import com.ledgerline.app.data.local.dao.HomeGroupDao
import com.ledgerline.app.data.local.entity.HomeGroupItemEntity
import com.ledgerline.app.data.local.entity.HomeGroupEntity
import androidx.room.Database
import androidx.room.RoomDatabase
import com.ledgerline.app.data.local.dao.AccountDao
import com.ledgerline.app.data.local.dao.BudgetDao
import com.ledgerline.app.data.local.dao.CapturedSpendDao
import com.ledgerline.app.data.local.dao.CategoryDao
import com.ledgerline.app.data.local.dao.GoalDao
import com.ledgerline.app.data.local.dao.DebtDao
import com.ledgerline.app.data.local.dao.DebtMonthPlanDao
import com.ledgerline.app.data.local.dao.DebtRateChangeDao
import com.ledgerline.app.data.local.dao.DebtSettlementDao
import com.ledgerline.app.data.local.dao.ExpenseNoteDao
import com.ledgerline.app.data.local.dao.MerchantDao
import com.ledgerline.app.data.local.dao.NotificationSourceDao
import com.ledgerline.app.data.local.dao.RecurringDao
import com.ledgerline.app.data.local.dao.RecurringOccurrenceDao
import com.ledgerline.app.data.local.dao.SettingsDao
import com.ledgerline.app.data.local.dao.SyncRecordDao
import com.ledgerline.app.data.local.dao.TransactionDao
import com.ledgerline.app.data.local.dao.TransactionMatchDao
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
import com.ledgerline.app.data.local.entity.ExpenseNoteEntity
import com.ledgerline.app.data.local.entity.MerchantCategoryRuleEntity
import com.ledgerline.app.data.local.entity.MerchantEntity
import com.ledgerline.app.data.local.entity.NotificationSourceEntity
import com.ledgerline.app.data.local.entity.RecurringOccurrenceEntity
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
        RecurringOccurrenceEntity::class,
        BudgetEntity::class,
        DebtEntity::class,
        DebtMonthPlanEntity::class,
        DebtRateChangeEntity::class,
        DebtSettlementEntity::class,
        NotificationSourceEntity::class,
        TransactionMatchEntity::class,
        SyncRecordEntity::class,
        UserSettingsEntity::class,
        ExpenseNoteEntity::class,
        CapturedSpendEntity::class,
        SavingsGoalEntity::class,
        GoalContributionEntity::class,
        HomeGroupEntity::class,
        HomeGroupItemEntity::class,
        com.ledgerline.app.data.local.entity.CycleAmountEntity::class,
        com.ledgerline.app.data.local.entity.CycleReviewEntity::class,
    ],
    version = 16,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao

    abstract fun categories(): CategoryDao

    abstract fun merchants(): MerchantDao

    abstract fun transactions(): TransactionDao

    abstract fun recurring(): RecurringDao

    abstract fun occurrenceRecords(): RecurringOccurrenceDao

    abstract fun budgets(): BudgetDao

    abstract fun debts(): DebtDao

    abstract fun debtPlans(): DebtMonthPlanDao

    abstract fun debtRates(): DebtRateChangeDao

    abstract fun debtSettlements(): DebtSettlementDao

    abstract fun notificationSources(): NotificationSourceDao

    abstract fun matches(): TransactionMatchDao

    abstract fun syncRecords(): SyncRecordDao

    abstract fun settings(): SettingsDao

    abstract fun expenseNotes(): ExpenseNoteDao

    abstract fun capturedSpends(): CapturedSpendDao

    abstract fun goals(): GoalDao

    abstract fun homeGroups(): HomeGroupDao

    abstract fun cyclePlans(): com.ledgerline.app.data.local.dao.CyclePlanDao
}
