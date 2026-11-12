# Fuel E01: Truck Stop & Driver Services, v1 (2026-11-12)

## 1. Implemented
- **Services:** a Bookings service can be marked as a truck stop service of a kind (parking, shower, wash, weighbridge, workshop, food, other). Price, length, opening hours and bays stay in Bookings (owner decision: reservations on Bookings via a facade).
- **Reservations:** reserve a slot for a truck (registration, driver, phone). The booking is created by Bookings, so its lead-time, advance-limit and no-double-booking rules apply, and it appears in Bookings. Fuel keeps only the truck and driver. Cancel, complete and no-show go through Bookings.
- **Bays:** a service that has staff assigned in Bookings is treated as having bays; a reservation must pick one, because Bookings can only stop double booking per bay. A service with no bays assigned is not capacity-limited.
- **Screen:** Fuel > Truck stop (only when the Truck Stop division is active): services, reserve dialog with live free slots, reservations with status and actions.
- **Division:** `/api/v1/fuel/truck-stop/**` requires the Truck Stop division (interceptor rule), checked on the server. The wizard blurb no longer says "set-up only".

## 2. Preserved
No existing Fuel, Bookings or Fleet behaviour changes. Bookings controllers, permissions and tables are untouched; Bookings gains only a facade.

## 3. Files
Bookings: `application/BookingsFacade`, `application/package-info` (named interface). Fuel: `FuelTruckStopOffering`, `FuelTruckStopReservation`, two repositories, `FuelTruckStopService`, `FuelTruckStopController`, `TruckStopDtos`, `FuelDivisionRules`, `package-info`. Web: `TruckStopTab`, `truckStopOps`, `FuelPage`, `moduleSections`, `setupOps`. Tests: `FuelTruckStopServiceTest` (11), `FuelDivisionRulesTest` +1, `FuelEndpointSecurityTest` (reads V669 as well as V666), web tests.

## 4. Database
`V669`: `fuel_truck_stop_offerings` (unique per Bookings service per tenant, kind check), `fuel_truck_stop_reservations` (unique per booking), and permission `FUEL_TRUCKSTOP_MANAGE` (module fuel, NORMAL, granted to ADMIN). No foreign keys into Bookings tables. Applied twice on scratch PostgreSQL 16 (idempotent) and the kind check rejects a bad value. Not run through Flyway.

## 5. API (all under /api/v1/fuel/truck-stop)
`GET offerings`, `GET available-services`, `POST offerings`, `PUT offerings/{id}/status`, `GET offerings/{id}/slots?date&resourceId`, `GET reservations?vehicle&limit`, `POST reservations`, `POST reservations/{id}/cancel|complete|no-show`. Reads: FUEL_READ, FUEL_MANAGE or FUEL_TRUCKSTOP_MANAGE; writes: FUEL_MANAGE or FUEL_TRUCKSTOP_MANAGE.

## 6. Module dependency (needs your attention)
`fuel/package-info` now allows `bookings` and `bookings::*`. Bookings allows only `shared` and `crm`, and no module lists `bookings`, so there is no cycle. `ArchitectureVerificationTest` not run here; please run it. Fuel only uses `BookingsFacade`, which returns its own small records, never Bookings entities or DTOs.

## 7. Security
Tenant from TenantContext; every Fuel query and every Bookings call is tenant-scoped; a reservation id from another tenant is "not found" and Bookings is not called. The facade does not apply BOOKINGS_* authorities, so Fuel's own permissions govern: someone with FUEL_TRUCKSTOP_MANAGE can reserve in Bookings through Fuel without holding BOOKINGS_MANAGE. That is intended but is a decision you may want to revisit.

## 8. Transactions
Reserve joins one transaction with Bookings, so a failure saving the Fuel record rolls the booking back. Bookings sends its confirmation email on its own path (see below).

## 9. Gaps and risks
- **Multi-night parking is not supported.** Bookings holds a date, start time and length within a day. Overnight stays need either multi-day support in Bookings or a Fuel-owned stay model; that needs your decision.
- **Confirmation emails:** the booking's client is the truck and driver; no email is sent because none is captured. Bookings' own reminders do not apply.
- No fleet accounts or charge-to-account, no invoice or payment, no POS items (showers/meals/fuel on one bill), no driver self-service, no occupancy view.
- Reservations are not linked to Fleet vehicles; registration is free text.
- Switching a service off blocks new reservations only; existing ones stay in Bookings.
- Deactivating the Truck Stop division is not blocked by active reservations (the architecture note says it should be).
- A service with no staff assigned in Bookings can be double booked.

## 10. Tests
Java is parse-checked only here; Maven not run. vitest and tsc run (see delivery message).

## 11. Next
Decide on multi-night stays; link reservations to Fleet vehicles; charge a reservation to an invoice through InvoicingFacade; block division deactivation while reservations are open.
