# Roadmap

This version is the local ledger plus detection, forecasting, reconciliation, and planning. The conversational assistant from the original brief is intentionally absent. Do not describe the app as if that assistant is available.

## Done here

- Accounts, manual transactions, categories, budgets, calendar, export
- Safe-to-spend and a documented daily forecast
- Duplicate scoring, review, and match records that keep the original row
- Notification listener and parsers, gated by user settings
- Repeating-merchant suggestions and subscription totals
- Money-leak notes and unusual-amount notes
- Debt estimates and what-if scenarios
- Development fixture behind `FinancialDataProvider`
- PIN lock, optional biometrics, optional screenshot blocking, optional reminders

## Still open

- A real aggregation adapter that uses a token from a provider the user connects outside the app
- Encrypted database storage
- A full instrumented test pass on a device, including the notification listener
- Richer institution parsers only where a real notification sample shows the generic extractor is wrong
- The assistant, if it is added later, should call the existing calculations and must not change records without a confirmation step
