# Fuel: decision register, 2026-11-17

The 11 open decisions are closed with the recommended defaults (attachment received 20:42). Principle: configuration and shared services, no hard-coded business rules.

| # | Decision | Outcome | Build status (before this register) | Work to do |
|---|---|---|---|---|
| 1 | D1 shift variance | Auto-accept within tolerance when all checks pass; supervisor notified; exceptions to site manager; record variance, tolerance, actor, time; repeated variances alert; tolerance per tenant/site/product | Auto-accept exists (0860) for gauge by company-entered difference and shifts; no supervisor notice, no per-site/product tolerance, no repeat-variance alert | Audit record + notices + per-site/product tolerance + repeat alert |
| 2 | D3 alert exclusions | Role-targeted alerts; administrators may be excluded from routine mail but keep the alert centre and mandatory escalation | Recipients = admins plus up to 10 extras (0859) | "Exclude administrators" switch per topic; more topics; mandatory escalations never excluded |
| 3 | Bookings VAT | Prices VAT-exclusive; show ex-VAT, VAT, total; tax via shared engine; no double VAT on tab invoicing | Prices read from Bookings at add time; VAT treatment unverified | Verify tab/stay invoicing does not add VAT twice; show breakdown |
| 4 | Till-paid tabs | Tab charge on the day charged; payment in payment-date takings; not counted as revenue twice; cash/card/EFT | Tab settle at till exists; not wired to takings | Payment line in cash-up by method |
| 5 | Statements | Whole Fuel account: deliveries/invoices, truck-stop, bookings, payments, credits, adjustments, balance; filters date/entity/account/division | Truck-stop charges only (0863) | Widen to the Fuel account ledger |
| 6 | Provider times | Actual provider times drive shifts-by-date reports in site timezone; keep scheduled separate; flag fallback | Times recorded (0861) | Use in reports with site timezone and fallback flag |
| 7 | Eligibility review roles | Fuel Manager, Dispatch Supervisor, Compliance Officer, ADMIN; review separate from override; permission-based | Single permission FUEL_ELIGIBILITY_REVIEW (V696) | Split review from override permission; grant to the three roles |
| 8 | Cleaning certificates | Uploaded evidence via shared Document/Evidence service, verified; block incompatible loading until present | Not built | Build on the shared document service |
| 9 | Depot compliance visibility | Limited users see existence/status/expiry, not documents or details | Not built | Status-only view for limited users |
| 10 | Supply split | Attribution-only | Built that way | Keep; no change |
| 11 | Forecourt replenishment | Internal depot-to-forecourt = one stock transfer linked to the dispatch; external = receipt; inter-entity = inter-entity transfer | Dispatch matching report (0850s) | Link dispatch to transfer; never double-count |

Order I will build in: 2 (D3), 1 (D1), 7, 5, 4, 6, 3 check, 11, 9, 8. Each is a separate patch; Java stays "written, not compiled or run".

## Build status (updated 2026-11-17, evening)

| # | Decision | Status | Patch |
|---|---|---|---|
| 1 | D1 shift variance | Built | 0869 |
| 2 | D3 alert exclusions (and D2 contract volume pricing, separately) | Built | 0865, 0866 |
| 5 | Statements | Built, with the limits in its notes (no payment ledger, no legal-entity filter) | 0870 |
| 7 | Eligibility review roles | Built (review decisions log) | 0868 |
| 8 | Cleaning certificates | Built | 0873 |
| 9 | Depot compliance visibility | Built | 0871 |
| 11 | Forecourt replenishment | Built (inter-entity transfers not built) | 0872 |
| 4 | Till-paid tabs feed takings | Not built | |
| 6 | Provider times in reports | Not built | |
| 3 | Bookings VAT check | Not built | |
| 10 | Supply split | No change needed | |
