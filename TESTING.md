# Testing

Unit tests cover the calculations that do not need a device:

```bash
./gradlew :app:testDebugUnitTest
```

`FinanceEngineTest` covers safe-to-spend, duplicate scores, merchant normalization, fingerprints, recurring dates, budgets, transfers, credit availability, the dashboard total, category scope, the schedule preview, and CSV formula escaping.

`AnalysisEngineTest` covers the daily forecast, what-if buffer checks, notification parsing, detection decisions, repeating merchants, unusual amounts, small-purchase notes, debt order, and subscription annualizing.

`CalculationCoverageTest` covers Home's per-day figure and cycle day, daily spending totals in Activity, the plain-language safe-to-spend lines (they must still add up), cash warnings at each severity, budget pace, monthly conversion of repeating bills, signed money labels, and pay cycles stepping back and forth without gaps.

`CapturePipelineTest` runs the real parser, ingestor, actions, and preferences from a posted notification to the inbox and on to a transaction, with storage replaced by fakes: capture off, apps not chosen, personal texts, duplicates inside and outside the 20-minute window, auto-add, notices on and off, add, dismiss and undo, and account choice.

`NotificationCorpusTest` holds real-world alert formats from US, Indian, UK, and European banks and wallets with the expected amount, currency, merchant, card ending, and direction, plus messages that must be ignored. Add a case here whenever a bank's format is misread.

## On an emulator

`scripts/e2e-capture.sh` sends real SMS to an emulator and checks what was captured. It refuses to run on a physical device. On the Android 15 emulator image, Google's notification assistant marks every SMS notification as sensitive, so Android hides the text from Ledgerline; capture works through "Read new texts directly", and the setup screen says when Android is hiding text.

`BillCalendarTest` covers the calendar: month totals (transfers and other months left out, debts not counted twice), what each day cell shows, compact amounts such as $1.2k, preset categories, and the next-dates preview including the 31st in short months.

`SpendingPaceTest` covers today's limit (fixed for the day, what tomorrow's becomes after going over), the pace forecast and run-out date, the calendar's left-to-spend by date matching the run-out date, and the purchase check in days of your limit. `AutoPayTest` covers which dates autopay records: never before it was turned on, never paid or skipped dates, nothing for paused bills.

`CashFlowPlanTest` covers the cash-flow headline (lowest point, the day cash runs out, the day before payday) and the timeline of days money moves. `SavingsGoalTest` covers goal progress from contributions or an account balance, the monthly amount a date needs, the finish date a monthly amount gives, how saving during a cycle counts toward it, and what Safe to spend holds back.

`scripts/ui-crawl.py` taps every tappable element two levels deep from each tab on an emulator and reports crashes. Run it against the non-debuggable perf build for speed.

## Smoothness

Measure with `adb shell dumpsys gfxinfo <package>` after `reset`, around a fixed sequence such as opening Settings and swiping back five times. Debuggable builds run far slower than real ones: on the emulator with the same sample data, a debuggable build skipped 77 frames after launch with a slowest frame near 950 ms, and the same build made non-debuggable skipped none. Judge smoothness on a non-debuggable build (`perf` installs alongside under its own id; `phone` replaces a debug install and keeps its data).

Room, WorkManager, and the biometric prompt are not covered by instrumented tests in this version.

When you add a schema change, add a Room migration and a test that opens version 1 and migrates forward. Do not delete the database to get past a migration error.
