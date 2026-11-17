# Changelog

## 0.17.1 — 2026-11-12

- Goals on Home: a goal you reached and are still holding stays on Home every cycle (ticked, with what was saved) until you spend it; once it is spent (or finished as Done or Dropped) it shows only in the cycle that happened, then leaves. A finished goal no longer vanishes in the cycle it was finished.
- The pay bar calls savings "Goals", and its key says what is left: "Monthly expenses $446 left of $750", "Goals $780 left to save of $900", "Un-marked $3 spent".
- No database change.

## 0.17.0 — 2026-11-12

New
- Spend from a goal: record how much, whether it was for the goal or something else, a note, and (on by default when the goal has an account) add it as a spend from that account. Spent on the goal still counts as saved toward it but is no longer held; used for something else makes the goal that much less funded. The goal page shows saved, spent on the goal, used elsewhere, and still holding; its history labels each entry and notes the ones added as a spend. Removing such an entry removes its spend too.
- Finishing a goal is now "Done" or "Dropped" under its money summary.

Calendar
- Heat map with the pay cycle on colours only the cycle's days (its days shown from the months either side too, fainter); with the pay cycle off it colours every day. A quiet day is a faint tile, and the picked day keeps its colour under its outline.
- The pay cycle's days that fall in the next (or previous) month now show as cycle boxes on the grid, fainter, so you can see the cycle carries on.

Database
- Version 16 adds what a goal entry was and the spend linked to it (two columns on goal entries). Nothing existing changes.

## 0.16.0 — 2026-11-12

New
- Cycle review (Settings › Each cycle, and a nudge on Home in a cycle's last 3 days): this cycle next to the one before (money in, bills paid, spent, not marked, saved, all out, with the change), each spending item planned against spent, which bills and pay carry on next cycle (with their dates), which end, which start, spending items ending or starting, and what is still to pay. Any cycle with ‹ ›.
- Goals: "Where's the money?" on each goal. Still holding it (held in its account and kept out of what is free), spent on it (the goal is done and the money is free; record the purchase from there), or used for something else (the goal closes). A finished goal keeps its history and can be reopened.
- Money already saved for a goal shows in its account's holds ("Saved: Moving") and counts as spoken for.
- Each hold in an account's list opens to say what it is and how it is worked out (bill dates, planned against spent, the free sum).

Fixes
- The pay bar on Home shows how much of each part is used (paid or spent), not its share of a pay figure, so it is not stuck at 0% without one. The key reads "spent / planned".
- Reminders no longer announce old unpaid bills as "payments today" every day: the forecast dates overdue bills today for the cash line, and the reminder counted them. Only bills really due that day are listed now, matching Today's summary.

Database
- Version 15 adds what became of a goal's money (two columns on savings goals). Nothing existing changes.

## 0.15.0 — 2026-11-10

New
- Fresh balances for any cycle (New cycle › Balances): switch it on and set what each account starts that cycle with. Earlier cycles stay exactly as they were; bills, subscriptions, and items carry on. Saved as a "Cycle start balance" entry on the cycle's first day; switching it off removes it. The New cycle page now steps to any cycle with ‹ ›.
- Changing a bill's or spending item's amount on Home asks "This cycle only" or "All cycles" (this cycle only by default), and shows its usual amount.
- Spending items can end: never, after a number of cycles, or on a date. Existing bills can change how they end from Home too.
- How a repeat ends reads in plain words: "6 payments: Oct 22, 2026 to Mar 22, 2027, then it stops."
- A goal's page shows last cycle's saving, this cycle's plan and what is still to put aside, and every cycle by date: saved in each past one, planned for each one ahead until the goal date.

Fixes
- Goals are planned per payday before the goal date, split evenly; the finish date is a payday, so a goal kept to plan reads "On track" instead of "Behind".
- A goal reached leaves Home after the cycle it was reached in; one not reached by its date stays.
- Activity › All › Coming up is in date order: planned spends and scheduled bills together, overdue first (it listed all planned spends first).

Database
- Version 14 adds start, end, and number-of-cycles to spending items. Nothing existing changes.

## 0.14.0 — 2026-11-10

New
- Pick a pay cycle on Home: arrows on the pay card step to earlier or later cycles, and Home's groups and accounts show that cycle (a past one as it ended, a coming one with its bills and items ahead). "Today" comes back.
- Savings goals on the calendar: each cycle's set-aside on its first day, money saved on its day, and the goal date, with a "Goal" dot in the key. Tap one to open the goal.
- Today's summary on Home has Paid for bills and planned spends due today, the same as Paid on the calendar.

Fixes
- Every account now shows on Home, including credit cards and loans kept out of Safe to spend. Cards show what is owed (and the share of the limit used, if you set one); they do not change "free until payday".
- Adding a bill on Home: the first due date is now picked on a calendar. The old "due day" box could not be cleared, so "10" could only become "12", never "22".
- Add transaction › Repeats: it repeats from the date you choose ("Every month on the 22nd"). The separate day counter, which could quietly move the start to another month, is gone.
- Recent on Home no longer lists planned spends that are not paid yet.
- Paid on Home and on the calendar are the same record and show in both; a bill after the current cycle shows by stepping Home to the next cycle.

## 0.13.2 — 2026-11-09

- The faint money-icon pattern is back behind every page.

## 0.13.1 — 2026-11-09

- Home shows its whole layout before anything is entered: an empty section holds one faded example ring at $0 ("My bank" under Accounts, "Rent", "Streaming", "Emergency fund"); tapping it adds a real one.
- The "Put your monthly bills here" card is gone from the Calendar.
- No database change.

## 0.13.0 — 2026-11-09

New
- New cycle set-up (Settings › Each cycle, or the card on Home when a cycle begins): tick off each account's statement as you download and review it, set any balance to the bank's figure, then choose what this cycle starts with. Every bill and pay date, spending item, and savings goal can stay at its usual amount, get its own amount for this cycle only, or be switched off for this cycle. Bills left unpaid from before can be skipped in one tap. "Same amounts as last cycle" copies last cycle's choices. "Next cycle" lets you plan ahead.
- The amounts you set for a cycle are used everywhere for that cycle (Home rings and pay bar, Safe to spend, cash flow, calendar) and nowhere else; the usual amounts are never changed.
- A reminder on the first day of each cycle to download and review every bank statement. Tapping it opens the set-up page. On by default; turn it off in Reminders.

Look
- A calmer, more modern design across the app: Inter type with aligned figures, quiet graphite and paper colours, flat cards with hairline edges, thin rings without glow, a simple pill Add button, a flat tab bar, muted chart and account colours, and a lighter calendar selection. The background pattern is gone.
- Amounts below zero read "-$355", not "$-355". Average balance kept turns red when it is negative.

Database
- Version 13 adds two tables (this cycle's own amounts, and the set-up checklist). Nothing existing changes; "Delete all data" clears them too.

## 0.12.0 — 2026-11-09

Fixes
- Deleted transactions no longer appear in Home's Recent list or in a ring's, an account's, or un-marked spending's list. (They were kept for undo and history but not filtered out on Home.)
- Spends saved as "Not paid yet" now show on the calendar on their date (amount, due dot, a row with Paid) and in "Still to pay".
- False and repeated capture alerts: a bank's "your debit card was used… at a location where a merchant keyed" email no longer becomes a merchant called that; shop order and ticket confirmation emails (not bank alerts) are ignored instead of being read as refunds; and the same payment reported by another app within a day (a wallet notice, then the bank's email) is one capture, not two or three.
- Planned spending explained: the graph now says what the planned line is made of (bills, spending items spread over the cycle, planned spends). Deleted transactions were never counted; the line grew because spending items are spread day by day.

New
- Swipe a transaction left in Activity to delete it (with Undo). It leaves every list and total at once.
- Repeats can end: never, after a number of times, or on a date, in Add transaction and in the bill sheet on Home, which also offers every week / 2 weeks / month / 3 months / year.
- Settings › Start over: "Start a fresh cycle" (a new pay cycle from today, each account set to its real balance with a correction so history still adds up, and scheduled payments kept—skipping any still unpaid from before today—or all removed) and "Delete all data" (everything, with a clear warning).
- Today's summary shows two items and "+N more", which opens and closes the rest.
- Bolder titles and labels for easier scanning.
- No database change.

## 0.11.2 — 2026-11-03

- Add transaction: a "Paid / Not paid yet" choice for expenses. It starts as Paid for today or earlier and Not paid yet for a later date. Not paid ones are saved as planned: they show on the planned line and in the cash forecast, not as spent.
- A not-paid transaction's page has a "Not paid yet" card with a Mark as paid button (Activity's list has a Paid button too). Once paid it counts as spent, and its page shows the day it was planned for.
- No database change.

## 0.11.1 — 2026-11-03

- Calendar: under the chosen day, "Tap to see this day's account ins and outs" opens a table of each account's start, money in, money out, and end of day, with a total. Today and later days include bills, income, and planned spends still to come, so the end is an estimate (marked "est.").
- No database change.

## 0.11.0 — 2026-11-03

- One way to add: "Add transaction" (Home and Calendar), drawn as the banknote button. The calendar's separate Add bill button is gone.
- Repeats: turn a transaction into a schedule, either the same day each month (pick the day) or a custom pattern (every week, 2 weeks, month, 3 months, or year) from the chosen date. It becomes a bill (or regular income) on the calendar and Home, in the group of the item you picked; if its first date has come, that one is recorded as paid.
- Reminders: every scheduled payment (and planned spend) gets a notification the day before, at your reminder time, on by default. It replaces the separate "bills due tomorrow" and "subscription renewing" reminders.
- No database change.

## 0.10.2 — 2026-11-03

- Planned vs paid fixes: card and loan payment bills now count on the paid line once paid (they were planned but never "paid", so the graph looked under plan); skipped bill dates are not planned; bills still owed from before the dates shown are planned on the first day; a planned spend for a spending item (such as Groceries) is counted once, in that item's budget.
- A bill paid at a different amount shows what was actually paid on its ring, still counts as paid, and "Left to spend" uses the real amount.
- Paying a bill from Add spend keeps the amount and account you typed.
- A month-long test runs the same entries through Home, the calendar, the heat map, planned vs paid, the cash projection, and Insights, and checks they agree.
- No database change.

## 0.10.1 — 2026-11-03

- Cash flow: each chosen account has its own balance line, in the same colour as on Home, with its balance shown for the day you are reading. The accounts button (now inside the graph card) decides which lines show. Paid spending is a dark line so it never looks like an account.
- Home rings look like buttons: a raised disc with a soft back-light in its colour around the edge, and a very thin line of colour along the edge for what is used.
- No database change.

## 0.10.0 — 2026-11-03

- Add spend picks what it is for from your Home items, grouped under faint labels (Monthly expenses, Subscriptions...). Picking a bill pays that bill. Picking nothing leaves it un-marked, listed under "Un-marked spending" on Home (which replaces "Other spending").
- Add bill on the calendar has the same groups, so a new bill lands where you choose on Home.
- Home: "Today's summary" says how many payments are scheduled today and lists them, or "No payments scheduled today". Alerts from captured spends sit above it and only show when there are some. The pay card leads with what is left to spend this cycle and the days to payday; the bar shows each part as a percentage of your pay, and tapping a part (or its label) explains how it is worked out. Rings float like buttons with a hairline edge; account rings are coloured by what their balance holds.
- Reminders: a notification each day listing that day's payments, at a time you choose (Settings › Reminders).
- Goals: just name, target, and how much a month, with how long it will take worked out as you type; "different amount each month" asks for a date instead; and the account it is held in. These fields use the phone keyboard.
- Cash flow: the graph shows planned and paid spending and your what-ifs (added to the planned line), with a switch beside "What if" to show or hide them. The From and To dates are two matching buttons.
- Insights: averages (balance kept, a day, a month), each category this month against its usual month, top places, needs vs wants and by account as bars. The what-if button is gone.
- Calendar: the year sits under the month in a smaller accent colour; the add and arrow buttons are gone (swipe to change month, Today button stays).
- Messages after a change now say what happened ("Transaction added", "Marked paid", "Bill updated").
- No database change.

## 0.9.0 — 2026-11-03

- Home: a small "Today" strip at the top lists what is due today (or says nothing is), scrolling inside its own space. Spent / To pay / Left are gone from the pay card. Alerts are one line. Titles are bigger; rings show just their names.
- Rings are flat again with a faint mirror sheen and a very thin glow, in the colours of the accounts that paid.
- Tap a ring for its transactions; the pencil edits it, and bills have Paid. Tap Other spending for its list.
- Accounts are rings like the groups, with an Add account tile. Tap one for Transactions or Holds: what its balance is set aside for (each bill, each item kept in it, savings) and what is free. The pencil edits the account.
- "Monthly" is now "Monthly expenses".
- Amounts use a small in-app keypad instead of the phone keyboard.
- Cash flow is one bigger graph with labels: cash (happened and ahead), planned spending, paid spending, and what-if purchases, with thicker lines. The dates are under it, starting with this pay cycle. "What if" lets you try spends on any day, past or future.
- Calendar: Heat map and Pay cycle can be on together (the cycle becomes an outline). The month swipes with your finger, and the header no longer changes height between months.
- A faint pattern of small money icons behind every page. Less space between sections everywhere. Shorter Add button.
- No database change.

## 0.8.0 — 2026-11-03

- Plan is no longer a tab: bills, spending, and goals are all added from Home. Plan details (income, subscriptions, loans, notes) are under Settings. Tap your pay on Home to set it.
- Rings, redesigned: a raised disc with a soft shadow; the arc glows in the colour of the account that paid, a share for each account. Inside: what was spent from all accounts over what was set aside ("$131 / $400"). Paid bills show a green tick with the amount.
- Savings goals are rings on Home, with a + to add one. A goal can save the same amount every month, or vary: each month shows what is needed to still reach the date.
- Accounts view as rings: what is planned out of each account against what is free.
- Spending items with no category count the category their name means ("Rent" counts Rent, "Loan" counts Other Debt). Only "Everyday spending" catches everything else. New bills added on Home count in this pay cycle even if their day has passed.
- Not paid yet: plan a spend in Add, then tap Paid in Activity when you pay. It counts nowhere until then; cash flow shows it on its day.
- Planned vs paid: a chart on Cash flow of what you planned to have spent by each day (bills, planned spends, budgets spread evenly) against what you actually paid.
- Cash flow: pick any dates, past and future, to see what happened and what is coming.
- Calendar: always six weeks, so it no longer jumps between months. A Heat map button colours days red by how much went out and green for money in. Tap "Still to pay" for a list of what is due, with Paid.
- Add button looks like a banknote; cards across the app are raised with soft shadows. Larger text and tighter spacing.
- Database version 12 adds a paid flag and planned date to transactions. Existing transactions are all paid; nothing else changes.

## 0.7.0 — 2026-11-03

- Home, simpler: your pay bar with the amount written on each part, three numbers (spent, to pay, left), and your groups. The daily limit and spending-pace cards are gone from Home.
- Groups: "Monthly" and "Subscriptions" to start, and any group you make (Car, Kids...). Each holds bills and spending items as small rings, three to a row, with what is left in the middle.
- A ring fills as money goes out: in its colour when paid from the item's own account, in amber when paid from another account. Spending in a category always counts in that category, whichever account paid.
- Add, change, move, or delete a bill or spending item from Home: tap a ring or the + on a group. Fixed bills go on the calendar; spending items become pay-cycle budgets. Names you type find or make the matching category.
- Mark a bill paid from its ring. Rename or delete groups from their menu; items in a deleted group move to the first group.
- Cash flow no longer assumes daily spending: the line only moves on bills, income, and purchases you try in "What if".
- New look: calm indigo with clearer money colours (green in, rose out, amber caution), in light and dark. Selected chips are easier to see.
- Add spend: the name is optional; a spend with none is called by its category.
- Database version 11 adds two tables for Home groups. Nothing existing changes.

## 0.6.0 — 2026-11-03

- Home rebuilt around your pay. "Your pay" shows what comes in this cycle and one bar split into envelopes: bills, savings goals, and spending envelopes such as groceries or gas, each darker for what is used and lighter for what is left. Underneath: spent, still to pay, and left to spend, plus how much of your pay has no job yet (or how much more you planned than you get).
- Envelopes: each shows what is left as the big number, what was put in and spent, which account it comes from, a mark for where spending should be by today, and an even amount a day until payday. Going faster than the cycle turns it amber; going over turns it red.
- Each spend counts in exactly one envelope: the one for its category and account first, then its category from any account, then a catch-all "Everyday spending" envelope. Spending no envelope takes is shown as "Not in any envelope".
- Bills are an envelope of their own, filled from the calendar, with one-tap Paid. Bills left unpaid from earlier cycles are carried in.
- By account: each spending account's balance split into bills still to pay from it, money left in its envelopes, savings to put aside, and what is truly free, with a warning and a date when an account is short.
- Split my pay: type your pay (or use the income on your calendar), see bills and savings come off the top, then add envelopes from your own categories, each with an amount and an account. A counter runs down to "Every dollar has a job", and each envelope shows what you spent on it last cycle as a starting point.
- Money left in envelopes is held back from Safe to spend until payday, so Home and Safe to spend always agree.
- Cash flow now shows your real past: a grey line of what your cash actually was, up to Today, then the projection. Dots mark income, bills, and big spending days. Drag back to read any past day: spent, bills, and income that day.
- "What if I buy this?" replaces the daily-spending slider: type an amount and pick the day (today, tomorrow, this weekend, before payday, or any date), or tap a day on the chart. It is drawn as a dashed purple line, with whether you would run out or dip into your buffer, the lowest point and the cash before payday before and after, and how many days of your usual spending it is. Try up to four at once. Nothing is saved.
- "What happened" lists the past days money moved, next to "Coming up" and "Every day ahead".
- Calendar days show a dot for each thing on them: income (green), due (red), overdue (dark red), paid (blue), and spending (orange). The legend matches.
- The amount field on Add spend, bills, goals, and the purchase check is centred with its currency symbol, on a card you can tap anywhere.
- No database change. Budgets gain an "each pay cycle" period and an account, both in existing columns.

## 0.5.0 — 2026-11-03

- Cash flow rebuilt: a headline that says when cash runs out or where its lowest point is, cash at the end of today, the day before payday, and the end of the period; a chart you can drag to read any day, with the safety buffer and paydays marked; a "What if I spent less?" slider that starts at your real pace and shows what a different daily amount does to your lowest point; and a timeline of only the days money moves, with overdue bills marked. Every day is one tap away.
- The headline also says when cash covers the bills but budgets, the buffer, and savings goals already take more than is free, so it never seems to disagree with Safe to spend.
- Savings goals: a name, a target, an optional date, and where the money is kept. An account kept only for the goal makes its balance the progress; otherwise add or take out money yourself, with a history. A date works out the monthly amount, a monthly amount works out the finish date, and each says whether you are on track.
- Pay yourself first: what is still to save for goals this pay cycle, and goal money kept in a spending account, are held back from Safe to spend as "Saving for your goals".
- Goals appear on Plan (step 4) and on Home under "Saving for".
- Calendar: the pay-cycle shading is a clear green band, stronger on the first and last day, and the Pay cycle button is aligned with a check mark when on.
- Database version 10 adds the savings goal tables. Nothing existing changes.


## 0.4.0 — 2026-11-02

- Calendar rebuilt around bills: a month summary (still to pay, paid of total, income expected), the amount due shown on each day, the chosen day with Add bill and Add spend, and every bill in the month as a list with a one-tap Paid. Swiping the month slides the grid the way you swiped.
- New bill editor: Bill, Subscription, Income, or Loan; one-tap starts for common bills (Rent, Phone, Netflix, Salary...); a big amount; how often as chips; the first due date with the next three dates shown; the paying account; and one switch for "set aside before Safe to spend". Category, end date, priority, and notes are under More options.
- Back swipes no longer stutter: the gesture and the release now play the same animation, and the tab bar moves with its page instead of appearing at the end.
- Editors rise from the bottom; screens you browse into slide in from the side.
- Home and Calendar are worked out on a background thread and drawn once per change, and the dashboard is computed once and shared, so less work happens while screens move.
- A non-debuggable "phone" build type for everyday use on a personal phone.
- Home now leads with "Left to spend today": an even share of what is left until payday, fixed at the start of the day. Going over turns it red and says what tomorrow's limit becomes.
- Spending forecast on Home: your everyday spending pace this cycle, the day the money runs out at that pace, and a chart of your pace against your daily limit.
- "Thinking of buying something?": type a price to see it in days of your daily limit and what your limit becomes. Wait a day saves it to spending notes for tomorrow; Buy it opens the add screen filled in.
- Autopay for bills, subscriptions, loan payments, and income: each due date from the day it is turned on is recorded as paid automatically when the app opens and once a day. New subscriptions and loan payments start with autopay on. Earlier dates are never back-filled.
- Calendar forecast: a future day shows what is left to spend by then at your current pace, with cash in the accounts as a second line. Days where the money would run short are marked red, and days with under three days of spending left are marked amber.
- Database version 9 adds the autopay column to bills. Existing bills stay paid by hand until you turn autopay on.


## 0.3.1 — 2026-11-02

- Calendar is a tab. Swipe the month sideways to change months; the day summary labels cash clearly and explains card spending.
- Screens move by direction: detail screens slide in from the right and out on back, and the back gesture previews the screen underneath. Tabs cross-fade. The add screen rises from the bottom.
- "Read new texts directly" for phones that hide message notifications from other apps (Android 15 and later). The setup screen says when Android is hiding text.
- Captures read more bank formats: SBI amounts without a currency, HDFC run-together UPI texts, Axis UPI/P2M lines, ICICI "credited" merchants, store numbers, NEFT prefixes, and European decimal commas.
- Personal texts in a messaging app are no longer captured, and card-bill payment confirmations are skipped so they do not double-count.
- The same purchase twice in a day is no longer dropped as a duplicate.
- Tapping a bundle of "tap to add" notices opens the inbox, not Home.
- Switch rows can be tapped anywhere and are announced with their labels by screen readers.
- Safe to spend explains its lines in plain words and no longer implies expected income is counted.


- Redesigned around four tabs: Home, Activity, Plan, and Insights. Settings open from the gear icon. Calendar, cash flow, what-if, subscriptions, and debt now open from Plan.
- Home leads with Safe to spend, how long it has to last, and about how much that is per day. Below it: this cycle's spending, what is coming up with a Paid button, and recent activity.
- One Add button on Home and Activity. The add screen takes the amount as you type it (450 is 450.00), starts on your main account and today, and offers Today and Yesterday chips.
- Activity groups transactions by day with a daily total. Quick views for All, Spending, Income, and Upcoming; finer filters are in one sheet.
- Plan walks through income, bills, budgets, and what is left, with an Add button on each step.
- Auto-capture: payment alerts from Google Messages, Samsung Messages, Gmail, Outlook, Google Wallet, Venmo, Cash App, PayPal, and others are read on the device and wait in a Captured inbox. Add, review, or dismiss each one. Optional: a "tap to add" notification, and auto-add when the card ending matches exactly one account.
- The parser handles US card alerts and Indian bank and UPI messages in $, ₹, €, £ and more, and skips passcodes, bill reminders, requests, offers, and declined payments.
- Database version 8 adds the `captured_spends` table. Existing data is kept.


## 0.2.0 — 2026-10-12

- Undo the last added, edited, or deleted transaction, including both sides of a transfer.
- Undo the last schedule edit, skip, or mark-paid.
- Home shows at most five upcoming events, with overdue items listed separately and a path to the full calendar.
- Calendar dates list posted and scheduled activity, and delete asks before removing a date or a series.
- Excel workbook export for the current pay period or all dates, for every account or one selected account. You choose where to save the file.
- Undoing a balance change made on an account screen removes that adjustment and restores the previous balance.
- Settings shows version 0.2.0 (version code 2) and a manual reset that deletes financial records only after confirmation.
- Debug builds can enable reset-on-launch. It stays off unless you turn it on, and release builds cannot run it.
- Normal updates keep the on-device database. Schema changes still use the version 2 migration.

## 0.1.0 — 2026-10-11

- Offline ledger for accounts, transactions, budgets, safe-to-spend, and a cash-flow forecast.
- Recurring payments stored as rules, with one-date paid and skip records.
- Pay period from the 20th through the 19th, with expandable home totals.
- No live bank connection.

Known limitations: there is no live bank aggregation. Marking a scheduled transfer paid records the payment on the scheduled account. Budget pace cards still follow the calendar month.
