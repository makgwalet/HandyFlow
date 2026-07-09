# HandyFlow Platform — Complete Gap Analysis
**Compiled:** July 2026  
**Scope:** All modules reviewed across backend (Java/Spring Boot) and frontend (React/TypeScript)  
**Basis:** Live source code review — controllers, services, domain models, schedulers, frontend pages

---

## Executive Summary

HandyFlow is a multi-vertical business operating system targeting South African SMEs. Across thirteen modules reviewed, the platform has strong domain-specific foundations — particularly in Security (guard auth, armoury compliance, patrol rounds), HR/Payroll (correct SA tax law implementation), Accountant (SARS deadline engine), and Clinic (HPCSA/Medicines Act compliant PDFs). The Security module's `NotificationService` + `TenantAdminRecipients` pattern, introduced in the most recent review cycle, is the most important piece of shared infrastructure in the codebase and should be propagated to every remaining module immediately.

**The three highest-leverage cross-cutting gaps across the entire platform:**

1. **Notification infrastructure** — partially built in Security and Billing; missing or broken in Projects, SCM, Fleet, HR, Clinic, Desk, Creative, POS, and Earthmoving.
2. **PDF generation** — inconsistent across modules. Security (3 PDFs), HR (payslip + EMP201), Clinic (medical certificate + prescription), Projects (4 PDFs) and Accountant (fee note via template only) have real implementations. SCM, POS, Fleet, Creative, and Earthmoving have zero.
3. **Module tier architecture** — every module is binary (on/off). Small operators (taxi owner, cash-in-transit company, 3-guard estate) need entry-level tiers without paying for enterprise features they don't have yet.

---

## Cross-Cutting Gaps

### 1. Shared Notification Service

**Status:** Built in Security module (`NotificationService` + `TenantAdminRecipients` + `REQUIRES_NEW` transaction isolation). Missing or broken in all other modules.

**Confirmed false promises in UI (not just gaps):**
- `FleetPage` registration: *"You will be alerted 60, 30, and 7 days before any document expires"* — no scheduled job sends anything
- `DeskTab` reply box: *"Sends email notification to requester"* — unverified; likely aspirational copy
- `CreativeService`: *"Client has been notified"* / *"Designer has been notified"* — unverified; `PdfService.java` absent from review

**Events that must fire but don't (by module):**

| Module | Missing notification events |
|--------|----------------------------|
| Projects | Task BLOCKED, change order REJECTED, RFI response received, snag assigned |
| SCM | PO rejected, invoice disputed, PO sent to supplier (actual email, not just status flip), payment confirmation to supplier |
| POS | Large refund/void above threshold, cash session closed with variance, low stock, Z-report daily digest |
| Fleet | Licence/roadworthy/insurance expiring (promised but not sent), service due, breakdown reported, active trip running long |
| HR | Payslip distribution on pay run approval, leave approved/rejected, EMP201 due |
| Clinic | Appointment confirmation, appointment reminder (24h + 2h), lab result ready (`isNotified` field exists but never set to `true`), claim rejected |
| Security | CRITICAL incident created (no immediate alert), armoury issue/return outside hours, shift swap approved/rejected |
| Desk | New ticket alert to team, customer reply received, SLA approaching breach (currently only breach-after-the-fact badge), assignment notification |
| Creative | Proof opened by client, unapproved proof reminder after N days, deliverable uploaded |
| Earthmoving | Breakdown reported, service due, hire period expiring |

**Recommended fix:** Adopt the Security module's `NotificationRequest` builder pattern platform-wide. All modules should inject `NotificationService` and `TenantAdminRecipients`. Build once, propagate — not per-module implementations.

---

### 2. PDF Generation

**Current state by module:**

| Module | PDFs that exist | PDFs missing (confirmed) |
|--------|----------------|--------------------------|
| Security | Site Coverage, Guard Attendance, Monthly Summary | Incident report (insurance/SAPS), Armoury register (SAPS inspection), Guard payroll batch, Shift schedule (site posting) |
| HR/Payroll | Payslip (professional, masked bank), EMP201 | IRP5 (most critical year-end doc), UIF-19 termination, Disciplinary warning letter |
| Clinic | Medical certificate (DoH compliant), Prescription (Medicines Act compliant) | Referral letter (daily volume), Patient account statement, Medical aid claim summary |
| Accountant | None (templates only — service file not provided) | Fee note/tax invoice (SA B2B legal requirement), SARS deadline calendar per client, Debtors aging report, Journal sign-off |
| Projects | Risk register, Site diary, Snag list, Change order | Project status report (client-facing) |
| SCM | None | PO PDF (blocks supplier email send), GRN PDF, Remittance advice, Supplier invoice approval pack |
| POS | None | Receipt PDF (data already HTML-rendered), Z-report PDF, Tax invoice (SARS compliant) |
| Fleet | None | SARS logbook PDF/Excel (data model complete — highest value miss), Vehicle compliance certificate, Service history |
| Earthmoving | None | Incident report, Maintenance/service history, Deployment delivery note |
| Creative | None | Approval certificate PDF (IP+timestamp captured — just not packaged), Job summary/brief |
| Billing | None | Subscription tax invoice (SARS compliance), Payment receipt |
| Identity | None | None required |
| Desk | `.txt` export only | Ticket thread PDF transcript |

**Recommended fix:** Standardise on iText 7 (already used in Security, HR, Clinic) or OpenPDF (used in Projects). Build a `PdfExportController` pattern per module (Projects has this — reuse it). Priority order: PO PDF (SCM/POS — unblocks supplier communication), IRP5 (HR — year-end legal requirement), Fee note/tax invoice (Accountant), Referral letter (Clinic), SARS logbook (Fleet), Incident report (Security/Earthmoving).

---

### 3. Module Tier Architecture

**Problem:** Every module is binary — a small security company pays the same as a large one, and gets armoury features they don't need. A taxi owner pays the same fleet price as a 50-vehicle logistics operation. This creates pricing resistance at the SME entry point.

**Recommended tier structure (applies to all industry modules):**

```
STARTER → CORE → PRO → ENTERPRISE
```

**Priority modules for tier rollout:**

**Security**
- STARTER R199/mo: Guard register, manual shifts, basic incidents, PSiRA tracking. Max 25 guards.
- CORE R399/mo: QR patrol verification, control room dashboard, client portal, SAPS incident export. Max 200 guards.
- PRO R699/mo: Armoury (Firearms Control Act compliance), biometric integration, SLA reporting, PrDP tracking. Unlimited.
- ENTERPRISE R1,299/mo: Multi-branch, CCTV integration hooks, bulk payroll export.

**Fleet**
- STARTER R149/mo: Vehicle register, manual odometer, SARS logbook, service reminders, compliance expiry alerts. Max 10 vehicles.
- CORE R299/mo: Driver profiles (PDP tracking), cost-per-km reporting, accident/incident reporting. Max 50 vehicles.
- PRO R549/mo: GPS telematics integration (Cartrack/MiX API), live location, geofencing, driver behaviour scoring. Unlimited.
- ENTERPRISE R999/mo: Multi-depot, fuel card integration, predictive maintenance.

**Earthmoving**
- STARTER R249/mo: Asset register, manual hour meter, service due alerts, deployment log. Max 10 assets.
- CORE R499/mo: Maintenance records, fuel consumption, site cost allocation, breakdown incidents. Max 50 assets.
- PRO R899/mo: Telematics integration (Prolec/Trimble/CAT VisionLink), pre-start checklists, operator certification. Unlimited.
- ENTERPRISE R1,799/mo: Mine health & safety (DMR Act), OEM warranty tracking, depreciation planning.

**HR/Payroll**
- STARTER R199/mo: Employee register, leave management, basic fixed salary payroll, PAYE/UIF/SDL, digital payslips. Max 30 employees.
- CORE R399/mo: Recruitment pipeline, performance reviews, disciplinary records, employee self-service. Max 100 employees.
- PRO R699/mo: Variable pay (overtime, commissions, 13th cheque), EE reporting, SETA tracking. Unlimited.
- ENTERPRISE R1,299/mo: Multi-entity payroll, SARS e@syFile integration, benefits administration.

**Accounting**
- LITE R199/mo (for business owners, not accountants): Basic chart of accounts, bank reconciliation, management accounts, VAT return prep.
- PRO/Practice R649/mo: Full accountant module as built — client portfolio, SARS deadline engine, fee notes, journal workflow, FICA.

**Technical change required:** Replace `List<String> moduleKeys` on `TenantCreatedEvent` and tenant module activation with `Map<String, String>` (moduleKey → tier). Add `module_tier_config` table seeded per module. `EntitlementService.isFeatureEnabled(moduleKey, featureKey)` replaces binary `isModuleActive()`.

---

### 4. Discount and Pricing Engine

**Current state:** No server-side pricing engine. Module prices are hardcoded in `RegisterPage.tsx` (`monthlyPrice` on each `ALL_MODULES` entry). No volume discounts, no pack discounts, no promo codes.

**Recommended structure:**

Volume discounts (auto-applied):
- 3+ paid modules: 10% off total
- 5+ paid modules: 15% off total

Named packs (beat volume discount to drive full-pack adoption, applies to pack modules only):
- Industry Pack (Security + Fleet + Fuel + Earthmoving): 20% off
- Finance Pack (Accounting + AP + Invoicing + Expenses): 20% off
- People Pack (HR + Recruiter + Desk): 20% off
- Agency Pack (Creative + Contracting + Tasks + Marketing): 20% off

Promo code model:
```sql
promo_codes (
  code, type (PCT/FIXED/MODULE_FREE/TRIAL_EXTENSION),
  value, max_redemptions, redeemed_count,
  valid_from, valid_until,
  applicable_to JSONB,
  created_by, tenant_id (null = global)
)
```

Centralise in `PricingEngine.calculate(tenantId, moduleKeys, tier, promoCode)` returning `PricingBreakdown` with line items, discount applied, subtotal, VAT (15%, always explicit), and total. Never compute pricing in the frontend.

---

## Module-by-Module Gap Analysis

### Identity & Authentication

**Confirmed bugs:**
- `AuthResponse.expiresIn` hardcoded to `86400L` regardless of `JwtService` configured `expirationMs` — these must match
- `LoginPage.tsx` reads `data.subscriptionStatus` to route to `/account-locked` but `AuthResponse` record has no `subscriptionStatus` field — the lock screen never triggers from login
- `TenantDomainService.java` and `UserDomainService.java` are empty stub classes (4 lines each)

**Session security gaps:**
- 24-hour access tokens with no refresh mechanism and no revocation path. Industry standard: 15–60 minute access token + 7–30 day httpOnly refresh token with server-side tracking and rotation
- No device fingerprinting — the same token works from any browser/device
- No concurrent session limit enforcement
- Guard module correctly implemented revocable `GuardToken` records — user sessions need the same treatment

**Registration gaps:**
- No `cipcRegNumber` (CIPC company registration), `entityType`, or `tradingName` on `Tenant` entity or `RegisterRequest`
- No email confirmation on registration — `AuthService.register()` fires no email
- No welcome email — `BillingEventHandlers` listens to `TenantCreatedEvent` but sends nothing
- Phone number is collected in `RegisterPage.tsx` but not in `RegisterRequest.java` — silently dropped
- Password-reset email uses raw inline HTML, not the branded `EmailTemplates.wrap()` pattern
- Invitation email uses raw inline HTML, not `EmailTemplates` — missing company name, inviter name, role name
- No password-changed confirmation email after successful reset

**Billing communication routing gap:**
- `TenantAdminRecipientsImpl` sends ALL billing communications to all active users (up to 5, ordered by `created_at`). A security guard supervisor and a clinic receptionist should not receive the subscription invoice. Need: `billing_email` + `billing_contact_name` on `Tenant`, `receives_billing_comms` boolean on `User`, three-tier routing: (1) `billingEmail` for financial comms, (2) module-permission-holders for operational alerts, (3) specific user only for auth/security events.

**POPIA compliance:**
- No explicit consent capture at registration (agreed to terms checkbox exists but no granular data processing consent)
- No data retention policy enforcement — no scheduled purge of deleted tenant data
- No data subject request (DSR) workflow — right to access, right to erasure
- No POPIA officer designation field on `Tenant`
- No audit log of data access (who viewed a patient file, who exported a payroll run)

**Missing email templates (should be added to `EmailTemplates.java`):**
- `registrationConfirmation()` — welcome + slug reminder + getting-started checklist
- `userInvitation()` — branded, includes company name, inviter, role, 72h expiry
- `passwordResetRequest()` — branded version replacing inline HTML
- `passwordChanged()` — security confirmation after successful reset
- `accountSuspended()` — currently `notifySuspended()` in Billing is a stub
- `pilotExpiredNoUpgrade()` — sent at pilot end before data retention period
- `newDeviceLogin()` — future, pairs with device fingerprinting

---

### Billing & Subscription

**Confirmed bugs:**
- `notifySuspended()` in `SubscriptionService.suspendGraceExpired()` is a stub — logs "suspension notification queued" but sends nothing. Tenants are suspended with zero communication.
- `getCancelPreview` creates a brand-new `TenantModule` (activatedAt = now) to call `calculateEndOfBillingPeriodPublic()`, returning wrong "access until" date for existing tenants
- `BillingScheduler` directly injects `ApService` and `DeskService` for their unrelated scheduled jobs — inverts module dependency direction; these modules should own their own schedulers

**Missing features:**
- No payment gateway integration — `markPastDue`/`reinstate` are manual ops-triggered API calls. No Stripe/PayFast/Peach Payments/Paystack webhook
- No card-on-file / payment method management
- No proration on mid-cycle plan or module changes
- No annual billing option
- No dunning sequence — one grace-period warning and then silence until suspension
- No VAT handling on plan pricing — `priceInRands` is a flat integer with no VAT split
- No subscription tax invoice PDF — `BillingPage.tsx` shows no invoice history
- No pilot-expiring-soon email sequence (D-45, D-15, D-7, D-3) — `pilotCountdown` template exists in `EmailTemplates` but is never called
- No module-trial-expiring notification (60-day module trials end silently)
- No self-service billing portal — plan upgrades/downgrades require ops intervention

---

### Projects

**Resolved since original audit:** 4 PDFs via `PmPdfService`, real notifications via `PmNotificationService`, scheduler at 07:00/08:00 SAST, EVM `planPct` fixed from hardcoded 50, N+1 on project list fixed, UUID-as-display-name fixed in 3 controllers, change order → budget line linkage fixed, RFI workflow (DRAFT→SUBMITTED→RESPONDED→CLOSED) implemented, `@Validated`+`@Valid` systematically applied.

**Remaining gaps:**
- `notifyRfiSubmitted`/`notifyRfiResponded` methods exist in `PmNotificationService` but `RfiController` has no `PmNotificationService` injection — not wired
- Task BLOCKED status has no notification — no `notifyTaskBlocked()` exists
- Change order REJECTED has no notification — asymmetric with the APPROVED notification which is correctly wired
- Snag assignment has no notification — `FieldController` has no `PmNotificationService` injection
- No project status report PDF (the most commonly needed client deliverable — EVM + budget + risks + milestones on one page)
- Change order PDF exists but is not attached to the approval email
- EVM still uses 50% planPct from frontend on some call sites (needs audit)
- No task dependency graph — `isCritical` is a manual boolean, not computed from predecessor/successor links
- No stakeholder registry — stakeholder management is binary (internal team vs anonymous client portal token)
- No earned-value baseline comparison view in Gantt

---

### Supply Chain Management (SCM)

**Resolved since original audit:** 3-way match (MATCHED/PARTIAL_MATCH/DISPUTE/PENDING with 2% tolerance), atomic sequence numbers via `SequenceService`, six UUID-as-display-name fixes, GR line audit trail, PO line qty tracking, inventory upsert race condition fixed, `notifyPoApproved` wired, `countLowStock`/`countOverdue` replacing `.size()` calls, blacklisted supplier guard on PO creation, tenant isolation on inventory fixed.

**Remaining gaps:**
- `notifyPoRejected` method exists but `rejectPurchaseOrder()` never calls it
- `notifyInvoiceDisputed` method exists but `performThreeWayMatch()` never calls it — highest operational impact since disputed invoices need immediate human action
- Low-stock weekly digest is generic ("log in to review") — should list specific item names, quantities, and reorder points
- `markSent` flips status but sends no actual email to the supplier — the PO never reaches the supplier unless manually forwarded
- No PO PDF — blocks the supplier email send (most critical missing PDF in SCM)
- No GRN PDF — proof of delivery for site deliveries
- No remittance advice PDF — sent to supplier on `markPaid`
- No supplier portal (mirroring the Security `ClientPortalService` token-based pattern)
- POS and SCM have parallel, non-integrated purchase order and inventory systems — a sale in POS doesn't deduct from SCM inventory and vice versa. For tenants using both, data silently diverges.

---

### POS

**Resolved since original audit:** Cash session open/close with variance, Z-report, split payment methods, void + refund with stock restoration, barcode scanning, real 3-way PO system within module.

**Remaining gaps:**
- `paymentMethod: 'CARD'` records a manually typed reference — no Yoco/SnapScan/Zapper/PayFast integration; card reconciliation is trust-based
- VAT hardcoded to 15% in frontend — zero-rated items (basic foodstuffs) not supported, violating SA VAT Act for qualifying retailers
- Receipt HTML rendered but `window.print()` only — no PDF attachment, no email send despite controller doc comment saying "for printing or emailing"
- No Z-report PDF — end-of-day compliance record
- PO PDF missing — second instance of PO-with-no-document gap
- No customer/loyalty profile — `customerName` is free text with no linked record
- No multi-till/multi-register support — sessions appear to be one-per-tenant
- No offline mode — requires live API for every sale
- POS `Supplier` is free-text `supplierName`; no link to SCM `Supplier` entity — two separate supplier registries
- POS and SCM inventory are completely separate — see SCM gap above

---

### Fleet

**Confirmed false promise:** Registration modal states "You will be alerted 60, 30, and 7 days before any document expires." Full `FleetService.java` confirmed — no scheduler, no email, no notification. This is a live misleading claim in production UI.

**Remaining gaps:**
- No notification infrastructure despite the promise
- SARS logbook PDF/Excel export — data model is complete (Business/Private trip classification, odometer readings, dates, purposes), highest-value missing PDF in this module
- No GPS/telematics integration — odometer is entirely manual
- No driver record entity — `driverName` is free text with no PDP/licence expiry tracking
- No cost-per-km reporting despite having all source data (service cost + fuel cost + distance from trips)
- Vehicle compliance certificate PDF missing
- Service history PDF missing
- Trip running-long alert missing (forgotten trip-end is a real data-quality risk with manual start/end)

---

### Earthmoving

**Confirmed bug:** `IncidentsTab.tsx` uses `const [incidents, setIncidents] = useState<any[]>([])` — no `EarthIncident` entity, repository, or controller endpoint exists. Every incident report is React component state only. Refresh the page and all incident history is gone. This is the highest-priority fix in the Earthmoving module.

**Remaining gaps:**
- No GPS/telematics integration — `currentHours` is manually typed
- No utilisation analytics — no idle-time vs productive-time reporting
- No depreciation/asset value tracking — `dailyRate`/`hourlyRate` are charge-out rates, not book value
- No document attachment per asset (COC, insurance, registration papers)
- No operator certification/licence tracking per machine type
- No pre-start inspection checklist
- No parts/inventory linkage on maintenance records
- Incident report PDF (once persisted) needed for insurance and OHSA
- Maintenance/service history PDF for resale and warranty
- Deployment delivery note PDF for damage-dispute protection

---

### HR & Payroll

**Confirmed bugs:**
- `EmployeeNumberGenerator` uses `COUNT(*) + 1` — race condition under concurrent registration (same pattern fixed in SCM via `SequenceService`; apply same fix here)
- `terminateEmployee()` accepts `reason` parameter but passes only `(tenantId, id, endDate)` to service — reason silently dropped. Termination reason required for EE reporting and UIF documentation.
- `toLeaveResponse` and `toDisciplinaryResponse` call `employeeRepo.findActiveById()` inside a loop — N+1 queries at 50+ employees

**Remaining gaps (payroll correctness):**
- `PayrollEngine` handles fixed monthly salary only — no overtime (BCEA requires 1.5×), no variable pay. `PayslipResponse` has `overtimeAmount`/`bonusAmount` fields but they're always zero
- No IRP5 generation — the most important annual SARS document for employees; all YTD data is already computed and stored
- No EMP501 annual reconciliation
- No SARS e@syFile CSV export format
- No leave pay-out calculation on termination (BCEA requirement)
- No payslip email distribution on pay run approval — most operationally impactful missing notification
- No leave request/approval notification (employee ↔ manager)
- Disciplinary warning letter PDF missing (LRA requires written warnings with acknowledgment)
- UIF-19 termination document PDF missing
- No employee self-service portal
- Security module has its own `SecurityPayrollService` computing shift-based gross pay — this never feeds into HR's `PayrollEngine` for PAYE computation. Guards whose gross pay is computed in Security are not PAYE-taxed unless manually re-entered in HR.

---

### Clinic

**Critical security issue:** `ConsultationSession.tsx` and `LabsTab.tsx` call `https://api.anthropic.com/v1/messages` directly from the browser. The API key is visible to any user in DevTools → Network tab. Must be proxied through backend: `POST /api/v1/clinic/ai/extract-soap` and `POST /api/v1/clinic/ai/interpret-lab`.

**Remaining gaps:**
- `getPayments()` and `getRevenue()` in `ClinicBillingService` are documented stubs returning empty lists — `BillingTab` frontend is complete and waiting for real data
- No appointment confirmation email — `SCHEDULED` status fires nothing
- No appointment reminder — 24-hour pre-appointment reminders reduce no-shows 20–30%
- `ClinicLabResult.isNotified` field exists and is returned in API responses but is never set to `true` — lab result notification is modelled but never sent
- Referral letter PDF missing — highest daily clinical volume document not generated
- Patient account statement PDF missing — needed for outstanding balance collection
- Medical aid claim summary PDF missing for manual submission
- No HealthBridge EDI integration — claims are manually re-keyed into HealthBridge portal by staff; `submitClaim` only flips a status flag
- No patient self-booking portal
- No chronic medication repeat management — `repeats` field is captured but never acted on
- ICD-10 validation exists but tariff rates (NRPL V79) go stale annually in January with no update mechanism
- No SOAP/clinical template per appointment type

---

### Help Desk

**Remaining gaps (from original audit, partially unresolvable without `DeskService.java`):**
- Email-in ticket creation — the biggest functional gap; email is how customers contact support everywhere
- Whether the "Sends email notification to requester" copy is real or aspirational is unverifiable without the service file
- No SLA approaching-breach notification — only breach-after-the-fact badge in UI
- No assignment notification to team member
- No customer-reply notification to assigned agent
- No CSAT survey on CLOSE (data points all exist)
- No canned responses/macros
- No AI-suggested category/priority on ticket creation
- Ticket thread PDF export is `.txt` format — unbranded, unprofessional

---

### Creative

**Confirmed bugs:**
- `CreativeController.fetchUserName()` is a hardcoded stub returning `"Team Member"` regardless of caller. Every team comment on a proof is attributed to "Team Member". One-line fix: inject `UserRepository`, look up `userId`. Same bug class fixed in Desk (`addComment` was previously "Support Agent").
- `fileBase64` sent in JSON POST body for both proof uploads and deliverables — strongly implies files stored as base64 blobs in DB. Base64 inflates size ~33%, bloats DB, and every proof view downloads the full encoded file through the API with no CDN. Must move to object storage (S3/Azure Blob/GCS) before real client volume.

**Remaining gaps:**
- Approval certificate PDF — IP+timestamp+name are all captured as a "legally binding record" but never packaged into a PDF. The legal-weight data exists; it's just not exported.
- No point-and-click annotation on proofs — comments are thread-level with no x/y coordinate or video timecode anchor. This is the single biggest gap vs Frame.io/Filestage.
- Whether `sendProof` actually emails the client is unverifiable without `CreativeService.java`
- No version comparison view between proof revisions
- No multi-stakeholder approval chain (sequential or parallel)
- No time tracking against jobs — no profitability reporting despite having `budget` vs `quotedAmount`

---

### Security

**The most architecturally complete module. Remaining gaps:**

- Incident CRITICAL severity created in `IncidentService.createIncident()` has no immediate `NotificationService` call — a guard files ARMED or ROBBERY incident and nobody in the control room gets a push notification unless looking at the screen
- No guard panic/duress button endpoint — all infrastructure exists (`ControlRoomService`, `IncidentService`, `NotificationService`), just no `POST /api/v1/security/guards/panic` endpoint
- `SecurityPayrollService` computes shift-based gross pay but never feeds into HR's `PayrollEngine` for PAYE — guards processed through Security payroll are not tax-calculated unless manually re-entered in HR
- Incident report PDF missing (insurance claims, SAPS dockets)
- Armoury register PDF missing (SAPS compliance inspections — mandatory)
- Guard shift schedule PDF missing (physical site posting)
- Deployment delivery note PDF missing
- `RotationService.toAssignmentResponse()` calls two repository lookups inside a loop — N+1 at scale
- No PSiRA API verification — PSiRA numbers are stored strings with no live registry check
- Alarm panel webhook endpoint missing — control room `ingest()` endpoint is internal only; third-party alarm panels (DSC/Paradox/Texecom) cannot auto-create `AlarmEvent` records

---

### Accountant

**Confirmed bugs in `DeadlineEngine`:**
- `ITR12` (individual income tax) is documented in Javadoc and listed in frontend `TYPE_COLOR` but has no generation code in `generateForClient()` — individual clients get incomplete deadline calendars
- `IRP6_P3` (third provisional payment) is documented and in `TYPE_COLOR` but not generated
- `CIPC_RETURN` is in `TYPE_COLOR` and Javadoc but not generated
- `FeeNoteNumberGenerator` uses `COUNT(*) + 1` — race condition under concurrent fee note generation (same pattern fixed in SCM, HR; fix using database sequence)

**Remaining gaps:**
- Fee note/tax invoice PDF — legally required for B2B invoicing; `sendFeeNote` endpoint presumably emails something but no PDF generator is visible
- SARS deadline calendar PDF per client — all data is computed; no export
- Debtors aging report PDF — aging buckets computed client-side, no printable version
- Journal entry sign-off PDF — PREPARED→REVIEWED→POSTED workflow has no printable artifact
- No SARS eFiling API integration — all returns are manually submitted
- No EMP501 reconciliation (sum of 12 EMP201s vs sum of all IRP5s)
- No client portal — clients have no self-service view of their upcoming deadlines, outstanding fee notes, or filed return history
- Fee note overdue reminder email to debtor clients — `processOverdueFeeNotes` marks OVERDUE but sends nothing to the client
- Filing confirmation email to client when a return is marked FILED
- FICA/onboarding follow-up email for clients with `ficaCompleted = false`
- Year-end boundary handling for clients with October year-end may compute IRP6 P1 in wrong calendar year (needs verification)

---

## SA Regulatory Compliance Gaps (Cross-Module)

| Regulation | Gap |
|-----------|-----|
| POPIA (Act 4 of 2013) | No consent capture at registration, no DSR workflow, no data retention enforcement, no POPIA officer field, no audit log of personal data access |
| SARS VAT Act | POS hardcodes 15% — zero-rated items not handled; Billing module has no VAT split on subscription invoices; Accountant fee notes have no VAT calculation visible |
| BCEA (Act 75 of 1997) | HR/Payroll has no overtime calculation (1.5×), no leave pay-out on termination |
| LRA (Act 66 of 1995) | Disciplinary warning letter PDF missing — written warnings with acknowledgment are required |
| Firearms Control Act (Act 60 of 2000) | Armoury issue has hard compliance gates (correct) but armoury register PDF for SAPS inspection missing |
| PSiRA Act 56 of 2001 | No live PSiRA registry API verification; deployed guard with expired PSiRA is a live regulatory violation |
| Medicines Act s.22A | Prescription PDF compliant — no gap |
| HPCSA | Medical certificate compliant — no gap |
| OHSA (Act 85 of 1993) | Project risk register is OHSA-tagged; Earthmoving incidents not persisted — safety record gap |
| ECT Act 25 of 2002 | Contracting module correctly cites this in templates |
| CIPC (Companies Act 71 of 2008) | Company registration number not collected at registration |

---

## New Technology Opportunities (Cross-Module)

| Opportunity | Modules | Notes |
|------------|---------|-------|
| Payment gateway (PayFast/Peach Payments/Yoco) | Billing, POS | Removes manual ops dependency in Billing; removes trust-based CARD reconciliation in POS |
| WhatsApp Business API | Fleet, Clinic, Security, SCM, POS | SA market: appointment reminders, guard alerts, PO delivery, receipt send — higher engagement than email |
| SARS eFiling API | Accountant, HR | Most commercially differentiating addition for both modules |
| GPS/telematics API (Cartrack, MiX Telematics) | Fleet, Earthmoving | Eliminates manual odometer/hour entry; unlocks geofencing |
| HealthBridge EDI | Clinic | Dominant SA medical aid claim submission channel; eliminates manual re-keying |
| OCR invoice capture | SCM, Clinic, Accountant | Auto-populate SCM invoice form, extract lab results, capture source documents |
| AI-assisted clinical tools (server-side) | Clinic | Move Anthropic API calls from browser to backend — security fix + enables rate limiting, audit logging |
| Alarm panel webhook (DSC/Paradox/Texecom) | Security | Auto-ingest alarm events into control room without manual phone call |
| Supplier self-service portal | SCM | Token-based, no account required — reuse `SecurityClientPortalService` pattern |
| Client self-booking portal | Clinic, Bookings | Token-based — reuse Creative proof portal pattern |
| Guard panic/duress endpoint | Security | All infrastructure exists; needs one endpoint wired |
| CIPC API verification | Identity | Verify company registration at onboarding |
| PSiRA API verification | Security | Verify guard registration at enrolment |
| Predictive maintenance (Earthmoving/Fleet) | Earthmoving, Fleet | Anomaly detection on cost/frequency history once sufficient data exists |

---

## Priority Matrix

### Must Fix — Bugs / False Promises / Legal Exposure

| # | Issue | Module | Impact |
|---|-------|--------|--------|
| 1 | Earthmoving incidents not persisted | Earthmoving | Data loss — safety/legal record |
| 2 | `notifySuspended()` is a stub — tenants suspended with no email | Billing | Customer trust / churn |
| 3 | "You will be alerted" — Fleet compliance alerts don't fire | Fleet | False promise in production UI |
| 4 | Anthropic API key exposed in browser | Clinic | Security breach risk |
| 5 | `fetchUserName()` returns hardcoded "Team Member" | Creative | Wrong data in production |
| 6 | `EmployeeNumberGenerator` race condition | HR | Duplicate employee numbers |
| 7 | Termination reason silently dropped | HR | Missing data for EE/UIF |
| 8 | ITR12/IRP6_P3/CIPC deadlines missing from engine | Accountant | Wrong compliance calendars for clients |
| 9 | `FeeNoteNumberGenerator` race condition | Accountant | Duplicate fee note numbers |
| 10 | `subscriptionStatus` missing from `AuthResponse` | Identity | Account-lock screen never triggers |
| 11 | `expiresIn` hardcoded to 86400 regardless of config | Identity | Token lifetime mismatch |

### High Priority — Major Functional Gaps

| # | Issue | Module |
|---|-------|--------|
| 1 | PO PDF + supplier email on markSent | SCM |
| 2 | Registration confirmation + welcome email | Identity |
| 3 | IRP5 PDF generation | HR |
| 4 | Payslip email distribution on pay run | HR |
| 5 | Incident report PDF (insurance/SAPS) | Security |
| 6 | Armoury register PDF (SAPS inspection) | Security |
| 7 | SARS logbook PDF/Excel export | Fleet |
| 8 | Referral letter PDF | Clinic |
| 9 | Fee note/tax invoice PDF | Accountant |
| 10 | `notifyInvoiceDisputed` wire-up | SCM |
| 11 | Guard panic/duress endpoint | Security |
| 12 | Receipt PDF + email send | POS |
| 13 | Z-report PDF + auto daily email | POS |
| 14 | VAT zero-rated items in POS | POS |
| 15 | Billing contact designation on Tenant | Identity/Billing |

### Strategic — Platform Maturity

| # | Issue |
|---|-------|
| 1 | Refresh token architecture (15-60min access + revocable refresh) |
| 2 | Module tier architecture (STARTER/CORE/PRO/ENTERPRISE) |
| 3 | PricingEngine service (centralise discount/pack/promo logic) |
| 4 | Promo code model |
| 5 | CIPC registration number capture at registration |
| 6 | POS ↔ SCM inventory unification |
| 7 | Security payroll → HR PAYE bridge |
| 8 | Payment gateway integration (PayFast/Peach Payments) |
| 9 | POPIA compliance framework |
| 10 | Propagate NotificationService pattern to all remaining modules |

---

## Appendix: Email Templates Currently in `EmailTemplates.java`

The following templates exist and are confirmed in the codebase (1,014 lines):

**Existing (confirmed wired):** `quoteExpiry`, `pilotCountdown` (template exists, never called), `invoiceGenerated`, `invoiceGeneratedWithPdf`, `contractSigningInvitation`, `contractFullyExecuted`, `contractTerminated`, `contractDeclined`, `contractAmendmentRequested`, `contractSigningTurnNotification`, `otpSmsText`, `leaseCreated`, `leaseTerminated`, `leaseRenewed`, `rentEscalation`, `rentReceipt`, `rentOverdueReminder`, `taxDeadlineReminder`, `feeNote`, `clientOnboardingWelcome`, `bookingCreated`, `bookingConfirmed`, `bookingCancelled`, `bookingReminder`, `psiraComplianceAlert`, `quoteSentToClient`, `quoteExpiringSoon`, `paymentReceipt`, `invoiceOverdueReminder`, `invoiceOverdueReminderEscalating`, `recurringInvoiceGeneratedWithPdf`

**Missing (should be added):**
- `registrationConfirmation()` — welcome + slug + modules activated + getting started
- `userInvitation()` — branded, includes company name, inviter, role, link
- `passwordResetRequest()` — branded version replacing inline HTML in `PasswordResetService`
- `passwordChanged()` — security confirmation
- `accountSuspended()` — replaces stubbed `notifySuspended()`
- `pilotExpiringSoon()` — call the existing `pilotCountdown()` from `BillingScheduler`
- `labResultReady()` — sets `isNotified = true` in `ClinicLabResult`
- `appointmentConfirmation()` — fires on booking SCHEDULED
- `appointmentReminder()` — 24-hour pre-appointment
- `claimRejected()` — fires when medical aid claim moves to REJECTED
- `guardNoShow()` — already in `NoShowAlertScheduler` but needs template
- `payslipDistribution()` — fires from HR payroll on run approval with PDF attachment
- `incidentCriticalAlert()` — fires from `IncidentService` on CRITICAL/HIGH severity
- `taskBlocked()` — fires from Projects when task → BLOCKED
- `invoiceDisputeAlert()` — fires from SCM 3-way match DISPUTE result

---

*Document generated from live source code review. All gaps are confirmed from actual code, not inferred from UI or documentation.*
