# Cash flow and safe-to-spend

Both numbers are estimates. The screens say so.

## Safe to spend

```
liquid balance
- committed payments inside the horizon
- minimum buffer
- planned transfers out of liquid accounts
+ income marked high or medium confidence
- remaining discretionary budget the user reserved
```

Committed payments are mandatory and important outflows on liquid accounts. Transfers are listed separately. Optional and flexible items stay on the calendar and are not subtracted. Low-confidence income is not added. Negative inputs that would increase the result are ignored.

The home screen shows each line. A negative result means the estimate is below zero, not that a bank rejected a payment.

## Forecast

`CashFlowForecastEngine` walks each day of the horizon:

- start from the liquid balance
- add scheduled inflows and outflows dated that day
- subtract an estimated discretionary amount

The daily discretionary amount is the remaining reserved budget spread across the horizon. If nothing is reserved, it is the last 30 days of discretionary, potentially unnecessary, unknown, or uncategorized spending divided by 30. Scheduled bills are not added again as discretionary spending.

The home timeline still lists only the scheduled items. The forecast screen lists the daily projection, including the discretionary estimate.

## What if

`WhatIfEngine` runs the same projection twice. The scenario can add a one-time expense, remove one scheduled key, or add a monthly amount set aside. It returns the two ending balances and whether the scenario drops below the buffer. It does not write accounts or transactions.

## Subscriptions

Active subscription outflows are converted to a monthly figure and multiplied by 12 for the annualized total. Weekly amounts use 52/12. Yearly amounts are divided by 12.
