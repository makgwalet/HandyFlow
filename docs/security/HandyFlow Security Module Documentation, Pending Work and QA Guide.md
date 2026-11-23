# HandyFlow Security Module: Documentation, Pending Work and QA Guide

Nov 23, 2026 · @Thabang

The Security module runs a guarding company's operations, from guards, sites and shifts to patrols, incidents, firearms, payroll and client billing. As of 23 Nov 2026 it starts and logs in on your machine, but most of the newest work (chunks 99 to 104) has not yet been checked in the running app.

## 1. Overview and status

**What it is.** HandyFlow's module for private security companies in South Africa: a web back office for managers and a "Shield" phone app for guards. It sits in the Spring Boot platform (`security` package) and the React web app (`pages/security`).

**Who uses it.**

- Company admins and managers: set up sites, branches and regions; schedule guards; approve payroll; read reports.
- Control-room operators and supervisors: watch alarms, incidents, live locations and phone health.
- Site managers and regional managers: see only their own sites or regions (site scope).
- Guards: clock in, scan patrol checkpoints, report incidents, raise duress alarms, collect firearms, swap shifts, all from the Shield phone app.
- Clients: a read-only portal and an emailed monthly service report.

**Where it stands.**

| Area | Status |
| --- | --- |
| Back-end build | Compiles; app boots; login works (log of 23 Nov 11:12) |
| Database | Migrations V858 to V870 applied cleanly |
| Chunks 1 to 98 | Delivered; earlier startup problems fixed (fix-1 to fix-7) |
| Chunks 99 to 104 | Delivered and tested in my test setup; not yet exercised in the running app |
| Guard phone app | Separate app; needs updates for the new phone-posture report |
| Production readiness | Not yet: see pending work and QA sections |

The original audit of 19 Nov 2026 called the module not production-ready (broken guard-app endpoints, data leaks, payroll that could pay twice). Most of those P0 and P1 findings have since been fixed in chunks 1 to 98.

## 2. Features

The module has about 30 feature areas. Each row says what it does and which chunks built it.

| Area | What it does | Chunks |
| --- | --- | --- |
| Guards | Profiles, PSiRA registration, screening and vetting, competency per firearm category, ID and bank details masked (reveal needs a reason), face data card, bulk app access, offboarding closes the open clock-in | 11, 15, 18, 30, 33, 35, 36, 62, 64 |
| Sites, branches, regions | Sites with posts and post orders; branches; regions above branches (two levels) | 94, 102 |
| Scheduling | Shifts, rotation patterns (night shifts fixed), missed and cancelled shifts, swap requests with hard blocks (e.g. expired PSiRA) | 7, 10 |
| Clock-in and sessions | Server-side identity check, 30-minute shift window, device sessions, shared site devices with handover | 6, 19, 29, 32 |
| Patrols | Checkpoints, signed QR scans, location check, patrol rounds | 7 |
| Control room | Duress and CRITICAL alarms that notify a person, dispatches, live map, configurable alert recipients | 5, 8, 40 |
| Incidents | Reporting, events, SLA targets per site (acknowledge and resolve times, breach flags) | 8, 101 |
| Armoury | Firearm register with integrity checks, ammunition accounting, SAPS lost-firearm chase, controlled firearm types | 13, 14, 15, 33, 34 |
| Close protection | Principals, protection details, itineraries, vetting (site scope applied) | 89 |
| Devices and phones | Personal and shared devices; Lost, Revoke, Reinstate; fleet health (silent, flat, old app); alerts; device compliance policy and report | 27, 28, 38, 39, 104 |
| Occurrence Book | Daily OB, supervisor sign-off, read audit, print to PDF | 41 to 45 |
| Coverage and hours | Coverage board, short posts on the dashboard, weekly-hours warnings, working-time policies per company | 46 to 53, 68, 69 |
| Payroll | Periods with no double pay, maker-checker approval, split permissions, pay statements, Sunday and public-holiday rates, company time zone | 4, 20, 21, 74, 99 |
| Billing | Contract rate per site, billing preview (hours x rate), invoice drafts with approval, who saved/approved/voided | 81, 83, 86, 87, 92, 95 |
| Reporting | Monthly service report per site, emailed to the client (monitored contact required), reports screen | 51 to 55, 67 |
| Client portal and API | Client portal link, public API keys, webhooks with signing and retries | 3, 9, 10, 25, 31 |
| Access control | Per-user site scope, regions, branch access, limited-user screens, row-level security, sensitive-read access log | 79 to 97, 100, 102, 57, 63 |
| Time and platform | Company time zone and business calendar, rate-limit sees real client IP, webhook send guard, JWT filter fix | 56, 60, 70 to 78 |
| Single sign-on | Per-company OIDC settings, break-glass users, lock-out protection (API only; no provider hand-off yet) | 103 |
| Load testing | Script with pass/fail budgets and a database volume check | 103 |

### Permissions

- `SECURITY_READ` reads data; `SECURITY_MANAGE` changes it; `SECURITY_ADMIN` administers user access.
- Chunks 21 and 22 split payroll, integrations, firearm issue, screening decisions and bank details out of `SECURITY_MANAGE`, with role templates.
- `SECURITY_GUARD` is the guard-app permission.
- A user with no scope sees the whole company. A user limited to sites or a region sees only those; anything outside answers 404, and company-wide screens answer 403 `SITE_SCOPE_LIMITED`.

## 3. How it is built

| Layer | Where | Notes |
| --- | --- | --- |
| Web back office | `handyflow-web/src/pages/security/` (React, TypeScript, Vite) | One tab per feature on the Security page; logic files hold the rules, panels hold the screens |
| Staff API | `/api/v1/security/**` | JWT users; permission checks with `@PreAuthorize`; module subscription checked with `featureGuard.requireModule("security")` |
| Guard API | `/api/v1/guard/**` and `/api/v1/auth/guard/**` | Guard token; tenant and guard identity come from the token, never the request body |
| Public API | API keys scoped to `/api/v1/security/` | Webhooks with signed, retried deliveries |
| Services | `security/application/internal/` | Pure rule classes (`*Rules`) separate from services that run SQL, so rules can be tested alone |
| Data | Tables named `security_*`; Flyway migrations (mine: V820 to V870) | Money is stored in whole cents (ZAR) |
| Audit | `security_audit_log` for security changes; `access_audit_events` for identity changes | Changes record who, what, and before/after where it matters |

### Tenant isolation, two walls

1. Every query filters by `tenant_id`.
2. Row-level security (chunk 100): the database refuses rows from another company even if a query forgets the filter. It covers the `security_` tables (74 at last run) and takes effect only if the app's database account is **not** a superuser.

### Who sees what (site scope)

- No scope row means the whole company.
- A user can be limited by site, client, branch assignment, or region. A region is read live from its branches, so branches added later are included.
- A region-only user with an empty region sees nothing, never everything.
- Only a whole-company user may change branches, regions, rates and user access.

### Time

The database stores UTC. Each company has a time zone (default Africa/Johannesburg); "today", month boundaries, payroll periods and legal date checks use that zone, not the server's.

### Phone fleet

Phones send a heartbeat (last seen, battery, app version). Fleet health flags silent, never-seen, low-battery and out-of-date phones; a job alerts recipients. Chunk 104 adds a per-company policy (lowest app version, lowest Android/iOS version, screen lock, no rooted phones) and a report, using a new guard endpoint `POST /api/v1/guard/me/device-posture`.

### Sign-in

Chunk 103 added per-company OIDC settings (`tenant_sso_settings`) and a check in `AuthService.login`. When enforcement is on, password login is refused for the listed email domains unless the user is a break-glass user. There is no provider redirect yet, so enforcement must stay off.

## 4. Latest delivery: chunks 99 to 104 and fixes

| Chunk | What it adds | Main limit |
| --- | --- | --- |
| 99 | Sunday and public-holiday pay rates | Holiday source (table vs calculated) still to be decided |
| 100 | Row-level security: a second wall behind the tenant filters | Does nothing if the app connects as a superuser; covers `security_` tables only |
| 101 | Incident SLA targets per site | Targets only; no escalation automation |
| 102 | Regions above branches | Two levels; guards and branch filters not region-aware yet |
| 103 | Load test script with budgets; SSO first slice | SSO has no provider redirect; do not enforce |
| 104 | Device compliance policy and report (report only) | Phones do not send posture yet; nothing is blocked |

**Fixes after your first real start-up.**

- `SsoPolicyServiceTest` did not compile against the real audit class: fixed and re-zipped.
- Three SSO files had been copied into the wrong folder with the wrong package, so Spring saw two `AuthService` classes; remove the strays and copy from the zip.
- Chunk 79 overwrote Fix 3 and brought back duplicate coverage URLs. Fix 7 restores `/coverage-posts` and adds `ControllerMappingsTest`, which fails if two security endpoints share a method and URL.

## 5. Pending work and open decisions

### Build work not yet done

| Item | Why it matters |
| --- | --- |
| SSO provider hand-off and web screen | Without the redirect, SSO enforcement would only block password sign-in. Also needs a domain-ownership check, and cover for guard login, invitations, refresh and password reset |
| Device enforcement | Compliance is report only. Blocking or warning at clock-in is not built |
| Phone posture in the Shield app | The app must call `POST /api/v1/guard/me/device-posture`, or OS, screen-lock and root checks stay "not reported" |
| Limited users: guard list, duress trigger, camera motion webhook, device sessions | These still ignore site scope (decision g) |
| Region awareness | Guard checks and branch filters do not expand regions; two levels only |
| Actual hours payroll | Today pay uses scheduled times; chunk 58 only records the real clock-out (decision j) |
| Close protection permission split | VIP\_DETAIL\_ACCESS and CLOSE\_PROTECTION\_\* roles with audit of VIP reads (decision a) |
| Training provider isolation | Seven evidence items requested (decision c) |
| Integrations | Webhooks and API keys exist; nothing new added beyond them |
| Invoicing hand-off | Drafts exist; no link to Xero, Sage or HandyFlow finance; VAT not calculated |
| Region/alert follow-ups | Posture alerts through the fleet alert job; per-site compliance report |

### Decisions needed from you

- Holiday source for payroll: the `acc_public_holidays` table or calculated dates.
- Invoicing system, VAT, and rates by post type, shift time or grade.
- Whether site managers may read rates, invoice drafts and the armoury register.
- Whether limited users see the full guard list or a minimal picker.
- Load-balancer range for `handyflow.network.trusted-proxies`.
- Face-template retention for active guards without a consent date.
- First sign-in with no registered device: require an activation code?
- What "guards who worked" shows on the monthly summary for limited users (a number today).
- Rule for a company zone change while a payroll period is unpaid.

### Platform items outside the module

- Encryption at rest is off (`encryption=off`); key management is needed.
- Token entitlement enforcement is report-only (log shows `TOKEN-ENTITLEMENT-REPORT`).
- Flyway database sits at version 999 with the latest file at 870; migrations run out of order. Decide the policy.
- iText 7 is AGPL; a commercial licence or a swap to OpenPDF is needed for closed SaaS.
- Git push is blocked: the Claude GitHub App is not installed for makgwalet/handyflow.

## 6. What must be updated on the app

### Code and files

1. Apply the chunks in numeric order, each from its zip into the same relative paths. Later chunks replace earlier copies of the same file. Fix folders go after the chunk they repair.
2. Keep each file's original package and folder. Files from chunk 103 belong in `identity/application/internal/`, not `identity/application/`.
3. Apply `sec-chunk-fix-7` last so the coverage URLs are `/coverage-posts`.
4. Check that production classes (`RateLimitFilter`, `PublicApiService`, `WebhookSendGuard`, `ClientAddress`) are not only under `src/test`. Your `git status` listed them there.
5. Run `mvn clean test-compile`, then `mvn test` (including `BeanNamesTest` and `ControllerMappingsTest`), then start the app.
6. Run the web checks: tests, `tsc`, lint. Only the two old `TanksTab` type errors are expected.

### Configuration and database

- Confirm the app connects with a database role that is **not** a superuser, or row-level security does nothing.
- Confirm production runs in UTC and set each company's time zone.
- Set `handyflow.network.trusted-proxies` to the load balancer's range so the rate limit sees real client addresses.
- Run the chunk 66 script to find demo-data rows (report only first). Move demo migrations out of `db/migration`.
- Decide how to handle Flyway version 999 against a latest file of 870.
- Do not turn on SSO enforcement.

### Guard phone app (Shield)

- Call `POST /api/v1/guard/me/device-posture` with `deviceHardwareId`, `osVersion`, `screenLockOn` and `rooted`.
- Keep sending the heartbeat (battery and app version) about every 5 minutes while open.
- Wire `GuardRequestIdentity` from chunk 1 and confirm the retry-safe queue from chunk 37.
- Test duress, clock-in, scan, incident and swap end to end against the real API.

### Before go-live

- Resolve the iText licence, turn on encryption at rest, and set the entitlement and fleet-alert flags deliberately.
- Close the open decisions in section 5, especially the ones that change money (holidays, VAT, rates).

## 7. How to QA test it

Run the automated tests first, then the manual cases. Record a result for each case and keep screenshots of failures.

### Setup

- A test company with two branches, one region, at least four sites, ten guards, shifts for this week and last week, and one phone per guard type.
- Four logins: **A** whole-company admin; **B** limited to one site; **C** limited to one region; **D** a guard on the Shield app.
- A second company with its own data, to prove companies cannot see each other.

### Automated checks

| Check | Command | Expect |
| --- | --- | --- |
| Back-end compile and tests | `mvn clean test-compile`, then `mvn test` | No errors; includes `BeanNamesTest` and `ControllerMappingsTest` |
| App starts | `mvn spring-boot:run` | `Started HandyFlowApplication`, no ERROR lines |
| Web tests, types, lint | `npx vitest run`, `npx tsc -p tsconfig.app.json --noEmit`, `npx eslint src` | Pass; only the two old `TanksTab` type errors |
| SQL checks | Scripts in `scripts/` (`regions-*`, `sso-migration-check.sql`, `device-compliance-check.sql`, `db-volume-check.sql`) | No exception; each rolls back |
| Load test | `scripts/load/loadtest.mjs` with your config | Within the stated budgets |

### Manual cases

| # | Area | Steps | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Login | Sign in as A, B, C | All succeed; no SSO prompt while SSO is off |  |
| 2 | Phone requirements | As A: Phones tab, set lowest app version 2.0, screen lock required, Save | List shows phones that fall short or have not reported; audit entry `DEVICE_POLICY_CHANGED` |  |
| 3 | Phone requirements | Enter app version "2.x" | Save is blocked with a clear message |  |
| 4 | Phone requirements | As B open the same panel | Told it is unavailable (403); no list |  |
| 5 | Posture report | Send `POST /api/v1/guard/me/device-posture` as guard D for D's phone | 200; phone leaves "not reported" for those fields. Another guard's phone returns 404; a revoked phone returns 403 |  |
| 6 | Fleet health | Stop a phone's heartbeat during a shift | Phone shows as silent; alert goes to the configured recipients |  |
| 7 | Site scope | As B list guards, shifts, incidents, reports | Only B's site; opening another site's record answers not found |  |
| 8 | Regions | Create region, add two branches, give C that region; add a third branch | C sees all three branches' sites; deleting a region held by a user is refused |  |
| 9 | Company isolation | As A of company 1, request an id from company 2 | Not found; no data |  |
| 10 | Row-level security | As a database check, query a `security_` table as the app role without setting the tenant | No rows (and role is not a superuser) |  |
| 11 | Scheduling | Create, edit, cancel a shift; mark one missed; generate a rotation with nights | Statuses correct; night shifts generated |  |
| 12 | Clock-in | Guard D clocks in 10, 29 and 31 minutes early or late | Within 30 minutes matches the shift; outside is handled as designed, never silently unpaid |  |
| 13 | Patrol | Scan a checkpoint with valid and tampered QR; far from the checkpoint | Valid accepted; tampered and far scans refused or flagged |  |
| 14 | Duress | Guard D triggers duress | A person is notified; control room shows an alarm; failures show an error |  |
| 15 | Incident and SLA | Raise an incident; set a short SLA; wait | Acknowledge and resolve clocks run; breach flag appears |  |
| 16 | Armoury | Issue a firearm to a guard with and without competency or PSiRA | Refused without competency or with expired PSiRA; ammunition counts match; lost firearm asks for SAPS case |  |
| 17 | Swap | Approve a swap to a guard with expired PSiRA | Hard block |  |
| 18 | Payroll | Create a period; approve with the same user, then with another; mark paid; create an overlapping period | Maker-checker enforced; no shift paid twice |  |
| 19 | Sunday and holiday pay | Include a Sunday and a public holiday shift in a period | Premium applies as configured; cents add up to the statement |  |
| 20 | Time zone | Shift at 00:30 on the 1st (SAST) | Counted in the right month, day and payroll period |  |
| 21 | Billing | Set a contract rate per site; open billing preview; save a draft; approve with another user; void | Amounts = hours x rate in whole cents; names recorded |  |
| 22 | Monthly report | Email a site's monthly report | Refused without a monitored contact; otherwise sent |  |
| 23 | Occurrence Book | Record entries, sign off the day, print to PDF | Locked after sign-off; read audit logged |  |
| 24 | Masking | Open a guard's ID and bank details | Masked; reveal asks for a reason and is logged in the access log |  |
| 25 | Guard offboarding | Suspend a guard with an open session | Session closes; tokens stop working |  |
| 26 | Client portal and API | Open a portal link; call the API with a key; trigger a webhook | Portal works and shows only that client; key limited to security paths; webhook is signed and retried |  |
| 27 | SSO (API) | Save settings with a free-mail domain, a bad issuer, no break-glass user | Each refused with a message. Leave enforcement off |  |
| 28 | Limited-user screens | As B and C open dashboard, reports, invoice drafts, branches | Clear "limited to some sites" messages; no blank or broken pages |  |
| 29 | Load | Run the load test against a copy of production-sized data | Meets budgets; no errors |  |
| 30 | Regression | Walk every Security tab as A | No 404, 409 or blank screen; no duplicate-URL errors in the log |  |

### Exit criteria

All manual cases pass for A, B, C and D; automated checks are green; no ERROR in the start-up log; the app role is confirmed not a superuser; open decisions that touch money are settled.

## 8. Known limits and risks

**What I could and could not check.** My test setup runs the Java rule and service code against a stand-in for Spring, real PostgreSQL 16 for the SQL, and the web tests with `vitest`, `tsc` and `eslint`. It has never run the full Spring application, a real browser, the guard phone app, or the load test against your app.

**Risks to watch.**

- **Row-level security is only a safety net.** If the app connects as a superuser it protects nothing.
- **Self-reported phone posture.** A rooted phone can say it is not rooted. Treat the compliance report as a guide, not proof.
- **SSO enforcement can lock people out.** There is no provider redirect yet; keep it off and keep break-glass users.
- **Money rules depend on open decisions.** Holiday source, VAT and rate structure change what payroll and invoices produce.
- **Chunk ordering mistakes.** Three start-up failures came from chunk overlap or wrong folders. `ControllerMappingsTest` and `BeanNamesTest` now catch two kinds; apply chunks in order and check packages match folders.
- **Scope gaps for limited users.** Guard list, duress trigger, camera webhook and device sessions do not yet honour site scope.
- **Pay uses scheduled hours.** Until actual hours are built, early or late clocking does not change pay.
- **Platform items.** Encryption at rest is off, token entitlement is report-only and iText is AGPL.

**Sources.** Project documents `MODULE-AUDIT-security.md` (19 Nov 2026) and `DECISIONS-2026-11-22.md`; chunk notes `SEC-CHUNK-1` to `SEC-CHUNK-104` and fixes 1 to 7; your start-up log of 23 Nov 2026.
