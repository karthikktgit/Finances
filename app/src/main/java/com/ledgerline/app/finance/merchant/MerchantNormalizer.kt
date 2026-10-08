package com.ledgerline.app.finance.merchant

import java.util.Locale

/**
 * Maps known raw merchant strings onto one display name and keeps the original text elsewhere.
 * Unknown merchants are cleaned, not guessed.
 */
object MerchantNormalizer {
    private val aliases = listOf(
        Regex("""\b(amzn|amazon)(\s+(mktp|marketplace|com|prime))?\b""") to "Amazon",
        Regex("""\b(whole\s*foods|wholefds)\b""") to "Whole Foods",
        Regex("""\b(uber\s*eats|ubereats)\b""") to "Uber Eats",
        Regex("""\buber\b""") to "Uber",
        Regex("""\blyft\b""") to "Lyft",
        Regex("""\bstarbucks|sbux\b""") to "Starbucks",
        Regex("""\bnetflix\b""") to "Netflix",
        Regex("""\bspotify\b""") to "Spotify",
        Regex("""\bshell\b""") to "Shell",
        Regex("""\btarget\b""") to "Target",
        Regex("""\bpaypal\b""") to "PayPal",
        Regex("""\btrader\s*joes?\b""") to "Trader Joe's",
    )

    fun normalize(raw: String): String {
        val cleaned = raw
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9&' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return ""
        aliases.firstOrNull { (pattern, _) -> pattern.containsMatchIn(cleaned) }?.let { return it.second }
        return cleaned.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
            }
        }
    }
}
