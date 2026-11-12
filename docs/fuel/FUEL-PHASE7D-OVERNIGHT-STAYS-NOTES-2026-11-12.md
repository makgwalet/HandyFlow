# Fuel Phase 7d: multi-night truck stop parking (patch 0803)

## Decision applied
Overnight stays are a **Fuel-owned model** beside the timed Bookings services. Bookings holds a slot within one day, so it is untouched.

## Implemented
- **Truck stop > Overnight stays** (only with the Truck Stop division, enforced on the server because the endpoints sit under `/truck-stop`).
- **Company settings:** nightly rate (rand) and number of parking bays. Both start empty. Nothing can be booked until a rate is set; with no bay count nothing is capacity-limited.
- **Stay:** truck registration, driver and phone, arrival and departure dates, note. It keeps the rate it was booked at, so changing the rate later only affects new stays.
- **Nights and amount:** each night from arrival up to, not including, departure, minimum one. Once the truck has left, nights run to the actual departure date. Amount = nights x the stay's rate. Nothing is invoiced.
- **Lifecycle:** Booked, then Check in (from the arrival date), then Check out (on a date you choose, default today). A booked stay can be cancelled (reason required) or marked no-show (only once the arrival date has come). The departure can be changed while booked or in the yard.
- **Bays:** when a bay count is set, a night with every bay taken cannot be booked or extended into; the message names the night. Cancelled, no-show and checked-out stays release their bay. A truck still in the yard past its planned departure keeps its bay up to today and is flagged "Past its departure date".
- **Yard today:** trucks in the yard, due to arrive, due to leave, and bays free.

## Preserved
Bookings, timed truck stop services and reservations (E01) are unchanged.

## Files
- DB: V682 `fuel_truck_stop_parking_settings` (one per company), `fuel_truck_stop_stays` (departure after arrival, status and rate checks, cancelled rows carry a reason, checked-out rows carry the actual date). No new permission: FUEL_TRUCKSTOP_MANAGE (V669) for changes, FUEL_READ to look.
- Java: FuelTruckStopParking, FuelTruckStopStay, two repositories, StayDtos, FuelTruckStopStayService, FuelTruckStopStayController.
- Web: StaysTab, stayOps, menu "Overnight stays" under Truck stop, FuelPage case.

## API (under /api/v1/fuel/truck-stop)
GET parking-settings, stays, stays/occupancy; PUT parking-settings; POST stays, stays/{id}/check-in, check-out, cancel, no-show; PUT stays/{id}/departure. Module guard on each; tenant from context.

## Tests
- Java: FuelTruckStopStayServiceTest (10): rate needed, rate kept, nights and check-out, booking refusals, capacity, no limit, overstay keeps its bay, changing departure, lifecycle, occupancy counts, settings rules. Parse-checked only; not run (you run Maven).
- Web: StaysTab (7), stayOps (4). tsc clean.

## Gaps and risks
- No invoice or charge to an account: the amount is shown only. (InvoicingFacade is the route, as in the E01 notes.)
- Two bookings made at the same moment could both pass the bay check (no row lock on the capacity count). Fine for a small yard; a lock or a database rule would close it.
- Bays are a count, not named bays; a truck is not assigned to a bay.
- A truck that stays past the planned departure is only flagged; the amount keeps counting only after check-out or by changing the departure.
- Registration is free text, not linked to Fleet.
- Division deactivation is still not blocked while stays are open.

## Next
Charge a stay through InvoicingFacade; named bays; a capacity lock; link registrations to Fleet.
