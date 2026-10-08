# Database

Room database `ledgerline.db`, schema version 1, with exported schemas under `app/schemas` after a build. `Migrations.ALL` is empty because this is the first schema. Do not enable destructive fallback. Add a `Migration` before raising the version.

## Tables

| Table | Role |
| --- | --- |
| accounts | Account name, type, opening balance, credit limit, currency, liquid and net-worth flags |
| categories | Hierarchical categories and a necessity label |
| merchants | Normalized merchant name |
| merchant_category_rules | Learned merchant-to-category confirmations |
| transactions | Ledger rows, including soft-deleted and non-counting statuses |
| recurring_payments | Bills, income, subscriptions, and inactive suggestions |
| budgets | Category budgets |
| debts | Principal, balance, APR in basis points, minimum, due day |
| notification_sources | Packages the user can enable for notification parsing |
| transaction_matches | Audit link from an incoming row to the survivor |
| sync_records | Short local messages for fixture imports and notification outcomes |
| user_settings | Currency, buffer, horizon, onboarding flag |

Amounts are integer minor units plus a currency code. Dates that belong to a statement are `LocalDate` strings. Timestamps are epoch millis. The app does not convert currencies.

Foreign keys point transactions at accounts, categories, merchants, and recurring rows. Deleting an account is refused while rows still reference it.

## Status and duplicates

`DETECTED` waits in review. `DUPLICATE` keeps the incoming record, sets its balance effect to zero, and stores a match row. `IGNORED` also stops counting. Nothing in this flow deletes the survivor.
