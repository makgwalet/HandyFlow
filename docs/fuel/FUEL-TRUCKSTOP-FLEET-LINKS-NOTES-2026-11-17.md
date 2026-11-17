# Fuel truck stop: linking stays and tabs to Fleet vehicles

Date: 2026-11-17. Written, not compiled or run (Java). Web tests (596 in fuel) and `tsc` pass. V708 is new.

## What it does
An overnight stay or a driver tab may point at one of the company's own Fleet vehicles ("Own truck"). Optional.
- V708 adds `fleet_vehicle_id` to stays and tabs. No foreign key (Fleet is another module).
- Uses the existing `FleetVehicleLookup` (Fuel defines it, Fleet implements it), as tankers do.
- **Fleet owns the registration**: a linked record takes Fleet's registration.
- A vehicle chosen from the list must be one of this company's, otherwise the booking is refused.
- When none is chosen and the typed registration matches **exactly one** Fleet vehicle (ignoring case and spaces), it is linked automatically. No match, or two matches, means no link.
- Fleet absent or failing never stops a booking; it is just not linked.
- Unlike a tanker, one vehicle can appear on many stays and tabs.
- `GET /api/v1/fuel/truck-stop/fleet-vehicles` lists the choices. Web: an "Own truck (Fleet)" select (shown only when Fleet has vehicles) and an "Own truck" chip.

## Not done
- No reports use the link yet (for example "own trucks' nights"). The link is recorded and shown.
- Existing stays and tabs are not back-filled.
- A stay's link cannot be changed after booking (cancel and rebook).
