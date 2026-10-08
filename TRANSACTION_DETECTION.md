# Transaction detection

`TransactionDetectionEngine` decides what to do after a parser has extracted fields. It does not invent a missing amount, type, or account.

1. Reject the event when the amount is missing or not positive.
2. Reject it when the wording does not identify expense, income, refund, or payment.
3. Match an active account only when exactly one account in the primary currency has the extracted last four digits.
4. Score nearby rows with `DuplicateDetector`.
5. At 95 or above, store the incoming row as `DUPLICATE` with a zero balance effect and write `transaction_matches`. The original row stays.
6. From 70 through 94, store a `DETECTED` row and a match. It stays out of balances until the user counts it, marks it duplicate, or ignores it.
7. Below 70, store a confirmed row when confidence is at least 80. Otherwise store it for review.

Confidence starts at 40 because an amount was found, then adds merchant, matched account, and type. Manual entry is always confirmed at 100 and still warns on a possible duplicate instead of merging silently.

`RecurringDetector` looks for the same normalized merchant on the same account at least three times. A stable gap becomes a weekly, biweekly, monthly, quarterly, or yearly suggestion. Suggestions are saved inactive so they do not change safe-to-spend until you open them and turn them on. Known subscription names are labeled subscriptions. Other names are labeled bills.
