# Fuel F02: actual versus estimated cost (patch 0791)

## Implemented
- Per route, an estimate (2 x farthest stop km x vehicle rate, plus default trip hours x driver rate, both from FuelSettings) beside actual costs recorded by staff (VEHICLE, DRIVER, TOLLS, OTHER).
- Variance is calculated per category only where both an estimate and an actual exist (vehicle, driver). Tolls and other are shown but not compared.
- Flags: RATES_NOT_SET, NO_DISTANCE, NO_ACTUALS, PARTIAL_DELIVERY. A route with no actual vehicle or driver cost is "not reconciled".
- Rollups by tanker and by depot (a route's figures are shared across depots by litres delivered from each depot's tanks).
- Actual entries are append-only; correcting means voiding with a reason and adding a new one.

## Preserved
Cost-to-serve per customer is unchanged; the route panel is added below it. No rates or prices are hard-coded.

## Files
- DB: V674__fuel_route_cost_entries.sql (new table, checks on category, amount >= 0, void fields).
- Java: FuelRouteCostEntry, FuelRouteCostEntryRepository, FuelRouteCostService, FuelRouteCostController, RouteCostDtos; FuelRouteRepository.findBetween; FuelDivisionRules adds "cost-actuals".
- Web: routeCostOps.ts, RouteCostsPanel.tsx, CostToServeTab.tsx.

## API (/api/v1/fuel/cost-actuals)
GET ?from&to and GET /routes/{id}/entries (FUEL_MARGIN_READ); POST /routes/{id}/entries and POST /entries/{id}/void (FUEL_MANAGE or FUEL_TRANSPORT_MANAGE). All behind featureGuard fuel; distribution division only.

## Tests
- Java: FuelRouteCostServiceTest (9), parse-checked only; not run.
- Web: routeCostOps (5), RouteCostsPanel (6), CostToServeTab updated. Fuel and navigation suite 330 pass; tsc clean.
- V674 constraints verified on a scratch PostgreSQL 16.

## Gaps and risks
- Fuel purchase cost is not part of this (only transport costs).
- Estimate uses the farthest stop, not a routed distance.
- Depot rollup by litres is an allocation, not a measured cost.
- Run Maven including ArchitectureVerificationTest and FuelRepositoryTenantScopingTest.

## Next
Photo and signature capture (0792).
