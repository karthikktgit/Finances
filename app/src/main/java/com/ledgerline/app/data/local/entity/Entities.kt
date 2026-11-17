package com.ledgerline.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
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
    val lastSyncedAtEpochMillis: Long?,
    val syncError: String?,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val necessity: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "merchants",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class MerchantEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val normalizedName: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "merchant_category_rules",
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["merchantId"], unique = true), Index("categoryId")],
)
data class MerchantCategoryRuleEntity(
    @PrimaryKey val id: String,
    val merchantId: String,
    val categoryId: String,
    val confirmations: Int,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["counterAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = MerchantEntity::class, parentColumns = ["id"], childColumns = ["merchantId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = RecurringPaymentEntity::class, parentColumns = ["id"], childColumns = ["recurringPaymentId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("accountId"),
        Index("counterAccountId"),
        Index("categoryId"),
        Index("merchantId"),
        Index("occurredOn"),
        Index("fingerprint"),
        Index("linkId"),
        Index("recurringPaymentId"),
        Index("deletedAtEpochMillis"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val counterAccountId: String?,
    val linkId: String?,
    val amountMinor: Long,
    val balanceEffectMinor: Long,
    val currency: String,
    val type: String,
    val merchantRaw: String,
    val merchantNormalized: String,
    val merchantId: String?,
    val description: String,
    val categoryId: String?,
    val occurredOn: String,
    val postedOn: String?,
    val detectionSource: String,
    val confidencePercent: Int,
    val recurringPaymentId: String?,
    val status: String,
    val userConfirmed: Boolean,
    val note: String,
    val fingerprint: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
    val unplanned: Boolean = false,
    /** False for a spend you planned but have not paid yet; it does not count until it is paid. */
    @androidx.room.ColumnInfo(defaultValue = "1") val paid: Boolean = true,
    /** The day it was planned for, kept after it is paid so planned and paid can be compared. */
    val plannedOn: String? = null,
)

@Entity(
    tableName = "recurring_payments",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("accountId"), Index("categoryId")],
)
data class RecurringPaymentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val accountId: String,
    val categoryId: String?,
    val direction: String,
    val kind: String,
    val amountMinor: Long,
    val currency: String,
    val frequency: String,
    val intervalDays: Int?,
    val dayOfMonth: Int?,
    val dayOfWeek: Int?,
    val startOn: String,
    val endOn: String?,
    val priority: String,
    val incomeConfidence: String,
    val active: Boolean,
    val note: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val maxOccurrences: Int? = null,
    /** Due dates on or after this day are marked paid automatically (autopay). Null means paid by hand. */
    val autopaySince: String? = null,
)

@Entity(
    tableName = "recurring_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = RecurringPaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringPaymentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["recurringPaymentId", "occurrenceOn"], unique = true),
        Index("recurringPaymentId"),
    ],
)
data class RecurringOccurrenceEntity(
    @PrimaryKey val id: String,
    val recurringPaymentId: String,
    val occurrenceOn: String,
    val state: String,
    val amountMinor: Long?,
    val name: String?,
    val paidTransactionId: String?,
    val note: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("categoryId"), Index("accountId")],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String?,
    val accountId: String?,
    val period: String,
    val amountMinor: Long,
    val currency: String,
    val reservesSafeToSpend: Boolean,
    val active: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    /** First day of the first cycle it is planned in; null means always. */
    val startOn: String? = null,
    /** First day of the last cycle it is planned in; null means it goes on. */
    val endOn: String? = null,
    /** How many cycles it runs for, when it was set that way (shown when editing). */
    val maxCycles: Int? = null,
)

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId")],
)
data class DebtEntity(
    @PrimaryKey val id: String,
    val name: String,
    val accountId: String?,
    val principalMinor: Long,
    val balanceMinor: Long,
    val aprBasisPoints: Int,
    val minimumPaymentMinor: Long,
    val dueDayOfMonth: Int,
    val frequency: String,
    val priority: String,
    val currency: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val debtType: String = "OTHER",
    val rateType: String = "UNKNOWN",
    val paymentAccountId: String? = null,
    val statementCloseDay: Int? = null,
    val originationOn: String? = null,
    val maturityOn: String? = null,
    val remainingTermMonths: Int? = null,
    val feeMinor: Long = 0,
    val rateUpdatedOn: String? = null,
    val accruedInterestMinor: Long? = null,
    val showInCalendar: Boolean = true,
    val aprKnown: Boolean = true,
    val minimumKnown: Boolean = true,
    val purchaseAprBasisPoints: Int? = null,
    val balanceTransferAprBasisPoints: Int? = null,
    val cashAdvanceAprBasisPoints: Int? = null,
    val graceOnPurchases: Boolean = false,
    val dayCountBasis: Int = 365,
    val recurringPaymentId: String? = null,
    val note: String = "",
)

@Entity(
    tableName = "debt_month_plans",
    foreignKeys = [
        ForeignKey(entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["debtId", "yearMonth"], unique = true)],
)
data class DebtMonthPlanEntity(
    @PrimaryKey val id: String,
    val debtId: String,
    val yearMonth: String,
    val plannedPaymentMinor: Long,
    val dueOn: String?,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "debt_rate_changes",
    foreignKeys = [
        ForeignKey(entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("debtId")],
)
data class DebtRateChangeEntity(
    @PrimaryKey val id: String,
    val debtId: String,
    val effectiveOn: String,
    val aprBasisPoints: Int,
    val note: String,
)

@Entity(
    tableName = "debt_settlements",
    foreignKeys = [
        ForeignKey(entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["debtId", "yearMonth"], unique = true), Index("linkId")],
)
data class DebtSettlementEntity(
    @PrimaryKey val id: String,
    val debtId: String,
    val yearMonth: String,
    val paidOn: String,
    val amountMinor: Long,
    val transactionId: String,
    val linkId: String?,
    val interestMinor: Long,
    val principalMinor: Long,
    val feeMinor: Long,
    val splitKnown: Boolean,
)

@Entity(
    tableName = "notification_sources",
    indices = [Index(value = ["packageName"], unique = true)],
)
data class NotificationSourceEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val label: String,
    val enabled: Boolean,
)

@Entity(
    tableName = "transaction_matches",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["survivorId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["incomingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("survivorId"), Index("incomingId")],
)
data class TransactionMatchEntity(
    @PrimaryKey val id: String,
    val survivorId: String,
    val incomingId: String,
    val score: Int,
    val reasons: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "sync_records")
data class SyncRecordEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val status: String,
    val message: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "expense_notes")
data class ExpenseNoteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amountMinor: Long?,
    val dueOn: String?,
    val note: String,
    val completed: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val primaryCurrency: String,
    val minimumBufferMinor: Long,
    val horizonDays: Int,
    val onboardingCompleted: Boolean,
    val updatedAtEpochMillis: Long,
    val cycleType: String = "MONTHLY",
    val cycleAnchor: String = "2026-01-20",
    val cycleStartDay: Int = 20,
    val cycleSecondDay: Int = 5,
)

/**
 * A payment read from an SMS, email, or wallet notification. It is a suggestion only: it never changes a balance
 * until you add it, which creates a normal transaction.
 */
@Entity(
    tableName = "captured_spends",
    indices = [Index(value = ["fingerprint"], unique = true), Index("status")],
)
data class CapturedSpendEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val sourceLabel: String,
    val channel: String,
    val amountMinor: Long,
    val currency: String,
    val merchant: String,
    val lastFour: String?,
    val direction: String,
    val refund: Boolean,
    val occurredOn: String,
    val capturedAtEpochMillis: Long,
    /** A short excerpt of the notification with long numbers masked, so you can tell what it was. */
    val preview: String,
    val status: String,
    val transactionId: String?,
    val fingerprint: String,
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    val targetDate: String?,
    val accountId: String?,
    val useAccountBalance: Boolean,
    val monthlyMinor: Long?,
    val createdOn: String,
    val archived: Boolean,
    val updatedAtEpochMillis: Long,
    /** What became of the money (GoalOutcome name); null while it is still held for the goal. */
    val outcome: String? = null,
    val outcomeOn: String? = null,
)

@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(entity = SavingsGoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("goalId")],
)
data class GoalContributionEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val amountMinor: Long,
    val onDate: String,
    val note: String,
    val createdAtEpochMillis: Long,
    /** GoalEntryKind name; null for older entries (saved if positive, taken out if negative). */
    val kind: String? = null,
    val transactionId: String? = null,
)

/** A group on Home, such as "Monthly" or "Subscriptions", holding bills and spending envelopes. */
@Entity(tableName = "home_groups")
data class HomeGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
)

/** Which group a bill ("bill:<id>") or spending envelope ("budget:<id>") sits in on Home. */
@Entity(tableName = "home_group_items")
data class HomeGroupItemEntity(
    @PrimaryKey val itemKey: String,
    val groupId: String,
    val sortOrder: Int,
)

/** One cycle's own amount for a spending item ("budget:<id>") or goal ("goal:<id>"). Zero means off that cycle. */
@Entity(tableName = "cycle_amounts", primaryKeys = ["cycleStart", "itemKey"])
data class CycleAmountEntity(
    val cycleStart: String,
    val itemKey: String,
    val amountMinor: Long,
    val updatedAtEpochMillis: Long,
)

/** The start-of-cycle checklist for one cycle: statements checked (account ids, comma separated), balances, done. */
@Entity(tableName = "cycle_reviews")
data class CycleReviewEntity(
    @PrimaryKey val cycleStart: String,
    val reviewedAccounts: String,
    val balancesChecked: Boolean,
    val completedAtEpochMillis: Long?,
)
