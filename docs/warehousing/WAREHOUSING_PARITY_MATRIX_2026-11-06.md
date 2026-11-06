# Warehousing: feature parity matrix against the WMS roadmap (2026-11-06)

Method: read the module source (`platform/.../warehousing`: 12 controllers' endpoint lists, services, V258 migration, tests) and the frontend (`handyflow-web/src/pages/warehousing`, 14 files, 1,644 lines) and matched them to the 20 engines in the roadmap. **Nothing was run.** "Exists" means I found code for it, not that it works in production. Status words: **EXISTS** (basic), **PARTIAL**, **MISSING**.

## 1. What is actually there
- **Tables (one migration, V258, nothing added since):** `whse_profile`, `whse_clients`, `whse_locations`, `whse_items`, `whse_inventory`, `whse_inbound_shipments` (+lines), `whse_outbound_orders` (+lines), `whse_stock_movements`, `whse_billing_invoices`, `whse_portal_access_grants`.
- **Backend:** about 1,800 lines of services across 12 services; permissions `WAREHOUSING_READ / MANAGE / ADMIN` only (132 uses), plus `PORTAL_USER`. 64 `FeatureGuard` calls, so module entitlement is enforced.
- **Frontend:** every call I traced from the pages hits an existing endpoint (no dead buttons found). Evidence upload and list endpoints exist on the backend for inbound and outbound but no page calls them.
- **Tests:** 9 backend test classes (6 entity tests, 2 controller tests, 1 more). No service-level tests for inventory, allocation or billing; no concurrency test; no frontend tests.

## 2. Matrix
| # | Engine | State | Evidence in the code | Main gap |
|---|---|---|---|---|
| 1 | Facility configuration | PARTIAL | `whse_locations`: code, free-text `zone`, `capacity_units`, active flag. One warehouse per tenant (profile is unique per tenant). | No warehouse entity, no zone/aisle/rack/bin hierarchy, no location type, status or barcode, no docks, no blocked locations. A location is a string, as the roadmap warns. |
| 2 | Item master | PARTIAL | `whse_items`: SKU, description, one `uom`, storage rate. SKU is **per client**. | No barcode/GTIN, dimensions, weight, handling flags, tracking flags, UOM conversions, min/max, lifecycle. |
| 3 | Inventory ledger | PARTIAL | `whse_stock_movements` with before/after, reference and actor. Balance is `whse_inventory` per client/item/location with `qty_on_hand`, `qty_allocated`, `@Version` and a CHECK (`allocated <= on_hand`). | Only 3 movement types (RECEIPT, PICK, ADJUSTMENT). No transfer, put-away, return, damage, quarantine. **No status dimension at all.** Allocation writes no movement. No append-only trigger on the ledger (the audit trail module has one). No idempotency key. |
| 4 | Batch/lot/serial/expiry | MISSING | Nothing in the schema. | Whole engine. |
| 5 | Inbound | PARTIAL | ASN header and lines, per-line receive, cancel, evidence endpoints. | One received quantity per line: no accepted/rejected/damaged split, no inspection, no discrepancy workflow, no dock appointment, receipt goes straight to on-hand. |
| 6 | Put-away / replenishment | MISSING | Receiving names the location; no tasks or rules. | Directed put-away, replenishment, min/max. |
| 7 | Allocation | PARTIAL | `startPicking` allocates from "best" position per line; refuses on insufficient stock. | One location per line, no partial or backorder, no batch/expiry rule, no reservation expiry. Available = `on_hand - allocated` only. |
| 8 | Picking / packing | PARTIAL | `start-picking`, per-line `pick`, `pack`, packing-slip PDF. | No tasks, no scanning, no shortage/substitution approval, no package/labels/weights. |
| 9 | Dispatch | PARTIAL | `ship` plus outbound evidence. | No staging/loading/manifest/carrier/POD states (only PENDING, PICKING, then packed/shipped). |
| 10 | Returns | MISSING | No return entities or endpoints. | Whole engine. |
| 11 | Barcode / mobile | MISSING | No barcode fields, no scan endpoints, no worker UI. | Whole engine. |
| 12 | 3PL contract, rating, billing | PARTIAL | `WhseBillingService` generates invoices, posts to the GL via `AccountingFacade`, payments, statement PDF. | **Storage fee uses current `qty_on_hand` x monthly rate x days in period** (the code's own header says it is a point-in-time snapshot). No daily snapshot table, no tariff versions, no contracts, minimums, bands, or dispute/adjustment flow. A late receipt or an early pick changes the whole month's bill. |
| 13 | Cycle count / stocktake | PARTIAL | A manual `adjust` endpoint with a reason and an ADJUSTMENT movement. | No count plans, blind counts, variance approval. **One user with `WAREHOUSING_MANAGE` can change any balance with no second person.** |
| 14 | Quality / inspection | MISSING | | Whole engine. |
| 15 | Tasks / labour | MISSING | | Whole engine. |
| 16 | Dock / yard | MISSING | | Whole engine. |
| 17 | Slotting | MISSING | | Later. |
| 18 | Analytics | PARTIAL | `WhseDashboard.tsx` (per the roadmap's earlier review, client-oriented). | No aggregate operations dashboard, no KPI definitions. |
| 19 | Integration / events | MISSING | Notification scheduler only. | No outbox/webhooks/idempotency. |
| 20 | Evidence / workflow / audit | PARTIAL | Evidence upload on inbound and outbound (backend only); movements record actor. | Not wired to the approval engine or audit trail; no UI for evidence. |

## 3. Cross-cutting findings
1. **Permissions are coarse:** three authorities for everything. Under the E6 split the Administrator would hold all of them. Adjustments, billing preparation and billing approval are the same permission; there is no maker-checker on stock adjustments or invoices. (Matches the roadmap's section 9 gap.)
2. **Client isolation:** staff access is tenant-scoped only; the portal checks a per-client grant (`requireAccess`), which is the right pattern. No test proves a portal user cannot read another client's stock.
3. **Concurrency:** optimistic `@Version` plus a CHECK constraint protect against overselling at the database, but a conflict surfaces as an error with no retry, and there is no test with two simultaneous allocations.
4. **Location is per tenant, not per client**, and items are per client, so the same physical SKU for two clients is two items (correct for 3PL ownership, wrong for an internal warehouse that has one owner).
5. **Billing correctness is the biggest commercial risk:** it is the only part that posts to the general ledger, and its stock input is the live balance.

## 4. Suggested order (my recommendation, for approval)
1. **Slice 1, inventory integrity (no new screens):** add a stock-status dimension and the missing movement types; make the ledger append-only (DB trigger) with idempotency keys; add the movement for allocation; add tests (reconstruct balance from movements; two threads allocating the last units; cross-client portal access).
2. **Slice 2, daily inventory snapshot and storage accrual:** a snapshot table filled by a nightly job, tariff versions, and billing computed from snapshots. Keep the old calculation behind a flag until results are compared on real data.
3. **Slice 3, adjustments with approval:** separate `WAREHOUSING_ADJUST` / `_ADJUST_APPROVE` and `_BILLING_APPROVE`, using the existing `SeparationOfDutiesChecker` and sole-user exception.
4. Then facility hierarchy and barcodes, receiving with inspection, returns, batch/serial.

## 5. Open product decisions
- Is Warehousing an internal-warehouse product, a 3PL product, or both from the start? The item and location model differs.
- Billable-day rule for storage: does a pallet count on the day it arrives, partial days, weekends, public holidays? (The roadmap raises this; it must be decided before slice 2.)
- Which regulated industries (food, pharma) are in scope for batch/expiry in the first release?
- Is Warehousing a priced module and where does it sit in the module catalogue? It is entitlement-guarded today.

## 6. Not verified
Everything above is from reading code and one migration. I did not run the module, check the dashboard component, read the portal controllers beyond `WhsePortalDataService`, or compare against the roadmap's earlier review (which I do not have).
