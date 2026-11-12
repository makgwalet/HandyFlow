# FUEL C04 (part 2): Routes and multi-stop (2026-11-12)

## 1. Implemented
- A **route** is one tanker and one driver carrying several loads (stops) in an order on one day. Created from the planning board (tanker, driver, optional name). Creating checks the same compliance and employment rules as allocation.
- **Stops** are ordinary deliveries that point at the route. Add a stop (a scheduled load on the route's day, with a compartment split), remove a stop, move a stop up or down, cancel the route (with a reason).
- The tanker is filled once for the whole route: across all stops each compartment holds **one fuel type** and its total cannot pass its capacity. Stops on the same route do not count as time conflicts for each other (they would under the 4-hour tanker rule); any other load still does.
- A load that is a stop can only be changed from its route (allocating it separately is refused). Taking a stop off a route, or cancelling the route, leaves the load allocated to the same tanker and driver.
- Route state is worked out from its stops: Planned, On the road, Completed (every stop delivered or cancelled), Cancelled. The planning board shows routes for the day with litres against capacity, per-stop window and status, and "Stop n" chips on loads. The driver app shows "stop n on your route".

## 2. Preserved
- No new delivery status. Stock, invoicing, variance, reversal, reports, the failed-attempt flow and ordinary allocation are untouched (the allocation code gained an optional route argument; with none it behaves as before). `DeliveryResponse` and planning rows only gained trailing fields.

## 3. Files
- DB: `V673__fuel_routes.sql`.
- Java: new `FuelRoute`, `FuelRouteRepository`, `FuelRouteService`, `FuelRouteController`, `RouteDtos`; `FuelDelivery` (routeId, routeSequence, join, move, leave), `FuelDeliveryRepository` (findByRoute, findByRoutes), `FuelTransportService.allocate(..., routeId)` and `checkRouteCompartments`, `FuelDivisionRules` ("routes" is Distribution), `DeliveryResponse`, `PlanningResponse`, `FuelService`.
- Web: new `RoutesPanel.tsx`; `PlanningTab`, `DriverTab`, `transportOps` (route types, `moveStop`, `routable`).

## 4. DB
- `fuel_routes` (status PLANNED or CANCELLED), `fuel_deliveries.route_id` and `route_sequence` (check: both set or both empty, position at least 1), and a **deferred** unique constraint on (route, position) so a reorder can swap positions in one transaction. Checked on a scratch PostgreSQL 16: swap inside a transaction is allowed, a duplicate position is rejected at commit, a route with no position is rejected.

## 5. API
- `GET /api/v1/fuel/routes?date=`, `GET /api/v1/fuel/routes/{id}` (FUEL_READ).
- `POST /routes`, `POST /routes/{id}/stops`, `DELETE /routes/{id}/stops/{deliveryId}`, `PUT /routes/{id}/stop-order`, `POST /routes/{id}/cancel` (FUEL_MANAGE or FUEL_TRANSPORT_MANAGE).

## 6. Security
- Every endpoint has `@PreAuthorize` and `requireModule("fuel")`; writes use the existing transport permission, so no new permission. Division rules route "routes" to Distribution. Tenant scoped.

## 7. Tests
- Java (parse-checked only, not run): new `FuelRouteServiceTest` (10), `FuelTransportServiceTest` +4 (route siblings, compartment capacity, fuel mixing, routed load needs route).
- Web (run): new `RoutesPanel.test.tsx` (9), `transportOps.test.ts` +4, `DriverTab.test.tsx` +1. Fuel and navigation suites: 319 pass.

## 8. Gaps
- No route optimisation or distance, travel time or ETA. Order is set by hand.
- A stop's own time (scheduled time or window) is not checked against its position in the order.
- The driver app lists loads as before with a stop number; it does not force stops to be done in order.
- A route belongs to one day and one tanker; a second trip is a second route. No tanker reload step between trips.
- Cancelling a route does not unallocate its loads.

## 9. Risks
- Reordering relies on the deferred unique constraint; the service always updates all stops in one transaction.
- `FuelTransportService.allocate` with a route is public so the route service can call it. Calling it directly with an arbitrary route id would bypass the route service's checks; only the route service does.

## 10. Next
- F02: actual versus estimated cost per vehicle, route and depot.
