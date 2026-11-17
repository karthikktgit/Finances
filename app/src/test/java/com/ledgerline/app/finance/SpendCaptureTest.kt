package com.ledgerline.app.finance

import com.ledgerline.app.detection.capture.CapturePreview
import com.ledgerline.app.detection.capture.MoneyDirection
import com.ledgerline.app.detection.capture.SpendTextParser
import com.ledgerline.app.domain.model.TransactionType
import com.ledgerline.app.finance.money.AmountInput
import com.ledgerline.app.finance.transactions.PaymentFilter
import com.ledgerline.app.ui.transactions.ActivityView
import com.ledgerline.app.ui.transactions.TransactionFilter
import com.ledgerline.app.ui.transactions.view
import com.ledgerline.app.ui.transactions.withView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendCaptureTest {
    @Test
    fun chaseStyleCardAlert() {
        val signal = SpendTextParser.parse("You made a \$45.67 transaction with STARBUCKS on Nov 2, 2026 at 8:15 AM ET on your card ending in 1234.", "Chase")
        assertNotNull(signal)
        signal!!
        assertEquals(4_567L, signal.amountMinor)
        assertEquals("USD", signal.currency)
        assertEquals("Starbucks", signal.merchant)
        assertEquals("1234", signal.lastFour)
        assertEquals(MoneyDirection.OUT, signal.direction)
    }

    @Test
    fun amexChargeAtMerchant() {
        val signal = SpendTextParser.parse("A charge of \$1,250.00 at AMAZON was made on your card ending 1005.")!!
        assertEquals(125_000L, signal.amountMinor)
        assertEquals("Amazon", signal.merchant)
        assertEquals("1005", signal.lastFour)
        assertEquals(MoneyDirection.OUT, signal.direction)
    }

    @Test
    fun venmoPaymentsBothWays() {
        val incoming = SpendTextParser.parse("John Smith paid you \$20.00", "Venmo")!!
        assertEquals(MoneyDirection.IN, incoming.direction)
        assertEquals("John Smith", incoming.merchant)

        val outgoing = SpendTextParser.parse("You paid Jane Doe \$15.00", "Venmo")!!
        assertEquals(MoneyDirection.OUT, outgoing.direction)
        assertEquals("Jane Doe", outgoing.merchant)
    }

    @Test
    fun cashAppSentTo() {
        val signal = SpendTextParser.parse("You sent \$10 to Bob for lunch")!!
        assertEquals(1_000L, signal.amountMinor)
        assertEquals(MoneyDirection.OUT, signal.direction)
        assertEquals("Bob", signal.merchant)
    }

    @Test
    fun indianUpiDebitWithVpaAndLakhGrouping() {
        val signal = SpendTextParser.parse(
            "Rs.1,23,456.50 debited from A/c XX1234 on 02-11-26 to VPA swiggy.store@axisbank (UPI Ref No 512345678901). Not you? Call 18002586161",
        )!!
        assertEquals(12_345_650L, signal.amountMinor)
        assertEquals("INR", signal.currency)
        assertEquals("1234", signal.lastFour)
        assertEquals("Swiggy Store", signal.merchant)
        assertEquals(MoneyDirection.OUT, signal.direction)
    }

    @Test
    fun debitThatMentionsCreditToBeneficiaryIsMoneyOut() {
        val signal = SpendTextParser.parse("INR 500.00 debited from your account and credited to ZOMATO on 02-Nov-26. Avl Bal INR 10,000.00")!!
        assertEquals(50_000L, signal.amountMinor)
        assertEquals(MoneyDirection.OUT, signal.direction)
        assertEquals("Zomato", signal.merchant)
    }

    @Test
    fun salaryCreditIsMoneyIn() {
        val signal = SpendTextParser.parse("Your a/c XX9876 is credited with Rs 50,000.00 by NEFT from ACME CORP on 01-11-26")!!
        assertEquals(MoneyDirection.IN, signal.direction)
        assertEquals("Acme Corp", signal.merchant)
    }

    @Test
    fun refundIsMarked() {
        val signal = SpendTextParser.parse("Refund of \$12.99 from Target has been credited to your card ending 4321")!!
        assertTrue(signal.refund)
        assertEquals(MoneyDirection.IN, signal.direction)
    }

    @Test
    fun googleWalletTapToPayUsesTitleAsMerchant() {
        val signal = SpendTextParser.parse("\$4.50 with Visa •••• 1234", title = "Blue Bottle Coffee", tapToPay = true)!!
        assertEquals(450L, signal.amountMinor)
        assertEquals("Blue Bottle Coffee", signal.merchant)
        assertEquals("1234", signal.lastFour)
        assertEquals(MoneyDirection.OUT, signal.direction)
    }

    @Test
    fun skipsMessagesThatAreNotPayments() {
        listOf(
            "Your OTP for transaction of Rs 500 is 482913. Do not share.",
            "Your credit card bill of \$1,240.00 is due on Nov 15.",
            "Rahul has requested Rs 300 via UPI.",
            "Transaction of \$25.00 at Shell was declined.",
            "Get cashback up to Rs 500 on your next recharge!",
            "Hey, are we still on for dinner tonight? I owe you \$20",
            "Your statement is ready. Total due \$310.00",
            "Lunch was \$12, see you tomorrow",
        ).forEach { text -> assertNull(text, SpendTextParser.parse(text)) }
    }

    @Test
    fun wordsInsideOtherWordsDoNotCount() {
        // "consent" contains "sent", and "present" too; neither means money moved.
        assertNull(SpendTextParser.parse("Thanks for your consent. Present this code \$5.00 at the counter"))
    }

    @Test
    fun firstAmountIsTheTransactionNotTheBalance() {
        val signal = SpendTextParser.parse("Spent Rs 250 at DMart. Avl bal Rs 9,750.00")!!
        assertEquals(25_000L, signal.amountMinor)
        assertEquals("DMart", signal.merchant)
    }

    @Test
    fun previewMasksLongNumbers() {
        val preview = CapturePreview.of("HDFC Bank", "Rs 500 debited, UPI Ref 512345678901, call 18002586161")
        assertFalse(preview.contains("512345678901"))
        assertTrue(preview.contains("•••01"))
        assertTrue(preview.startsWith("HDFC Bank · "))
        // A numeric sender is dropped rather than shown as a masked number.
        assertTrue(CapturePreview.of("33789", "Chase: You made a \$4.50 transaction").startsWith("Chase:"))
    }

    @Test
    fun amountInputReadsAsTyped() {
        assertEquals(45_000L, AmountInput.toMinor("450", 2))
        assertEquals(1_250L, AmountInput.toMinor("12.5", 2))
        assertEquals(1_234L, AmountInput.toMinor("12.345", 2))
        assertEquals(123_400L, AmountInput.toMinor("1,234", 2))
        assertEquals(0L, AmountInput.toMinor("", 2))
        assertEquals("12.", AmountInput.clean("12.", 2))
        assertEquals("0.5", AmountInput.clean(".5", 2))
        assertEquals("7", AmountInput.clean("007", 2))
        assertEquals("500", AmountInput.clean("500.25", 0))
        assertEquals(500L, AmountInput.toMinor("500", 0))
    }

    @Test
    fun amountInputShowsExistingAmountsSimply() {
        assertEquals("450", AmountInput.fromMinor(45_000L, 2))
        assertEquals("12.50", AmountInput.fromMinor(1_250L, 2))
        assertEquals("", AmountInput.fromMinor(0L, 2))
        assertEquals(1_250L, AmountInput.toMinor(AmountInput.fromMinor(1_250L, 2), 2))
    }

    @Test
    fun activityViewsMapToFilters() {
        val base = TransactionFilter()
        assertEquals(ActivityView.ALL, base.view())
        assertEquals(TransactionType.EXPENSE, base.withView(ActivityView.SPENDING).type)
        assertEquals(PaymentFilter.UNPAID, base.withView(ActivityView.UPCOMING).status)
        ActivityView.entries.forEach { view -> assertEquals(view, base.withView(view).view()) }
        // A finer choice from the filter sheet is not one of the quick views.
        assertNull(base.copy(type = TransactionType.TRANSFER).view())
        // Switching views keeps the search and other filters.
        val searched = base.copy(query = "coffee", accountId = "a1").withView(ActivityView.INCOME)
        assertEquals("coffee", searched.query)
        assertEquals("a1", searched.accountId)
    }
}
