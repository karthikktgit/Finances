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

    fun csv(transactions: List<LedgerTransaction>, accounts: List<Account>, categories: List<Category>): String {
        val accountNames = accounts.associate { it.id to it.name }
        val categoryNames = categories.associate { it.id to it.name }
        val header = listOf(
            "id",
            "date",
            "posted_date",
            "type",
            "amount_minor",
            "amount",
            "currency",
            "balance_effect_minor",
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
        ).joinToString(",")
        val rows = transactions.sortedBy { it.occurredOn }.map { tx ->
            listOf(
                tx.id,
                tx.occurredOn.toString(),
                tx.postedOn?.toString().orEmpty(),
                tx.type.name,
                tx.amountMinor.toString(),
                MoneyFormat.formatPlain(tx.amountMinor, tx.currency),
                tx.currency,
                tx.balanceEffectMinor.toString(),
                tx.merchantRaw,
                tx.merchantNormalized,
                tx.description,
                categoryNames[tx.categoryId].orEmpty(),
                accountNames[tx.accountId].orEmpty(),
                accountNames[tx.counterAccountId].orEmpty(),
                tx.detectionSource.name,
                tx.confidencePercent.toString(),
                tx.status.name,
                tx.note,
                tx.fingerprint,
                if (tx.deletedAtEpochMillis == null) "false" else "true",
            ).joinToString(",") { csvCell(it) }
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
                    it.syncStatus.name,
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
                )
            },
            recurring = recurring.map {
                ExportRecurring(
                    it.id, it.name, it.accountId, it.categoryId, it.direction.name, it.kind.name, it.amountMinor,
                    it.currency, it.frequency.name, it.startOn.toString(), it.priority.name, it.incomeConfidence.name, it.active,
                )
            },
            budgets = budgets.map {
                ExportBudget(it.id, it.name, it.categoryId, it.period.name, it.amountMinor, it.currency, it.reservesSafeToSpend, it.active)
            },
        )
        return json.encodeToString(document)
    }

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
