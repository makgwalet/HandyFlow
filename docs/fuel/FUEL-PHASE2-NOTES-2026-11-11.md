# Fuel Phase 2: Wholesale controls (patches 0769 backend, 0770 web)

Apply with `git am -3`. Migration **V659** (next free is V660). Nothing was pushed.

## 1. What was implemented
Phase 2 in the plan is B01 and B04 to B08. I built B01, B03 (small), B04 and B05. I did not build B06, B07 or B08 (see section 7).

- **B05 Purchase order approval.** "Place order" now asks the existing Approvals engine (module `fuel`, entity type `PURCHASE_ORDER`, metadata `totalAmount`, `litres`, `fuelType`). With no rule configured the engine approves immediately and the order is placed exactly as before. With a matching rule the order becomes **PENDING_APPROVAL** (cannot be received against), and the engine's outcome places it or sends it back to draft with a note. A failure to reach the engine does **not** place the order. Rules are set by the company in Approvals, not hard-coded.
- **B04 Supplier approval.** New setting "require supplier approval" (off by default). When on, a new supplier is PENDING_APPROVAL and cannot be ordered from until a **different** person with `FUEL_SUPPLIER_APPROVE` approves. Any supplier can be suspended with a reason; a suspended supplier cannot get new orders raised or placed (orders already placed are unaffected). Existing suppliers are APPROVED.
- **B01 Stock adjustment second person.** New setting "adjustment threshold (litres)", empty by default (no limit, today's behaviour). An adjustment of that many litres or more, up or down, is held as a request; stock moves only when a different person with `FUEL_STOCK_ADJUST_APPROVE` approves it. The requester can withdraw; the approver must give a reason to reject. If the stock can no longer take the adjustment at approval time (for example it is reserved), the approval fails and the request stays pending.
- **B03** Dip-reading queries now filter by company as well as tank.

## 2. Existing functionality preserved
- Defaults keep today's behaviour: no threshold, supplier approval off, and no approval rule means orders are placed at once.
- Existing suppliers are backfilled APPROVED. No existing column, status or endpoint was removed.
- **Changed response shapes (callers to check):** `POST /tanks/{id}/adjust` now returns `{ pendingApproval, movement, request }` instead of the movement alone (the web app ignored the body). `SupplierResponse` gained `approvalStatus`, `statusReason`. `PurchaseOrderResponse` gained `approvalNote`. `POST /purchase-orders/{id}/place` now returns status PENDING_APPROVAL when a rule applies.

## 3. Database (V659, additive)
`fuel_settings` + `require_supplier_approval`, `adjustment_threshold_litres`; `fuel_suppliers` + `approval_status`, `status_reason`, `status_changed_by/at`, `created_by`; `fuel_purchase_orders` + `approval_request_id`, `approval_note` and status check now includes PENDING_APPROVAL; new `fuel_stock_adjustment_requests`. Permissions `FUEL_SUPPLIER_APPROVE` (SENSITIVE) and `FUEL_STOCK_ADJUST_APPROVE` (FINANCIAL), granted to ADMIN.

## 4. API
`POST /suppliers/{id}/approve|suspend` (FUEL_SUPPLIER_APPROVE); `GET /adjustment-requests` (ADJUST or ADJUST_APPROVE); `POST /adjustment-requests/{id}/approve|reject` (ADJUST_APPROVE), `/withdraw` (ADJUST); `GET/PUT /settings/controls` (read FUEL_READ, write FUEL_CONFIGURATION_MANAGE). Division rules are unaffected: these paths are shared foundation.

## 5. Web
Suppliers: status line and Approve/Reinstate/Suspend (with reason). Purchase orders: "Waiting for approval" status, rejection note, cancel allowed. Stock: panel of adjustments waiting for a second person (approve/reject/withdraw; the requester is never shown Approve) and a clear message when your adjustment is held. Settings: "Approvals and controls" card.

## 6. Verification: what was and was not run
- **Java is parse-only. Maven has not been run**, including the new tests: `FuelWholesaleControlsTest` (10, entity rules) and `FuelWholesaleControlsServiceTest` (17, services with mocks). One existing test file is unchanged but `FuelPurchasingService` gained a constructor argument; tests that use `@InjectMocks` should still work.
- **V659 run twice in a scratch Postgres 16** with a stub suppliers table: idempotent; a bad supplier status, a zero threshold, a zero-litre request and a bad request status are all rejected; the PO status check includes PENDING_APPROVAL; both permissions exist.
- **Web: vitest 187 pass in `src/pages/fuel`** (plus fuel-portal, navigation and shell earlier in the run); `tsc` clean.
- Not tested: the Approvals event path (`ApprovalCompletedEvent` to the Fuel handler) against a running app; any controller, security or cross-tenant test with MockMvc (gap H01 still open); `ArchitectureVerificationTest`.

## 7. Not done, and why
- **B08 supplier invoice to Accounts Payable**: waiting on your decision whether Fuel may depend on AP.
- **B06 enquiries, quote revisions, contracts**: a new commercial sub-domain; too large to ship unverified alongside the above.
- **B07 price approval and margin guard**: not started; it needs a pending-price state and a cost basis rule that should be agreed first.
- Supplier banking details and documents; purchase requisitions.

## 8. Risks
- A PO that is PENDING_APPROVAL when the Approvals rule is later deleted stays pending; cancel it and raise a new one.
- If the approval event is lost, the PO stays PENDING_APPROVAL; there is no manual "refresh" yet.
- Only ADMIN holds the two new approval permissions until granted to others.
- A single-person company cannot approve its own large adjustments or suppliers by design; leave the settings off in that case.
- Phase 0.5 items (run Maven, architecture test, add MockMvc/cross-tenant tests) are still the biggest unknown.
