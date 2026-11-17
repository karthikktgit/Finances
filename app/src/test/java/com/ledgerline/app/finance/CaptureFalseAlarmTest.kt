package com.ledgerline.app.finance

import com.ledgerline.app.detection.capture.CaptureDedupe
import com.ledgerline.app.detection.capture.SpendTextParser
import com.ledgerline.app.detection.capture.CaptureChannel
import com.ledgerline.app.domain.model.CaptureStatus
import com.ledgerline.app.domain.model.CapturedSpend
import com.ledgerline.app.detection.capture.MoneyDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The false and repeated capture alerts seen on a real phone, and what they should become. */
class CaptureFalseAlarmTest {
    @Test
    fun aBankDebitEmailHasNoMerchantRatherThanItsBoilerplate() {
        val signal = SpendTextParser.parse(
            body = "Your debit card was used Your debit card was used online, by phone, by mail or at a location where a merchant keyed in your card. Amount: \$16.69 Card ending in 1234",
            title = "Bank of America",
            requireBankWording = true,
        )!!
        assertEquals(1_669L, signal.amountMinor)
        assertEquals(MoneyDirection.OUT, signal.direction)
        assertNull(signal.merchant)
    }

    @Test
    fun aShopsOrderConfirmationIsNotAPaymentOrARefund() {
        val receipt = SpendTextParser.parse(
            body = "Fandango Purchase Confirmation - Please Read Thank you for your ticket order. View Web Version Confirmation #61 Total \$50.07. Refunds are not available for prior exchanged purchases.",
            title = "Fandango",
            requireBankWording = true,
        )
        assertNull(receipt)
    }

    @Test
    fun theSamePaymentFromAnotherAppHoursLaterIsOneCapture() {
        val wallet = capture("com.google.android.apps.walletnfcrel", 5_007, at = 0)
        val bankEmail = capture("com.google.android.gm", 5_007, at = 3 * 60 * 60 * 1000L)
        assertTrue(CaptureDedupe.isSamePayment(wallet, bankEmail))
        // The same app a few hours later is a new purchase (you bought it twice).
        assertFalse(CaptureDedupe.isSamePayment(wallet, capture("com.google.android.apps.walletnfcrel", 5_007, at = 3 * 60 * 60 * 1000L)))
        // A different amount, or more than a day later, is not the same payment.
        assertFalse(CaptureDedupe.isSamePayment(wallet, capture("com.google.android.gm", 5_008, at = 60_000)))
        assertFalse(CaptureDedupe.isSamePayment(wallet, capture("com.google.android.gm", 5_007, at = 25 * 60 * 60 * 1000L)))
    }

    private fun capture(pkg: String, amount: Long, at: Long) = CapturedSpend(
        id = "$pkg$at", packageName = pkg, sourceLabel = pkg, channel = CaptureChannel.WALLET, amountMinor = amount, currency = "USD",
        merchant = "", lastFour = null, direction = MoneyDirection.OUT, refund = false, occurredOn = LocalDate.of(2026, 11, 7),
        capturedAtEpochMillis = at, preview = "", status = CaptureStatus.PENDING, transactionId = null,
    )
}
