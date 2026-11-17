# Fuel: dashboard summary endpoint — notes, 2026-11-17

Written, not compiled or run (Java). Parse-checked only; Maven is not reachable from my sandbox, so please run the Java tests. No migration. Web: Fuel and navigation tests (94 files) and `tsc` pass.

## Why
The dashboard read the first 200 rows of dispatches, deliveries and receipts and counted from those. A busy company's month, or its open deliveries, could be cut off silently, and the figures were wrong without any sign.

## What changed
- `GET /api/v1/fuel/dashboard-summary` (FUEL_READ, Fuel module). Worked out on the server, **over the whole month and every open delivery**, for **only the tanks the caller may see** (the same access limits as the lists; no tanks means zeros and the database is not asked).
- Returns: month litres, dispatch count, receipt count, litres per day up to today, dip readings with a negative variance, open / in-transit / overdue delivery counts, and the 8 soonest open deliveries.
- The month is the **calendar month in South African time** (a late-night dispatch lands on its South African day).
- Tank levels stay on the tanks list (it is not paged).
- Web: the dashboard now calls this one endpoint plus the tanks list instead of three lists with `size=200`. If the summary cannot be read, the banner says the figures may be incomplete and the numbers show as zero rather than guessed.

## Two fixes found on the way (behaviour changes, please note)
1. **The "negative variance" alert never fired.** It counted dispatches with a `varianceLitres` field, which dispatches do not have. The real variance is on tank **dip readings**. The alert now counts this month's dip readings more than 5 L below the book figure, and links to Tanks (where dips are recorded) instead of Dispatches.
2. **Reversed dispatches and receipts are no longer counted** in the month figures. The old screen counted them.

## Not changed
Tanks list, the lists themselves, dispatch and receipt rules. Overdue still means scheduled more than a day ago and not yet on the road.

## Not done
- The summary does not cover the Retail forecourt (shift takings, variances) or the Truck stop (yard, requests). The dashboard has no tiles for them yet; they would be added here.
- No caching. It runs four small queries per load.

## Third fix
The overdue alert read "2 deliveryies are overdue" (a typo in the wording). It now reads "2 deliveries are overdue".
