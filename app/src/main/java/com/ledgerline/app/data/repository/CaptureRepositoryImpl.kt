package com.ledgerline.app.data.repository

import com.ledgerline.app.data.local.database.LedgerDatabase
import com.ledgerline.app.data.local.entity.CapturedSpendEntity
import com.ledgerline.app.detection.capture.CaptureChannel
import com.ledgerline.app.detection.capture.CaptureDedupe
import com.ledgerline.app.detection.capture.MoneyDirection
import com.ledgerline.app.domain.model.CaptureStatus
import com.ledgerline.app.domain.model.CapturedSpend
import com.ledgerline.app.domain.repository.CaptureRepository
import com.ledgerline.app.domain.time.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureRepositoryImpl @Inject constructor(
    private val database: LedgerDatabase,
    private val clock: AppClock,
) : CaptureRepository {
    override fun observePending(): Flow<List<CapturedSpend>> =
        database.capturedSpends().observePending().map { rows -> rows.map { it.toDomain() } }

    override fun observeRecent(): Flow<List<CapturedSpend>> =
        database.capturedSpends().observeRecent().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): CapturedSpend? = database.capturedSpends().get(id)?.toDomain()

    override suspend fun record(capture: CapturedSpend, fingerprint: String): Boolean {
        val dao = database.capturedSpends()
        // The same payment often arrives twice: an SMS and a wallet notice, or a notification that is updated.
        val since = CaptureDedupe.windowStart(capture.capturedAtEpochMillis)
        if (dao.similarSince(capture.amountMinor, capture.direction.name, since) != null) return false
        // The same payment reported later by another app (a bank email after a wallet notice).
        val otherSince = CaptureDedupe.otherSourceWindowStart(capture.capturedAtEpochMillis)
        if (dao.similarFromOtherSince(capture.amountMinor, capture.direction.name, capture.packageName, otherSince) != null) return false
        val inserted = dao.insert(capture.toEntity(fingerprint)) != -1L
        dao.pruneHandled(clock.now().toEpochMilli() - KEEP_HANDLED_MILLIS)
        return inserted
    }

    override suspend fun setStatus(id: String, status: CaptureStatus, transactionId: String?) {
        database.capturedSpends().setStatus(id, status.name, transactionId)
    }

    private companion object {
        const val KEEP_HANDLED_MILLIS = 60L * 24 * 60 * 60 * 1000
    }
}

private fun CapturedSpendEntity.toDomain() = CapturedSpend(
    id = id,
    packageName = packageName,
    sourceLabel = sourceLabel,
    channel = runCatching { CaptureChannel.valueOf(channel) }.getOrDefault(CaptureChannel.BANK),
    amountMinor = amountMinor,
    currency = currency,
    merchant = merchant,
    lastFour = lastFour,
    direction = runCatching { MoneyDirection.valueOf(direction) }.getOrDefault(MoneyDirection.OUT),
    refund = refund,
    occurredOn = LocalDate.parse(occurredOn),
    capturedAtEpochMillis = capturedAtEpochMillis,
    preview = preview,
    status = runCatching { CaptureStatus.valueOf(status) }.getOrDefault(CaptureStatus.PENDING),
    transactionId = transactionId,
)

private fun CapturedSpend.toEntity(fingerprint: String) = CapturedSpendEntity(
    id = id,
    packageName = packageName,
    sourceLabel = sourceLabel,
    channel = channel.name,
    amountMinor = amountMinor,
    currency = currency,
    merchant = merchant,
    lastFour = lastFour,
    direction = direction.name,
    refund = refund,
    occurredOn = occurredOn.toString(),
    capturedAtEpochMillis = capturedAtEpochMillis,
    preview = preview,
    status = status.name,
    transactionId = transactionId,
    fingerprint = fingerprint,
)
