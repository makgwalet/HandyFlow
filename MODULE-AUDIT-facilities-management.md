# Facilities Management (FM provider) and internal Facilities — backend audit, 2026-10-27

Scope: the `facilitiesmanagement` backend module (contract coverage, SLAs, billing, credit notes, notifications, client portal, reporting) and the internal `facilities` module (reviewed, fixed, reporting added).

## Migrations

| Version | Author | What |
|---|---|---|
| V502–V509 | you | facilities / FM tables (unchanged) |
| V510 | this pass | `facility_work_orders` lifecycle columns + `facility_work_order_cost_lines` (V505 was a copy of V504; these objects had no migration). Idempotent. |
| V511 | this pass | client/supplier VAT + address, invoice party snapshot, `credited_amount`, payment `idempotency_key`, `fm_credit_notes` |
| V512 | this pass | `fm_work_orders.requested_by_portal_user_id` + partial index (client portal requests) |
| V513 | this pass | reporting indexes only (work orders by tenant+created/completed, invoices by status/issue date, credit notes by status, certificates by expiry). No table or column changes. Idempotent. |
| **V514** | next free | |

All new migrations (V510–V513) were run twice against PostgreSQL 16 (idempotency) and every FM entity was checked against the resulting schema (0 mismatches).
Do **not** edit V506–V509 to tidy their "rename" header comments: Flyway checksums them.

## Fixed

- **Four stale test files did not compile** (FmBillingControllerTest, FmInvoiceTest, FmServiceAgreementTest, FmWorkOrderTest; FmWorkOrderControllerTest had the wrong record arity). One of these breaks `mvn test-compile` for the whole project. All rewritten against current APIs.
- **PPM work orders got SLA clocks** and would breach (and alert) days before anyone was meant to touch them. PPM now has no SLA.
- **Completion time** could be in the future or before creation; now validated (5-minute clock-skew allowance).
- **Cost-line pricing**: a technician could set the client charge or mark lines non-billable. Now manager/admin only (403 `COST_LINE_PRICING_RESTRICTED`).
- **Time zones**: services mixed `systemDefault`, UTC and `LocalDate.now()`. Everything now uses `FmTime` (Africa/Johannesburg).
- **Credit-note approval** applied the credit to the invoice before the note rejected a non-draft state; there is now an up-front DRAFT check (found by the new service tests).
- SLA alerts used generic notification types; now `FM_SLA_AT_RISK` / `FM_SLA_BREACHED`.

## Client portal (reviewed and changed)

- **Blocker fixed:** every portal data endpoint called `TenantContext.getTenantIdAsObject()`, but portal tokens are deliberately tenant-less (separate secret, not tenant-scoped), so sites, work orders and invoices would have failed with a 500. Tenant and client are now read off the caller's ACTIVE grant. A person with several clients passes `?clientId=` (`GET /portal/me/clients` lists them; 400 `CLIENT_SELECTION_REQUIRED` otherwise); a `clientId` with no grant is a 403, never trusted.
- Fully credited invoices disappeared from the portal and credited invoices showed a balance with no explanation: CREDITED invoices are now visible and the response carries `creditedAmount`.
- New: approved credit notes (`/portal/me/credit-notes`), invoice and credit-note PDFs (`.../invoice.pdf`, `.../credit-note.pdf`), each checked to belong to the caller's client and to be visible.
- New: **client self-service requests** — `POST /portal/me/work-orders`. Lands as REQUESTED (nothing is billable until staff approve), site/asset must be the client's own, category CORRECTIVE/EMERGENCY/OTHER, **priority is never client-set** (safety → HIGH, emergency → EMERGENCY; staff confirm on approval), contract coverage and SLA stamped, possible duplicates flagged, audit (`PORTAL_REQUEST_RAISED`) and admin alert, throttled to 20 per client per 24 h (429 `REQUEST_LIMIT`). The client sees its own requests as SUBMITTED / DECLINED (staff's reason stays internal).
- **Portal round 2 (V514):**
  - **Gap fixed:** `FmWorkOrderController` had no endpoints for approving or declining requests, or for verifying or sending back completed work, so a portal request could never be approved through the API. Added `POST /work-orders/requests`, `/{id}/approve`, `/{id}/reject`, `/{id}/verify`, `/{id}/rework` (MANAGE, ADMIN or WORKORDER_ASSIGN; segregation of duties and audit unchanged in the service).
  - Declining takes `reason` (internal) and an optional `clientMessage`, stored in the new `client_message` column and shown to the client as `declineMessage`.
  - Clients can withdraw a request they submitted (`DELETE /portal/me/work-orders/{id}`): same person only, REQUESTED only (409 `REQUEST_NOT_WITHDRAWABLE` once staff have picked it up), audited `PORTAL_REQUEST_WITHDRAWN`, admins alerted.
  - Portal work orders now carry `resolutionDueAt` and `resolutionSla` (ON_TRACK, AT_RISK, PAUSED, MET, BREACHED). Only the fix-by target is shown, never the internal response target, and nothing before approval or for declined or withdrawn requests.
  - `GET /portal/me/summary`: work by portal status, open work, SLA at-risk and breached counts, outstanding and overdue balance.
- Stale comment in the auth controller removed: `/portal/auth/**` is already permitted and rate-limited.
- Not changed, worth a look: `bookkeeping` has a portal controller using the same `TenantContext.getTenantIdAsObject()` pattern (not verified, not touched).

## Internal facilities module (reviewed and changed)

- **Time zones**: `LocalDate.now()` / server zone were used for PPM dates, certificate expiry and the overdue sweep, so a job completed after 22:00 SAST landed on the wrong day. Everything now uses `FacilityTime` (Africa/Johannesburg).
- **A notification failure could roll back the business action** (e.g. raising an urgent request failed if mail was down). All alerts now go through `FacilityAlerts`, which logs and swallows failures.
- **Overdue alerts repeated every day forever.** They now fire on days 1, 3, 7, 14, 30 and every 30 days after (`FacilityOverduePolicy`).
- **Missing audit entries** for work-order creation, start and hold; added. Vendor deactivate/reactivate/delete are now audited too.
- **Completion time** could be in the future or before the work order existed; now validated (5-minute clock-skew allowance).
- **Missing alerts**: a new (non-urgent) request now tells admins it is waiting, and completion that needs a verifier tells admins it is waiting (`FACILITY_WORKORDER_REQUESTED`, `FACILITY_WORKORDER_AWAITING_VERIFICATION`).
- **Tests that did not compile**: `FacilityWorkOrderTest` called `complete(...)` with 3 arguments (the method takes 6) and `FacilityWorkOrderControllerTest` built `WorkOrderResponse` with 20 of its 32 components and was missing a `@MockitoBean` for `FacilityWorkOrderCostService` (the context would not have loaded). Either breaks `mvn test-compile`/the test run. `FacilityComplianceCertificateTest` built certificates that expired before they were issued, so two of its tests could never pass. All fixed.
- Reviewed, no change needed: controllers all carry `@PreAuthorize`; entities match the schema (0 mismatches); access scope; cost lines.
- Not changed, worth knowing: a cancelled PPM work order is regenerated by the next daily sweep (the schedule is still due); PPM completion moves the next due date by the frequency from the completion date (a floating interval, not a fixed calendar).

## Inspections, corrective actions and action centre (internal facilities, V515)

- **Inspection templates** (`/api/v1/facilities/inspection-templates`): named checklists of up to 100 checks, each optionally critical. Editing replaces the checks and bumps `revision`; inspections already started keep the prompts they started with. Names are unique per tenant (409 `TEMPLATE_NAME_TAKEN`). Templates can be switched off.
- **Inspections** (`/api/v1/facilities/inspections`): start against a site (and optional asset), save answers PASS/FAIL/NA a few at a time (a FAIL needs a comment), complete only when every check is answered. Site scope applies; out-of-scope reads as not found. Completing audits the result.
- **Corrective actions** (`/api/v1/facilities/corrective-actions`): every failed check raises one automatically (critical failure: HIGH, due in 2 days; otherwise NORMAL, 14 days), or log one by hand (MANUAL or CERTIFICATE). Raise a work order from an action, which links it; an action with an unfinished work order cannot be closed (409 `WORK_ORDER_NOT_COMPLETE`). Close, cancel and reschedule are all audited.
- **Action centre** (`GET /api/v1/facilities/action-centre`): lapsed certificates (critical), expiring certificates (high within 14 days, medium within 30), critical open actions (critical), overdue actions (high, critical after a week), most severe first, capped at 200 items with full counts.
- Alert `FACILITY_INSPECTION_FAILED` to tenant admins when an inspection fails. New authority string `FACILITIES_INSPECT` for people who may run inspections but nothing else (templates and actions need MANAGE, ADMIN or COMPLIANCE_MANAGE).
- Not done: scheduling recurring inspections (no "inspection due" reminders yet), inspection photos (the evidence module is not wired to inspections), and turning a lapsed certificate into an action automatically. A manual CERTIFICATE action covers it for now.

## Reporting (new, both modules)

All figures are computed on request from the work orders / invoices themselves, inside the caller's access scope, and the vendor and asset arithmetic lives once in `shared.MaintenanceAnalytics` so the two modules cannot drift. FM SLA figures use the same `FmSlaStatus` rules as the API and the SLA monitor.

Provider (`/api/v1/facilitiesmanagement/reports`):
- `GET /dashboard` — open work by status and priority, unassigned, on hold, overdue, awaiting approval, awaiting verification, SLA at risk / breached (counted once per work order), 30-day throughput, average repair time and resolution-SLA compliance. **Finance KPIs** (receivables and overdue receivables, invoiced this month, drafts, approved-not-sent, credit notes awaiting approval, unbilled completed work) only for callers with ADMIN / COST_READ / BILLING_MANAGE / BILLING_APPROVE, and never for field technicians (they see their own jobs only).
- `GET /vendor-performance?from&to` — per subcontractor: jobs, completed, cancelled, open, average resolution hours, rework rate, resolution-SLA compliance, internal cost (cost only with a cost/billing authority).
- `GET /asset-reliability?from&to&limit` — worst assets first: failures, repeat failures (within 30 days of the previous), downtime, MTTR, MTBF, top failure cause, cost (cost gated as above).
- `GET /sla-portfolio?from&to` — SLA compliance across every client the caller may see, worst first. The per-client report now shares its counting with this one (regression-tested).

Internal (`/api/v1/facilities/reports`): `GET /dashboard` (open work, overdue, approvals, 30-day throughput, rework rate, downtime and cost, overdue PPM schedules, certificates expiring in 30 days / lapsed), `GET /vendor-performance`, `GET /asset-reliability`. Technicians and requesters see the dashboard for their own work only and are refused the vendor and asset reports (403 `SCOPE_RESTRICTED`).

Definitions worth knowing: a *failure* is a reactive (non-PPM, non-inspection), non-cancelled work order; MTBF = (period hours − recorded downtime) ÷ failures, and a period that runs into the future is measured only up to now; a *repeat failure* is one within 30 days of the previous failure on the same asset; rework rate = completed jobs sent back at least once ÷ completed jobs; SLA compliance = met ÷ (met + breached), absent when nothing has concluded. Periods are capped at 366 days because matching work orders are loaded into memory.

## Built (from your roadmap)

- **Credit notes** (SA VAT Act s21): prepare → approve by a different person (admin override audited) → ledger posting after commit with retry; reduces the invoice balance; PDF "Tax Credit Note"; counted against client profitability. Endpoints under `/invoices/{id}/credit-notes` and `/credit-notes/{id}`.
- **Tax invoice content** (s20(4)): client and supplier VAT numbers (10 digits, starts with 4), supplier/client name and address snapshotted onto the invoice at issue, recipient name/address required above R5,000, "Tax Invoice" title only when the supplier is VAT-registered.
- **Payment double-submit protection**: `Idempotency-Key` header (or body field) replays the original result; duplicate bank reference on an invoice is a 409 `DUPLICATE_PAYMENT`; concurrent payments are stopped by the invoice's optimistic lock.
- **Notifications**: `FM_WORKORDER_REQUESTED`, `FM_WORKORDER_AWAITING_VERIFICATION`, `FM_INVOICE_AWAITING_APPROVAL`, `FM_CREDIT_NOTE_AWAITING_APPROVAL` (plus the two SLA types above).

## Not done (honest list)

Portal quote approvals (the rest of the portal roadmap is done); site and client scope for vendor and technician records and audit on FM vendor changes; report exports (CSV/PDF), scheduled report emails and trend-over-time charts (the reports are point-in-time); mobile/QR/offline; inventory and procurement; vendor onboarding with insurance/licence tracking; multi-entity portfolios and integrations. None have migrations or code.

## Known limitations (carried over from your list, unchanged)

No browsable agreement version history; a priority raised at approval takes the agreement's *current* target; "response" means first assignment only; escalation stops at tenant admins; no call-out/after-hours fees, service credits for breaches, or client-visible SLA status; retainers bill a flat fee regardless of period length; the SLA monitor loads all open SLA work orders every 10 minutes.
Reports: they load matching work orders into memory (hence the 366-day cap); "response" and "resolution" SLA use the first-assignment and technician-completion definitions above; vendor cost is the work order's recorded internal cost, not vendor invoices; the internal module has no SLA so no compliance figure; there is no trend-over-time view yet.
New: an approved-but-unsent invoice that is fully credited shows to the client as CREDITED (with its credit note); a staff-side flag for portal-raised requests is not in the work-order response yet (staff see reportedBy "Client portal" and the audit entry). Credit notes only reduce what is still owed — refunding money already paid is out of scope. `findOverdueAcrossTenants` uses the database `CURRENT_DATE` rather than the SA date.

## Tests — what was and was not executed

Maven Central is unreachable from the build sandbox, so **no Spring or Mockito test was run here**. To still execute logic, the domain and service tests were written as plain JUnit with hand-written fakes and run on a stub harness: **398 tests passed**, and deliberate breaks of key rules (credit ignored in balance, holiday skipping, R5,000 threshold, VAT back-out, site overlap, idempotency, duplicate reference, alert failures rolling back, cost visible without authority, scope ignored, technicians allowed into management reports, awaiting-approval counted as open, rework threshold) were all caught.

- Executed (harness), FM: FmInvoice, FmCreditNote, FmVatNumber, FmWorkOrder, FmServiceAgreement, FmInvoicePayment, FmSlaClock, FmSlaStatus, FmPriorityRules, FmBillingSettings, FmTime, FmGlJournals, and the services FmContractTermsService, FmServiceAgreementService, FmCreditNoteService, FmBillingService, FmWorkOrderCostService, FmSlaMonitor, FmPortalDataService, FmPortalRequestService, FmPortalStatus, FmSlaReportService, FmReportingService.
- Executed (harness), internal facilities: FacilityWorkOrder (22), FacilityAsset, FacilityPpmSchedule, FacilityComplianceCertificate, FacilityOverduePolicy, and the services FacilityWorkOrderService (20), FacilityVendorService, FacilityReportService (15). Shared: MaintenanceAnalytics (19).
- **Written but not executed**: FmBillingControllerTest, FmWorkOrderControllerTest, FacilityWorkOrderControllerTest and FacilityComplianceCertificateControllerTest (`@WebMvcTest` + `@MockitoBean`); the record constructors they use were compile-checked against the DTOs. Pre-existing FmAssetTest and FmPpmScheduleTest pass unchanged.
- **Not covered**: the new report controllers (no controller tests), the FM portal controller, FmPortalService invite/revoke and FmPortalAuthService, FmGlPostingService, FmPdfService, FmWorkOrderService, FmAccessPolicy, FmAlerts, the other internal-facilities services (site, asset, technician, PPM, compliance, access admin), FacilityNotificationScheduler, FacilityPdfService, FacilityAccessPolicy, and **the JPQL queries** (only checked by schema/entity comparison, never executed — the new reporting queries, including a subquery in the PPM overdue count, are the first thing to exercise). Please run `mvn test` and the FM and facilities integration paths once.
