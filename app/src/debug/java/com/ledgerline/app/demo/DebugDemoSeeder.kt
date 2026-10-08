package com.ledgerline.app.demo

import androidx.room.withTransaction
import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.entity.AccountEntity
import com.ledgerline.app.data.local.entity.BudgetEntity
import com.ledgerline.app.data.local.entity.MerchantCategoryRuleEntity
import com.ledgerline.app.data.local.entity.MerchantEntity
import com.ledgerline.app.data.local.entity.RecurringPaymentEntity
import com.ledgerline.app.data.local.entity.TransactionEntity
import com.ledgerline.app.data.mapper.defaultSettings
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.repository.DemoSeeder
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.fingerprint.TransactionFingerprint
import com.ledgerline.app.finance.merchant.MerchantNormalizer
import com.ledgerline.app.preferences.UiPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugDemoSeeder @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
    private val preferences: UiPreferences,
) : DemoSeeder {
    override suspend fun seedIfEmpty() {
        if (database.accounts().getAll().isNotEmpty()) return
        val today = clock.today()
        val now = clock.now().toEpochMilli()
        val previous = YearMonth.from(today).minusMonths(1)
        database.withTransaction {
            val accounts = listOf(
                account("acct_checking", "Chase Checking", "Chase", AccountType.CHECKING, "4521", true, 0, now),
                account("acct_savings", "Ally Savings", "Ally", AccountType.SAVINGS, "7782", false, 1, now),
                account("acct_card", "Capital One Visa", "Capital One", AccountType.CREDIT_CARD, "1234", false, 2, now, creditLimit = 300_000),
                account("acct_cash", "Cash Wallet", "", AccountType.CASH_WALLET, null, true, 3, now),
            )
            accounts.forEach { database.accounts().insert(it) }
            val rows = mutableListOf<TransactionEntity>()
            fun add(
                id: String,
                accountId: String,
                type: TransactionType,
                amount: Long,
                merchant: String,
                categoryId: String?,
                date: LocalDate,
                counter: String? = null,
                link: String? = null,
                effect: Long = BalanceMath.effectFor(type, amount),
            ) {
                val normalized = MerchantNormalizer.normalize(merchant)
                rows += TransactionEntity(
                    id = id,
                    accountId = accountId,
                    counterAccountId = counter,
                    linkId = link,
                    amountMinor = amount,
                    balanceEffectMinor = effect,
                    currency = "USD",
                    type = type.name,
                    merchantRaw = merchant,
                    merchantNormalized = normalized,
                    merchantId = null,
                    description = merchant,
                    categoryId = categoryId,
                    occurredOn = date.toString(),
                    postedOn = date.toString(),
                    detectionSource = "MANUAL",
                    confidencePercent = 100,
                    recurringPaymentId = null,
                    status = "CONFIRMED",
                    userConfirmed = true,
                    note = "",
                    fingerprint = TransactionFingerprint.of("USD", amount, accountId, date, type, normalized),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    deletedAtEpochMillis = null,
                )
            }
            add("txn_sep_pay", "acct_checking", TransactionType.INCOME, 240_000, "Payroll", "cat_paycheck", previous.atDay(1))
            add("txn_sep_groceries", "acct_checking", TransactionType.EXPENSE, 9_210, "Whole Foods", "cat_groceries", previous.atDay(3))
            add("txn_sep_chipotle", "acct_checking", TransactionType.EXPENSE, 1_480, "Chipotle", "cat_restaurants", previous.atDay(8))
            add("txn_sep_shell", "acct_checking", TransactionType.EXPENSE, 4_820, "Shell", "cat_fuel", previous.atDay(12))
            add("txn_sep_target", "acct_checking", TransactionType.EXPENSE, 6_400, "Target", "cat_household", previous.atDay(18))
            add("txn_sep_bistro", "acct_checking", TransactionType.EXPENSE, 6_240, "Local Bistro", "cat_restaurants", previous.atDay(22))
            add("txn_sep_trader", "acct_checking", TransactionType.EXPENSE, 7_135, "Trader Joe's", "cat_groceries", previous.atDay(28.coerceAtMost(previous.lengthOfMonth())))
            add("txn_sep_amazon", "acct_card", TransactionType.EXPENSE, 3_800, "AMZN MKTP", "cat_shopping_other", previous.atDay(5))
            add("txn_sep_eats", "acct_card", TransactionType.EXPENSE, 2_750, "Uber Eats", "cat_delivery", previous.atDay(14))
            add("txn_sep_netflix", "acct_card", TransactionType.EXPENSE, 1_549, "Netflix", "cat_entertainment", previous.atDay(20))
            add("txn_sep_coffee", "acct_cash", TransactionType.EXPENSE, 525, "Corner Coffee", "cat_coffee", previous.atDay(9))
            add("txn_oct_pay", "acct_checking", TransactionType.INCOME, 240_000, "Payroll", "cat_paycheck", dayInMonth(today, 1))
            add("txn_oct_groceries", "acct_checking", TransactionType.EXPENSE, 8_640, "Whole Foods", "cat_groceries", dayInMonth(today, 2))
            add("txn_oct_shell", "acct_checking", TransactionType.EXPENSE, 4_215, "SHELL OIL", "cat_fuel", dayInMonth(today, 3))
            add("txn_oct_transfer_out", "acct_checking", TransactionType.TRANSFER, 20_000, "Transfer to Ally Savings", "cat_transfer", dayInMonth(today, 4), counter = "acct_savings", link = "link_savings")
            add("txn_oct_transfer_in", "acct_savings", TransactionType.TRANSFER, 20_000, "Transfer from Chase Checking", "cat_transfer", dayInMonth(today, 4), counter = "acct_checking", link = "link_savings", effect = 20_000)
            add("txn_oct_target", "acct_checking", TransactionType.EXPENSE, 5_418, "Target", "cat_household", dayInMonth(today, 5))
            add("txn_oct_amazon", "acct_card", TransactionType.EXPENSE, 4_329, "AMAZON.COM", "cat_shopping_other", dayInMonth(today, 5))
            add("txn_oct_grill", "acct_card", TransactionType.EXPENSE, 3_800, "Neighborhood Grill", "cat_restaurants", dayInMonth(today, 6))
            add("txn_oct_eats", "acct_card", TransactionType.EXPENSE, 2_840, "Uber Eats", "cat_delivery", dayInMonth(today, 6))
            add("txn_oct_cafe", "acct_cash", TransactionType.EXPENSE, 650, "Cafe Luna", "cat_coffee", dayInMonth(today, 4))
            add("txn_oct_coffee", "acct_cash", TransactionType.EXPENSE, 575, "Corner Coffee", "cat_coffee", today)
            val merchants = linkedMapOf<String, String>()
            rows.forEach { row ->
                if (row.merchantNormalized.isNotBlank()) merchants.putIfAbsent(row.merchantNormalized, row.merchantRaw)
            }
            val merchantIds = merchants.map { (normalized, raw) ->
                val id = "merch_${normalized.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
                database.merchants().insert(MerchantEntity(id, raw, normalized, now))
                normalized to id
            }.toMap()
            rows.forEach { row ->
                database.transactions().insert(row.copy(merchantId = merchantIds[row.merchantNormalized]))
            }
            merchantIds["Shell"]?.let { id ->
                database.merchants().upsertRule(MerchantCategoryRuleEntity("rule_shell", id, "cat_fuel", 3, now))
            }
            merchantIds["Whole Foods"]?.let { id ->
                database.merchants().upsertRule(MerchantCategoryRuleEntity("rule_whole", id, "cat_groceries", 3, now))
            }
            val targets = mapOf(
                "acct_checking" to 324_000L,
                "acct_savings" to 450_000L,
                "acct_card" to -63_000L,
                "acct_cash" to 12_000L,
            )
            val stored = database.transactions().getAll()
            accounts.forEach { entity ->
                val effect = stored.filter { it.accountId == entity.id }.sumOf { it.balanceEffectMinor }
                database.accounts().update(entity.copy(openingBalanceMinor = targets.getValue(entity.id) - effect))
            }
            suspend fun recurring(
                id: String,
                name: String,
                accountId: String,
                categoryId: String,
                direction: String,
                kind: String,
                amount: Long,
                daysAhead: Long,
                priority: String,
                confidence: String,
            ) {
                val date = today.plusDays(daysAhead)
                database.recurring().insert(
                    RecurringPaymentEntity(
                        id = id,
                        name = name,
                        accountId = accountId,
                        categoryId = categoryId,
                        direction = direction,
                        kind = kind,
                        amountMinor = amount,
                        currency = "USD",
                        frequency = "MONTHLY",
                        intervalDays = null,
                        dayOfMonth = date.dayOfMonth,
                        dayOfWeek = null,
                        startOn = date.toString(),
                        endOn = null,
                        priority = priority,
                        incomeConfidence = confidence,
                        active = true,
                        note = "",
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                )
            }
            recurring("rec_insurance", "Insurance", "acct_checking", "cat_insurance", "OUTFLOW", "BILL", 18_600, 2, "MANDATORY", "NONE")
            recurring("rec_phone", "Phone", "acct_checking", "cat_phone", "OUTFLOW", "BILL", 4_500, 7, "MANDATORY", "NONE")
            recurring("rec_utilities", "Utilities", "acct_checking", "cat_utilities", "OUTFLOW", "BILL", 13_200, 7, "MANDATORY", "NONE")
            recurring("rec_rent", "Rent", "acct_checking", "cat_rent", "OUTFLOW", "BILL", 145_000, 12, "MANDATORY", "NONE")
            recurring("rec_card", "Capital One payment", "acct_checking", "cat_card_payment", "OUTFLOW", "DEBT", 20_000, 17, "MANDATORY", "NONE")
            recurring("rec_netflix", "Netflix", "acct_card", "cat_entertainment", "OUTFLOW", "SUBSCRIPTION", 1_549, 10, "OPTIONAL", "NONE")
            recurring("rec_pay", "Paycheck", "acct_checking", "cat_paycheck", "INFLOW", "INCOME", 240_000, 23, "IMPORTANT", "HIGH")
            listOf(
                BudgetEntity("bud_groceries", "Groceries", "cat_groceries", null, "MONTHLY", 40_000, "USD", true, true, now, now),
                BudgetEntity("bud_dining", "Dining", "cat_food", null, "MONTHLY", 28_000, "USD", true, true, now, now),
                BudgetEntity("bud_fuel", "Fuel", "cat_fuel", null, "MONTHLY", 15_000, "USD", true, true, now, now),
                BudgetEntity("bud_shopping", "Shopping", "cat_shopping", null, "MONTHLY", 15_000, "USD", true, true, now, now),
            ).forEach { database.budgets().insert(it) }
            database.settings().upsert(
                defaultSettings(now).copy(
                    primaryCurrency = "USD",
                    minimumBufferMinor = 50_000,
                    horizonDays = 30,
                    onboardingCompleted = true,
                ),
            )
        }
        preferences.setSampleData(true)
    }

    private fun dayInMonth(today: LocalDate, day: Int): LocalDate {
        val month = YearMonth.from(today)
        val date = month.atDay(day.coerceIn(1, month.lengthOfMonth()))
        return if (date.isAfter(today)) month.atDay(1) else date
    }

    private fun account(
        id: String,
        name: String,
        institution: String,
        type: AccountType,
        lastFour: String?,
        liquid: Boolean,
        sort: Int,
        now: Long,
        creditLimit: Long? = null,
    ) = AccountEntity(
        id = id,
        name = name,
        institution = institution,
        type = type.name,
        lastFour = lastFour,
        openingBalanceMinor = 0,
        creditLimitMinor = creditLimit,
        currency = "USD",
        active = true,
        includeInNetWorth = true,
        includeInSafeToSpend = liquid,
        syncStatus = SyncStatus.MANUAL.name,
        lastSyncedAtEpochMillis = null,
        syncError = null,
        sortOrder = sort,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoModule {
    @Binds
    abstract fun bind(impl: DebugDemoSeeder): DemoSeeder
}
