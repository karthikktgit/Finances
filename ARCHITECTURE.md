# Architecture

Ledgerline is one Android application module. Packages under `com.ledgerline.app` keep the UI, the ledger, and the calculations separate.

```
ui → ViewModel → use case → repository → Room
```

Financial calculations live in `finance` and do not import Android types, so unit tests run on the JVM. Detection parsers live in `detection` and are also plain Kotlin. Room, WorkManager, the notification listener, and the keystore sit at the edges.

## Modules of behavior

- `domain` holds the ledger models and repository interfaces.
- `data` implements those interfaces with Room. `data.remote.FinancialDataProvider` is the replaceable bank boundary. The only implementation is `FixtureFinancialDataProvider`.
- `finance` calculates balances, safe-to-spend, forecasts, budgets, duplicates, leaks, anomalies, subscriptions, debt plans, and what-if results.
- `detection` parses notification text and decides whether a row is confirmed, needs review, linked, or rejected.
- `worker` runs one periodic maintenance job. It records the latest safe-to-spend figure, stores inactive repeating-merchant suggestions, and posts reminders the user enabled.
- `security` checks a PIN with an Android Keystore HMAC key. Biometrics use the platform prompt as an alternate unlock.

## Balances

An account balance is its opening balance plus the balance effect of rows whose status is confirmed, reconciled, or modified. Detected, duplicate, and ignored rows stay in the database and do not change balances. Transfers and payments are paired rows that share a `linkId`. They move money between accounts and are not counted as spending or income.

## Replacing the bank provider

Implement `FinancialDataProvider` and bind it in `RepositoryModule`. The app must not collect a banking password. A new provider should pass rows through `TransactionRepository.ingestDetected` so duplicate linking and review rules stay in one place.
