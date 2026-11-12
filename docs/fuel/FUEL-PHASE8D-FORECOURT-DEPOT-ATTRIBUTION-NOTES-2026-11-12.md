# Fuel Phase 8d: forecourt-to-depot attribution (patch 0809)

## Finding that changed the plan
There is no forecourt-site table. A forecourt is, in the data, the depot that holds the pump tanks (pump -> tank -> depot). The group report said forecourt sales are "not tied to a depot", but each shift reading already carries its tank, so sales can be attributed. I read "link each forecourt site to its supplying depot" as: let a depot record the depot that supplies it. No new master record is created.

## 1. Implemented
- `fuel_depots.supplying_depot_id` (nullable self-reference). Set on the Depot dialog ("Supplied by"), shown in the depots list.
- Server checks: supplier must be an active depot of the company; a depot cannot supply itself; the chain of suppliers cannot loop back (any length).
- New read-only report: forecourt sales (closed, not-reversed shifts) grouped by supplying depot, with each forecourt's litres, shifts and sales value (price fixed when the shift opened). Shown under the group report. Forecourts with no supplier, and tanks in no depot, go in "No supplier set" so nothing drops out.
- Group report wording corrected.

## 2. Preserved
Existing depots are unchanged (supplier null). The old 3-argument `DepotRequest` and 7-argument `DepotResponse` constructors still exist. No migration edits. No prices, tax or regulatory values.

## 3. Files
`V685`, `FuelDepot.setSupplier`, `DepotRequest`/`DepotResponse`, `FuelStockService` (`checkSupplier`), `ForecourtSupplyDtos`, `FuelForecourtSupplyReportService`, `FuelForecourtSupplyController`. Web: `StockTab` (depot dialog and list), `ForecourtSupplySection`, `forecourtSupplyOps`, `GroupReportTab`.

## 4. DB
V685: one nullable column, a not-self check and a partial index on `fuel_depots`.

## 5. API
`GET /api/v1/fuel/forecourt/supply-report?days=` (`FUEL_READ`, `requireModule("fuel")`; sits under `/forecourt`, so the Retail division rule applies; money only with `FUEL_MARGIN_READ`). `POST/PUT /depots` accept optional `supplyingDepotId`.

## 6. Security
Tenant-scoped queries only. Depot limits from 8c apply to the depots list; the new report is not depot-filtered (see gaps).

## 7. Tests
`FuelDepotSupplierTest` (8), `FuelForecourtSupplyReportServiceTest` (7), web `ForecourtSupplySection.test` (7), `StockTab.test` (+1). Web tests and tsc pass. Java is parse-checked only; I could not run Maven here.

## 8. Gaps
- A PUT on a depot with no `supplyingDepotId` clears the supplier (full replace). The screen always sends it; other API clients must too.
- Forecourt sales are not attributed to legal entities in the main group-report table; they appear in the new section only.
- The supply report is not filtered by a user's depot limits (8c).
- Supply is one level of link per depot; no volume split between several suppliers.
- Forecourt stock receipts are not matched to the supplying depot's dispatches.

## 9. Risks
The report reads all closed shifts since the start of the period (capped at 366 days). Fine at current volumes; page it if shifts reach many thousands.

## 10. Next
8e: inbound pump/POS import API (D03, V686).
