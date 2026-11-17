# Fuel: split a forecourt's supply across several depots (2026-11-17)

Written, not compiled or run (Java). Web tests and tsc pass.

## Rule (my default; change if you mean something else)
Percentages are the share of a forecourt's supply **attributed** to each supplying depot. They apply to attribution only: the supply report shares litres and sales by percentage, and the reconciliation treats transfers from any depot in the split as "from supplying depots". They do not move stock or limit transfers.
- Must total 100; each line > 0 and <= 100; a depot once only; not itself; not a depot that this one supplies (single-supplier chain).
- Empty set removes the split; the depot goes back to `supplying_depot_id`. That field is untouched, so existing companies see no change.
- Rounding: last line takes the remainder, so litres and sales add back to each reading.

## Pieces
V699 `fuel_depot_supply_shares`; `FuelDepotSupplyShare`, repository, `FuelDepotSupplyShareService`; endpoints `GET/PUT /api/v1/fuel/depots/{id}/supply-shares` (read FUEL_READ, write FUEL_MANAGE or FUEL_STOCK_MANAGE); `FuelForecourtSupplyReportService` and the reconciliation use it; Depot dialog gets "Split supply across several depots".
## Not done
Split is not checked against depot limits for the supplier side; chain check ignores other depots' splits.
