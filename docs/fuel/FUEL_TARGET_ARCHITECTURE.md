# Fuel: target architecture, assessment and phase plan (Phase 0 output)

This document is a **proposal built on the audit**; nothing in it is implemented yet. Where the brief and the repository disagree, the repository wins and the difference is stated.

## 1. Assessment
- The existing module is one Spring Modulith module (`fuel`) with a controlled stock ledger, orders, deliveries, invoicing hand-off, retail shifts and a customer portal. It should be extended, not replaced.
- There is **no concept of operating division, legal entity or licence**. That is the foundation the four-division design needs, and it is Phase 1.
- Truck Stop does not exist.
- Three items need verification before building on top: GAP-A01 (fuel/fleet dependency vs `ArchitectureVerificationTest`), a full backend test run, and whether `fuel_tankers` duplicates Fleet vehicles (GAP-C03).

## 2. Four controls (brief section 7.3), as they map onto this codebase
1. **Subscription entitlement** (exists): `featureGuard.requireModule("fuel")`.
2. **Operating division configuration** (new): per-tenant rows saying which of WHOLESALE_SUPPLY, DISTRIBUTION, RETAIL_FORECOURT, TRUCK_STOP are ACTIVE. A `FuelDivisionGuard.require(division)` called from the application layer (not only controllers), so API calls cannot bypass it.
3. **Capability configuration** (extends `fuel_settings`): optional features and tolerances.
4. **User permissions** (exists, to be made granular): see section 6.

A permission never overrides 1 or 2.

## 3. Phase 1 design (shared configuration foundation)
New tables (Flyway V658+, additive only):
- `fuel_operating_profiles` — one per tenant: business size, organisation structure, setup state (`NOT_STARTED | IN_PROGRESS | ACTIVE`), wizard progress as JSON, `legacy_mode` flag, audit columns, version.
- `fuel_division_activations` — (tenant, division) unique; status ACTIVE/INACTIVE, activated_at/by, deactivated_at/by, reason. Rows are never deleted.
- `fuel_legal_entities` — name, registration number, tax and VAT numbers, address, status. Links added as nullable columns on depots, orders, sales orders, receipts and stock movements (nullable so existing rows stay valid).
- `fuel_licences` — legal entity, licensed activity, number, status, issue/expiry, conditions, site/depot reference, responsible person, document reference. Linked to `fuel_compliance_items` rather than replacing them.
Backward compatibility: every existing tenant gets a profile with `legacy_mode = true`, in which the division guard allows what the tenant could already do (all current Fuel capabilities) until an authorised user completes setup. No division or licence is inferred from usage. Existing permissions keep working; new granular permissions are granted to roles that hold FUEL_MANAGE today.
Backend: `FuelOperatingProfileService`, `FuelDivisionGuard`, profile and legal-entity controllers (`FUEL_CONFIGURATION_READ/MANAGE`), and guard calls on each existing application service entry point by division.
Frontend: division-aware navigation (driven by a profile endpoint), setup wizard with save-and-resume, setup checklist. Hidden navigation is not treated as security.
Deactivation: refused while open orders, scheduled deliveries, active reservations, open shifts or pending approvals exist for that division; history is never touched.

## 4. Division mapping to existing code
| Division | Reuses | Missing (see gap register) |
|---|---|---|
| Wholesale & Supply | suppliers, POs, supplier invoices, customers, pricing, quotes/orders, ledger, portal | enquiries/RFQ, contracts, approvals, supplier onboarding, AP hand-off |
| Distribution | tankers, compartments, planning, driver loads, receipts PDF, invoicing queue | Fleet linkage, routes, POD evidence, variance review, general offline queue |
| Retail & Forecourt | pumps, shifts, readings, tender totals | nozzles, tank-to-pump reconciliation, approvals, refunds/voids, POS boundary |
| Truck Stop | nothing | all; to integrate Bookings, POS & Stock, Fleet, Invoicing through facades |

## 5. Ownership and integration (verified so far)
- Invoicing: Fuel calls `InvoicingFacade` (allowed dependency).
- Approvals: Fuel uses `ApprovalFacade` for dispatches.
- Fleet: Fuel publishes `FuelDispatchedToVehicleEvent`; the consumer currently lives in Fuel and imports Fleet types (GAP-A01).
- Accounts Payable: **no dependency allowed today**; supplier invoice approval stops in Fuel.
- Documents, Evidence, Workflow, Bookings, POS & Stock: not yet used by Fuel; whether shared Evidence/Document services exist as facades was not audited and must be confirmed before Phase 3.

## 6. Permissions (proposal)
Keep FUEL_READ, FUEL_MANAGE, FUEL_STOCK_ADJUST, FUEL_MARGIN_READ, FUEL_FORECOURT, FUEL_DRIVER, FUEL_CREDIT_OVERRIDE. Add the brief's granular names incrementally per phase, seeded with the V652/V655 pattern, each mapped so existing FUEL_MANAGE holders keep access. Phase 1 adds FUEL_CONFIGURATION_READ/MANAGE and FUEL_LICENCE_MANAGE only.

## 7. Assumptions and decisions needed
Assumptions I will make unless told otherwise (all configurable):
- Existing tenants run in `legacy_mode` until setup is completed.
- Units are litres; currency ZAR; VAT default 15% stays configurable (no tax rate is hard-coded in Fuel today).
- Tolerances become settings; no regulatory thresholds are invented.

Decisions that need you:
1. Allow Fuel to depend on Accounts Payable for supplier-invoice posting? (open since Stage "supplier invoices")
2. Is `fuel_tankers` meant to stay a Fuel table, or should each tanker reference a Fleet vehicle?
3. Who may activate/deactivate a division (proposed: FUEL_CONFIGURATION_MANAGE, owner/admin)?
4. Truck Stop: build on Bookings for reservations, or a Fuel-owned bay model? (Brief prefers Bookings.)

## 8. Phase plan
- **Phase 0.5 (before any build):** run backend tests and the architecture test; resolve A01; add cross-tenant, permission and concurrency tests (H01); close B03.
- **Phase 1:** section 3 above.
- **Phase 2 Wholesale:** B01, B04–B08.
- **Phase 3 Distribution:** C01–C05.
- **Phase 4 Retail:** D01–D03.
- **Phase 5 Truck Stop:** E01.
- **Phase 6:** group reporting, automation, advanced costing (F02).
No P2/P3 work starts while a P0 item is open.
