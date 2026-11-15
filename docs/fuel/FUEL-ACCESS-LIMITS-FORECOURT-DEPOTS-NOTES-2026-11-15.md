# Fuel: depot limits across more views, scope cache, forecourt per legal entity

Status: Java written, not compiled or run. Web tests (fuel folder) and `tsc` run. No migration.

## 1. Depot limits now apply to
A user with depot limits (Setup > Access limits) now sees only their depots in:
| View | Rule for a depot-limited user |
|---|---|
| Stock availability | Tanks in their depots, and the depot totals for those depots. A tank in no depot is left out. |
| Stock ledger | One tank: refused (403 `FUEL_SCOPE_DEPOT`) if the tank is outside their depots. All tanks: only movements on their tanks. |
| Transfers | Transfers with at least one end on their tanks. |
| Reservations | Reservations on their tanks. |
| Forecourt shifts, one shift, forecourt summary | A shift is shown only when every pump read in it is on their tanks, because a shift's takings cover all its pumps. Otherwise it is hidden (list, summary) or refused with 403 (one shift). |
| Forecourt supply report | Only their forecourts, so the totals match what they may see. A supplier is still named above its forecourts. |
A user with no depot limits, an impersonation session, or a call with no identified user is unchanged.

## 2. Scope cache
The per-request scope lookup is cached for 15 seconds per tenant and user, and evicted when that user's limits are saved. On another server node a change can take up to 15 seconds to be seen. Short on purpose, because a newly added limit should bite quickly.

## 3. Group report
Each legal entity row now has forecourt litres, forecourt sales (only for people who can see margin) and shift count for the period, from closed, not-reversed shifts. A pump reading belongs to the entity that owns its tank's depot; a removed tank or a depot with no entity goes in "Not assigned". A shift that spans two entities counts once in the group total.

## 4. Known gaps
- Orders, the customer list and other lists not tied to a tank or depot are still not depot-filtered.
- The forecourt shifts list applies the row limit before the depot filter, so a limited user can get fewer rows than asked for.
- A shift with no readings is hidden from depot-limited users (nothing places it).
- Reservations, transfers and the ledger are filtered in the query, so their paging is correct.
- Forecourt sales are for the current period only; there is no previous-period comparison for them.
- A depot update with no `supplyingDepotId` still clears the supplier (full replace). The screen always sends it; other API clients must too.
- Not done, large: split one forecourt's volume between several supplying depots; match forecourt receipts to the supplying depot's dispatches.

## 5. Tests
`FuelDepotLimitFilteringTest` (new, 6), `FuelScopeServiceTest` (+4: cache window, eviction on save, keyed per user and tenant, shift visibility), `FuelGroupReportServiceTest` (+2, and two new mocks), web `GroupReportTab.test` (+2).
