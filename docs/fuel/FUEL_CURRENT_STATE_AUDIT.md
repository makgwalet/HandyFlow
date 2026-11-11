# Fuel: current-state audit (Phase 0)

Date: 2026-11-11. Branch `fuel-stage0`. Method: read of the repository (controllers, services, entities, repositories, migrations, tests, web pages, navigation). **No Maven run was possible in the audit environment**, so build and test status of the backend is *not* established here (see FUEL_TEST_COVERAGE_AUDIT.md). Statuses below mean "evidence found in code", not "verified working".

Legend: **W** implemented end to end (UI, API, service, entity, migration, tests found) · **I** implemented but incomplete · **P** partial · **N** not implemented · **C** needs business confirmation · **X** needs external integration.

## Inventory

| Area | Existing implementation | Relevant files | Status | Missing | Risk |
|---|---|---|---|---|---|
| Module boundary | `@ApplicationModule(allowedDependencies = shared, billing, crm, identity, notifications(+::*), approvals(+::*), invoicing(+::*), hr(+::*))` | `fuel/package-info.java` | W | No `fleet` entry, yet `FleetFuelDispatchEventHandler` (in fuel) imports fleet types; fleet allows `fuel`. See GAP-A01 | Possible Modulith violation, unverified |
| Tanks and depots | Depots, tanks, placement, deactivate, dip readings, reconciliation PDF, utilisation forecast, reorder suggestion | `FuelStockController`, `FuelTankController`, `FuelStockService`, `FuelService`, `FuelDepot`, `FuelTank`, V15/V282/V649/V650 | W | Depot `legal_entity_id` exists on the entity but is never set or exposed (`DepotRequest` has name/code/address only); no product master; single `fuel_type` string | Medium |
| Stock ledger | Append-only movements (OPENING, RECEIPT, DISPATCH, DELIVERY, ADJUSTMENT, TRANSFER_OUT/IN, REVERSAL); pessimistic tank lock; unique (tenant, source_type, source_id, movement_type, tank) | `FuelStockLedger`, `FuelStockMovement`, V649 | W | No legal entity/product/unit/approval columns on a movement; reversal relationship is by REVERSAL type only | Medium |
| Stock adjustment | `POST /tanks/{id}/adjust`, FUEL_STOCK_ADJUST, reason required, cannot eat reserved fuel | `FuelStockService.adjust` | I | No approval / maker-checker, no tolerance rule | **High** (control gap) |
| Reservations, transfers | Reserve/release, availability, tank-to-tank transfer | `FuelStockService`, `FuelReservation`, `FuelTransfer` | W | No depot-to-depot with in-transit | Low |
| Suppliers and procurement | Supplier CRUD, statement PDF, POs (draft, place, cancel, close-short), receipts against PO with per-tenant tolerance, match report, supplier invoices with matching and approval | `FuelTankController` (suppliers), `FuelPurchasingService`, `FuelSupplierInvoiceService`, V653/V656/V657 | I | No supplier onboarding or approval, banking, documents, scorecards, requisitions, PO approval, supplier price lists. Supplier invoices do **not** reach AP | Medium |
| Customers | Accounts over CRM customer, credit limit/terms, credit hold, multiple delivery sites with distance | `FuelCustomerService`, `FuelCustomerController`, V650/V657 | I | No group/hierarchy, billing accounts, contacts beyond site contact, documents, statements | Medium |
| Customer portal | Invitation, accept, login, accounts, sites, orders, deliveries, order requests (quote, capped at 10 open), staff alert | `FuelPortal*`, `pages/fuel-portal/*`, V654 | I | No statements, invoices, documents, POD view | Low |
| Pricing | Effective-dated price lists per product/customer/site, resolve endpoint | `FuelPricingService`, `FuelPrice`, V653 | I | No approval, versioning of terms, volume breaks, contracts | Medium |
| Quotes and orders | Sales order starts as QUOTE, edit, confirm (credit check, reservation), cancel, partial delivery, customer reference | `FuelOrderService`, `FuelSalesOrder`, V651 | I | No enquiry/RFQ, revision history, quote acceptance record, contracts, recurring orders | Medium |
| Distribution | Tankers with compartments, delivery allocation, planning board, driver "my loads", dispatch and complete, delivery receipt PDF, loading record with seals/meter | `FuelTransportService`, `FuelTransportController`, `FuelDelivery`, `FuelTanker`, V652 | I | Tankers are a Fuel table (possible duplicate of Fleet vehicle master, unverified); no routes, multi-stop, delivery windows, vehicle/driver eligibility, failed delivery, returns | Medium |
| Proof of delivery | Receiver name, signature URL, signed-on-behalf flag, delivered litres, meter end; `complete()` is once-only via a status guard | `FuelDelivery.complete`, V18/V51 | I | No photos, no evidence service use, no GPS, no verification step, no correction process | **High** |
| Delivery variance | Loaded litres and seals recorded; comment states variance is "reviewed, never auto-written-off" | `FuelDelivery` | P | No tolerance, no exception record, no review/approval workflow found | **High** |
| Offline driver queue | Browser-local queue with `clientRequestId` on completion, flush on reconnect | `DriverTab.tsx`, `transportOps.ts`, `CompleteDeliveryRequest` | I | Only completion is queued; no attachment sync, conflict handling or sync status per item beyond count | Medium |
| Invoicing | Delivered-not-invoiced queue, draft invoice via `InvoicingFacade`, claim columns to prevent double invoicing | `FuelInvoicingService`, `FuelInvoicingTx`, `FuelDelivery` | I | No credit notes, consolidated invoicing rules, payment status, outbox/retry UI | Medium |
| Retail forecourt | Pumps, shifts (open/close/reverse), per-pump readings, weekly summary, attendant role FUEL_FORECOURT | `FuelForecourtService`, `FuelShift`, `FuelPump`, V655, `ForecourtTab.tsx` | I | Shift close records cash/card/account/other. No nozzles, tank-to-pump reconciliation, fleet-card tender, refunds/voids/discounts, POS, supervisor approval; variance tolerance is a constant (R5) | Medium |
| Truck stop | Nothing found (no tables, code or UI) | grep for nozzle/truck/parking: no Fuel hits | N | Whole division | n/a |
| Compliance | Items with expiry, renewals, reminders by email (D30/D7/expired) | `FuelComplianceService`, `FuelComplianceNotificationScheduler`, V653/V656 | I | Not linked to legal entity, licensed activity, site, vehicle or person; no evidence upload | **High** (spec) |
| Cost and margin | Per-delivery cost snapshot, margin report, cost-to-serve (estimates, flagged) | `FuelDashboardService`, `FuelCostToServeService`, V271/V657 | I | No actual vs estimated split beyond flags, no by vehicle/route/depot | Medium |
| Settings | Per-tenant rates, trip hours, PO tolerance | `FuelSettings*`, V657 | W | Other tolerances still constants | Low |
| Operating profile / divisions | None. Entitlement is `featureGuard.requireModule("fuel")` only | all controllers | N | Four-division configuration, wizard, backend division guards | Foundational |
| Legal entities, licences | None beyond the unused depot column | `FuelDepot` | N | Entity, licensed activity, licence, site relationships | Foundational |
| Events / outbox | One event `FuelDispatchedToVehicleEvent` with `@ApplicationModuleListener`; schedulers for delivery reminders and compliance | `FuelService:471`, `FleetFuelDispatchEventHandler` | P | No outbox for invoicing, no event for delivery/POD/shift | Medium |
| Frontend | 20 tabs behind `/fuel/:section?`, portal routes, sidebar by permission | `FuelPage.tsx`, `moduleSections.ts` | I | Not division-aware; hidden nav is the only gating | Medium |

## Maturity statements the evidence supports
- Stock ledger, orders-to-delivery and invoice hand-off are the strongest parts: a controlled ledger with a locked write path and a uniqueness rule against duplicate postings.
- Delivery assurance (POD evidence, variance review) and compliance traceability are the weakest relative to the brief.
- No percentage of completion is given.
