package com.ledgerline.app.detection.parser

import com.ledgerline.app.domain.model.TransactionType

data class NotificationData(
    val packageName: String,
    val text: String,
)

data class ParsedTransaction(
    val amountMinor: Long?,
    val merchant: String?,
    val lastFour: String?,
    val type: TransactionType?,
    val parserId: String,
)

interface NotificationParser {
    val id: String

    fun canParse(notification: NotificationData): Boolean

    fun parse(notification: NotificationData): ParsedTransaction?
}

object NotificationFields {
    private val money = Regex("""(?i)(?:\$\s*|usd\s+)(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{2}))?|(?<!\d)(\d{1,6})\.(\d{2})(?!\d)""")
    private val merchant = Regex("""(?i)\bat\s+([A-Za-z0-9][A-Za-z0-9&.' ]{1,40})""")
    private val lastFour = Regex("""(?i)(?:ending(?:\s+in)?|x{2,}|•{2,}|\*{2,})\s*(\d{4})""")

    fun amountMinor(text: String): Long? {
        val match = money.find(text) ?: return null
        val dollars = match.groupValues[1].ifBlank { match.groupValues[3] }.replace(",", "")
        val cents = match.groupValues[2].ifBlank { match.groupValues[4] }
        val major = dollars.toLongOrNull() ?: return null
        val minor = if (cents.isBlank()) 0 else cents.toIntOrNull() ?: return null
        return major * 100 + minor
    }

    fun merchant(text: String): String? {
        val raw = merchant.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        if (raw.isBlank()) return null
        val cut = listOf(" using ", " on ", " with ", " for ", " via ")
            .map { raw.indexOf(it, ignoreCase = true) }
            .filter { it > 0 }
            .minOrNull() ?: raw.length
        val name = raw.substring(0, cut).trim().trimEnd('.', ',')
        return name.ifBlank { null }
    }

    fun lastFour(text: String): String? = lastFour.find(text)?.groupValues?.get(1)

    fun type(text: String): TransactionType? {
        val value = text.lowercase()
        return when {
            listOf("refund", "returned", "reversal").any { it in value } -> TransactionType.REFUND
            listOf("paycheck", "direct deposit", "salary", "deposited").any { it in value } -> TransactionType.INCOME
            listOf("card payment", "payment posted", "autopay").any { it in value } -> TransactionType.PAYMENT
            listOf("purchase", "spent", "debit", "charged").any { it in value } -> TransactionType.EXPENSE
            else -> null
        }
    }

    fun parsed(notification: NotificationData, parserId: String): ParsedTransaction? {
        val amount = amountMinor(notification.text) ?: return null
        return ParsedTransaction(
            amountMinor = amount,
            merchant = merchant(notification.text),
            lastFour = lastFour(notification.text),
            type = type(notification.text),
            parserId = parserId,
        )
    }
}

object InstitutionCatalog {
    val banks = listOf("chase", "wellsfargo", "bankofamerica", "usbank", "pnc", "citibank", "ally")
    val cards = listOf("capitalone", "americanexpress", "discover", "barclays", "citi")
    val wallets = listOf("paypal", "venmo", "cashapp", "walletnfcrel")

    val knownApps = listOf(
        "com.chase.sig.android" to "Chase",
        "com.infonow.bofa" to "Bank of America",
        "com.wf.wellsfargomobile" to "Wells Fargo",
        "com.konylabs.capitalone" to "Capital One",
        "com.citi.citimobile" to "Citi",
        "com.paypal.android.p2pmobile" to "PayPal",
        "com.venmo" to "Venmo",
        "com.squareup.cash" to "Cash App",
        "com.google.android.apps.walletnfcrel" to "Google Wallet",
    )
}

class BankNotificationParser : NotificationParser {
    override val id = "bank"

    override fun canParse(notification: NotificationData): Boolean =
        InstitutionCatalog.banks.any { notification.packageName.contains(it, ignoreCase = true) }

    override fun parse(notification: NotificationData): ParsedTransaction? = NotificationFields.parsed(notification, id)
}

class CardNotificationParser : NotificationParser {
    override val id = "card"

    override fun canParse(notification: NotificationData): Boolean =
        InstitutionCatalog.cards.any { notification.packageName.contains(it, ignoreCase = true) }

    override fun parse(notification: NotificationData): ParsedTransaction? = NotificationFields.parsed(notification, id)
}

class WalletNotificationParser : NotificationParser {
    override val id = "wallet"

    override fun canParse(notification: NotificationData): Boolean =
        InstitutionCatalog.wallets.any { notification.packageName.contains(it, ignoreCase = true) }

    override fun parse(notification: NotificationData): ParsedTransaction? = NotificationFields.parsed(notification, id)
}

class GenericNotificationParser : NotificationParser {
    override val id = "generic"

    override fun canParse(notification: NotificationData): Boolean = NotificationFields.amountMinor(notification.text) != null

    override fun parse(notification: NotificationData): ParsedTransaction? = NotificationFields.parsed(notification, id)
}

object ParserRegistry {
    val parsers: List<NotificationParser> = listOf(
        BankNotificationParser(),
        CardNotificationParser(),
        WalletNotificationParser(),
        GenericNotificationParser(),
    )

    fun parse(notification: NotificationData): ParsedTransaction? {
        val parser = parsers.firstOrNull { it.canParse(notification) } ?: return null
        return parser.parse(notification)
    }
}
