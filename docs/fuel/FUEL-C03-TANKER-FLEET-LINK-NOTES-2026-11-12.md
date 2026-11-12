# Fuel C03: tankers reference Fleet vehicles (2026-11-12)

## 1. Implemented
- A Fuel tanker can be linked to a vehicle in Fleet (owner decision: tankers reference Fleet vehicles). Fleet stays the only vehicle master; Fuel keeps only the id.
- While linked, the registration follows Fleet (set on link, shown live in lists). A typed registration is ignored when a vehicle is chosen.
- One Fleet vehicle backs at most one live tanker (service check, plus a partial unique index).
- Unlinked tankers whose plate matches a Fleet vehicle show a "Also in Fleet as ..." hint, so the duplicate is visible and can be linked.
- New list endpoint of Fleet vehicles for the picker, showing which are already tankers.

## 2. Preserved
- Existing tankers, allocation, compartments and old request/response constructors are unchanged. Nothing is auto-linked or migrated; linking is a person's choice.
- `FuelTransportService.updateTanker(tenant,id,reg,name)` keeps its signature.

## 3. Files
Backend (fuel): `FleetVehicleLookup` (interface), `FuelTanker`, `FuelTankerRepository`, `FuelTransportService`, `FuelTransportController`, `TankerRequest`, `TankerResponse`, `FleetVehicleOption`. Backend (fleet): `FleetVehicleLookupImpl`. Web: `PlanningTab`, `transportOps`.

## 4. Database
`V667`: `fuel_tankers.fleet_vehicle_id UUID` (nullable) and unique index `uq_fuel_tanker_fleet_vehicle (tenant_id, fleet_vehicle_id)` where not deleted and not null. Deliberately no foreign key (cross-module). Checked on a scratch PostgreSQL 16: applies; a second tanker on the same vehicle is rejected.

## 5. API
- `GET /api/v1/fuel/tankers/fleet-vehicles` (FUEL_MANAGE or FUEL_TRANSPORT_MANAGE).
- Tanker create/update accept `fleetVehicleId` and (update) `unlinkFleetVehicle`. Tanker responses add `fleetVehicleId`, `fleetVehicleLabel`, `fleetSuggestionId`, `fleetSuggestionLabel`.

## 6. Module dependency (needs your attention)
Fleet already declares `fuel` as an allowed dependency (it consumes `FuelDispatchedToVehicleEvent`). A Fuel -> Fleet dependency would make a cycle, so Fuel does NOT depend on Fleet. Fuel defines `FleetVehicleLookup` in its own root package and Fleet implements it (dependency inversion). `fuel/package-info` is unchanged. Not run here: `ArchitectureVerificationTest`; please run it.

## 7. Security
Tenant comes from TenantContext; the Fleet lookup and the uniqueness query are tenant-scoped; the endpoint has `@PreAuthorize` and `requireModule("fuel")`. If Fleet is not present, linking is refused with a clear message; listing tankers still works.

## 8. Tests
`FuelTransportServiceTest` +7 (link takes Fleet plate, unknown vehicle, one vehicle one tanker, Fleet missing, suggestion, Fleet failure does not break the list, link+unlink together). Web: `transportOps` +2, `PlanningTab` +1 and one payload expectation updated. Java is parse-checked only here; vitest run; tsc re-run pending at time of writing.

## 9. Gaps
- Fleet-side facts (service due, licence disc, odometer) are not yet shown on the tanker or used to block allocation. 
- No edit-link UI for an existing tanker yet (API supports it); the dialog covers creation.
- Sold or deleted Fleet vehicles show "(vehicle removed from Fleet)" and keep the last plate.

## 10. Risks
Fleet list is read at most once per request, capped at 2,000 vehicles per tenant.

## 11. Next
Edit-link action on the tankers table; surface Fleet compliance on allocation.
