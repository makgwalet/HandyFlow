# FUEL C04 (part 1): Delivery windows and failed delivery (2026-11-12)

## 1. Implemented
- **Delivery window** on a load (start and/or end, either may be open). Office sets it from the planning board; the driver sees it; the planning board shows it. `FuelDelivery.isOutsideWindow(at)` is available for later use.
- **Failed delivery attempt**: a load on the road can be recorded as not delivered, with a reason (Site closed, No access, Nobody to receive, Customer refused, Safety, Vehicle fault, Other with a note), optional litres brought back, and an optional new time. The load goes back to Scheduled with its tanker and driver, the attempt count goes up, and it can be dispatched again.
- Append-only record of every attempt (who, when, why, attempt number).
- Driver app: "Could not deliver" with the same offline queue as C05 (kind FAILED), replay-safe by request id. Office: "Could not deliver" and "Window" buttons on the planning board.

## 2. Preserved
- No new delivery status, so stock, invoicing, variance, reversal and reports are untouched. Delivered, cancelled and reversed loads behave as before. `DeliveryResponse` and the planning rows only gained trailing fields.

## 3. Files
- DB: `V672__fuel_delivery_windows_and_failed_attempts.sql`.
- Java: `FuelDelivery` (window, `failedAttempts`, `recordFailedAttempt`), new `FuelDeliveryException`, `FuelDeliveryExceptionRepository`, `FuelDeliveryExceptionService`, `DeliveryExceptionDtos`; `FuelTransportController` (4 endpoints); `DeliveryResponse`, `PlanningResponse.PlanLoad`, `FuelService.toDeliveryResponse`, `FuelTransportService`.
- Web: `DeliveryExceptionDialogs.tsx`, `PlanningTab`, `DriverTab`, `offlineQueue` (kind FAILED), `transportOps` helpers.

## 4. DB
- `fuel_deliveries`: `window_start`, `window_end` (check: end after start), `failed_attempts` (default 0).
- `fuel_delivery_exceptions` (append-only) with a unique index on (tenant, delivery, client request id). Checked on a scratch PostgreSQL 16: existing rows get 0 attempts and no window; a window ending before it starts is rejected.

## 5. API
- `GET /api/v1/fuel/deliveries/{id}/exceptions` (FUEL_READ).
- `PUT /api/v1/fuel/deliveries/{id}/window` and `POST /api/v1/fuel/deliveries/{id}/failed-attempt` (FUEL_MANAGE or FUEL_TRANSPORT_MANAGE).
- `POST /api/v1/fuel/driver/loads/{id}/failed-attempt` (driver; own load only, another driver's load looks missing).

## 6. Security
- Every endpoint has `@PreAuthorize` and `requireModule("fuel")`; writes use the existing transport permission, so no new permission and no change to the security test. Division routing is unchanged (these paths already fall under distribution).

## 7. Tests
- Java (parse-checked only, not run): new `FuelDeliveryExceptionServiceTest` (9 tests: reschedule and count, second failure, only-in-transit, reason validation, litres limits, replay, driver ownership, windows, missing load).
- Web (run): new tests in `transportOps.test.ts` (3), `offlineQueue.test.ts` (+1), `DriverTab.test.tsx` (+2), `PlanningTab.test.tsx` (+2).

## 8. Gaps
- Multi-stop routes, route ordering or optimisation, and stop sequencing are NOT built. They need a route entity of their own (several deliveries on one tanker run).
- Litres brought back are recorded only. They are not booked back into a depot tank, because stock is only deducted when a delivery is completed. If your process needs a stock movement on return, that is a decision for you.
- A window is informational. Nothing blocks completing outside it, and there is no alert for a missed window.
- Customer eligibility checks (credit hold, licence) are not built.
- A failed attempt cannot be undone. A wrong one stays in the history.

## 9. Risks
- The rescheduled time is not checked against tanker double-booking (the allocation check does that when the load is reallocated).
- The reason list is fixed in code (operational reasons only). It does not say who is at fault.

## 10. Next
- Route entity for multi-stop runs, then F02 (actual-vs-estimate costing), which needs it.
