package com.ledgerline.app.detection.capture

import java.math.BigDecimal

enum class MoneyDirection { OUT, IN }

/** What a payment notification says, before it becomes a transaction. Nothing here touches balances. */
data class SpendSignal(
    val amountMinor: Long,
    /** ISO code when the text names one ("$", "Rs", "INR", "€"...). Null means use the app currency. */
    val currency: String?,
    val merchant: String?,
    val lastFour: String?,
    val direction: MoneyDirection,
    val refund: Boolean,
)

/**
 * Reads SMS, email, and wallet notification text. It is deliberately strict: a notification needs an amount and a
 * word that says money moved. One-time passcodes, bill reminders, offers, requests, card-bill payments, and declined
 * payments are skipped. For text messages and email, the text must also read like a bank alert (a card or account
 * ending, or banking words), so a friend's "I paid $12 for lunch" is not picked up.
 */
object SpendTextParser {
    private val currencyTokens = listOf(
        "US$" to "USD", "USD" to "USD", "$" to "USD",
        "₹" to "INR", "INR" to "INR", "Rs." to "INR", "Rs" to "INR",
        "€" to "EUR", "EUR" to "EUR",
        "£" to "GBP", "GBP" to "GBP",
        "CA$" to "CAD", "CAD" to "CAD",
        "AED" to "AED", "SGD" to "SGD", "AUD" to "AUD",
    )

    private val tokenPattern = currencyTokens.joinToString("|") { Regex.escape(it.first) }
    private const val NUMBER = """[0-9][0-9,]*(?:\.[0-9]{1,2})?"""

    // Currency before the number ("$12.50", "Rs. 1,250", "INR 500.00"), or after it ("500.00 INR", "12,50 €").
    private val amountBefore = Regex("""(?i)(?<![A-Za-z])($tokenPattern)\s?($NUMBER)""")
    private val amountAfter = Regex("""(?i)(?<![0-9.,])($NUMBER)\s?(USD|INR|EUR|GBP|CAD|AED|SGD|AUD|€|£|₹)(?![A-Za-z])""")

    // Some banks leave the currency out: "A/C X1234 debited by 250.0".
    private val amountBare = Regex("""(?i)(?:debited|credited|withdrawn)\s+(?:by|for|with|of)\s+($NUMBER)""")

    private val ignorePhrases = listOf(
        "otp", "one time password", "one-time password", "verification code", "security code", "passcode",
        "is due", "due on", "due by", "due date", "payment due", "minimum due", "min due", "amount due", "total due",
        "will be debited", "will be charged", "is scheduled", "upcoming payment", "reminder",
        "requested", "request of", "requesting", "has requested", "collect request",
        "declined", "failed", "unsuccessful", "was not completed", "could not be processed", "reversed due to failure",
        "offer", "cashback up to", "cashback of up to", "pre-approved", "pre approved", "eligible for", "voucher",
        "statement is ready", "statement generated",
        // A shop's own receipt or order email is not a bank alert; the bank's alert for the same purchase is.
        "order confirmation", "purchase confirmation", "booking confirmation", "confirmation #", "confirmation number",
        "thank you for your order", "thank you for your ticket order", "thank you for shopping", "your order has",
        "view web version", "view in browser", "unsubscribe",
        // A card bill being paid shows up as a debit from the bank as well; counting both would double it.
        "payment received", "payment has been received", "thank you for your payment", "payment posted", "payment was received",
    )

    private val refundWords = listOf("refund", "refunded", "reversal", "reversed", "returned", "cashback of", "cashback credited")
    private val outWords = listOf(
        "debited", "debit of", "spent", "you spent", "paid", "you paid", "payment of", "sent", "you sent",
        "purchase", "purchased", "withdrawn", "withdrawal", "charged", "transaction of", "made a", "txn of",
        "used at", "used for", "card was used", "transferred to", "transaction with", "charge of", "pos",
        "approved", "withdrew", "payment to", "debit card transaction",
    )
    private val inWords = listOf(
        "credited", "received", "you received", "deposited", "deposit of", "direct deposit", "added to",
        "sent you", "paid you", "salary",
    )

    /** Words a bank or card alert uses and a chat message rarely does. */
    private val bankWords = listOf(
        "bank", "card", "a/c", "acct", "account", "upi", "ref", "txn", "transaction", "debit", "debited", "credited",
        "credit", "wallet", "balance", "avl", "avbl", "purchase", "venmo", "zelle", "paypal", "cash app", "imps", "neft",
        "deposit", "direct deposit", "atm",
        // Bank names that appear as the sender or in the text.
        "chase", "citi", "amex", "american express", "discover", "capital one", "wells fargo", "bank of america",
        "barclays", "hsbc", "monzo", "revolut", "hdfc", "icici", "sbi", "axis", "kotak",
    )

    private fun phrase(word: String) = Regex("""(?i)(?<![A-Za-z])""" + Regex.escape(word) + """(?![A-Za-z])""")

    private val ignoreRegexes = ignorePhrases.map { phrase(it.trim()) }
    private val refundRegexes = refundWords.map { phrase(it) }
    private val outRegexes = outWords.map { phrase(it) }
    private val inRegexes = inWords.map { phrase(it) }
    private val bankRegexes = bankWords.map { phrase(it) }
    private val bareTransaction = phrase("transaction")

    // "Your payment of $500.00 has been received": a card bill being paid, which the bank debit already covers.
    private val cardBillPaid = Regex("""(?i)payment\b.{0,40}\b(?:has been |was )?received""")

    /**
     * @param body the notification text
     * @param title the notification title. Messaging apps put the sender here; wallet apps often put the summary here.
     * @param tapToPay true for wallet apps whose payment notices carry only an amount ("$4.50 with Visa ••1234")
     * @param requireBankWording true for text-message and email apps, where most notifications are personal
     */
    fun parse(body: String, title: String = "", tapToPay: Boolean = false, requireBankWording: Boolean = false): SpendSignal? {
        val flat = squash(body)
        val heading = squash(title)
        if (flat.isEmpty() && heading.isEmpty()) return null
        val everything = "$flat $heading".trim()
        if (ignoreRegexes.any { it.containsMatchIn(everything) } || cardBillPaid.containsMatchIn(everything)) return null
        val (amount, currency) = amount(flat) ?: amount(heading) ?: return null
        if (amount <= 0) return null
        val lastFour = lastFour(flat) ?: lastFour(heading)
        if (requireBankWording && lastFour == null && bankRegexes.none { it.containsMatchIn(everything) }) return null
        val refund = refundRegexes.any { it.containsMatchIn(everything) }
        val direction = direction(flat, refund)
            ?: direction(heading, refund)
            ?: if (tapToPay || bareTransaction.containsMatchIn(everything)) MoneyDirection.OUT else return null
        val merchant = merchant(flat, direction)
            ?: merchant(heading, direction)
            ?: heading.takeIf { tapToPay && it.isNotBlank() && amount(it) == null }?.let { tidy(it) }
        return SpendSignal(
            amountMinor = amount,
            currency = currency,
            merchant = merchant,
            lastFour = lastFour,
            direction = direction,
            refund = refund,
        )
    }

    private fun squash(text: String) = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()

    fun amount(text: String): Pair<Long, String?>? {
        val before = amountBefore.find(text)
        val after = amountAfter.find(text)
        // Use whichever amount appears first in the text; the first amount is the transaction, later ones are balances.
        val pick = listOfNotNull(before, after).minByOrNull { it.range.first }
        if (pick == null) {
            val bare = amountBare.find(text) ?: return null
            return toMinor(bare.groupValues[1])?.let { it to null }
        }
        val (number, code) = if (pick === before) {
            pick.groupValues[2] to codeFor(pick.groupValues[1])
        } else {
            pick.groupValues[1] to codeFor(pick.groupValues[2])
        }
        return toMinor(number)?.let { it to code }
    }

    private fun toMinor(number: String): Long? {
        // "12,50" is a European decimal comma; "1,250" and "1,00,000" are grouping.
        val decimalComma = !number.contains('.') && Regex("""^\d{1,3},\d{2}$""").matches(number)
        val digits = if (decimalComma) number.replace(',', '.') else number.replace(",", "")
        return runCatching { BigDecimal(digits).movePointRight(2).longValueExact() }.getOrNull()
    }

    private fun codeFor(token: String): String? =
        currencyTokens.firstOrNull { it.first.equals(token, ignoreCase = true) }?.second

    private fun direction(text: String, refund: Boolean): MoneyDirection? {
        if (refund) return MoneyDirection.IN
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val outAt = outRegexes.mapNotNull { it.find(text)?.range?.first }.minOrNull()
        val inAt = inRegexes.mapNotNull { it.find(text)?.range?.first }.minOrNull()
        return when {
            outAt == null && inAt == null -> null
            // "Rs 500 debited from a/c ... credited to X" is money out: the first verb decides.
            inAt == null -> MoneyDirection.OUT
            outAt == null -> MoneyDirection.IN
            // "X sent you $20" and "X paid you" put "sent"/"paid" before "you", which is money in.
            "sent you" in lower || "paid you" in lower -> MoneyDirection.IN
            outAt <= inAt -> MoneyDirection.OUT
            else -> MoneyDirection.IN
        }
    }

    // Boundaries use "not a letter" rather than \b, because some banks run words together ("*1234To Zomato").
    private val merchantPatterns = listOf(
        // Chase style "You made a $4.50 transaction with STARBUCKS"
        Regex("""(?i)transaction with\s+([^.,;:\n]{2,40})"""),
        Regex("""(?i)(?<![A-Za-z])VPA\s+([A-Za-z0-9._-]+@[A-Za-z0-9.-]+)"""),
        // Axis style "UPI/P2M/512345678901/BLINKIT"
        Regex("""(?i)UPI/(?:P2[AM]/)?(?:\d+/)?([A-Za-z][A-Za-z0-9 &.'-]{1,30})"""),
        // Venmo: "You paid Jane Doe $15.00", "Jane Doe charged you $12.00"
        Regex("""(?i)you paid\s+([A-Za-z][^$₹€£\d.,;:\n]{1,40}?)\s+(?:US\$|\$|₹|€|£|Rs|INR|USD)"""),
        Regex("""([A-Z][A-Za-z0-9&']*(?: [A-Z][A-Za-z0-9&']*){0,3}) charged you"""),
        Regex("""(?i)(?<![A-Za-z])(?:at|@)\s+([A-Za-z0-9][^.,;:\n]{1,40})"""),
        Regex("""(?i)(?<![A-Za-z])(?:paid to|sent to|payment to|transferred to|trf to|to)\s+([A-Za-z0-9][^.,;:\n]{1,40})"""),
        Regex("""(?i)(?<![A-Za-z])towards\s+([A-Za-z0-9][^.,;:\n]{1,40})"""),
        // ICICI style "...; AMAZON PAY credited."
        Regex("""(?i)[;.]\s*([A-Za-z][A-Za-z0-9&' ]{1,40}?)\s+credited"""),
        Regex("""(?i)(?<![A-Za-z])Info:?\s*([A-Za-z0-9][^.,;:\n]{1,40})"""),
    )
    private val incomingPatterns = listOf(
        Regex("""([A-Z][A-Za-z0-9&']*(?: [A-Z][A-Za-z0-9&']*){0,3}) (?:sent you|paid you)"""),
        Regex("""(?i)(?<![A-Za-z])from\s+([A-Za-z0-9][^.,;:\n]{1,40})"""),
        Regex("""(?i)(?<![A-Za-z])by\s+([A-Za-z0-9][^.,;:\n]{1,40})"""),
    )
    private val stopWords = Regex(
        """(?i)(?:\s+|(?<=\d))(?:on|using|via|ref|refno|ref\.?no|upi|avl|avbl|bal|balance|for|with|dated|thru|through|from|in|is|was|has|at|txn|card|a/c|acct|if|not|call|-)(?![A-Za-z]).*$""",
    )
    private val accountWords = Regex("""(?i)^(?:your\s+)?(?:a/c|ac|acct|account|card|bank|hdfc|icici|sbi|axis|kotak|chase|citi|amex)(?![A-Za-z])""")
    private val leadingWords = Regex("""(?i)^(?:your|an|a|the)\s+""")
    private val transferPrefix = Regex("""(?i)^(?:NEFT|IMPS|RTGS|UPI|ACH|POS)[-/ ]+""")
    private val trailingNumbers = Regex("""(?:\s+#?[A-Za-z]*\d[\w-]*)+$""")
    private val boilerplate = Regex("""(?i)merchant keyed|location where|where a merchant|by phone|by mail|exchanged purchase|^(?:a )?location$|^merchant$""")

    fun merchant(text: String, direction: MoneyDirection): String? {
        if (text.isBlank()) return null
        val patterns = if (direction == MoneyDirection.IN) incomingPatterns + merchantPatterns else merchantPatterns
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                val name = tidy(match.groupValues[1]) ?: continue
                return name
            }
        }
        return null
    }

    private fun tidy(raw: String): String? {
        var name = raw.trim()
        name = stopWords.replace(name, "").trim()
        name = transferPrefix.replace(name, "")
        name = leadingWords.replace(name, "")
        if (!name.contains('@')) {
            // Store and terminal numbers: "COSTCO WHSE #0123", "SHELL OIL 57444", "TESCO STORES 1234".
            val withoutNumbers = trailingNumbers.replace(name, "").trim()
            if (withoutNumbers.length >= 2) name = withoutNumbers
        }
        name = name.trimEnd('.', ',', '-', ' ', ':', '®', '!')
        if (name.length < 2) return null
        // Bank boilerplate is not a shop: "at a location where a merchant keyed...", "online, by phone, by mail".
        if (boilerplate.containsMatchIn(name)) return null
        if (name.count { it.isDigit() } > name.length / 2) return null
        if (accountWords.containsMatchIn(name)) return null
        if (name.contains('@')) {
            // A UPI handle like "swiggy.store@axisbank" reads better as "Swiggy Store".
            name = name.substringBefore('@').replace('.', ' ').replace('_', ' ').replace('-', ' ').trim()
            if (name.length < 2) return null
        }
        // A name written all in capitals ("BEST BUY") is title-cased word by word. In mixed names only long
        // all-capital words are, so short ones such as ATM or LLC keep their capitals.
        val words = name.split(' ').filter { it.isNotBlank() }
        val allCaps = name.any { it.isLetter() } && name == name.uppercase()
        return words.joinToString(" ") { word ->
            val keepCaps = word.length <= 3 && (!allCaps || words.size == 1)
            val shouty = word == word.uppercase() && !keepCaps
            val quiet = word == word.lowercase()
            if (shouty || quiet) word.lowercase().replaceFirstChar { it.titlecase() } else word
        }.ifBlank { null }
    }

    private val lastFourPatterns = listOf(
        Regex("""(?i)(?:ending|ending in|ends with|ending with)\s*[:#]?\s*(\d{4})(?!\d)"""),
        Regex("""(?i)(?:[xX*•]{2,}|(?<![A-Za-z])[xX*])\s?(\d{4})(?!\d)"""),
        Regex("""(?i)(?:a/c|acct|account|card)\s*(?:no\.?|number|#)?\s*[:.]?\s*(?:[xX*•]+)?(\d{4})(?!\d)"""),
    )

    fun lastFour(text: String): String? =
        lastFourPatterns.firstNotNullOfOrNull { pattern -> pattern.find(text)?.groupValues?.get(1) }
}
