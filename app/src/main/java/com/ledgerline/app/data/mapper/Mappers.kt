package com.ledgerline.app.data.mapper

import com.ledgerline.app.data.local.entity.AccountEntity
import com.ledgerline.app.data.local.entity.BudgetEntity
import com.ledgerline.app.data.local.entity.CategoryEntity
import com.ledgerline.app.data.local.entity.RecurringPaymentEntity
import com.ledgerline.app.data.local.entity.TransactionEntity
import com.ledgerline.app.data.local.entity.UserSettingsEntity
import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.Budget
import com.ledgerline.app.domain.model.BudgetPeriod
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.model.Category
import com.ledgerline.app.domain.model.DetectionSource
import com.ledgerline.app.domain.model.IncomeConfidence
import com.ledgerline.app.domain.model.LedgerTransaction
import com.ledgerline.app.domain.model.Necessity
import com.ledgerline.app.domain.model.PaymentPriority
import com.ledgerline.app.domain.model.RecurrenceFrequency
import com.ledgerline.app.domain.model.RecurringPayment
import com.ledgerline.app.domain.model.ScheduledKind
import com.ledgerline.app.domain.model.SyncStatus
import com.ledgerline.app.domain.model.TransactionStatus
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.domain.model.UserSettings
import com.ledgerline.app.finance.policy.FinancePolicy
import java.time.LocalDate

internal fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    institution = institution,
    type = enumValueOf<AccountType>(type),
    lastFour = lastFour,
    openingBalanceMinor = openingBalanceMinor,
    creditLimitMinor = creditLimitMinor,
    currency = currency,
    active = active,
    includeInNetWorth = includeInNetWorth,
    includeInSafeToSpend = includeInSafeToSpend,
    syncStatus = enumValueOf<SyncStatus>(syncStatus),
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
    syncError = syncError,
    sortOrder = sortOrder,
)

internal fun CategoryEntity.toDomain() = Category(
    id = id,
    parentId = parentId,
    name = name,
    necessity = enumValueOf<Necessity>(necessity),
    archived = archived,
    sortOrder = sortOrder,
)

internal fun TransactionEntity.toDomain() = LedgerTransaction(
    id = id,
    accountId = accountId,
    counterAccountId = counterAccountId,
    linkId = linkId,
    amountMinor = amountMinor,
    balanceEffectMinor = balanceEffectMinor,
    currency = currency,
    type = enumValueOf<TransactionType>(type),
    merchantRaw = merchantRaw,
    merchantNormalized = merchantNormalized,
    merchantId = merchantId,
    description = description,
    categoryId = categoryId,
    occurredOn = LocalDate.parse(occurredOn),
    postedOn = postedOn?.let(LocalDate::parse),
    detectionSource = enumValueOf<DetectionSource>(detectionSource),
    confidencePercent = confidencePercent,
    recurringPaymentId = recurringPaymentId,
    status = enumValueOf<TransactionStatus>(status),
    userConfirmed = userConfirmed,
    note = note,
    fingerprint = fingerprint,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis,
)

internal fun RecurringPaymentEntity.toDomain() = RecurringPayment(
    id = id,
    name = name,
    accountId = accountId,
    categoryId = categoryId,
    direction = enumValueOf<CashFlowDirection>(direction),
    kind = enumValueOf<ScheduledKind>(kind),
    amountMinor = amountMinor,
    currency = currency,
    frequency = enumValueOf<RecurrenceFrequency>(frequency),
    intervalDays = intervalDays,
    dayOfMonth = dayOfMonth,
    dayOfWeek = dayOfWeek,
    startOn = LocalDate.parse(startOn),
    endOn = endOn?.let(LocalDate::parse),
    priority = enumValueOf<PaymentPriority>(priority),
    incomeConfidence = enumValueOf<IncomeConfidence>(incomeConfidence),
    active = active,
    note = note,
)

internal fun BudgetEntity.toDomain() = Budget(
    id = id,
    name = name,
    categoryId = categoryId,
    accountId = accountId,
    period = enumValueOf<BudgetPeriod>(period),
    amountMinor = amountMinor,
    currency = currency,
    reservesSafeToSpend = reservesSafeToSpend,
    active = active,
)

internal fun UserSettingsEntity.toDomain() = UserSettings(
    primaryCurrency = primaryCurrency,
    minimumBufferMinor = minimumBufferMinor,
    horizonDays = horizonDays,
    onboardingCompleted = onboardingCompleted,
)

internal fun defaultSettings(now: Long) = UserSettingsEntity(
    id = 1,
    primaryCurrency = "USD",
    minimumBufferMinor = FinancePolicy.DEFAULT_BUFFER_MINOR,
    horizonDays = FinancePolicy.DEFAULT_HORIZON_DAYS,
    onboardingCompleted = false,
    updatedAtEpochMillis = now,
)
