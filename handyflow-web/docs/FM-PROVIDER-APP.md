# FM provider app — specification (not built yet)

Status: **documented only, 2026-10-27.** No code. Decision recorded: build it as a **mobile-first web app inside
`handyflow-web`** (own layout, no main sidebar shell), not a native app. Revisit native only if background GPS or reliable
push become hard requirements.

Sources: the FM backend (`/api/v1/facilitiesmanagement`, see `FM-BACKEND-AUDIT-2026-10-27.md`) and the mocked screens
supplied on 2026-10-27. **The mockup images were not re-read when this was written**, so layouts below come from what the
backend supports; check every screen against the mockups before building and note differences here.

## 1. Who it is for

The **FM provider** is the facilities-management company that looks after sites on behalf of its clients. The provider app is
for that company's own people, one login, different authorities:

| Role | Authority (backend) | What they see |
|---|---|---|
| Field technician | `FACILITIESMANAGEMENT_TECHNICIAN` (no MANAGE/READ) | Only jobs assigned to the technician record linked to their user, and those jobs' sites. No cost, no pricing, no finance. |
| Dispatcher / coordinator | `WORKORDER_ASSIGN`, `WORKORDER_CREATE` | All work, assign and triage, raise work for a client. |
| Account / contract manager | `MANAGE`, `READ`, `SLA_MANAGE`, `CONTRACT_MANAGE` | Everything operational, SLA and contract views. |
| Finance | `BILLING_MANAGE`, `BILLING_APPROVE`, `COST_READ` | Invoices, credit notes, margins. |
| Admin | `ADMIN` | All of the above. |

Not for client users: clients use the **client portal** (separate spec, separate token, tenant-less). Never return
staff-facing DTOs (`FmWorkOrderResponse`) to portal users.

The app shows only what the user's authorities allow; the server enforces it regardless. Hide, do not rely on hiding.

## 2. Two experiences, one app

1. **Field view (phone, one-handed, gloves, poor signal).** Technicians. Large touch targets, one job at a time.
2. **Office view (tablet/desktop).** Dispatchers and managers: the same data in lists, boards and panels. The existing
   internal Facilities module (`/facilities`) is the visual and component reference.

Routes: `/fm` (landing by role), `/fm/jobs`, `/fm/jobs/:id`, `/fm/dispatch`, `/fm/clients`, `/fm/sites`, `/fm/assets`,
`/fm/ppm`, `/fm/billing`, `/fm/reports`, `/fm/settings`. Technicians land on **My jobs**; managers on **Today**.

## 3. Screens

### Field
- **My jobs**: today, upcoming, overdue. Card: job number, client and site, priority, SLA state chip (on track, at risk,
  breached, paused), scheduled date, short description. Pull to refresh. Filter by status.
- **Job detail**: client, site, asset, safety flag, SLA clock (resolution due, response due), description, history of
  holds/rework, evidence, cost lines (if allowed). Sticky action bar by status:
  - ASSIGNED → **Start**
  - IN_PROGRESS → **Hold** (reason), **Complete**
  - ON_HOLD → **Start** (resume)
- **Complete job**: notes, completed-at (default now; server rejects future times and times before creation, 5-minute skew),
  downtime minutes, failure cause, cost only if no itemised lines. Warn if no after-photo yet.
- **Evidence**: take or choose photos (before/after/other), upload with progress, list with thumbnails.
- **Job card**: link to the PDF (fetch with the auth header, then open as a blob; a bare link will not carry the token).
- **Asset / QR**: scan asset QR to open the asset, its open jobs and PPM due. See gap G4.

### Office
- **Today** (manager landing): `GET /reports/dashboard`. Open by status and priority, unassigned, on hold, overdue, awaiting
  approval, awaiting verification, SLA at risk / breached, 30-day throughput. Finance tiles only with finance authority.
- **Requests queue**: client portal and staff requests in REQUESTED. Approve (set priority, SLA applies), decline (internal
  reason plus optional message the client sees), flag duplicates.
- **Dispatch**: unassigned and overdue work, assign to technician or subcontractor vendor. A technician or vendor picker
  shows who is active. Hold with **pause SLA** only for users with SLA_MANAGE/MANAGE/ADMIN.
- **Verification**: completed work awaiting verification: verify or send back (reason, rework count shown).
- **Raise work**: client, site (must belong to the client), optional asset, description, category (CORRECTIVE/EMERGENCY/OTHER;
  PPM cannot be requested), safety impact, priority resolved by rules.
- **Clients, sites, assets**: registers with create/edit; client deactivate/reactivate; site close/reopen; asset status.
- **Service agreements and SLA**: resolve terms for a client/site; view coverage; SLA targets; business calendar and holidays.
- **PPM**: schedules per asset; overdue list.
- **Billing**: invoices (generate for a client, approve, send, record payment with idempotency key, cancel), payments,
  credit notes (prepare, approve by a different person), PDFs, GL exceptions and retry. Finance authority only.
- **Reports**: SLA performance and portfolio, vendor performance, asset reliability, client profitability (cost-gated).
- **Technicians and vendors (subcontractors)**: list, create, edit, deactivate; link technician to a user.
- **Settings**: provider profile (name, VAT, address, billing), portal users (invite/revoke per client), site access admin.

## 4. API map (all under `/api/v1/facilitiesmanagement`)

| Need | Endpoint |
|---|---|
| Jobs list | `GET /work-orders?clientId&status&page&size&sort` (technician scope applied server-side) |
| Job | `GET /work-orders/{id}` |
| Raise | `POST /work-orders/requests` |
| Triage | `POST /work-orders/{id}/approve`, `/reject`, `/verify`, `/rework` |
| Assign | `POST /work-orders/{id}/assign` |
| Run | `POST /work-orders/{id}/start`, `/hold`, `/complete`, `/cancel` |
| Evidence | `POST` and `GET /work-orders/{id}/evidence` (multipart: `file`, `evidenceType`) |
| Job card | `GET /work-orders/{id}/job-card.pdf` |
| Clients | `GET/POST /clients`, `PUT /clients/{id}`, `/deactivate`, `/reactivate` |
| Sites, assets | `/sites`, `/assets` (CRUD, close/reopen, status, asset evidence) |
| PPM | `GET /assets/{id}/ppm-schedules`, `/ppm-schedules` CRUD |
| Agreements | `/service-agreements` (`/resolve`, `{id}`, `/end`), `/sla/calendar` |
| Technicians, vendors | `/technicians`, `/vendors` |
| Reports | `/reports/dashboard`, `/vendor-performance`, `/asset-reliability`, `/sla-portfolio`, `/sla-performance`, `/client-profitability` |
| Billing | `/invoices`, `/invoices/{id}/approve|send|cancel|payments|invoice.pdf`, `/clients/{id}/invoices/generate`, `/credit-notes`, `/billing/gl-exceptions` |
| Profile, access | `/profile`, `/access`, `/clients/{id}/portal-users` |

Response envelope is `{ success, data }`; lists are Spring pages (`content`). Work order DTO: `FmWorkOrderResponse`
(includes `responseSla`, `resolutionSla`, `resolutionDueAt`, `slaPausedAt`, `coverage`, `internalCost` only with cost
authority). Technicians must never see `internalCost` or pricing; the server omits it.

## 5. Business rules the UI must respect (server enforces all)

- Nothing is billable until a request is approved. Declined requests carry an internal reason and an optional client message.
- Priority on portal requests is never client-set. Safety impact → HIGH, emergency → EMERGENCY, confirmed at approval.
- SLA: response means first assignment; resolution is technician completion. PPM has no SLA. Pausing the SLA on hold needs
  SLA_MANAGE/MANAGE/ADMIN and is audited. Clock resumes on restart.
- Verification and rework: jobs flagged `requiresVerification` go to verification; sending back increments `reworkCount`.
- Segregation of duties: the person who prepared a credit note or invoice approval cannot approve it (admin override audited).
- Cost lines: types LABOUR, PARTS, TRAVEL, CONTRACTOR, OTHER. Setting the client charge or marking lines non-billable is
  manager/admin only (403 `COST_LINE_PRICING_RESTRICTED`).
- Completion time cannot be in the future or before creation.
- All dates and clocks are Africa/Johannesburg.

## 6. Backend gaps found while writing this (must be decided before or during the build)

| # | Gap | Impact | Suggested handling |
|---|---|---|---|
| G1 | **No REST endpoint for FM work-order cost lines.** `FmWorkOrderCostService` exists (add/remove, pricing restrictions, tests) but no controller exposes it (checked 2026-10-27). | Technicians cannot log labour, parts or travel; dispatchers cannot cost a job; billing has nothing itemised. | Add `GET/POST/DELETE /work-orders/{id}/costs` (mirror the internal module). **Do first.** |
| G2 | **No technician "my profile / my day" endpoint.** Scope is applied implicitly to the list; there is no explicit "who am I as a technician" call. | App cannot show the technician's name or handle an unlinked user gracefully. | Add `GET /technicians/me` (404 with a clear code when the user is not linked). |
| G3 | **No FM offline sync or change feed.** Only the internal module has `/sync` and `/sync/work-orders`. | Field use with poor signal needs queued start/hold/complete. | Port the internal sync design to FM (op ids, per-op transactions, 50 ops, duplicate-safe). |
| G4 | **No FM QR lookup.** Internal has `/assets/lookup` and `/assets/{id}/qr`. | Scan-to-open and print labels not possible for FM assets. | Port lookup and QR payload text. |
| G5 | **Quotes do not exist.** The mockup name suggests quotes; there is no `FmQuote` entity or approval flow (listed under "portal quote approvals, not done"). | Quote screens and client approval cannot be built. | Treat as a separate backend feature; app spec leaves a placeholder. |
| G6 | **No check-in / arrival / GPS capture.** No endpoint or fields. | "Check in on site" cannot be real. | Decide if required. Simple version: add `arrivedAt` plus optional lat/long to start. A web app can read the browser location with consent, foreground only. |
| G7 | **Evidence upload is online only**, and file type and size limits are whatever the evidence module enforces. | Photos fail on a bad connection. | Queue uploads in the browser, retry with backoff; confirm the limits and surface them in the UI. |
| G8 | **FM vendor and technician records have no site/client scope** and changes are not audited (listed as not done). | Fine for a first release; matters for multi-branch providers. | Backlog. |
| G9 | **Report exports and trends** do not exist. | Reports are point-in-time only. | Backlog. |
| G10 | Documents for FM vendors (insurance, COIDA, tax clearance) exist for the internal module only. | Subcontractor compliance cannot be shown or enforced for FM. | Port the onboarding design when subcontractors matter. |

## 7. Frontend design

- **Location:** `src/pages/fm/` with `FmLayout` (no shell sidebar; bottom tab bar on phones: Jobs, Today, Scan, More; top bar
  on larger screens). Registered as `/fm/*` in `App.tsx`; module key `facilitiesmanagement` (check the key returned by
  `/api/v1/billing/modules/mine`).
- **API client:** `src/api/fm.api.ts`, same `data()`/`page()` unwrap helpers as `facilities.api.ts`.
- **Reuse:** `pages/facilities/ui.tsx` pieces (Dialog, Field, Btn, DataTable, chips) after moving them to a shared folder;
  token colours only (`var(--hf-*)`), light and dark.
- **State:** TanStack Query; optimistic updates only for the field actions that are queued offline (later phase).
- **Mobile:** 44px minimum touch targets, no hover-only controls, forms use the right input types (`inputmode`, `capture`
  for camera), sticky action bar, safe-area padding.
- **Offline (phase 3):** service worker caches the shell and the technician's job list; a queue stores start/hold/complete
  and uploads with `opId`s; show a visible "queued, not yet sent" state. Needs G3.
- **Installable:** web app manifest and icons so it can be added to the home screen.
- **Accessibility:** labelled controls, focus management in dialogs (already in `Dialog`), colour not the only signal for SLA
  state (text plus icon).
- **Testing:** install vitest and Testing Library first (not installed today). Test the status-to-actions mapping, SLA chip
  logic, permission gating and the completion form validation.

## 8. Phases

1. **Backend first (small):** G1 cost endpoints, G2 `technicians/me`. 
2. **Field MVP:** My jobs, job detail, start/hold/complete, evidence upload, job card PDF, installable.
3. **Office core:** Today dashboard, requests queue, dispatch, verification, raise work.
4. **Registers and billing:** clients, sites, assets, PPM, invoices, credit notes, reports.
5. **Offline and QR:** G3, G4, service worker, upload queue.
6. **Later:** quotes (G5), check-in (G6), exports (G9).

## 9. Open questions for the owner

1. Are quotes and on-site check-in required for the first release? (Both need backend work.)
2. Do subcontractor vendors log in to this app, or are they managed by the provider's staff only? (Today vendors are records, not users.)
3. Should technicians see client contact details and site access notes? (Not currently exposed to the technician view.)
4. Any need for push notifications? (Web app can email and show in-app; true push on iOS needs install to home screen.)
5. Branding: tenant colour only, or provider logo on the job card and app header?
