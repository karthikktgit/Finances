package com.ledgerline.app.finance.export

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.money.MoneyFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object LedgerExport {
    private val json = Json { prettyPrint = true }

    /**
     * Amounts are integer minor units in storage. Here they become plain decimal numbers in major units,
     * for example 4500 minor units in USD is `45.00`. Numeric columns are written without quotes, symbols,
     * thousands separators, or locale formatting, so spreadsheets read them as numbers.
     * `amount` is the absolute size; `balance_effect` is the signed change to the account balance.
     */
    fun csv(transactions: List<LedgerTransaction>, accounts: List<Account>, categories: List<Category>): String {
        val accountNames = accounts.associate { it.id to com.ledgerline.app.finance.account.AccountDeletion.label(it.id, accounts) }
        val categoryNames = categories.associate { it.id to it.name }
        val header = listOf(
            "id",
            "date",
            "posted_date",
            "type",
            "amount",
            "balance_effect",
            "currency",
            "merchant",
            "normalized_merchant",
            "description",
            "category",
            "account",
            "counter_account",
            "source",
            "confidence_percent",
            "status",
            "note",
            "fingerprint",
            "deleted",
            "unplanned",
        ).joinToString(",")
        val rows = transactions.sortedBy { it.occurredOn }.map { tx ->
            // Each cell is either already-safe numeric/ISO text or goes through csvCell.
            listOf(
                csvCell(tx.id),
                tx.occurredOn.toString(),
                tx.postedOn?.toString().orEmpty(),
                csvCell(tx.type.name),
                csvNumber(tx.amountMinor, tx.currency),
                csvNumber(tx.balanceEffectMinor, tx.currency),
                csvCell(tx.currency),
                csvCell(tx.merchantRaw),
                csvCell(tx.merchantNormalized),
                csvCell(tx.description),
                csvCell(categoryNames[tx.categoryId].orEmpty()),
                csvCell(accountNames[tx.accountId].orEmpty()),
                csvCell(accountNames[tx.counterAccountId].orEmpty()),
                csvCell(tx.detectionSource.name),
                tx.confidencePercent.toString(),
                csvCell(tx.status.name),
                csvCell(tx.note),
                csvCell(tx.fingerprint),
                if (tx.deletedAtEpochMillis == null) "false" else "true",
                if (tx.unplanned) "true" else "false",
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun jsonDocument(
        exportedAtEpochMillis: Long,
        settings: UserSettings,
        accounts: List<Account>,
        categories: List<Category>,
        transactions: List<LedgerTransaction>,
        recurring: List<RecurringPayment>,
        budgets: List<Budget>,
    ): String {
        val document = ExportDocument(
            schemaVersion = 1,
            exportedAtEpochMillis = exportedAtEpochMillis,
            primaryCurrency = settings.primaryCurrency,
            minimumBufferMinor = settings.minimumBufferMinor,
            horizonDays = settings.horizonDays,
            accounts = accounts.map {
                ExportAccount(
                    it.id, it.name, it.institution, it.type.name, it.lastFour, it.openingBalanceMinor,
                    it.creditLimitMinor, it.currency, it.active, it.includeInNetWorth, it.includeInSafeToSpend,
                    it.syncStatus.name, it.deleted, it.deletedAtEpochMillis,
                )
            },
            categories = categories.map {
                ExportCategory(it.id, it.parentId, it.name, it.necessity.name, it.archived)
            },
            transactions = transactions.map {
                ExportTransaction(
                    it.id, it.accountId, it.counterAccountId, it.amountMinor, it.balanceEffectMinor, it.currency,
                    it.type.name, it.merchantRaw, it.merchantNormalized, it.categoryId, it.occurredOn.toString(),
                    it.detectionSource.name, it.confidencePercent, it.status.name, it.note, it.deletedAtEpochMillis != null,
                    it.unplanned,
                )
            },
            recurring = recurring.map {
                ExportRecurring(
                    it.id, it.name, it.accountId, it.categoryId, it.direction.name, it.kind.name, it.amountMinor,
                    it.currency, it.frequency.name, it.startOn.toString(), it.endOn?.toString(), it.maxOccurrences,
                    it.priority.name, it.incomeConfidence.name, it.active,
                )
            },
            budgets = budgets.map {
                ExportBudget(it.id, it.name, it.categoryId, it.period.name, it.amountMinor, it.currency, it.reservesSafeToSpend, it.active)
            },
        )
        return json.encodeToString(document)
    }

    /** A plain decimal such as `45.00`, `-12.30`, or `0.01`. Never quoted, prefixed, or localized. */
    fun csvNumber(minor: Long, currency: String): String = MoneyFormat.formatPlain(minor, currency)

    fun csvCell(value: String): String {
        val guarded = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"${guarded.replace("\"", "\"\"")}\""
    }
}

@Serializable
private data class ExportDocument(
    val schemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val primaryCurrency: String,
    val minimumBufferMinor: Long,
    val horizonDays: Int,
    val accounts: List<ExportAccount>,
    val categories: List<ExportCategory>,
    val transactions: List<ExportTransaction>,
    val recurring: List<ExportRecurring>,
    val budgets: List<ExportBudget>,
)

@Serializable
private data class ExportAccount(
    val id: String,
    val name: String,
    val institution: String,
    val type: String,
    val lastFour: String?,
    val openingBalanceMinor: Long,
    val creditLimitMinor: Long?,
    val currency: String,
    val active: Boolean,
    val includeInNetWorth: Boolean,
    val includeInSafeToSpend: Boolean,
    val syncStatus: String,
    val deleted: Boolean = false,
    val deletedAtEpochMillis: Long? = null,
)

@Serializable
private data class ExportCategory(
    val id: String,
    val parentId: String?,
    val name: String,
    val necessity: String,
    val archived: Boolean,
)

@Serializable
private data class ExportTransaction(
    val id: String,
    val accountId: String,
    val counterAccountId: String?,
    val amountMinor: Long,
    val balanceEffectMinor: Long,
    val currency: String,
    val type: String,
    val merchant: String,
    val normalizedMerchant: String,
    val categoryId: String?,
    val date: String,
    val source: String,
    val confidencePercent: Int,
    val status: String,
    val note: String,
    val deleted: Boolean,
    val unplanned: Boolean = false,
)

@Serializable
private data class ExportRecurring(
    val id: String,
    val name: String,
    val accountId: String,
    val categoryId: String?,
    val direction: String,
    val kind: String,
    val amountMinor: Long,
    val currency: String,
    val frequency: String,
    val startOn: String,
    val endOn: String? = null,
    val maxOccurrences: Int? = null,
    val priority: String,
    val incomeConfidence: String,
    val active: Boolean,
)

@Serializable
private data class ExportBudget(
    val id: String,
    val name: String,
    val categoryId: String?,
    val period: String,
    val amountMinor: Long,
    val currency: String,
    val reservesSafeToSpend: Boolean,
    val active: Boolean,
)
