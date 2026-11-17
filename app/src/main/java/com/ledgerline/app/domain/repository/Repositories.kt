package com.ledgerline.app.domain.repository

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountDraft
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetDraft
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.Debt
import com.ledgerline.app.domain.model.DebtDraft
import com.ledgerline.app.domain.model.DebtMonthPlan
import com.ledgerline.app.domain.model.DebtRateChange
import com.ledgerline.app.domain.model.DebtSettlement
import com.ledgerline.app.domain.model.DetectedDraft
import com.ledgerline.app.domain.model.IngestResult
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.MerchantCategoryRule
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.NotificationSource
import com.ledgerline.app.domain.model.RecurringDraft
import com.ledgerline.app.domain.model.OccurrenceAdjustment
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.SeriesScope
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.SyncRecord
import com.ledgerline.app.domain.model.TransactionDraft
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

interface AccountRepository {
    fun observe(): Flow<List<Account>>

    suspend fun get(id: String): Account?

    suspend fun save(draft: AccountDraft): SaveResult

    suspend fun deleteIfUnused(id: String): SaveResult

    /** Active accounts plus deleted ones, for labeling history. */
    fun observeIncludingDeleted(): Flow<List<Account>>

    suspend fun deletionImpact(id: String): com.ledgerline.app.finance.account.AccountDeletionImpact

    /** Deletes the account while keeping its transaction history. Schedules and budgets on it are paused; debts are unlinked for review. */
    suspend fun delete(id: String): SaveResult
}

interface TransactionRepository {
    /** Transactions on accounts that have not been deleted. */
    fun observeAll(): Flow<List<LedgerTransaction>>

    /** Every stored transaction, including rows on deleted accounts. */
    fun observeHistory(): Flow<List<LedgerTransaction>>

    suspend fun get(id: String): LedgerTransaction?

    suspend fun editingSource(id: String): LedgerTransaction?

    suspend fun save(draft: TransactionDraft): SaveResult

    suspend fun softDelete(id: String)

    suspend fun restore(id: String)

    suspend fun ingestDetected(draft: DetectedDraft): IngestResult

    suspend fun setStatus(id: String, status: TransactionStatus)

    /** Marks a planned (not yet paid) spend as paid on [on]. */
    suspend fun markSpendPaid(id: String, on: java.time.LocalDate)
}

interface CategoryRepository {
    fun observe(): Flow<List<Category>>

    suspend fun ensureDefaults()

    suspend fun addCustom(name: String, parentId: String?, necessity: Necessity)

    suspend fun setArchived(id: String, archived: Boolean)
}

interface RecurringRepository {
    fun observe(): Flow<List<RecurringPayment>>

    fun observeAdjustments(): Flow<List<OccurrenceAdjustment>>

    suspend fun get(id: String): RecurringPayment?

    suspend fun save(
        draft: RecurringDraft,
        scope: SeriesScope = SeriesScope.ENTIRE_SERIES,
        occurrenceOn: LocalDate? = null,
    ): SaveResult

    suspend fun delete(
        id: String,
        scope: SeriesScope = SeriesScope.ENTIRE_SERIES,
        occurrenceOn: LocalDate? = null,
    )

    /** Records the payment for one date. [automatic] is for autopay: no undo notice, and the note says so. */
    suspend fun markPaid(id: String, occurrenceOn: LocalDate, automatic: Boolean = false): SaveResult

    suspend fun skip(id: String, occurrenceOn: LocalDate): SaveResult

    /**
     * Sets one date's own amount ([amountMinor], or null for the usual) and whether it is skipped, quietly, for
     * planning a cycle ahead. A date already paid is left alone.
     */
    suspend fun setOccurrence(id: String, occurrenceOn: LocalDate, amountMinor: Long?, skipped: Boolean): SaveResult =
        if (skipped) skip(id, occurrenceOn) else SaveResult.Invalid("Not supported here.")
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

    /** Active and deleted accounts, so exports can label history on deleted accounts. */
    suspend fun accountsIncludingDeleted(): List<Account> = accounts()

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

    fun observePlans(): Flow<List<DebtMonthPlan>>

    fun observeRates(): Flow<List<DebtRateChange>>

    fun observeSettlements(): Flow<List<DebtSettlement>>

    suspend fun save(draft: DebtDraft): SaveResult

    suspend fun delete(id: String)

    suspend fun savePlan(debtId: String, yearMonth: YearMonth, plannedPaymentMinor: Long, dueOn: LocalDate?): SaveResult

    suspend fun addRateChange(debtId: String, effectiveOn: LocalDate, aprBasisPoints: Int): SaveResult

    suspend fun setShowInCalendar(debtId: String, show: Boolean)

    suspend fun markMonthPaid(debtId: String, yearMonth: YearMonth): SaveResult

    suspend fun recordInterest(debtId: String, amountMinor: Long, on: LocalDate): SaveResult

    suspend fun adoptAccountOwed(debtId: String): SaveResult

    suspend fun postAccountCorrection(debtId: String): SaveResult
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

interface HomeGroupRepository {
    fun observe(): kotlinx.coroutines.flow.Flow<List<com.ledgerline.app.domain.model.HomeGroup>>

    fun observeItems(): kotlinx.coroutines.flow.Flow<List<com.ledgerline.app.domain.model.HomeGroupItem>>

    /** Adds "Monthly" and "Subscriptions" when there are no groups yet. */
    suspend fun ensureDefaults()

    /** Creates a group and returns its id. */
    suspend fun create(name: String): String

    suspend fun rename(id: String, name: String)

    /** Removes the group. Its items go back to the first group. */
    suspend fun delete(id: String)

    suspend fun place(itemKey: String, groupId: String)

    suspend fun forget(itemKey: String)
}
