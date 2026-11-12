# Fuel Phase 8b: route optimisation suggestion (patch 0807)

## Finding that changed the plan
Customer sites had **no map position**, only a one-way distance from the depot. A straight-line optimiser needs coordinates, so this patch adds optional latitude and longitude to sites (V683). Where they are missing the suggestion falls back to windows and stored distance.

## 1. Implemented
- `GET /api/v1/fuel/routes/{id}/suggested-order` (optional `startLat`, `startLng`, the depot) returns a **suggestion only**; nothing is saved. The planner applies it with the existing `PUT /routes/{id}/stop-order`, or ignores it.
- **STRAIGHT_LINE** (every movable stop has coordinates): cheapest insertion then 2-opt on straight-line distance. Stops with a delivery window keep window order (earliest window end first); a change that would swap two of them is rejected. The suggestion is never longer than the current order when the current order already respects the windows. With a start point the route is measured out and back; otherwise from the last fixed stop before the movable ones.
- **WINDOWS_AND_DISTANCE** (some coordinates missing): stops with a window first, earliest first, then the rest nearest first by the stored site distance. The sites with no position are named.
- Only **scheduled** stops move. A stop on the road or delivered keeps its place, as the existing route rules require.
- Web: "Suggest order" on a route (needs the transport manage permission and at least two scheduled stops) opens a dialog with the new order, which stops moved, the km saved, a plain note, and "Use this order" or "Keep my order". Site dialog has optional latitude and longitude.

## 2. Preserved
Routes, stop order rules, windows, costs and all existing site fields. Existing sites keep no position. Old clients that omit the new fields still work (the request record keeps its 7-argument constructor); a site edit without them clears any saved position, as it already does for distance.

## 3. Files
- DB: `V683__fuel_site_coordinates.sql`.
- Java: `FuelRouteOptimiser` (pure), `FuelRouteOptimiserService`, `RouteDtos` (`SuggestedStop`, `SuggestedOrder`), `FuelRouteController`, `FuelCustomerSite`, `CustomerSiteRequest`/`Response`, `FuelCustomerService`.
- Web: `RoutesPanel.tsx` (`SuggestDialog`), `routeSuggestOps.ts`, `CustomersTab.tsx`, `customerOps.ts`.

## 4. DB
Two nullable columns on `fuel_customer_sites` and a check that both are set or neither, within -90..90 and -180..180. No backfill.

## 5. API
One new GET (`FUEL_READ`, `requireModule("fuel")`, tenant from the token). Site create and update accept `latitude` and `longitude`; responses return them.

## 6. Security
Read-only endpoint. Sites from another tenant are ignored. Start point validated for range and for being given as a pair.

## 7. Tests
Not run here: Java is parse-checked only; run Maven. The optimiser logic itself was compiled and run standalone against the scenarios in the tests (sweep, window order, fixed stops, fallback, never-worse) and behaved as the tests expect. Java: `FuelRouteOptimiserTest` (11), `FuelRouteOptimiserServiceTest` (6), `FuelCustomerSiteCoordinatesTest` (5). Web: 4 new in `RoutesPanel.test.tsx`, 5 in `routeSuggestOps.test.ts`, 1 in `customerOps.test.ts`, 1 in `CustomersTab.test.tsx`; those four files pass (35 tests).

## 8. Gaps
- Straight-line only: no roads, traffic, turn restrictions or vehicle speed. Window times are used for order, not for arrival-time feasibility.
- No capacity or compartment check, and no multi-route balancing.
- The depot start point is a request parameter, not stored; the screen does not ask for it yet.
- Coordinates are typed in; there is no map picker or address lookup.
- Closed loops have two equal directions; the suggestion picks one.

## 9. Risks
- A suggestion looks authoritative; the note says it is straight-line. A real routing provider would replace the distance function, not the screens.
- Coordinates entered the wrong way round (lat and long swapped) give plausible-looking but wrong orders; range checks catch only impossible values.

## 10. Next
Store a depot position, show a map, optionally plug in a road-distance provider behind the same endpoint.
