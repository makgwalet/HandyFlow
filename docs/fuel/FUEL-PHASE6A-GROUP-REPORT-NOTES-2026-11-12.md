# Fuel Phase 6a: group report across legal entities (patch 0795)

## Implemented
- A read-only report, Insights > Group report, with one row per legal entity plus a "Not assigned to a legal entity" row (only when something sits there) and a whole-group total.
- Per entity, for a chosen period (7, 30 or 90 days): deliveries, litres delivered, revenue, margin; each compared with the period of equal length immediately before it. As at now: stock litres, stock value (litres x the tank's weighted-average cost) and open delivery variances.
- Ownership rule: a delivery, tank or open variance belongs to the legal entity that owns the depot the delivery's tank is in. Tanks removed since a delivery still own that history. Anything with no depot, or a depot with no entity, goes to "Not assigned" so the total still adds up.
- Money gating: anyone with FUEL_READ sees deliveries, litres, stock litres and open variances. Revenue, margin and stock value are returned only to FUEL_MARGIN_READ (the server sends null otherwise and the screen hides the columns and says why).
- The screen states what the figures leave out: deliveries with no cost at sale (in revenue and litres, not margin), tanks with fuel but no known cost (stock value is a floor), reversed deliveries left out, and depots with no entity.

## Preserved
Nothing is written. No existing table, endpoint or permission changed. No migration.

## Files
- Java: GroupReportDtos, FuelGroupReportService, FuelGroupReportController (GET /api/v1/fuel/group-report?days=, FUEL_READ, fuel module guard).
- Web: GroupReportTab, groupReportOps, Insights menu entry, FuelPage case.

## Tests
- Java: FuelGroupReportServiceTest (7): grouping and totals, money hidden, previous period, cost and reversals, open variances, empty group, period clamp. Parse-checked only; not run (you run Maven).
- Web: GroupReportTab (5), groupReportOps (2). tsc clean; navigation tests pass.

## Gaps and risks
- Forecourt sales are not in the report. A forecourt shift is not tied to a depot, so it cannot be given to an entity honestly. Fixing that means choosing a depot per pump or per shift, which is a data decision.
- No split by operating division. Deliveries are all distribution; the forecourt gap above is the other half.
- Margin uses the cost recorded at sale; deliveries without one are counted, not guessed.
- Variances on deleted deliveries show under "Not assigned".
- No export or saved view yet. Period choices are fixed at 7, 30, 90 days.
- Run Maven including FuelEndpointSecurityTest and FuelRepositoryTenantScopingTest (no new @Query was added).

## Next
Phase 6b: automation (0796).
