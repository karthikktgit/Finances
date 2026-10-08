package com.ledgerline.app.finance.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

object MoneyFormat {
    fun fractionDigits(currencyCode: String): Int {
        val currency = currencyOrUsd(currencyCode)
        return currency.defaultFractionDigits.coerceAtLeast(0)
    }

    fun format(minor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val digits = fractionDigits(currencyCode)
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = currencyOrUsd(currencyCode)
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        val major = BigDecimal.valueOf(minor).movePointLeft(digits)
        return format.format(major)
    }

    fun formatPlain(minor: Long, currencyCode: String): String {
        val digits = fractionDigits(currencyCode)
        return BigDecimal.valueOf(minor)
            .movePointLeft(digits)
            .setScale(digits, RoundingMode.HALF_UP)
            .toPlainString()
    }

    fun signedLabel(minor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val body = format(abs(minor), currencyCode, locale)
        return when {
            minor > 0 -> "+$body"
            minor < 0 -> "−$body"
            else -> body
        }
    }

    private fun currencyOrUsd(currencyCode: String): Currency =
        runCatching { Currency.getInstance(currencyCode) }.getOrElse { Currency.getInstance("USD") }
}
