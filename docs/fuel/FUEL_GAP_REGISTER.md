# Fuel: gap register (Phase 0)

Priority: P0 security/integrity/financial/tenant/critical · P1 essential · P2 advanced · P3 enhancement. "Evidence" is where in the repository the current behaviour was observed.

| ID | Division | Capability | Existing state (evidence) | Exact gap | Business impact | Security impact | Pri | Depends on | Recommended solution |
|---|---|---|---|---|---|---|---|---|---|
| A01 | Platform | Module boundaries | `FleetFuelDispatchEventHandler` in `fuel/application/internal` imports `fleet` model/repository; `fuel/package-info` does not allow `fleet`, `fleet/package-info` allows `fuel` | Possible Fuel↔Fleet cycle / undeclared dependency. `ArchitectureVerificationTest` tolerates only the billing↔identity cycle, so it may already fail. **Not verified: needs a Maven run** | Build gate may be red | None | P0 (verify first) | Maven run | Run `ArchitectureVerificationTest`; if red, move the listener to `fleet` consuming `FuelDispatchedToVehicleEvent` |
| A02 | All | Division enforcement | Only `featureGuard.requireModule("fuel")` on controllers | No tenant division configuration; nothing stops a tenant using any capability once Fuel is entitled | Cannot sell/run divisions separately | A user cannot be blocked from a non-activated division at API level | P0 (foundational) | Phase 1 | `fuel_operating_profile` + division guard in every controller (backend) |
| A03 | All | Legal entity and site scope | `FuelDepot.legalEntityId` is never set (`DepotRequest`, grep) | No legal entity model; stock, orders, compliance not entity-scoped | Wrong consolidation, licence validity unclear | No entity-level access control | P0 (foundational) | A02 | `fuel_legal_entities`, link depots/sites/ledger/orders |
| B01 | Wholesale | Stock adjustment control | `FuelStockService.adjust`: FUEL_STOCK_ADJUST only, reason required | No approval, no tolerance, no second person | Unreviewed stock write-offs | Single person can alter stock | P1 | IAM `requires_second_person` or Approvals module | Route adjustments over a configurable threshold through `ApprovalFacade` (already used for dispatches) |
| B02 | All | Idempotency | Only `CompleteDeliveryRequest.clientRequestId`; invoicing has claim columns | Receipts, dispatches, transfers, adjustments, order creation, shift open/close have no idempotency key | Double-click or retry duplicates movements | none | P1 | none | Idempotency-Key header + store keyed per tenant |
| B03 | All | Dip reading tenant predicate | `DipReadingRepository` queries `WHERE tankId = :tankId` only; both callers first call `findActiveTank(tenantId, tankId)` | Defence in depth: repository itself is not tenant-scoped | None today | Latent cross-tenant read if a new caller skips the tank check | P1 | none | Add tenant parameter to the queries |
| B04 | Wholesale | Supplier onboarding/approval | `FuelSupplier` CRUD + statement | No approval status, banking, documents, scorecard | Procurement risk | Banking details not present, so none exposed | P1 | Documents module | Supplier status workflow, documents, performance report |
| B05 | Wholesale | PO approval, requisitions | PO draft→ordered→received/closed short/cancelled | No approval before placing, no requisition | Spend control | none | P1 | Approvals | Approval threshold on place |
| B06 | Wholesale | Enquiry/RFQ, contracts, quote revisions | **B06a built (V670)**: enquiries, quotation version history, acceptance record. **Not built (B06b)**: contracts, volume pricing | Contracts (built as group-and-report, 0802), volume pricing, RFQ to suppliers | Commercial lifecycle | none | P1 | A02 | New commercial sub-domain |
| B07 | Wholesale | Pricing approval/versioning | `FuelPrice` effective-dated, end endpoint | No approval, margin threshold, history of changes | Price errors | none | P1 | Approvals | Margin-guard and approval |
| B08 | Wholesale | Supplier invoice → AP | `FuelSupplierInvoice` approve/reject, no posting | No hand-off to Accounts Payable. Fuel may not depend on `ap` today | Double capture | none | P1 | **Decision** | Facade/event to AP if allowed |
| C01 | Distribution | POD evidence | `FuelDelivery.complete`: receiverName, signature URL, signedOnBehalf | No photos, GPS, evidence verification, correction trail | Disputes | `complete()` is once-only (status guard), so the API cannot overwrite it; but there is no verification or auditable correction process | P0 | Evidence service | Evidence records via shared service, locked after verification |
| C02 | Distribution | Delivery variance | Loading record stores litres/seals; comment says variance is reviewed | No tolerance, exception, review, decision record | Unexplained losses | none | P0 (financial) | A02 | Variance entity + tolerance setting + review flow |
| C03 | Distribution | Fleet integration | `fuel_tankers` is a Fuel table | Possible duplicate vehicle master (unverified) | Double maintenance | none | P1 | Fleet owner decision | Reference Fleet vehicle id from tanker |
| C04 | Distribution | Routes, multi-stop, windows, failed delivery, returns, eligibility | none found | built: windows, failed attempts, litres returned recorded, routes with ordered multi-stop (V673); not built: route optimisation, eligibility checks | Planning capability | none | P2 | C03 | New route domain |
| C05 | Distribution | Offline | Queue of completion only | Attachments, conflict handling, per-item status | Field reliability | Server-side validation exists in complete | P1 | C01 | Generic offline queue in web |
| D01 | Retail | Nozzles, tank-to-pump reconciliation | pumps + readings | no nozzles | Pump accuracy | none | P1 | A02 | Nozzle entity |
| D02 | Retail | Refunds, voids, discounts, fleet-card tender, supervisor approval | `FuelShift.close` records cash, card, account and other, closed_by, variance vs a R5 constant (V655) | No separate fleet-card tender, refunds, voids, discounts or supervisor approval; tolerance is a code constant; no check that the closer differs from the opener (not found) | Cash control | Self-close possible (no rule found) | P1 | none | Configurable tolerance, supervisor approval over tolerance, tender types |
| D03 | Retail | POS/pump integration | none | Needs real integration | n/a | n/a | P2 | External | Define integration boundary only |
| E01 | Truck stop | Entire division | **v1 built (V669)**: timed services reserved through Bookings. Multi-night parking built in 0803 (V682). Not built: fleet accounts, invoicing/payment, POS items | everything else | New revenue line | n/a | P2 | A02, Bookings/POS/Fleet facades | Phase 5 |
| F01 | Compliance | Entity/licence/site linkage | `FuelComplianceItem` generic with expiry | no legal entity, licensed activity, site, vehicle or person link; no evidence | Regulatory traceability | none | P0 (spec) | A03 | Licence model on legal entity |
| F02 | Cost | Actual vs estimate | cost-to-serve flags estimates | no per vehicle/route/depot, no reconciled costs | Profit accuracy | none | P2 | C04 | Extend | **Done (route level, 0791, V674)**: estimate vs user-entered actuals per route, rollups by tanker and depot. Rates come from tenant settings. Tolls/other shown, not compared. Fuel purchase cost excluded. |
| G01 | Platform | Permissions granularity | 10 FUEL_* permissions; 72 uses of FUEL_MANAGE; inline `FUEL_CREDIT_OVERRIDE` check | Coarse write permission; no division/site scope | Least privilege | Over-broad write access | P1 | A02 | Granular permissions per brief, mapped from FUEL_MANAGE for existing roles |
| G02 | Platform | Outbox | Direct facade call for invoicing; Modulith listener for one event | No outbox/retry UI for failed invoice request | Silent failure risk | none | P1 | none | Outbox table + retry |
| H01 | Tests | Controller/security/cross-tenant tests | 22 Fuel unit-test classes (181 @Test), none for controllers/tenant isolation | no security tests | Regression risk | Unproven isolation | P0 | none | Add MockMvc + cross-tenant tests |

Each row is a gap statement; the backend/frontend/database/permission/test lists per gap are in FUEL_TARGET_ARCHITECTURE.md (change map) and are to be detailed per phase.

## Status after Phase 2 (code written; Java not compiled or run by the author)
- **B01** done in code: adjustments at or above a per-tenant litre threshold are held until a different person with FUEL_STOCK_ADJUST_APPROVE approves. No threshold means no change from before.
- **B03** done in code for dip readings (tenant parameter added). `FuelReceiptRepository.findByTank` and `FuelDispatchRepository.findByTank` have the same shape but no callers; left alone.
- **B04** done in code (supplier status, off by default). Banking details built in 0793 (V676): see below. Supplier documents and a scorecard are **not** built.
- **B05** done in code through the Approvals engine (module fuel, type PURCHASE_ORDER). Requisitions built in 0793 (V676): see below.
- **B06 (quotes, contracts), B07 (price approval/margin guard)**: not started. **B08**: built (V668), see FUEL-B08-SUPPLIER-INVOICE-TO-AP-NOTES-2026-11-12.

## Status after Phase 3 (code written; Java not compiled or run by the author)
- **C02** done in code: loaded-versus-delivered variance with a per-company tolerance that has **no default** (nothing is flagged until set), a review record (open, investigating, closed with EXPLAINED or WRITTEN_OFF and a note). A decision is a record only and moves no stock.
- **C01** done in code: append-only evidence records (photo, signature, document, GPS) with replace-before-verify and verify-by-someone-else-then-lock. Files are referenced; real photo and signature upload was added in 0792 (V675). The existing completion fields are unchanged.
- **C03** built (V667): a tanker can reference a Fleet vehicle; see FUEL-C03-TANKER-FLEET-LINK-NOTES-2026-11-12. Existing tankers stay unlinked until someone links them. **C04** partly built (V672): delivery windows, failed delivery attempts with reason and litres returned, rescheduling; see FUEL-C04-WINDOWS-FAILED-DELIVERY-NOTES-2026-11-12. Routes with ordered stops built in part 2 (V673); route optimisation, returns booking back into a tank, and customer eligibility checks are NOT built. **C05** built (V671): offline queue for start trip, delivery and GPS location, with per-item status, retry and discard; see FUEL-C05-OFFLINE-QUEUE-NOTES-2026-11-12. Photo and signature capture built in 0792 (V675): driver camera and signature pad, kept in the offline queue, stored through the shared file service; see FUEL-C05-PHOTO-SIGNATURE-NOTES-2026-11-12. Not built: replacing a file record with a new file in one step, virus scanning, image thumbnails.

## Status after Phase 4 (code written; Java not compiled or run by the author)
- **D02** done in code: per-company cash tolerance (default R5.00, as before), fleet-card tender, refunds/voids/discounts with supervisor approval (never the recorder), supervisor review of out-of-tolerance shifts, optional "closer differs from opener" rule. Review and approval are records only and move no stock or money.
- **D01 (nozzles)** not started: changes the meter model. **D03 (POS/pump integration, P2)** not started: boundary only.

## Status after F01 (code written; Java not compiled or run by the author)
- **F01** done in code: licences link to a legal entity, a licensed activity and an issuing authority; evidence references (append-only); per-entity coverage (information only, no required-licence rules). Site link, opt-in blocking by entity and evidence files with verification were added later (0794, see below).
- Owner decisions recorded 2026-11-11: Truck Stop reservations on Bookings (E01); tankers to reference Fleet vehicles (C03); Fuel may depend on Accounts Payable via a facade (B08). Each adds a module dependency, so A01 should be settled first.

## Maven run of 2026-11-12 (full suite, user's machine, PostgreSQL 16)
- 8132 tests: 3 failures, 12 errors. **No failure in any Phase 1-4 or F01 test.**
- Flyway applied V658-V662 to a real database and `HandyFlowApplicationTests` (full Spring context) and `PermissionConsistencyTest` passed. This is the first real-database check of the Fuel migrations and bean wiring.
- Fuel failures, now fixed in tests only: `FuelCostToServeServiceTest` (5) and `FuelDashboardServiceTest` (2) built mocks inside `when(...).thenReturn(...)`; hoisted to locals. `ControllerAccessCoverageTest`: `FuelPortalAuthController` added to the reviewed public list (customer login, as every other portal) and `FuelPortalAuthController` and `FuelPortalDataController` added to the module-guard debt list, matching the other portals. **That adds two entries to a list meant to shrink.** Adding `requireModule("fuel")` to the portal data controller would remove one but needs a check that portal tokens carry the tenant.
- Not Fuel, not touched: `EventsSchemaAndSecurityGuardTest` (1), `ControllerAccessCoverageTest` also lists Admin pricing and Events controllers (7 files), `WhseFefoTest` (5).
- **`ArchitectureVerificationTest` does not appear in the log, so A01 is still unverified.** Run `mvn -Dtest=ArchitectureVerificationTest test`.
- H01 (controller, security, cross-tenant tests) still open.

## H01 - Fuel security and cross-tenant tests (2026-11-12)
- Status: tests written, NOT yet run by Maven (parse-checked only).
- Production fix: `FuelReceiptRepository.findMostRecentForTank`, `findByTank` and `FuelDispatchRepository.findByTank` now filter by tenant; `FuelService` passes tenantId.
- New tests: `FuelEndpointSecurityTest` (157 endpoints: @PreAuthorize, seeded authorities, no read-only guard on writes, requireModule per endpoint, no client-supplied tenantId) and `FuelRepositoryTenantScopingTest` (every @Query tenant-filtered or on a reviewed allow-list).
- Known debt: `FuelPortalDataController` has no `requireModule("fuel")` (listed in controllers-without-module-guard.txt).
- Not covered: MockMvc/runtime security behaviour and DB-level cross-tenant integration tests.

## B07 - Pricing approval (2026-11-12)
Built (patch 0779): threshold and margin guard, approval via ApprovalFacade, change history. Margin uses latest receipt cost only. See FUEL-B07-PRICING-APPROVAL-NOTES-2026-11-12.md.

## G02 - Invoice attempt trail (2026-11-12)
Built (patch 0780): attempt trail, needs-attention panel, relink/release, block on re-invoicing unlinked deliveries. No automatic retry by design (no idempotency key in Invoicing). See FUEL-G02-INVOICE-ATTEMPTS-NOTES-2026-11-12.md.

## D01 - Nozzles and tank-to-pump reconciliation (2026-11-12)
Built (patch 0781): reconciliation report + panel; nozzle grouping as a label layer on pump records (shift flow untouched by design). See FUEL-D01-NOZZLES-RECONCILIATION-NOTES-2026-11-12.md.

## G01 - Granular permissions (2026-11-12)
Built (patch 0782): 12 area MANAGE permissions beside FUEL_MANAGE (umbrella kept). Division/site scope not built (needs IAM scope model, a decision). See FUEL-G01-GRANULAR-PERMISSIONS-NOTES-2026-11-12.md.

## E01 - Truck Stop v1 (2026-11-12)
- Built: truck stop services (a Bookings service marked as parking, shower, wash, weighbridge, workshop, food or other), reservations through a new `BookingsFacade` with truck and driver details kept in Fuel, cancel/complete/no-show, a Truck stop screen gated by the Truck Stop division. See FUEL-E01-TRUCK-STOP-NOTES-2026-11-12.
- Multi-night parking built separately in 0803 (Bookings is single-day slots). Not built here, fleet accounts and charge-to-account, invoicing or payment, POS items, driver self-service, capacity view.

## B06a - Enquiries, quote history, acceptance (2026-11-12)
- Built: enquiries (EQ-numbers, quote / lost / cancelled with reasons), append-only quotation version history with price change, optional acceptance record on confirmation (who, how, reference, which version). See FUEL-B06A-NOTES-2026-11-12.
- Not built (B06b): contracts and volume pricing. Existing contract prices are account-specific price-list entries (B07 controls); a contract entity must not become a second price master, so it needs a decision first.

## H02 - Portal module check and variance alert (2026-11-12)
- Built: the customer portal now checks the supplier's Fuel entitlement using the access grant's tenant (`FeatureGuard.hasModuleFor`); a customer of a tenant whose plan no longer includes Fuel (or that is suspended or cancelled) gets "not found" and no accounts. This closes the `FuelPortalDataController` debt noted above (the controller itself has no tenant context, so the check is in `FuelPortalDataService.requireGrant`).
- Built: daily variance alert (07:15 SAST, `fuel.variance.alerts.enabled`, default on). For each tenant that has set a tolerance and still has Fuel, new variances are raised and the administrators get one in-app and email notice with the count and a pointer to the list. Nothing is written off and no cause is implied.
- Not built: alert for variances that stay open too long; per-tenant choice of who is told (administrators only for now).

## B04 / B05 - Supplier banking and requisitions (2026-11-12)
Built (patch 0793, V676): supplier bank details proposed by one person and verified by another (enforced in the database too), masked lists, full number only to FUEL_SUPPLIER_BANK_READ with a log line, old details kept as history. Purchase requisitions through the Approvals engine (entity type REQUISITION; no rule means approved straight away), converted to a draft purchase order. Not built: supplier documents and scorecard, Accounts Payable reading the verified account, a hold period after a change, tank selection in the requisition screen. See FUEL-B04-B05-SUPPLIER-BANKING-REQUISITIONS-NOTES-2026-11-12.md.

## F01 leftovers (2026-11-12)
Built (patch 0794, V677): licence-to-depot link and per-site coverage; opt-in setting for which licensed activities block loading when expired on the loading depot's legal entity (off by default); licence evidence file upload and second-person verification (FUEL_COMPLIANCE_VERIFY, ADMIN only). Not built: required-licence rules (deliberately none), locking verified evidence, virus scan. See FUEL-F01-LEFTOVERS-NOTES-2026-11-12.md.

## Phase 6a - Group report (2026-11-12)
Built (patch 0795, no migration): read-only report per legal entity of deliveries, litres, revenue, margin, stock, stock value and open variances, with previous-period comparison; money only for FUEL_MARGIN_READ. Not built: forecourt sales (a shift is not tied to a depot), division split, export. See FUEL-PHASE6A-GROUP-REPORT-NOTES-2026-11-12.md.

## Phase 6b - Automation (2026-11-12)
Built (patch 0796, V678): per-tank reorder rules that draft (never submit or order) a requisition when the tank is below the company's level, daily at 06:30 with a notice, a "Check now" button and a log; opt-in overdue purchase order alert. Licence expiry reminders already existed. Not built: rules by fuel type or forecast, orders raised without a requisition are invisible to the rule, per-user notice choice. See FUEL-PHASE6B-AUTOMATION-NOTES-2026-11-12.md.

## Phase 6c - Landed cost per litre (2026-11-12)
Built (patch 0797, no migration): per route, tanker and depot, the cost at sale plus every recorded route cost (F02 actuals) gives landed cost per litre and margin per litre. Only routes with every cost recorded feed the per-litre figures and rollups; the rest are listed with the reason and counted as left out. FUEL_MARGIN_READ only. Not built: inbound freight from supplier to depot (not recorded anywhere), allocation by customer, forecourt, export. See FUEL-PHASE6C-LANDED-COST-NOTES-2026-11-12.md.

## Phase 7a - Returned fuel (2026-11-12)
Built (patch 0800, V679): confirm which tank fuel brought back on a failed attempt was put into; limit is what was declared returned plus loaded-not-delivered litres; append-only; books NO stock movement because tanks are only deducted at delivery completion. Not built: fuel-type check against the tank, link to tank reconciliation. See FUEL-PHASE7A-RETURNED-FUEL-NOTES-2026-11-12.md.

## Phase 7b - Inbound freight (2026-11-12)
Built (patch 0801, V680): optional freight on a stock receipt, spread over the litres and added to the tank's weighted-average cost; supplier price and receipt total unchanged so PO and invoice matching are unaffected. Not built: freight added after the receipt, carrier invoices through AP, freight on the PO match screen, VAT handling. See FUEL-PHASE7B-INBOUND-FREIGHT-NOTES-2026-11-12.md.

## Phase 7c - Supply contracts, B06b (2026-11-12)
Built (patch 0802, V681): contracts that group and report only: number, customer account, dates, optional fuel and committed litres, terms; link to the account's existing prices (no price held); litres delivered against commitment, days left, status; cancel with reason. Not built: terms document upload, expiry reminder, any enforcement in orders or invoices, volume-tier pricing (decided against). See FUEL-PHASE7C-CONTRACTS-NOTES-2026-11-12.md.

## Phase 7d - Overnight stays (2026-11-12)
Built (patch 0803, V682): Fuel-owned multi-night parking for the Truck Stop division: company-set nightly rate and bay count, stays that keep the rate they were booked at, check in and out, cancel, no-show, change departure, bay capacity per night, overstay flag, yard today. Bookings untouched. Not built: invoicing or charge to account, named bays, capacity lock under concurrent booking, Fleet link. See FUEL-PHASE7D-OVERNIGHT-STAYS-NOTES-2026-11-12.md.

## Phase 8a - Route cost by customer (2026-11-12)
Built (patch 0806, no migration): a report that shares each route's recorded actual cost across the customers delivered to, by litres, equal per stop, or site distance, chosen per report. Cent-exact, skips routes with no recorded actuals, never estimates. Not built: posting to invoices or statements, a saved default method, export.

## Phase 8b - Route optimisation suggestion (2026-11-12)
Built (patch 0807, V683): optional map position on customer sites; a suggested stop order for a route (straight-line cheapest insertion plus 2-opt, window order kept, only scheduled stops move; falls back to windows and stored distance when positions are missing). Suggestion only; applied with the existing stop-order call. Not built: road distances or traffic, arrival-time feasibility, capacity checks, stored depot position, map picker.

## G01 scope - Division and depot access limits (2026-11-12, patch 0808)
Built: per-user division and depot limits (V684), enforced in the Fuel interceptor; tank and depot lists filtered; Setup > Access limits screen. Also fixed the ungated `load-returns` API. Open: depot filtering of non-tank lists and reports; forecourt site depot (8d). See FUEL-PHASE8C-SCOPED-PERMISSIONS-NOTES-2026-11-12.md.

## Forecourt-to-depot attribution (2026-11-12, patch 0809)
Built: depots can record a supplying depot (V685, loop-safe); forecourt sales by supplying depot report. Open: attribution to legal entities in the main group table; depot-limit filtering of the report; supplier volume split. See FUEL-PHASE8D-FORECOURT-DEPOT-ATTRIBUTION-NOTES-2026-11-12.md.

## D03 - Pump/POS import (2026-11-12, patch 0810)
Built the inbound boundary only: key-authenticated idempotent shift import (V686), review queue, accept through the normal shift code. Not built: device or vendor adapters, live transactions, tank-gauge feeds, auto-accept, pump alias mapping. See FUEL-PHASE8E-POS-IMPORT-NOTES-2026-11-12.md.

## Backlog
The open work after Phase 8 is listed in FUEL-MODULE-BACKLOG-2026-11-12.md.
