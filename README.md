# Ledgerline

Ledgerline is an offline-first Android ledger. It answers a practical question: how much money is available after the balances you entered, the payments you scheduled, and the income you marked as reliable.

The app stores accounts, transactions, categories, budgets, debts, and schedules on the device. Safe-to-spend, the cash-flow forecast, duplicate checks, and spending notes are calculated locally.

## Run it

Install a recent Android SDK and JDK 17, then:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Open the `app` module in Android Studio and run the debug build. The ledger starts empty. A debug build can load sample accounts only from the onboarding button "Explore with sample finances". Release builds do not offer that sample.

Minimum SDK is 28. The project compiles against Android API 37.

## What this version does

- Manual expenses, income, transfers, and payments
- Checking, savings, credit card, cash, prepaid, loan, investment, and other accounts
- Categories, budgets, and a payment calendar
- Safe-to-spend with the formula shown in the app
- A daily cash-flow projection
- Duplicate scoring for manual entry and for notification or fixture imports
- Review queue for low-confidence rows
- Repeating-merchant suggestions, subscriptions, and an annualized total
- Debt payoff estimates for avalanche, snowball, and the order you entered
- What-if scenarios that do not change the ledger
- Auto-capture: payment alerts from text-message, email, and wallet apps land in a "Captured" inbox to add with one tap, after you turn it on and pick each app
- A development bank fixture behind `FinancialDataProvider`
- CSV and JSON export, screenshot blocking, and an optional PIN lock
- Reminder notifications you enable yourself

## What it does not do

There is no conversational financial assistant. There is no live bank aggregation provider. The bank screen imports a labeled development fixture and does not contact a bank. Notification access does nothing until you grant it and enable specific apps.
