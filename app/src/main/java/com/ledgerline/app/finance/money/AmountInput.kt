package com.ledgerline.app.finance.money

import java.math.BigDecimal

/**
 * Typed amounts read the way people write them: "450" is 450.00 and "12.5" is 12.50.
 * Grouping commas are ignored. Extra decimals beyond the currency's precision are dropped while typing.
 */
object AmountInput {
    private const val MAX_MAJOR_DIGITS = 10

    /** Keeps only what a person could mean as an amount: digits and a single decimal point. */
    fun clean(raw: String, fractionDigits: Int): String {
        val out = StringBuilder()
        var seenPoint = false
        var decimals = 0
        var majorDigits = 0
        for (char in raw) {
            when {
                char.isDigit() && !seenPoint -> {
                    if (majorDigits >= MAX_MAJOR_DIGITS) continue
                    // No leading zeros like "007".
                    if (out.toString() == "0") out.setLength(0)
                    out.append(char)
                    majorDigits++
                }
                char.isDigit() && seenPoint -> if (decimals < fractionDigits) {
                    out.append(char)
                    decimals++
                }
                // Whole-unit currencies have no decimals; anything after the point is dropped.
                char == '.' && fractionDigits == 0 -> break
                char == '.' && !seenPoint -> {
                    if (out.isEmpty()) out.append('0')
                    out.append('.')
                    seenPoint = true
                }
            }
        }
        return out.toString()
    }

    fun toMinor(text: String, fractionDigits: Int): Long {
        val cleaned = clean(text, fractionDigits).trimEnd('.')
        if (cleaned.isEmpty()) return 0L
        return runCatching { BigDecimal(cleaned).movePointRight(fractionDigits).longValueExact() }.getOrDefault(0L)
    }

    /** Text for an existing amount; whole amounts show without decimals so editing stays simple. */
    fun fromMinor(minor: Long, fractionDigits: Int): String {
        if (minor <= 0L) return ""
        val value = BigDecimal.valueOf(minor).movePointLeft(fractionDigits)
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() <= 0) stripped.toBigInteger().toString() else value.setScale(fractionDigits).toPlainString()
    }
}
