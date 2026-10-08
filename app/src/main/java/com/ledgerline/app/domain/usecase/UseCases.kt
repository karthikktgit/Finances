package com.ledgerline.app.domain.usecase

import com.ledgerline.app.domain.model.Dashboard
import com.ledgerline.app.domain.model.Insights
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.BudgetRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.LedgerReadModel
import com.ledgerline.app.domain.repository.RecurringRepository
import com.ledgerline.app.domain.repository.SettingsRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.dashboard.DashboardAssembler
import com.ledgerline.app.finance.export.LedgerExport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveDashboard @Inject constructor(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val recurring: RecurringRepository,
    private val budgets: BudgetRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    fun observe(): Flow<Dashboard> = combine(
        combine(accounts.observe(), transactions.observeAll(), categories.observe(), ::Triple),
        combine(recurring.observe(), budgets.observe(), settings.observe(), ::Triple),
    ) { left, right ->
        DashboardAssembler.assemble(
            today = clock.today(),
            settings = right.third,
            accounts = left.first,
            categories = left.third,
            transactions = left.second,
            recurring = right.first,
            budgets = right.second,
        )
    }

    suspend fun current(): Dashboard {
        val today = clock.today()
        return DashboardAssembler.assemble(
            today = today,
            settings = settings.observe().first(),
            accounts = accounts.observe().first(),
            categories = categories.observe().first(),
            transactions = transactions.observeAll().first(),
            recurring = recurring.observe().first(),
            budgets = budgets.observe().first(),
        )
    }
}

class ObserveInsights @Inject constructor(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val recurring: RecurringRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    fun observe(): Flow<Insights> = combine(
        accounts.observe(),
        transactions.observeAll(),
        categories.observe(),
        recurring.observe(),
        settings.observe(),
    ) { accountRows, transactionRows, categoryRows, recurringRows, userSettings ->
        DashboardAssembler.insights(clock.today(), userSettings, accountRows, categoryRows, transactionRows, recurringRows)
    }
}

class ExportLedger @Inject constructor(
    private val readModel: LedgerReadModel,
    private val clock: AppClock,
) {
    suspend fun csv(): String = LedgerExport.csv(readModel.transactions(), readModel.accounts(), readModel.categories())

    suspend fun json(): String = LedgerExport.jsonDocument(
        exportedAtEpochMillis = clock.now().toEpochMilli(),
        settings = readModel.settings(),
        accounts = readModel.accounts(),
        categories = readModel.categories(),
        transactions = readModel.transactions(),
        recurring = readModel.recurring(),
        budgets = readModel.budgets(),
    )
}
