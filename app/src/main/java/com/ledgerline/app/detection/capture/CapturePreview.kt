package com.ledgerline.app.detection.capture

/** A short excerpt kept with a capture. Long digit runs (account, reference, and phone numbers) are masked. */
object CapturePreview {
    private const val MAX_LENGTH = 160
    private val longNumber = Regex("""\d{5,}""")

    fun of(title: String, body: String): String {
        // A sender that is only a number or short code ("33789", "+1 555…") says nothing useful.
        val sender = title.takeIf { it.any(Char::isLetter) }.orEmpty()
        val text = listOf(sender, body).filter { it.isNotBlank() }.joinToString(" · ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val masked = longNumber.replace(text) { "•••" + it.value.takeLast(2) }
        return if (masked.length <= MAX_LENGTH) masked else masked.take(MAX_LENGTH - 1).trimEnd() + "…"
    }
}
