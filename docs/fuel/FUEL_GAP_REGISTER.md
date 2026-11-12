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
| B06 | Wholesale | Enquiry/RFQ, contracts, quote revisions | `FuelSalesOrder` QUOTE status only | No enquiry, revision, acceptance record, contract, volume pricing | Commercial lifecycle | none | P1 | A02 | New commercial sub-domain |
| B07 | Wholesale | Pricing approval/versioning | `FuelPrice` effective-dated, end endpoint | No approval, margin threshold, history of changes | Price errors | none | P1 | Approvals | Margin-guard and approval |
| B08 | Wholesale | Supplier invoice → AP | `FuelSupplierInvoice` approve/reject, no posting | No hand-off to Accounts Payable. Fuel may not depend on `ap` today | Double capture | none | P1 | **Decision** | Facade/event to AP if allowed |
| C01 | Distribution | POD evidence | `FuelDelivery.complete`: receiverName, signature URL, signedOnBehalf | No photos, GPS, evidence verification, correction trail | Disputes | `complete()` is once-only (status guard), so the API cannot overwrite it; but there is no verification or auditable correction process | P0 | Evidence service | Evidence records via shared service, locked after verification |
| C02 | Distribution | Delivery variance | Loading record stores litres/seals; comment says variance is reviewed | No tolerance, exception, review, decision record | Unexplained losses | none | P0 (financial) | A02 | Variance entity + tolerance setting + review flow |
| C03 | Distribution | Fleet integration | `fuel_tankers` is a Fuel table | Possible duplicate vehicle master (unverified) | Double maintenance | none | P1 | Fleet owner decision | Reference Fleet vehicle id from tanker |
| C04 | Distribution | Routes, multi-stop, windows, failed delivery, returns, eligibility | none found | not built | Planning capability | none | P2 | C03 | New route domain |
| C05 | Distribution | Offline | Queue of completion only | Attachments, conflict handling, per-item status | Field reliability | Server-side validation exists in complete | P1 | C01 | Generic offline queue in web |
| D01 | Retail | Nozzles, tank-to-pump reconciliation | pumps + readings | no nozzles | Pump accuracy | none | P1 | A02 | Nozzle entity |
| D02 | Retail | Refunds, voids, discounts, fleet-card tender, supervisor approval | `FuelShift.close` records cash, card, account and other, closed_by, variance vs a R5 constant (V655) | No separate fleet-card tender, refunds, voids, discounts or supervisor approval; tolerance is a code constant; no check that the closer differs from the opener (not found) | Cash control | Self-close possible (no rule found) | P1 | none | Configurable tolerance, supervisor approval over tolerance, tender types |
| D03 | Retail | POS/pump integration | none | Needs real integration | n/a | n/a | P2 | External | Define integration boundary only |
| E01 | Truck stop | Entire division | none (no tables, code, UI) | everything | New revenue line | n/a | P2 | A02, Bookings/POS/Fleet facades | Phase 5 |
| F01 | Compliance | Entity/licence/site linkage | `FuelComplianceItem` generic with expiry | no legal entity, licensed activity, site, vehicle or person link; no evidence | Regulatory traceability | none | P0 (spec) | A03 | Licence model on legal entity |
| F02 | Cost | Actual vs estimate | cost-to-serve flags estimates | no per vehicle/route/depot, no reconciled costs | Profit accuracy | none | P2 | C04 | Extend |
| G01 | Platform | Permissions granularity | 10 FUEL_* permissions; 72 uses of FUEL_MANAGE; inline `FUEL_CREDIT_OVERRIDE` check | Coarse write permission; no division/site scope | Least privilege | Over-broad write access | P1 | A02 | Granular permissions per brief, mapped from FUEL_MANAGE for existing roles |
| G02 | Platform | Outbox | Direct facade call for invoicing; Modulith listener for one event | No outbox/retry UI for failed invoice request | Silent failure risk | none | P1 | none | Outbox table + retry |
| H01 | Tests | Controller/security/cross-tenant tests | 22 Fuel unit-test classes (181 @Test), none for controllers/tenant isolation | no security tests | Regression risk | Unproven isolation | P0 | none | Add MockMvc + cross-tenant tests |

Each row is a gap statement; the backend/frontend/database/permission/test lists per gap are in FUEL_TARGET_ARCHITECTURE.md (change map) and are to be detailed per phase.

## Status after Phase 2 (code written; Java not compiled or run by the author)
- **B01** done in code: adjustments at or above a per-tenant litre threshold are held until a different person with FUEL_STOCK_ADJUST_APPROVE approves. No threshold means no change from before.
- **B03** done in code for dip readings (tenant parameter added). `FuelReceiptRepository.findByTank` and `FuelDispatchRepository.findByTank` have the same shape but no callers; left alone.
- **B04** done in code (supplier status, off by default). Banking details and supplier documents are **not** built.
- **B05** done in code through the Approvals engine (module fuel, type PURCHASE_ORDER). Requisitions are not built.
- **B06 (quotes, contracts), B07 (price approval/margin guard)**: not started. **B08**: blocked on the Accounts Payable decision.

## Status after Phase 3 (code written; Java not compiled or run by the author)
- **C02** done in code: loaded-versus-delivered variance with a per-company tolerance that has **no default** (nothing is flagged until set), a review record (open, investigating, closed with EXPLAINED or WRITTEN_OFF and a note). A decision is a record only and moves no stock.
- **C01** done in code: append-only evidence records (photo, signature, document, GPS) with replace-before-verify and verify-by-someone-else-then-lock. Files are referenced, not uploaded by this feature. The existing completion fields are unchanged.
- **C03** not started (waiting for your decision on `fuel_tankers` versus Fleet vehicles). **C04** not started (P2). **C05** not started (offline queue, depends on C01).

## Status after Phase 4 (code written; Java not compiled or run by the author)
- **D02** done in code: per-company cash tolerance (default R5.00, as before), fleet-card tender, refunds/voids/discounts with supervisor approval (never the recorder), supervisor review of out-of-tolerance shifts, optional "closer differs from opener" rule. Review and approval are records only and move no stock or money.
- **D01 (nozzles)** not started: changes the meter model. **D03 (POS/pump integration, P2)** not started: boundary only.

## Status after F01 (code written; Java not compiled or run by the author)
- **F01** done in code: licences link to a legal entity, a licensed activity and an issuing authority; evidence references (append-only); per-entity coverage (information only, no required-licence rules). Not done: site entity link, blocking by entity, evidence verification.
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
