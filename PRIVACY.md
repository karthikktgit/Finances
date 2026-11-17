# Privacy

Process data on the device when the device can do the work. Ledgerline follows that for balances, forecasts, parsing, and reminders.

## Stored on the device

Accounts, transactions, categories, budgets, debts, schedules, notification app toggles, sync outcome messages, and UI preferences. A captured payment notification contributes the extracted amount, currency, merchant, card or account ending, direction, the app it came from, and an excerpt of at most 160 characters with every run of five or more digits masked (for example `•••01`). Notifications that do not read as payments are dropped without being stored or logged. Handled captures are deleted after 60 days.

## Not uploaded

Transactions, notification text, and banking credentials are not sent to a server by this version. There is no cloud assistant. Exports are files you share yourself through the Android share sheet.

## Permissions

- Notification listener access, only after you open system settings and turn the master switch on. Each listed app starts off.
- Post notifications, only if you want reminders or "tap to add" notices.
- Receive SMS, only if you turn on "Read new texts directly". It lets Ledgerline see each new text as it arrives so bank alerts can be read when Android hides notification text. Ledgerline cannot read your message history and cannot send messages. Texts that are not bank alerts are dropped without being stored. Android's permission prompt names the whole SMS group ("send and view"), but only receiving is requested.
- Biometrics, only if you choose that unlock path.

The app does not request SMS, contacts, location, or internet access.
