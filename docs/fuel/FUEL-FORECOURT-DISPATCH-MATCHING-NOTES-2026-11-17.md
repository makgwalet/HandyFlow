# Fuel: match forecourt receipts to the supplying depot's dispatches (2026-11-17)

Written, not compiled or run (Java). Web: all fuel tests (537) and tsc pass.

## The data change (V700)
- `fuel_dispatches.destination_depot_id` (nullable FK to depots). Set on the dispatch form with "Delivering to forecourt" (shown only when the company has more than one depot). Existing dispatches and issues to vehicles, assets and customers are unchanged.
- `fuel_dispatch_receipt_matches`: a person links a receipt to the dispatch it came from. One receipt belongs to at most one dispatch; a dispatch may be received in several receipts (split deliveries).

## Rules
- A link needs: dispatch has a destination; receipt is in a tank of that forecourt; same fuel type; neither reversed; receipt not already linked.
- Nothing is matched automatically and no tolerance is applied. The report states the gap ("100 L less received than dispatched") and the company decides what is acceptable.
- Depot limits: a limited user sees and matches only forecourts in their depots (403 `FUEL_SCOPE_DEPOT` otherwise).
- A link whose receipt is later reversed drops out of the report (the dispatch shows as open again).

## Endpoints (under /forecourt, so the Retail division rule applies)
`GET /api/v1/fuel/forecourt/supply-report/dispatch-matching?days=` (FUEL_READ); `POST` same path with `{dispatchId, receiptId}` and `DELETE …/{matchId}` (FUEL_MANAGE or FUEL_STOCK_MANAGE).

## Not done
- A transfer between tanks (the other way fuel reaches a forecourt) is not part of this; it is one record and already reconciles in the supply reconciliation.
- Reversing a dispatch does not delete its links (it just drops out of the report as reversed).
- No alert for dispatches left unmatched.
