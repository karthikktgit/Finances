package com.ledgerline.app.detection.capture

import com.ledgerline.app.domain.model.Account
import com.ledgerline.app.domain.model.AccountType
import com.ledgerline.app.domain.model.CaptureStatus
import com.ledgerline.app.domain.model.CapturedSpend
import com.ledgerline.app.domain.model.SaveResult
import com.ledgerline.app.domain.model.TransactionDraft
import com.ledgerline.app.domain.repository.AccountRepository
import com.ledgerline.app.domain.repository.CaptureRepository
import com.ledgerline.app.domain.repository.CategoryRepository
import com.ledgerline.app.domain.repository.MerchantRepository
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.finance.merchant.MerchantNormalizer
import com.ledgerline.app.finance.policy.FinancePolicy
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CaptureAddResult {
    data object Added : CaptureAddResult

    data class PossibleDuplicate(val summary: String) : CaptureAddResult

    data object NoAccount : CaptureAddResult

    data class Invalid(val message: String) : CaptureAddResult
}

object CaptureAccounts {
    /** The one active account whose ending matches the notification, if exactly one does. */
    fun matched(capture: CapturedSpend, accounts: List<Account>): Account? {
        val ending = capture.lastFour ?: return null
        return accounts.filter { it.active && !it.deleted && it.lastFour == ending }.singleOrNull()
    }

    /** The account a capture lands on unless you pick another: the matched card, else your main spending account. */
    fun suggested(capture: CapturedSpend, accounts: List<Account>): Account? {
        matched(capture, accounts)?.let { return it }
        val live = accounts.filter { it.active && !it.deleted }
        return live.firstOrNull { it.includeInSafeToSpend && it.type == AccountType.CHECKING }
            ?: live.firstOrNull { it.includeInSafeToSpend }
            ?: live.firstOrNull()
    }
}

@Singleton
class CaptureActions @Inject constructor(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val merchants: MerchantRepository,
    private val categories: CategoryRepository,
    private val captures: CaptureRepository,
) {
    suspend fun suggestedCategory(merchant: String): String? {
        if (merchant.isBlank()) return null
        val id = merchants.suggestion(MerchantNormalizer.normalize(merchant), FinancePolicy.CATEGORY_LEARN_CONFIRMATIONS) ?: return null
        return id.takeIf { categories.observe().first().firstOrNull { it.id == id }?.archived == false }
    }

    /** Turns a capture into a normal transaction. A likely duplicate is reported instead of saved unless acknowledged. */
    suspend fun add(capture: CapturedSpend, accountId: String? = null, acknowledgeDuplicate: Boolean = false): CaptureAddResult {
        val accountList = accounts.observe().first()
        val account = accountId?.let { id -> accountList.firstOrNull { it.id == id } }
            ?: CaptureAccounts.suggested(capture, accountList)
            ?: return CaptureAddResult.NoAccount
        val result = transactions.save(
            TransactionDraft(
                existingId = null,
                existingLinkId = null,
                type = capture.transactionType,
                amountMinor = capture.amountMinor,
                merchant = capture.merchant,
                accountId = account.id,
                counterAccountId = null,
                categoryId = suggestedCategory(capture.merchant),
                occurredOn = capture.occurredOn,
                note = "From ${capture.sourceLabel}",
                acknowledgeDuplicate = acknowledgeDuplicate,
            ),
        )
        return when (result) {
            SaveResult.Saved -> {
                captures.setStatus(capture.id, CaptureStatus.ADDED)
                CaptureAddResult.Added
            }
            is SaveResult.PossibleDuplicate -> CaptureAddResult.PossibleDuplicate(result.summary)
            is SaveResult.Invalid -> CaptureAddResult.Invalid(result.message)
        }
    }

    suspend fun dismiss(id: String) = captures.setStatus(id, CaptureStatus.DISMISSED)

    suspend fun markAdded(id: String) = captures.setStatus(id, CaptureStatus.ADDED)

    suspend fun restore(id: String) = captures.setStatus(id, CaptureStatus.PENDING)
}
