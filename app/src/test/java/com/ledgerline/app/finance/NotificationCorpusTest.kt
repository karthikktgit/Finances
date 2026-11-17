package com.ledgerline.app.finance

import com.ledgerline.app.detection.capture.MoneyDirection
import com.ledgerline.app.detection.capture.MoneyDirection.IN
import com.ledgerline.app.detection.capture.MoneyDirection.OUT
import com.ledgerline.app.detection.capture.SpendTextParser
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-world alert formats from US, Indian, UK, and European banks and wallets, with the fields a person would
 * expect. Every case is checked, and all mismatches are reported together so a parser change shows its full effect.
 */
class NotificationCorpusTest {
    private data class Case(
        val text: String,
        val amountMinor: Long,
        val currency: String?,
        val merchant: String?,
        val lastFour: String?,
        val direction: MoneyDirection,
        val title: String = "",
        val tapToPay: Boolean = false,
        val refund: Boolean = false,
    )

    private val payments = listOf(
        // United States
        Case("You made a \$45.67 transaction with STARBUCKS on Nov 2, 2026 at 8:15 AM ET on your card ending in 1234.", 4_567, "USD", "Starbucks", "1234", OUT, title = "Chase"),
        Case("A \$25.00 debit card transaction to SHELL OIL 57444 on 11/02/2026 10:15 AM ET exceeded \$0.00.", 2_500, "USD", "Shell Oil", null, OUT, title = "Chase"),
        Case("Bank of America: Debit card transaction of \$8.75 at TARGET 00012345 on 11/02.", 875, "USD", "Target", null, OUT),
        Case("Wells Fargo card ending in 4321 was used for \$60.00 at COSTCO WHSE #0123 on 11/02/26.", 6_000, "USD", "Costco Whse", "4321", OUT),
        Case("Capital One: A transaction of \$34.20 was charged to your Quicksilver card ending in 8888 at UBER EATS.", 3_420, "USD", "Uber Eats", "8888", OUT),
        Case("Citi Alert: A \$150.00 transaction was made at BEST BUY on card ending in 5555.", 15_000, "USD", "Best Buy", "5555", OUT),
        Case("A charge of \$1,250.00 at AMAZON was made on your card ending 1005.", 125_000, "USD", "Amazon", "1005", OUT, title = "American Express"),
        Case("Large Purchase Approved: \$120.00 at Target on your card ending 1005", 12_000, "USD", "Target", "1005", OUT),
        Case("Discover Card Alert: A transaction of \$22.50 at WALGREENS was made on 11/02/2026.", 2_250, "USD", "Walgreens", null, OUT),
        Case("\$4.50 with Visa •••• 1234", 450, "USD", "Blue Bottle Coffee", "1234", OUT, title = "Blue Bottle Coffee", tapToPay = true),
        Case("John Smith paid you \$20.00", 2_000, "USD", "John Smith", null, IN, title = "Venmo"),
        Case("You paid Jane Doe \$15.00", 1_500, "USD", "Jane Doe", null, OUT, title = "Venmo"),
        Case("Jane Doe charged you \$12.00 for Pizza", 1_200, "USD", "Jane Doe", null, OUT, title = "Venmo"),
        Case("You sent \$10 to Bob for lunch", 1_000, "USD", "Bob", null, OUT, title = "Cash App"),
        Case("Bob Lee sent you \$25", 2_500, "USD", "Bob Lee", null, IN, title = "Cash App"),
        Case("You sent \$10.00 USD to Bob Smith", 1_000, "USD", "Bob Smith", null, OUT, title = "PayPal"),
        Case("You received \$50.00 from JOHN DOE via Zelle®.", 5_000, "USD", "John Doe", null, IN),
        Case("You sent \$50.00 to JANE DOE with Zelle", 5_000, "USD", "Jane Doe", null, OUT),
        Case("Chase: Your direct deposit of \$2,500.00 from ACME INC is available.", 250_000, "USD", "Acme Inc", null, IN),
        Case("Refund of \$12.99 from Target has been credited to your card ending 4321", 1_299, "USD", "Target", "4321", IN, refund = true),
        Case("You withdrew \$200.00 at an ATM with card ending 9876", 20_000, "USD", "ATM", "9876", OUT),
        // India
        Case("Rs.250.00 spent on HDFC Bank Card x1234 at AMAZON on 2026-11-02:10:15:20.Not You? Call 18002586161", 25_000, "INR", "Amazon", "1234", OUT),
        Case("Money Sent!Rs.500.00From HDFC Bank A/C *1234To Zomato Ltd On 02/11/26Ref 512345678901Not You?", 50_000, "INR", "Zomato Ltd", "1234", OUT),
        Case("Rs.1,23,456.50 debited from A/c XX1234 on 02-11-26 to VPA swiggy.store@axisbank (UPI Ref No 512345678901).", 12_345_650, "INR", "Swiggy Store", "1234", OUT),
        Case("ICICI Bank Acct XX123 debited for Rs 1,500.00 on 02-Nov-26; AMAZON PAY credited. UPI:512345678901. Call 18002662 for dispute.", 150_000, "INR", "Amazon Pay", null, OUT),
        Case("Dear UPI user A/C X1234 debited by 250.0 on date 02Nov26 trf to SWIGGY Refno 512345678901. If not u? call 1800111109. -SBI", 25_000, null, "Swiggy", "1234", OUT),
        Case("INR 120.00 debited\nA/c no. XX1234\n02-11-26, 10:15:20\nUPI/P2M/512345678901/BLINKIT\nNot you? SMS BLOCKUPI Cust ID to 919951860002", 12_000, "INR", "Blinkit", "1234", OUT),
        Case("Sent Rs.300.00 from Kotak Bank AC X1234 to chaipoint@okaxis on 02-11-26.UPI Ref 512345678901.", 30_000, "INR", "Chaipoint", "1234", OUT),
        Case("Debited from XXXXXXXX1234", 24_900, "INR", "Swiggy", "1234", OUT, title = "Paid ₹249 to Swiggy"),
        Case("₹100 paid to Raju Kirana", 10_000, "INR", "Raju Kirana", null, OUT, title = "Google Pay"),
        Case("Paid Rs.50 to Chai Point from Paytm Balance", 5_000, "INR", "Chai Point", null, OUT),
        Case("Rs 85,000.00 credited to A/c XX5678 on 01-11-26 by NEFT-ACME TECHNOLOGIES PVT LTD. Avl bal Rs 1,00,000", 8_500_000, "INR", "Acme Technologies Pvt Ltd", "5678", IN),
        Case("Cashback of Rs 25 credited to your Amazon Pay balance", 2_500, "INR", "Amazon Pay", null, IN, refund = true),
        Case("EMI of Rs 5,000 debited from your a/c XX1234 for loan 99887766", 500_000, "INR", null, "1234", OUT),
        Case("Spent Rs 250 at DMart. Avl bal Rs 9,750.00", 25_000, "INR", "DMart", null, OUT),
        // United Kingdom and Europe
        Case("You spent £4.50 at Pret A Manger", 450, "GBP", "Pret A Manger", null, OUT, title = "Monzo"),
        Case("Barclays: £25.00 payment to TESCO STORES 1234 on 02/11", 2_500, "GBP", "Tesco Stores", null, OUT),
        Case("Paid €12.00 at Lidl", 1_200, "EUR", "Lidl", null, OUT, title = "Revolut"),
        Case("Kartenzahlung: 12,50 € bezahlt bei REWE, Karte endet auf 4321. Card payment approved", 1_250, "EUR", null, null, OUT),
    )

    /** Notifications that must never become a capture. */
    private val ignored = listOf(
        "Your OTP for transaction of Rs 500 is 482913. Do not share.",
        "123456 is your Chase verification code. Don't share it.",
        "Your credit card bill of \$1,240.00 is due on Nov 15.",
        "Your HDFC Credit Card bill of Rs 12,345 is due on 15-Nov",
        "Rahul has requested Rs 300 via UPI.",
        "Rs.500 has been requested by Raju via UPI. Pay now",
        "Transaction of \$25.00 at Shell was declined.",
        "Your UPI transaction of Rs 400 failed. Any amount debited will be refunded in 3 days",
        "Get cashback up to Rs 500 on your next recharge!",
        "Recharge with Rs 299 & get 2GB/day",
        "Your statement is ready. Total due \$310.00",
        "Thank you! Your payment of \$500.00 has been received.",
        "Payment received: \$300.00 to your Capital One card. Thank you for your payment.",
        "Your available balance is \$1,234.56",
        "Your Amazon.com order of \$45.99 has shipped",
        "Rs 2,000 will be debited on 05-Nov for your SIP",
        "Get \$200 when you open an account",
    )

    /** Chat messages in a texting app. They mention money but are not bank alerts. */
    private val personal = listOf(
        "Hey, are we still on for dinner tonight? I owe you \$20",
        "Lunch was \$12, see you tomorrow",
        "I paid \$12 for lunch, you can pay me back later",
        "Can you send me \$20 for pizza?",
        "Thanks for your consent. Present this code \$5.00 at the counter",
    )

    @Test
    fun everyPaymentFormatIsReadCorrectly() {
        val problems = payments.mapNotNull { case ->
            val signal = SpendTextParser.parse(case.text, case.title, case.tapToPay, requireBankWording = !case.tapToPay && case.title.isEmpty())
            val issues = buildList {
                if (signal == null) {
                    add("not read at all")
                } else {
                    if (signal.amountMinor != case.amountMinor) add("amount ${signal.amountMinor} expected ${case.amountMinor}")
                    if (signal.currency != case.currency) add("currency ${signal.currency} expected ${case.currency}")
                    if (signal.merchant != case.merchant) add("merchant '${signal.merchant}' expected '${case.merchant}'")
                    if (signal.lastFour != case.lastFour) add("ending ${signal.lastFour} expected ${case.lastFour}")
                    if (signal.direction != case.direction) add("direction ${signal.direction} expected ${case.direction}")
                    if (signal.refund != case.refund) add("refund ${signal.refund} expected ${case.refund}")
                }
            }
            if (issues.isEmpty()) null else "${case.text.take(70)}\n    -> ${issues.joinToString("; ")}"
        }
        assertTrue("${problems.size} of ${payments.size} formats misread:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun nonPaymentsAreIgnored() {
        val wrong = ignored.filter { SpendTextParser.parse(it, requireBankWording = true) != null } +
            ignored.filter { SpendTextParser.parse(it) != null }
        assertTrue("Captured but should be ignored:\n" + wrong.distinct().joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun personalMessagesInATextingAppAreIgnored() {
        val wrong = personal.filter { SpendTextParser.parse(it, title = "Alex", requireBankWording = true) != null }
        assertTrue("Personal messages captured:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }
}
