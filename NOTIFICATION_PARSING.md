# Notification parsing

`LedgerNotificationListener` is a `NotificationListenerService`. Android starts it only after the user grants notification access. The Auto-capture switch inside Ledgerline must also be on, and the posting app must be turned on in Settings › Auto-capture spends. Every app starts off.

The listener skips Ledgerline's own notifications, group summaries, and ongoing notifications.

## When Android hides the text

Android 15 and later can replace a notification's text with "Sensitive notification content hidden" and clear its title for third-party listeners. On the Android 15 emulator image every SMS notification is hidden this way. The listener counts these from chosen apps, and the setup screen tells the person.

For texts, "Read new texts directly" uses the `SMS_RECEIVED` broadcast through `SmsCaptureReceiver`, which this does not affect. It needs `RECEIVE_SMS`, asked for only when the switch is turned on. It sees new messages only; there is no `READ_SMS`, so the history is never read. Each text goes through the same strict parser. If both paths deliver the same text, the 20-minute duplicate rule keeps one capture.

Google Play limits `RECEIVE_SMS` to default SMS apps and approved exceptions. A Play release would need to remove this path or apply for an exception; sideloaded builds are unaffected. It passes the title and the expanded text (or the short text) to `NotificationIngestor`.

## Capture, not posting

`NotificationIngestor` runs `SpendTextParser`. A notification that does not parse is dropped: nothing is stored or logged, so personal messages in a messaging app leave no trace. A notification that parses becomes a `CapturedSpend` with status `PENDING` in the `captured_spends` table. It does not change any balance.

From the Captured inbox the user can:

- Add: `CaptureActions.add` saves a normal transaction through `TransactionRepository.save`, so duplicate checks and learned merchant categories apply. A likely duplicate is shown on the card instead of being saved.
- Review: opens the add screen pre-filled from the capture. Saving marks the capture added.
- Dismiss: marks it dismissed, with undo.

The account is the one active account whose ending matches the notification; otherwise the first checking account counted in Safe to spend, then any account counted in Safe to spend.

With "Add automatically when the card matches" on, a capture whose ending matches exactly one account is added straight away. Anything else waits in the inbox.

The same payment often arrives twice (an SMS and a wallet notice, or an updated notification). A capture is skipped when another capture with the same amount and direction arrived in the previous 20 minutes (`CaptureDedupe`), or when the same app posts identical text inside the same 20-minute window. Identical text later in the day is a new purchase.

For text-message and email apps the text must also read like a bank alert: a card or account ending, banking words, or a bank's name. This keeps "I paid $12 for lunch" from a friend out. Card-bill payment confirmations ("your payment has been received") are skipped because the bank's debit already records that money.

## SpendTextParser

A notification needs all of:

- An amount with a currency marker before it (`$`, `US$`, `₹`, `Rs`, `Rs.`, `INR`, `€`, `£`, `CAD`, `AED`, ...) or an ISO code after it. Grouping commas, including lakh grouping, are ignored. The first amount in the text is used, so a balance quoted later does not win.
- A word that says money moved, matched on word boundaries: debited, spent, paid, sent, purchase, charged, withdrawn, transaction with, and similar for money out; credited, received, deposited, sent you, paid you for money in. When both appear, the first one decides, so "debited ... credited to X" is money out. Refund wording makes it a refund.

It is skipped when it mentions a one-time passcode, a due date or reminder, a money request, a declined or failed payment, an offer, or a statement.

Merchant: tried in order: "transaction with X", a UPI handle after `VPA`, "you paid X $..", "at X", "to X", "towards X", "Info: X". Money in also tries "X sent you", "from X", "by X". The name is cut at words such as on, using, via, ref, avl bal. Shouting and lower-case names are title-cased, and a UPI handle like `swiggy.store@axisbank` becomes "Swiggy Store".

Card or account ending: "ending 1234", "XX1234", "•••• 1234", "A/c no. 1234".

Google Wallet and Samsung Wallet post the merchant as the title and only an amount as the text. For those apps a missing verb means money out and the title is the merchant.

Examples:

- `You made a $45.67 transaction with STARBUCKS on your card ending in 1234` → Starbucks, $45.67, out, 1234.
- `Rs.1,23,456.50 debited from A/c XX1234 to VPA swiggy.store@axisbank` → Swiggy Store, ₹1,23,456.50, out, 1234.
- `John Smith paid you $20.00` → John Smith, $20.00, in.
- `Your OTP for transaction of Rs 500 is 482913` → skipped.

## Older parsers

`ParserRegistry` and `NotificationFields` remain for the development bank fixture and its tests. Live notifications use `SpendTextParser`.
