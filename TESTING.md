# Testing

Unit tests cover the calculations that do not need a device:

```bash
./gradlew :app:testDebugUnitTest
```

`FinanceEngineTest` covers safe-to-spend, duplicate scores, merchant normalization, fingerprints, recurring dates, budgets, transfers, credit availability, the dashboard total, category scope, the schedule preview, and CSV formula escaping.

`AnalysisEngineTest` covers the daily forecast, what-if buffer checks, notification parsing, detection decisions, repeating merchants, unusual amounts, small-purchase notes, debt order, and subscription annualizing.

Room, the notification listener, WorkManager, and the biometric prompt are not covered by instrumented tests in this version. Those paths were compiled with `:app:assembleDebug`. They were not exercised on an emulator here.

When you add a schema change, add a Room migration and a test that opens version 1 and migrates forward. Do not delete the database to get past a migration error.
