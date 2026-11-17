package com.ledgerline.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.database.Migrations
import com.ledgerline.app.data.repository.AccountRepositoryImpl
import com.ledgerline.app.data.repository.BudgetRepositoryImpl
import com.ledgerline.app.data.repository.CaptureRepositoryImpl
import com.ledgerline.app.data.repository.CategoryRepositoryImpl
import com.ledgerline.app.data.repository.GoalRepositoryImpl
import com.ledgerline.app.data.repository.DebtRepositoryImpl
import com.ledgerline.app.data.repository.ExpenseNoteRepositoryImpl
import com.ledgerline.app.data.repository.LedgerReadModelImpl
import com.ledgerline.app.data.repository.MerchantRepositoryImpl
import com.ledgerline.app.data.repository.NotificationSourceRepositoryImpl
import com.ledgerline.app.data.repository.RecurringRepositoryImpl
import com.ledgerline.app.data.repository.SettingsRepositoryImpl
import com.ledgerline.app.data.remote.FinancialDataProvider
import com.ledgerline.app.data.remote.FixtureFinancialDataProvider
import com.ledgerline.app.data.repository.SyncLogRepositoryImpl
import com.ledgerline.app.data.repository.TransactionRepositoryImpl
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.BudgetRepository
import com.ledgerline.app.domain.repository.CaptureRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.GoalRepository
import com.ledgerline.app.domain.repository.DebtRepository
import com.ledgerline.app.domain.repository.ExpenseNoteRepository
import com.ledgerline.app.domain.repository.LedgerReadModel
import com.ledgerline.app.domain.repository.MerchantRepository
import com.ledgerline.app.domain.repository.NotificationSourceRepository
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.SyncLogRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.domain.time.SystemAppClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LedgerDatabase =
        Room.databaseBuilder(context, LedgerDatabase::class.java, "ledgerline.db")
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("ledgerline_ui") },
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun accounts(impl: AccountRepositoryImpl): AccountRepository

    @Binds abstract fun transactions(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds abstract fun categories(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds abstract fun recurring(impl: RecurringRepositoryImpl): RecurringRepository

    @Binds abstract fun budgets(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds abstract fun merchants(impl: MerchantRepositoryImpl): MerchantRepository

    @Binds abstract fun readModel(impl: LedgerReadModelImpl): LedgerReadModel

    @Binds abstract fun debts(impl: DebtRepositoryImpl): DebtRepository

    @Binds abstract fun expenseNotes(impl: ExpenseNoteRepositoryImpl): ExpenseNoteRepository

    @Binds abstract fun notificationSources(impl: NotificationSourceRepositoryImpl): NotificationSourceRepository

    @Binds abstract fun goals(impl: GoalRepositoryImpl): GoalRepository

    @Binds abstract fun cyclePlans(impl: com.ledgerline.app.data.repository.CyclePlanRepositoryImpl): com.ledgerline.app.domain.repository.CyclePlanRepository

    @Binds abstract fun homeGroups(impl: com.ledgerline.app.data.repository.HomeGroupRepositoryImpl): com.ledgerline.app.domain.repository.HomeGroupRepository

    @Binds abstract fun captures(impl: CaptureRepositoryImpl): CaptureRepository

    @Binds abstract fun captureAlerts(impl: com.ledgerline.app.detection.capture.CaptureNotifier): com.ledgerline.app.detection.capture.CaptureAlerts

    @Binds abstract fun syncLog(impl: SyncLogRepositoryImpl): SyncLogRepository

    @Binds abstract fun financialData(impl: FixtureFinancialDataProvider): FinancialDataProvider

    @Binds abstract fun clock(impl: SystemAppClock): AppClock
}
