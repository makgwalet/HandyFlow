# Facilities Management (FM provider) — backend audit, 2026-10-27

Scope: the `facilitiesmanagement` backend module (contract coverage, SLAs, billing, credit notes, notifications, client portal).
**Not covered:** the internal `facilities` module has not been reviewed or changed (see "Not done").

## Migrations

| Version | Author | What |
|---|---|---|
| V502–V509 | you | facilities / FM tables (unchanged) |
| V510 | this pass | `facility_work_orders` lifecycle columns + `facility_work_order_cost_lines` (V505 was a copy of V504; these objects had no migration). Idempotent. |
| V511 | this pass | client/supplier VAT + address, invoice party snapshot, `credited_amount`, payment `idempotency_key`, `fm_credit_notes` |
| V512 | this pass | `fm_work_orders.requested_by_portal_user_id` + partial index (client portal requests) |
| **V513** | next free | |

Both new migrations were run twice against PostgreSQL 16 (idempotency) and every FM entity was checked against the resulting schema (0 mismatches).
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
- Stale comment in the auth controller removed: `/portal/auth/**` is already permitted and rate-limited.
- Not changed, worth a look: `bookkeeping` has a portal controller using the same `TenantContext.getTenantIdAsObject()` pattern (not verified, not touched).

## Built (from your roadmap)

- **Credit notes** (SA VAT Act s21): prepare → approve by a different person (admin override audited) → ledger posting after commit with retry; reduces the invoice balance; PDF "Tax Credit Note"; counted against client profitability. Endpoints under `/invoices/{id}/credit-notes` and `/credit-notes/{id}`.
- **Tax invoice content** (s20(4)): client and supplier VAT numbers (10 digits, starts with 4), supplier/client name and address snapshotted onto the invoice at issue, recipient name/address required above R5,000, "Tax Invoice" title only when the supplier is VAT-registered.
- **Payment double-submit protection**: `Idempotency-Key` header (or body field) replays the original result; duplicate bank reference on an invoice is a 409 `DUPLICATE_PAYMENT`; concurrent payments are stopped by the invoice's optimistic lock.
- **Notifications**: `FM_WORKORDER_REQUESTED`, `FM_WORKORDER_AWAITING_VERIFICATION`, `FM_INVOICE_AWAITING_APPROVAL`, `FM_CREDIT_NOTE_AWAITING_APPROVAL` (plus the two SLA types above).

## Not done (honest list)

Portal quote approvals and client work summary (self-service requests, credit notes and PDFs are done); cancelling a submitted request from the portal; a client-facing reason for a declined request; showing the SLA to clients; site and client scope for vendor and technician records and audit on vendor changes; inspection templates, corrective actions, compliance action centre; dashboard KPIs, vendor performance and asset reliability reports; mobile/QR/offline; inventory and procurement; vendor onboarding with insurance/licence tracking; multi-entity portfolios and integrations. None have migrations or code. The internal `facilities` module was not reviewed.

## Known limitations (carried over from your list, unchanged)

No browsable agreement version history; a priority raised at approval takes the agreement's *current* target; "response" means first assignment only; escalation stops at tenant admins; no call-out/after-hours fees, service credits for breaches, or client-visible SLA status; retainers bill a flat fee regardless of period length; the SLA monitor loads all open SLA work orders every 10 minutes.
New: an approved-but-unsent invoice that is fully credited shows to the client as CREDITED (with its credit note); a staff-side flag for portal-raised requests is not in the work-order response yet (staff see reportedBy "Client portal" and the audit entry). Credit notes only reduce what is still owed — refunding money already paid is out of scope. `findOverdueAcrossTenants` uses the database `CURRENT_DATE` rather than the SA date.

## Tests — what was and was not executed

Maven Central is unreachable from the build sandbox, so **no Spring or Mockito test was run here**. To still execute logic, the domain and service tests were written as plain JUnit with hand-written fakes and run on a stub harness: **268 tests passed**, and deliberate breaks of key rules (credit ignored in balance, holiday skipping, R5,000 threshold, VAT back-out, site overlap, idempotency, duplicate reference) were all caught.

- Executed (harness): FmInvoice, FmCreditNote, FmVatNumber, FmWorkOrder, FmServiceAgreement, FmInvoicePayment, FmSlaClock, FmSlaStatus, FmPriorityRules, FmBillingSettings, FmTime, FmGlJournals, and the services FmContractTermsService, FmServiceAgreementService, FmCreditNoteService, FmBillingService, FmWorkOrderCostService, FmSlaMonitor, FmPortalDataService, FmPortalRequestService, FmPortalStatus.
- **Written but not executed**: FmBillingControllerTest and FmWorkOrderControllerTest (`@WebMvcTest` + `@MockitoBean`); the record constructors they use were compile-checked. Pre-existing FmAssetTest and FmPpmScheduleTest pass unchanged.
- **Not covered**: the portal controller (no test), FmPortalService invite/revoke and FmPortalAuthService, FmGlPostingService, FmPdfService, FmWorkOrderService, FmAccessPolicy, FmAlerts (collaborators were faked), and JPQL queries (only checked by schema/entity comparison). Please run `mvn test` and the FM integration path once.
