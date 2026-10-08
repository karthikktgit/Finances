package com.ledgerline.app.domain.repository

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountDraft
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetDraft
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtDraft
import com.ledgerline.app.domain.model.DetectedDraft
import com.ledgerline.app.domain.model.IngestResult
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.MerchantCategoryRule
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.NotificationSource
import com.ledgerline.app.domain.model.RecurringDraft
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.SyncRecord
import com.ledgerline.app.domain.model.TransactionDraft
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface AccountRepository {
    fun observe(): Flow<List<Account>>

    suspend fun get(id: String): Account?

    suspend fun save(draft: AccountDraft): SaveResult

    suspend fun deleteIfUnused(id: String): SaveResult
}

interface TransactionRepository {
    fun observeAll(): Flow<List<LedgerTransaction>>

    suspend fun get(id: String): LedgerTransaction?

    suspend fun editingSource(id: String): LedgerTransaction?

    suspend fun save(draft: TransactionDraft): SaveResult

    suspend fun softDelete(id: String)

    suspend fun restore(id: String)

    suspend fun ingestDetected(draft: DetectedDraft): IngestResult

    suspend fun setStatus(id: String, status: TransactionStatus)
}

interface CategoryRepository {
    fun observe(): Flow<List<Category>>

    suspend fun ensureDefaults()

    suspend fun addCustom(name: String, parentId: String?, necessity: Necessity)

    suspend fun setArchived(id: String, archived: Boolean)
}

interface RecurringRepository {
    fun observe(): Flow<List<RecurringPayment>>

    suspend fun get(id: String): RecurringPayment?

    suspend fun save(draft: RecurringDraft): SaveResult

    suspend fun delete(id: String)
}

interface BudgetRepository {
    fun observe(): Flow<List<Budget>>

    suspend fun get(id: String): Budget?

    suspend fun save(draft: BudgetDraft): SaveResult

    suspend fun delete(id: String)
}

interface SettingsRepository {
    fun observe(): Flow<UserSettings>

    suspend fun ensureDefault()

    suspend fun update(settings: UserSettings)
}

interface MerchantRepository {
    suspend fun suggestion(normalizedName: String, minimumConfirmations: Int): String?
}

interface LedgerReadModel {
    suspend fun accounts(): List<Account>

    suspend fun transactions(): List<LedgerTransaction>

    suspend fun categories(): List<Category>

    suspend fun recurring(): List<RecurringPayment>

    suspend fun budgets(): List<Budget>

    suspend fun settings(): UserSettings

    suspend fun rules(): List<MerchantCategoryRule>
}

interface DemoSeeder {
    suspend fun seedIfEmpty()
}

interface DebtRepository {
    fun observe(): Flow<List<Debt>>

    suspend fun save(draft: DebtDraft): SaveResult

    suspend fun delete(id: String)
}

interface NotificationSourceRepository {
    fun observe(): Flow<List<NotificationSource>>

    suspend fun ensureCatalog()

    suspend fun setEnabled(id: String, enabled: Boolean)

    suspend fun isEnabled(packageName: String): Boolean
}

interface SyncLogRepository {
    fun observe(): Flow<List<SyncRecord>>

    suspend fun record(providerId: String, status: String, message: String)
}

fun interface DuplicateWindow {
    fun around(date: LocalDate): Pair<LocalDate, LocalDate>
}
