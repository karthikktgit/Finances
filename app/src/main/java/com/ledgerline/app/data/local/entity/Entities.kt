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

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val primaryCurrency: String,
    val minimumBufferMinor: Long,
    val horizonDays: Int,
    val onboardingCompleted: Boolean,
    val updatedAtEpochMillis: Long,
)
