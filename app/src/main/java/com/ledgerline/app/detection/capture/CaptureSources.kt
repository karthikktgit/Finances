package com.ledgerline.app.detection.capture

import com.ledgerline.app.detection.parser.InstitutionCatalog

enum class CaptureChannel { SMS, EMAIL, WALLET, BANK }

data class CaptureApp(val packageName: String, val label: String, val channel: CaptureChannel)

/**
 * Apps whose notifications can carry payment alerts. SMS and email apps carry the alerts your bank already sends,
 * so the bank's own app is not needed. Every app stays off until you turn it on.
 */
object CaptureSources {
    val apps: List<CaptureApp> = listOf(
        CaptureApp("com.google.android.apps.messaging", "Google Messages", CaptureChannel.SMS),
        CaptureApp("com.samsung.android.messaging", "Samsung Messages", CaptureChannel.SMS),
        CaptureApp("com.android.mms", "Messages", CaptureChannel.SMS),
        CaptureApp("com.truecaller", "Truecaller", CaptureChannel.SMS),
        CaptureApp("com.google.android.gm", "Gmail", CaptureChannel.EMAIL),
        CaptureApp("com.microsoft.office.outlook", "Outlook", CaptureChannel.EMAIL),
        CaptureApp("com.yahoo.mobile.client.android.mail", "Yahoo Mail", CaptureChannel.EMAIL),
        CaptureApp("com.samsung.android.email.provider", "Samsung Email", CaptureChannel.EMAIL),
        CaptureApp("com.google.android.apps.walletnfcrel", "Google Wallet", CaptureChannel.WALLET),
        CaptureApp("com.samsung.android.spay", "Samsung Wallet", CaptureChannel.WALLET),
        CaptureApp("com.paypal.android.p2pmobile", "PayPal", CaptureChannel.WALLET),
        CaptureApp("com.venmo", "Venmo", CaptureChannel.WALLET),
        CaptureApp("com.squareup.cash", "Cash App", CaptureChannel.WALLET),
        CaptureApp("com.zellepay.zelle", "Zelle", CaptureChannel.WALLET),
        CaptureApp("com.google.android.apps.nbu.paisa.user", "Google Pay (India)", CaptureChannel.WALLET),
        CaptureApp("com.phonepe.app", "PhonePe", CaptureChannel.WALLET),
        CaptureApp("net.one97.paytm", "Paytm", CaptureChannel.WALLET),
    ) + InstitutionCatalog.knownApps
        .filter { (pkg, _) -> pkg !in setOf("com.paypal.android.p2pmobile", "com.venmo", "com.squareup.cash", "com.google.android.apps.walletnfcrel") }
        .map { (pkg, label) -> CaptureApp(pkg, label, CaptureChannel.BANK) }

    /** Source id for texts read straight from Android's incoming-SMS broadcast rather than from a notification. */
    const val SMS_DIRECT = "ledgerline.sms"

    private val smsDirect = CaptureApp(SMS_DIRECT, "Text message", CaptureChannel.SMS)

    fun find(packageName: String): CaptureApp? =
        if (packageName == SMS_DIRECT) smsDirect else apps.firstOrNull { it.packageName == packageName }

    /**
     * Android 15 and later can replace a notification's text with this for apps like Ledgerline when it judges the
     * content sensitive. The title is cleared at the same time.
     */
    fun isHiddenByAndroid(title: String, body: String): Boolean =
        title.isBlank() && body.trim().equals("Sensitive notification content hidden", ignoreCase = true)

    fun channel(packageName: String): CaptureChannel = find(packageName)?.channel ?: CaptureChannel.BANK

    /** Tap-to-pay wallets post "Merchant" as the title and only an amount as the text. */
    fun isTapToPay(packageName: String): Boolean =
        packageName == "com.google.android.apps.walletnfcrel" || packageName == "com.samsung.android.spay"

    fun channelLabel(channel: CaptureChannel): String = when (channel) {
        CaptureChannel.SMS -> "Text messages"
        CaptureChannel.EMAIL -> "Email"
        CaptureChannel.WALLET -> "Wallets and payment apps"
        CaptureChannel.BANK -> "Bank and card apps"
    }
}
