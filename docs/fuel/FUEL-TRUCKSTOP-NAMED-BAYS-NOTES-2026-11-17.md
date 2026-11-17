# Fuel truck stop: named bays (2026-11-17)

Written, not compiled or run (Java). Web: StaysTab (14) and stayOps (14) tests, tsc clean.

## What changed
- V702: `fuel_truck_stop_bays` (name unique per company, ignoring case; active flag) and `fuel_truck_stop_stays.bay_id` (nullable). New table and column only.
- A truck can be put in a named bay while its stay is booked or in the yard, and taken out again. The bay must be active and free on every night of the stay (a truck still in the yard past its departure holds the bay up to today). The refusal names the night and the truck in the way.
- A bay is switched off, never deleted, so past stays keep their bay name. A bay with a truck booked or in it cannot be switched off.
- Assigning takes the same row lock as booking, so one bay is not given to two trucks at once.
- Endpoints under `/api/v1/fuel/truck-stop`: `GET|POST /bays`, `PUT /bays/{id}/active`, `PUT /stays/{id}/bay`. Same permissions as the other stay actions.
- Web: a Bay column on the stays list (choose a bay in a dropdown; read-only without manage permission) and a "Named bays" box.

## Not changed
Capacity is still the **bay count** in the parking settings; named bays do not change it and the two are not forced to agree. Bays are optional: with none named, everything works as before.

## Stale items in the old backlog
The capacity view, invoicing of stays to an account (V693), the capacity lock and the block on deactivating a division with open stays were already built (7d and the 2026-11-15 charging round).

## Still unbuilt at the truck stop
Linking registrations to Fleet (only useful for the company's own trucks), POS items, driver self-service, fleet statements.
