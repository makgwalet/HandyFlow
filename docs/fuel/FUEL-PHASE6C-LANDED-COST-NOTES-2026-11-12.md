# Fuel Phase 6c: landed cost per litre (patch 0797)

## Implemented
- **Insights > Landed cost** (FUEL_MARGIN_READ). For each route in the period: litres delivered, revenue, product cost (litres x the cost at sale recorded on each delivery), transport cost (every active cost entry on the route: vehicle, driver, tolls, other) and landed cost = product + transport. Per-litre product, transport and landed cost, revenue per litre, margin per litre and margin.
- **Completeness.** A route is counted only if every delivered stop has a cost at sale, litres were delivered, a vehicle or driver cost is recorded, and every live stop is delivered (the cost covers the whole trip). Other routes are listed with flags (no cost at sale, no vehicle or driver cost, partial delivery, no litres), show a dash instead of a figure, and are excluded from totals; the page says how many were left out. A missing cost is never read as zero.
- **Rollups** by tanker (whole routes) and by depot (a route's figures shared by litres delivered from each depot's tanks).
- Voided cost entries are ignored. Period is validated like F02 (start not after end, at most a year).

## Preserved
F02 route costs, cost to serve, margin and deliveries are unchanged; this page only reads.

## Files
- Java: LandedCostDtos, FuelLandedCostService, FuelLandedCostController (GET /api/v1/fuel/landed-cost?from&to), FuelDivisionRules ("landed-cost" with distribution). No migration, no new permission.
- Web: LandedCostTab, landedCostOps, menu entry "Landed cost" under Insights, FuelPage case.

## Security
FUEL_MARGIN_READ on the endpoint, requireModule("fuel"), tenant from context, division rule applied backend-side.

## Tests
- Java: FuelLandedCostServiceTest (7): full calculation, no transport, missing cost at sale, partial delivery, voided entries, depot shares, period rules. Parse-checked only; not run (you run Maven).
- Web: LandedCostTab (4), landedCostOps (3). tsc clean.

## Gaps and risks
- Inbound freight is included from patch 0801 onward, when it is entered at receipt. Receipts made before that, or without freight, carry the supplier price only.
- Cost at sale is the tank's weighted-average cost when sold, so it moves with purchase timing.
- Transport is not split by customer; use Cost to serve for customers.
- No forecourt, no export.

## Next
Inbound freight on purchase orders; optional allocation of route cost to customers by litres.
