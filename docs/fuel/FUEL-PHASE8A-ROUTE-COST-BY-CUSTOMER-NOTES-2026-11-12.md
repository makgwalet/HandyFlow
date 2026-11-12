# Fuel Phase 8a: route cost by customer (patch 0806)

## 1. Implemented
- `GET /api/v1/fuel/cost-actuals/by-customer?from&to&method` shares each route's **recorded actual cost** (vehicle, driver, tolls, other) across the customers it delivered to.
- `method`: `LITRES` (default, by litres delivered), `STOPS` (equal per delivered stop), `DISTANCE` (by the customer site's distance in km). Chosen per report; nothing is stored and no rate or weighting is built in.
- Rules: only delivered stops share cost; cancelled and reversed stops do not. Shares are in cents and add up to the route's recorded total (the largest share takes the rounding difference). If the method has no usable weights on a route (no litres, or no site distances) that route is split equally per stop and counted in `fallbackRoutes`. A delivered route with no recorded actuals is counted in `routesSkipped` and never filled with an estimate. Voided entries are ignored.
- Web: "Route cost by customer" section under the route costs panel, with a "How to share" choice, a table (routes, stops, litres, cost shared, per litre, total) and a plain note about skipped or equally split routes.

## 2. Preserved
Route cost report, entries, estimates, tanker and depot rollups, and permissions are unchanged. The new endpoint is read-only.

## 3. Files
- Java: `RouteCostAllocationDtos`, `FuelRouteCostAllocationService`, `FuelRouteCostController` (one new method and field).
- Web: `RouteCostsPanel.tsx` (`CustomerAllocation`), `routeCostOps.ts` (methods, note).

## 4. DB
None. No migration.

## 5. API
One new GET. `FUEL_MARGIN_READ`, `requireModule("fuel")`, no client tenant id. Existing queries reused; each already filters by tenant.

## 6. Security
Same permission as the existing cost report. No data is written.

## 7. Tests
Not run here: Java is parse-checked only; run Maven. Java `FuelRouteCostAllocationServiceTest` (11): litres, default method, stops, distance, fallback, cent-exact rounding, skipped without actuals, undelivered and voided, one customer on two stops, bad range, split helper. Web: 4 new in `RouteCostsPanel.test.tsx`, 2 new in `routeCostOps.test.ts`; all 17 in those two files pass.

## 8. Gaps
- The shares are a report, not postings. Nothing is added to invoices or customer statements.
- `DISTANCE` uses the one site distance already stored, not the route's actual path.
- Costs spread by customer, not by site or by product.
- Fuel purchase cost is not included (same as the route cost report).

## 9. Risks
- A customer on several routes gets the sum of different splits; the method choice changes the result, so compare like with like.
- Tolls and "other" are shared along with vehicle and driver cost.

## 10. Next
Optional: save a chosen method as the tenant default, and an export.
