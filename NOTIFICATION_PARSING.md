# Notification parsing

`LedgerNotificationListener` is a `NotificationListenerService`. Android starts it only after the user grants notification access. A second switch inside Ledgerline must be on, and the posting app must be enabled in Transaction detection.

The listener reads the title and text on the device and passes them to `NotificationIngestor`. The raw text is not written to the log, the database, or a sync record. Sync records describe the outcome in a short sentence, such as a missing amount or a linked duplicate.

## Parsers

`NotificationParser` has `canParse` and `parse`. `ParserRegistry` tries the bank, card, and wallet parsers before the generic parser. Those three parsers only claim notifications whose package name matches `InstitutionCatalog`. All of them use `NotificationFields` for extraction:

- Amount: a dollar amount, a `USD` amount, or a number with two decimals. A bare card ending such as 1234 is not treated as money.
- Merchant: the words after `at`, cut before `using`, `on`, `with`, `for`, or `via`.
- Last four: `ending`, `ending in`, or a masked card prefix.
- Type: refund, deposit, card payment, or purchase wording. If none of those appear, the type stays empty and no ledger row is created.

`Purchase alert: $43.29 at Amazon using Visa ending 1234` becomes Amazon, 4329 minor units, 1234, expense.

`Your debit card was used for $87.20` becomes 8720 minor units and an expense. Merchant and last four stay empty, so the row is rejected until an account can be matched.
