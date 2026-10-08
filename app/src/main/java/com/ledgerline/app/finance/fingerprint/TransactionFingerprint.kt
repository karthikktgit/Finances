package com.ledgerline.app.finance.fingerprint

import com.ledgerline.app.domain.model.TransactionType
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

object TransactionFingerprint {
    fun of(
        currency: String,
        amountMinor: Long,
        accountId: String,
        date: LocalDate,
        type: TransactionType,
        normalizedMerchant: String,
    ): String {
        val payload = listOf(
            currency.trim().uppercase(Locale.US),
            abs(amountMinor).toString(),
            accountId,
            date.toString(),
            type.name,
            normalizedMerchant.trim().lowercase(Locale.US),
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
