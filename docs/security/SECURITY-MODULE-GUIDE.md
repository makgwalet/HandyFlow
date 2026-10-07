# Security module guide

Operator and tester guide for the HandyFlow Security module: what each screen is for, who can do what, how to load a full set of demo data, and a walkthrough that tells you what you should see on every screen once it is loaded.

Contents

1. Screens and what they are for
2. Permissions
3. Demo data: load, remove, what it contains
4. Walkthrough and test checklist
5. Data model in brief
6. API reference
7. Known issues and findings

---

## 1. Screens and what they are for

The module opens at `/security`. The sidebar is grouped; each item is a section of that page (`/security/<section>`). Guards, complaints, incidents, sites and patrols also have detail pages.

| Group | Screen | Section id | Use it to |
|---|---|---|---|
| Overview | Dashboard | `dashboard` | See headline numbers for guards, shifts, incidents and compliance |
| | Control Room | `control-room` | Watch alarms and incidents live, dispatch, handle duress |
| | Live Operations | `live` | See every guard on shift with last scan and last position, on a map |
| Operations | Shifts | `shifts` | List, create, start and end shifts |
| | Scheduler | `scheduler` | Weekly grid by guard, drag-free assignment, with conflict warnings |
| | Incidents | `incidents` | Log and work incidents; detail page has a timeline and assignment |
| | Patrols | `patrols` | See patrol rounds by status; detail page shows scans against the route |
| | Patrol Routes | `patrol-routes` | Define routes, their checkpoints and the round interval |
| | Checkpoints | `checkpoints` | Maintain checkpoints, scan counts, print QR codes |
| | Post Orders | `post-orders` | Standing instructions per post, with guard acknowledgement |
| | Gate Dashboard | `gate-dashboard` | Who is on site now, overstays, entries today |
| | Gate Access | `gate-access` | Access points and the gate register |
| Workforce | Guards | `guards` | Guard list; Guard 360 profile page per guard |
| | Guard Screening | `guard-screening` | Screening queue and sign-off |
| | Complaints | `complaints` | Complaint and disciplinary workflow |
| | Risk Rules | `risk-rules` | Thresholds that turn ratings and events into a risk band |
| | Rotation Patterns | `rotation-patterns` | Repeating shift patterns assigned to guards |
| | Shift Swaps | `shift-swaps` | Request, approve and decline swaps |
| | Payroll | `payroll` | Pay periods built from completed shifts, grade rates |
| Sites & assets | Sites | `sites` | Client sites; detail page has a map and overview |
| | Branches | `branches` | Regional grouping of sites and guards |
| | Armoury | `armoury` | Firearms and equipment, licences, issue and return |
| | CCTV | `cctv` | Cameras and motion alarm events |
| | Devices | `sessions` | Tablets and phones, open sessions |
| Services | Close Protection | `close-protection` | Principals, vetting, details, vehicles, itineraries, surveys |
| Insights | Reports | `reports` | Report catalogue with last-generated tracking |
| | Public API | `public-api` | API keys and webhooks for client integration |

## 2. Permissions

Four authorities guard the endpoints. They are cumulative in practice (give a role the ones it needs):

| Authority | Meaning | Endpoints using it |
|---|---|---|
| `SECURITY_READ` | View anything in the module | 79 |
| `SECURITY_MANAGE` | Create and edit operational records | 112 |
| `SECURITY_ADMIN` | Destructive or sensitive actions (decommission or report a firearm lost, and similar) | 7 |
| `SECURITY_GUARD` | Guard-facing device endpoints (open a session, scan, duress) | 15 |

Every endpoint also checks that the tenant has the `security` module enabled (`featureGuard.requireModule("security")`).

## 3. Demo data

Two psql scripts live in `platform/src/main/resources/db/demo/`. They are **not** Flyway migrations and never run by themselves.

* `security-demo-data.sql` loads the data into one existing tenant.
* `security-demo-data-remove.sql` deletes exactly what the first script created.

Use them on a development, demo or test tenant. Do not load them into a tenant that has real operations: they refuse to run twice, but they do not know about your real data and will add rows beside it.

### Load

You need the tenant id (`select id, name from tenants;`).

```powershell
# Docker (PowerShell)
Get-Content platform\src\main\resources\db\demo\security-demo-data.sql | docker exec -i <db-container> psql -U <user> -d <database> -v tenant=<TENANT-UUID>
```

```bash
psql -h localhost -U <user> -d <database> -v tenant=<TENANT-UUID> -f platform/src/main/resources/db/demo/security-demo-data.sql
```

The whole script runs in one transaction. If anything fails nothing is loaded. If demo data is already present it stops with "Demo data is already loaded".

### Remove

```powershell
Get-Content platform\src\main\resources\db\demo\security-demo-data-remove.sql | docker exec -i <db-container> psql -U <user> -d <database>
```

It needs no arguments, prints how many rows it removed, and is safe to run twice. Rows are found by their id prefix `d3d3d3d3-`, so real data is untouched. To refresh the demo (so "now" moves forward), remove and load again.

### How the data is built

* Everything is fake and labelled: names start with `DEMO`, ID numbers are `DEMO-ID...`, PSiRA numbers `DEMO-PSR...`, phone numbers are `082 000 00xx`, bank details are not real.
* Times are relative to the moment you load it. "On duty now", "last Tuesday", "contract ends in 20 days" are all computed then. Load it the day you demo it.
* Shifts cover two weeks back and two weeks ahead of this week's Monday, in 06:00 to 14:00, 14:00 to 22:00 and 22:00 to 06:00 slots (South African time).
* One active patrol route per site.
* Evidence files are tiny generated PDFs stored in the database.
* Webhooks and API keys are deliberately not seeded, because they hold secrets.

### What you get

| Area | Rows |
|---|---|
| Branches | 2 (Gauteng North, Gauteng South) |
| Sites | 7, including one with a contract expiring soon, one terminated, one with no map position |
| Posts | 4 |
| Guards | 14, every grade A to E, statuses Active 11, Suspended 1, On leave 1, Terminated 1 |
| Shifts | 177, mixed Completed, Active, Scheduled, Missed, Pulled, Cancelled |
| Checkpoints | 25 (QR, NFC and BLE, some inactive) |
| Patrol routes | 5 (one switched off) |
| Patrol rounds | 150: Complete, Partial, Missed, Off schedule, In progress, Expected |
| Scan logs | 409 |
| Devices | 15, with a session open for each guard currently on duty |
| Incidents | 12: Open 4, Acknowledged 3, Resolved 5, each with a timeline |
| Gate register | 6 access points, 16 entries (on site, overstayed, departed) |
| Screening | 44 records across Cleared, Pending, Flagged and Unscreened guards |
| Competencies | 16 (including firearm competency) |
| Guard documents | 50 (one soft-deleted) |
| Ratings | 30 |
| Complaints | 8, one in every workflow state, with events |
| Post orders | 4, with acknowledgements |
| Armoury | 5 items with logs and custody |
| Rotation | 3 patterns, 4 assignments, 4 swap requests in 4 statuses |
| CCTV | 4 cameras, 6 alarm events, 3 dispatches |
| Payroll | 3 periods (Paid, Approved, Draft) with line items from completed shifts, grade rates and rate history |
| Close protection | 3 principals, vetting, 1 declined principal, vehicles, details, assignments, itinerary stops, 2 advance surveys |
| Reports | 5 report runs, plus 5 audit rows |

## 4. Walkthrough and test checklist

Load the demo, sign in as a user with `SECURITY_READ` and `SECURITY_MANAGE` (admin is simplest), open `/security`. Tick each line as you confirm it. Counts that depend on the clock say "about".

### Dashboard and Live Operations
- [ ] Dashboard shows guards, shifts and incident numbers that agree with the sections below (14 guards, 12 incidents).
- [ ] Live Operations lists the guards on an active shift (about 8). Some have a fresh position, some a stale position flagged as stale, some none.
- [ ] A site selector narrows the list to that site.
- [ ] The map renders markers for guards with positions. Fourways Estate has no map position and must not break the map.

### Shifts and Scheduler
- [ ] Shifts lists Completed, Active, Scheduled, Missed, Pulled and Cancelled shifts. Filter by status and by site.
- [ ] Scheduler shows this week Monday to Sunday with a row per guard.
- [ ] Conflict warnings appear for the deliberate cases: an overlap, a rest period under 8 hours, a guard over 45 weekly hours, a guard with an expired PSiRA registration, and a suspended guard on a shift.
- [ ] Previous and next week navigation works; the two-weeks-back shifts show as Completed.
- [ ] A shift that overran its end time shows as active past its end.

### Incidents
- [ ] 12 incidents: 4 Open, 3 Acknowledged, 5 Resolved.
- [ ] Open one: the timeline shows its events in order; assigning a guard adds a timeline entry.
- [ ] Resolving an Open incident moves the counts to 3 Open and 6 Resolved.

### Patrols, Routes and Checkpoints
- [ ] Patrols lists 150 rounds. Status filter shows Complete, Partial, Missed, Off schedule, In progress and Expected.
- [ ] Open a Partial round: scanned checkpoints are ticked, the missed ones are not.
- [ ] Patrol Routes shows 5 routes. The Centurion interior route is switched off.
- [ ] Checkpoints shows 25 with scan counts; inactive checkpoints are marked. QR print works. NFC and BLE checkpoints show their tag id.

### Gate
- [ ] Gate Dashboard shows 7 on site now for all sites (6 on site plus 1 overstayed; the overstayed person counts), 1 overstayed, and per-site cards. Today's entered and left counts depend on the time you load the data.
- [ ] Gate Access shows 6 access points and 16 entries, 9 departed.
- [ ] Recording a departure for an on-site visitor moves them to departed and drops the on-site count.

### Guards and Guard 360
- [ ] Guards lists 14 (25 per page). Status pills show counts (Active 11, On leave 1, Suspended 1, Terminated 1). Filter by grade, PSiRA state and screening; sort by clicking Guard, PSiRA No., Grade, Status or Last activity; the search box matches name, PSiRA number, employee code and phone.
- [ ] Flags column: Busisiwe Cele and the expired-PSiRA guard show "PSiRA expired"; two guards show "PSiRA 15d left" and "PSiRA 25d left"; screening flags follow the guard's screening status. The PSiRA banner says 1 expired and 2 expiring, and Show them filters to those three.
- [ ] Select two guards: the bar offers Set status and Export CSV. A guard the server refuses is named in the result and the others still change.
- [ ] Open a guard: the profile shows screening, competencies, documents, ratings and complaints.
- [ ] On a guard's Documents tab the "Guard file checklist" can say police clearance or POPIA consent is missing while Deployment Readiness is 100%. They are different lists, and the panel now says so.
- [ ] PSiRA badges: some valid, two expiring within 30 days (about 15 and 25 days), one expired about 10 days ago.
- [ ] Screening panel states: Cleared, Pending, Flagged, Unscreened across different guards.
- [ ] A guard with a firearm competency shows it on the profile and in Armoury.
- [ ] Documents tab: 50 documents open as PDFs. The soft-deleted one is not listed.

### Guard Screening
- [ ] Queue shows the Pending and Flagged guards. Signing off a Pending guard moves them to Cleared.

### Complaints
- [ ] 8 complaints, numbered CMP-DEMO-01 to CMP-DEMO-08, one per workflow state: Received, Under investigation, Finding made, Action taken, Closed, Withdrawn.
- [ ] Open one and advance it. Only valid next steps are offered; the event timeline shows every step.
- [ ] Open the closed one (CMP-DEMO-05) and choose **Reopen**, giving a reason. It returns to Under investigation, the finding and action are cleared, and the timeline records what they were. A withdrawn complaint cannot be reopened.
- [ ] Logging a complaint that is critical, or about excessive force, a firearm, theft or harassment, sends an in-app and email alert to the tenant's administrators (link opens the complaint).

### Automatic alerts and snapshots
- [ ] 06:45 every day: one digest of skills and certificates that expire within 30 days (the notification links to Guards).
- [ ] 02:15 every day: each active guard's score and risk recommendations are saved (table `security_guard_score_history`). A recommendation that was not on the guard's previous snapshot is announced once to administrators; one that keeps standing is not repeated. A guard's first snapshot is a baseline and announces nothing.

### Performance trend
- [ ] Guard 360, Performance tab: a **Score trend** chart (30 or 90 days) with a one-line summary ("Up 12 points since 20 Sep"). With the demo data it shows 30 illustrative days; they are seeded, not calculated, and the nightly job adds real ones from the day it first runs.
- [ ] Checkpoint compliance now has data: completed demo shifts carry scans, and about 85 per cent meet their minimum (guards 3 and 5 about half).

### Risk Rules and Ratings
- [ ] Risk Rules shows the seeded thresholds.
- [ ] Ratings on the guard profile show an operational score; guards with poor ratings or open complaints sit in a higher risk band.

### Rotation, Swaps and Payroll
- [ ] Rotation Patterns shows 3 patterns and 4 assignments.
- [ ] Shift Swaps shows 4 requests in 4 different statuses; approve and decline both work.
- [ ] Payroll shows 3 periods: Paid, Approved and Draft. Open one: line items per guard with hours, rate and totals that agree with the guard's grade rate.

### Sites, Branches and Post Orders
- [ ] Sites shows 7. Open one: map, overview counts, contact list (police, site manager, client contact, control room, ambulance, fire).
- [ ] The site with the near contract end shows an expiry warning; the terminated site is marked.
- [ ] Branches shows 2 with their sites and guards.
- [ ] Post Orders shows 4. Acknowledgement counts show who has and has not acknowledged.

### Armoury, CCTV and Devices
- [ ] Armoury shows 5 items, some with licences close to expiry, some issued to a guard, with issue and return history.
- [ ] CCTV shows 4 cameras and 6 alarm events; 3 have a dispatch.
- [ ] Devices shows 15, shared tablets and personal devices, with one open session per live guard.

### Close Protection
- [ ] 3 principals with vetting at different tiers, and 1 declined principal.
- [ ] A protection detail has assigned guards, a vehicle, itinerary stops and an advance survey.

### Reports
- [ ] The report catalogue shows each report with a last-generated time. Five report runs are seeded across 3 different reports, so 3 reports show a time and the rest show "never".
- [ ] Generating a report updates its last-generated time.

### Clean up
- [ ] Run the remove script; every screen above is empty again, and real data (if any) is unchanged.

## 5. Data model in brief

All tables are tenant scoped (`tenant_id`). Tables begin `security_` unless noted.

* **Structure:** `security_branches` has sites (`security_sites`), which have posts, checkpoints, contacts and patrol routes. Guards are linked to branches by `security_branch_assignments`.
* **Workforce:** `security_guards` with `security_guard_screening_records`, `security_guard_competencies`, `security_guard_documents`, `security_guard_ratings`, `security_guard_complaints` and its events, `security_grade_rates` and `security_guard_rate_history`.
* **Time:** `security_shifts` (guard, site, start, end, status), rotation patterns and assignments, shift swap requests, payroll periods and line items built from completed shifts.
* **Patrols:** routes, route checkpoints, `security_patrol_rounds` (unique per shift and round number), `security_checkpoint_logs` (every scan).
* **Live:** devices, device sessions (one open session per device), location pings and the current location per guard.
* **Events:** incidents and incident events, alarm events, dispatches, gate access points and register entries, audit log, report runs.
* **Assets:** armoury items, logs and custody; cameras.
* **Close protection:** principals, vetting, declined principals, protection details, detail assignments, itinerary stops, vehicles, advance surveys.
* **Evidence:** files live in `stored_files` with an `evidence` row pointing at them. By default storage is the database.

Time columns are `timestamp without time zone` holding UTC. South Africa is UTC+2 with no daylight saving, and the working week starts on Monday.

Demo ids follow `d3d3d3d3-<kind>-4000-8000-<number>`. The kind is a 4-digit number per table type (1 branch, 2 site, 3 guard, 4 checkpoint, 5 route, 7 shift, 8 round, 9 scan log, 10 incident, and so on), which makes demo rows easy to spot in a query.

## 6. API reference

Generated from the controllers. Paths are under the host root. A blank permission means the rule is not a simple `hasAuthority` on the method (device token, webhook, or a multi-line rule); read the controller before relying on it. Guard device endpoints are under `/api/v1/security/sessions` and `/api/v1/guard`.

### ArmouryController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/armoury` | SECURITY_READ |
| GET | `/api/v1/security/armoury/{id}` | SECURITY_READ |
| POST | `/api/v1/security/armoury` | SECURITY_MANAGE |
| PUT | `/api/v1/security/armoury/{id}/license` | SECURITY_MANAGE |
| POST | `/api/v1/security/armoury/{id}/service` | SECURITY_MANAGE |
| POST | `/api/v1/security/armoury/{id}/report-lost` | SECURITY_ADMIN |
| POST | `/api/v1/security/armoury/{id}/decommission` | SECURITY_ADMIN |
| POST | `/api/v1/security/armoury/{id}/issue` | SECURITY_MANAGE |
| POST | `/api/v1/security/armoury/{id}/return` | SECURITY_MANAGE |
| GET | `/api/v1/security/armoury/{id}/history` | SECURITY_READ |
| GET | `/api/v1/security/armoury/{id}/history/pdf` | SECURITY_READ |
| GET | `/api/v1/security/armoury/guard/{guardId}` | SECURITY_READ |
| POST | `/api/v1/security/armoury/guard/{guardId}/competency` | SECURITY_MANAGE |

### BranchController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/branches` | SECURITY_READ |
| GET | `/api/v1/security/branches/{id}` | SECURITY_READ |
| POST | `/api/v1/security/branches` | SECURITY_MANAGE |
| PUT | `/api/v1/security/branches/{id}` | SECURITY_MANAGE |
| DELETE | `/api/v1/security/branches/{id}` | SECURITY_MANAGE |

### CameraController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/cameras` | SECURITY_READ |
| GET | `/api/v1/security/cameras/{id}` | SECURITY_READ |
| GET | `/api/v1/security/cameras/site/{siteId}` | SECURITY_READ |
| POST | `/api/v1/security/cameras` | SECURITY_MANAGE |
| PUT | `/api/v1/security/cameras/{id}` | SECURITY_MANAGE |
| POST | `/api/v1/security/cameras/{id}/offline` | SECURITY_MANAGE |
| POST | `/api/v1/security/cameras/{id}/activate` | SECURITY_MANAGE |
| POST | `/api/v1/security/cameras/{id}/decommission` | SECURITY_ADMIN |
| POST | `/api/v1/security/cameras/{id}/webhook-secret` | SECURITY_MANAGE |
| POST | `/api/v1/security/cameras/motion-webhook` |  |

### CheckpointAdminController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/checkpoints` | SECURITY_READ |
| PATCH | `/api/v1/security/checkpoints/{id}` | SECURITY_MANAGE |

### CheckpointScanController

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/security/checkpoints/scan` | SECURITY_GUARD,SECURITY_MANAGE |

### CloseProtectionController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/cp/principals` |  |
| GET | `/api/v1/security/cp/principals/{id}` |  |
| POST | `/api/v1/security/cp/principals` |  |
| PUT | `/api/v1/security/cp/principals/{id}` |  |
| DELETE | `/api/v1/security/cp/principals/{id}` |  |
| GET | `/api/v1/security/cp/principals/{id}/audit` |  |
| GET | `/api/v1/security/cp/principals/{id}/audit/views` |  |
| GET | `/api/v1/security/cp/principals/{id}/vetting/pdf` |  |
| GET | `/api/v1/security/cp/details` |  |
| GET | `/api/v1/security/cp/details/{id}` |  |
| GET | `/api/v1/security/cp/principals/{principalId}/details` |  |
| POST | `/api/v1/security/cp/details` |  |
| POST | `/api/v1/security/cp/details/{id}/activate` |  |
| POST | `/api/v1/security/cp/details/{id}/complete` |  |
| POST | `/api/v1/security/cp/details/{id}/cancel` |  |
| POST | `/api/v1/security/cp/details/{id}/clone` |  |
| GET | `/api/v1/security/cp/details/{id}/team` |  |
| POST | `/api/v1/security/cp/details/{id}/team` |  |
| DELETE | `/api/v1/security/cp/team/{assignmentId}` |  |
| POST | `/api/v1/security/cp/details/{id}/team/{assignmentId}/firearms/{armouryId}/issue` |  |
| GET | `/api/v1/security/cp/details/{id}/armoury` |  |
| GET | `/api/v1/security/cp/details/{id}/itinerary` |  |
| GET | `/api/v1/security/cp/details/{id}/itinerary/current` |  |
| POST | `/api/v1/security/cp/details/{id}/itinerary` |  |
| POST | `/api/v1/security/cp/itinerary/{stopId}/arrive` |  |
| POST | `/api/v1/security/cp/itinerary/{stopId}/depart` |  |
| GET | `/api/v1/security/cp/itinerary/{stopId}/surveys` |  |
| POST | `/api/v1/security/cp/itinerary/{stopId}/surveys` |  |
| GET | `/api/v1/security/cp/itinerary/{stopId}/cleared` |  |
| GET | `/api/v1/security/cp/vehicles` |  |
| POST | `/api/v1/security/cp/vehicles` |  |
| POST | `/api/v1/security/cp/vehicles/{id}/driver` |  |
| DELETE | `/api/v1/security/cp/vehicles/{id}/driver` |  |
| POST | `/api/v1/security/cp/vehicles/{id}/service` |  |
| POST | `/api/v1/security/cp/vehicles/{id}/return-from-service` |  |
| POST | `/api/v1/security/cp/vehicles/{id}/decommission` |  |
| POST | `/api/v1/security/cp/principals/{id}/evidence` |  |
| GET | `/api/v1/security/cp/principals/{id}/evidence` |  |
| POST | `/api/v1/security/cp/details/{id}/evidence` |  |
| GET | `/api/v1/security/cp/details/{id}/evidence` |  |
| DELETE | `/api/v1/security/cp/evidence/{evidenceId}` |  |

### ControlRoomController

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/security/alarm-events` |  |
| GET | `/api/v1/security/alarm-events` | SECURITY_READ |
| GET | `/api/v1/security/sites/{siteId}/alarm-events` | SECURITY_READ |
| POST | `/api/v1/security/alarm-events/{id}/triage` | SECURITY_MANAGE |
| POST | `/api/v1/security/alarm-events/{id}/false-alarm` | SECURITY_MANAGE |
| POST | `/api/v1/security/alarm-events/{id}/dispatch` | SECURITY_MANAGE |
| POST | `/api/v1/security/dispatches/{id}/arrive` | SECURITY_MANAGE |
| PATCH | `/api/v1/security/dispatches/{id}/resolve` | SECURITY_MANAGE |
| POST | `/api/v1/security/duress` |  |
| GET | `/api/v1/security/dispatches/open` | SECURITY_READ |
| GET | `/api/v1/security/alarm-events/{id}/dispatches` | SECURITY_READ |

### DeviceSessionController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/sessions` | SECURITY_READ |
| GET | `/api/v1/security/sessions/current` |  |
| GET | `/api/v1/security/sessions/resolve-guard` |  |
| POST | `/api/v1/security/sessions/open` |  |
| POST | `/api/v1/security/sessions/{sessionId}/close` |  |
| POST | `/api/v1/security/sessions/{sessionId}/force-close` | SECURITY_MANAGE |
| POST | `/api/v1/security/sessions/{sessionId}/location` |  |
| POST | `/api/v1/security/sessions/{sessionId}/resources/checkout` |  |
| POST | `/api/v1/security/sessions/resources/{custodyId}/return` |  |

### GateAccessController

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/security/access-points` | SECURITY_MANAGE |
| GET | `/api/v1/security/sites/{siteId}/access-points` | SECURITY_READ |
| PUT | `/api/v1/security/access-points/{id}` | SECURITY_MANAGE |
| POST | `/api/v1/security/access-points/{id}/deactivate` | SECURITY_MANAGE |
| POST | `/api/v1/security/access-points/{id}/reactivate` | SECURITY_MANAGE |
| GET | `/api/v1/security/sites/{siteId}/on-site` | SECURITY_READ |
| GET | `/api/v1/security/gate-entries/{entryId}/attachments` | SECURITY_READ |
| GET | `/api/v1/security/sites/{siteId}/gate-log` | SECURITY_READ |
| GET | `/api/v1/security/reports/site-access` | SECURITY_READ |
| GET | `/api/v1/security/reports/site-access/pdf` | SECURITY_READ |

### GateDashboardController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/gate/dashboard` | SECURITY_READ |

### GuardAuthController.java

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/auth/guard/login` |  |
| POST | `/api/v1/auth/guard/activate-device` |  |
| POST | `/api/v1/guard/auth/change-pin` |  |
| POST | `/api/v1/guard/auth/logout` |  |
| POST | `/api/v1/security/guards/{id}/enrol` | SECURITY_MANAGE |
| POST | `/api/v1/security/guards/{id}/initiate-device-replacement` | SECURITY_MANAGE |
| GET | `/api/v1/security/guards/{id}/devices` | SECURITY_READ |
| POST | `/api/v1/security/guards/{id}/revoke-tokens` | SECURITY_MANAGE |

### GuardCheckpointController

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/guard/checkpoints/scan` | SECURITY_GUARD |

### GuardCompetencyController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/guards/{guardId}/competencies` | SECURITY_READ |
| POST | `/api/v1/security/guards/{guardId}/competencies` | SECURITY_MANAGE |
| PUT | `/api/v1/security/guards/{guardId}/competencies/{id}` | SECURITY_MANAGE |
| POST | `/api/v1/security/guards/{guardId}/competencies/{id}/verify` | SECURITY_MANAGE |
| DELETE | `/api/v1/security/guards/{guardId}/competencies/{id}` | SECURITY_MANAGE |
| GET | `/api/v1/security/guards/{guardId}/competencies/{id}/evidence` | SECURITY_READ |
| POST | `/api/v1/security/guards/{guardId}/competencies/{id}/evidence` | SECURITY_MANAGE |
| GET | `/api/v1/security/guards/{guardId}/competencies/{id}/evidence/{evidenceId}/download` | SECURITY_READ |
| DELETE | `/api/v1/security/guards/{guardId}/competencies/{id}/evidence/{evidenceId}` | SECURITY_MANAGE |

### GuardComplaintController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/complaints` | SECURITY_READ |
| GET | `/api/v1/security/complaints/{id}` | SECURITY_READ |
| POST | `/api/v1/security/complaints` | SECURITY_MANAGE |
| PUT | `/api/v1/security/complaints/{id}` | SECURITY_MANAGE |
| POST | `/api/v1/security/complaints/{id}/start` | SECURITY_MANAGE |
| POST | `/api/v1/security/complaints/{id}/finding` | SECURITY_MANAGE |
| POST | `/api/v1/security/complaints/{id}/action` | SECURITY_MANAGE |
| POST | `/api/v1/security/complaints/{id}/close` | SECURITY_MANAGE |
| POST | `/api/v1/security/complaints/{id}/withdraw` | SECURITY_MANAGE |
| POST | `/api/v1/security/complaints/{id}/reopen` | SECURITY_MANAGE |
| POST | `/api/v1/security/complaints/{id}/evidence` | SECURITY_MANAGE |
| GET | `/api/v1/security/complaints/{id}/evidence/{evidenceId}/download` | SECURITY_READ |
| DELETE | `/api/v1/security/complaints/{id}/evidence/{evidenceId}` | SECURITY_MANAGE |

### GuardController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/guards` | SECURITY_READ |
| GET | `/api/v1/security/guards/directory` | SECURITY_READ | Guards list with search, status, grade, PSiRA and screening filters, sorting, paging and counts |
| GET | `/api/v1/security/guards/{id}` | SECURITY_READ |
| GET | `/api/v1/security/guards/{id}/overview` | SECURITY_READ |
| POST | `/api/v1/security/guards` | SECURITY_MANAGE |
| PUT | `/api/v1/security/guards/{id}` | SECURITY_MANAGE |
| PATCH | `/api/v1/security/guards/{id}/status` | SECURITY_MANAGE |
| DELETE | `/api/v1/security/guards/{id}` | SECURITY_ADMIN |
| POST | `/api/v1/security/guards/{id}/photo` | SECURITY_MANAGE |
| POST | `/api/v1/security/guards/{id}/bank-details` | SECURITY_MANAGE |

### GuardCpController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/guard-cp/profile` |  |

### GuardDocumentController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/guards/{guardId}/documents` | SECURITY_READ |
| POST | `/api/v1/security/guards/{guardId}/documents` | SECURITY_MANAGE |
| DELETE | `/api/v1/security/guards/{guardId}/documents/{documentId}` | SECURITY_MANAGE |

### GuardDuressController

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/guard/duress` | SECURITY_GUARD |

### GuardGateAccessController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/guard/gate/access-points` | SECURITY_GUARD |
| GET | `/api/v1/guard/gate/on-site` | SECURITY_GUARD |
| POST | `/api/v1/guard/gate/entries` | SECURITY_GUARD |
| POST | `/api/v1/guard/gate/entries/{entryId}/exit` | SECURITY_GUARD |
| POST | `/api/v1/guard/gate/entries/{entryId}/attachments` | SECURITY_GUARD |

### GuardIncidentController

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/guard/incidents` | SECURITY_GUARD |

### GuardPerformanceController

`GET /api/v1/security/guards/{guardId}/performance/history?days=90` (SECURITY_READ) returns the daily snapshots, oldest first (1 to 365 days).

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/guards/{guardId}/performance` | SECURITY_READ |
| POST | `/api/v1/security/guards/{guardId}/ratings` | SECURITY_MANAGE |
| GET | `/api/v1/security/risk-settings` | SECURITY_READ |
| PUT | `/api/v1/security/risk-settings` | SECURITY_ADMIN |

### GuardPostOrderController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/guard/my-post` | SECURITY_GUARD |
| GET | `/api/v1/guard/my-post/posts/{postId}` | SECURITY_GUARD |
| POST | `/api/v1/guard/my-post/orders/{postOrderId}/acknowledge` | SECURITY_GUARD |

### GuardScreeningController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/guards/{guardId}/screening` |  |
| POST | `/api/v1/security/guards/{guardId}/screening` |  |
| POST | `/api/v1/security/guards/{guardId}/screening/{screeningId}/result` |  |
| POST | `/api/v1/security/guards/{guardId}/screening/{screeningId}/decision` |  |
| GET | `/api/v1/security/guards/{guardId}/screening/gate` |  |

### GuardScreeningEvidenceController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/guards/{guardId}/screening/{screeningId}/evidence` | SECURITY_READ |
| POST | `/api/v1/security/guards/{guardId}/screening/{screeningId}/evidence` | SECURITY_MANAGE |
| GET | `/api/v1/security/guards/{guardId}/screening/{screeningId}/evidence/{evidenceId}/download` | SECURITY_READ |
| DELETE | `/api/v1/security/guards/{guardId}/screening/{screeningId}/evidence/{evidenceId}` | SECURITY_MANAGE |

### GuardSessionController

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/guard/sessions/open` |  |
| POST | `/api/v1/guard/sessions/{sessionId}/close` |  |
| GET | `/api/v1/guard/sessions/current` |  |
| POST | `/api/v1/guard/sessions/{sessionId}/location` |  |
| POST | `/api/v1/guard/sessions/{sessionId}/resources/checkout` |  |
| POST | `/api/v1/guard/sessions/resources/{custodyId}/return` |  |

### GuardShiftSwapController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/guard/shifts/swaps/my-swaps` | SECURITY_GUARD |
| POST | `/api/v1/guard/shifts/swaps` | SECURITY_GUARD |
| POST | `/api/v1/guard/shifts/swaps/{id}/accept` | SECURITY_GUARD |
| DELETE | `/api/v1/guard/shifts/swaps/{id}` | SECURITY_GUARD |

### IncidentController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/incidents` | SECURITY_READ |
| POST | `/api/v1/security/incidents` | SECURITY_MANAGE |
| POST | `/api/v1/security/incidents/{id}/acknowledge` | SECURITY_MANAGE |
| POST | `/api/v1/security/incidents/{id}/resolve` | SECURITY_MANAGE |
| GET | `/api/v1/security/incidents/{id}` | SECURITY_READ |
| POST | `/api/v1/security/incidents/{id}/assign` | SECURITY_MANAGE |
| POST | `/api/v1/security/incidents/{id}/escalate` | SECURITY_MANAGE |
| POST | `/api/v1/security/incidents/{id}/notes` | SECURITY_MANAGE |
| POST | `/api/v1/security/incidents/{id}/reopen` | SECURITY_MANAGE |
| POST | `/api/v1/security/incidents/{id}/evidence` | SECURITY_MANAGE |
| GET | `/api/v1/security/incidents/{id}/evidence/{evidenceId}/download` | SECURITY_READ |
| DELETE | `/api/v1/security/incidents/{id}/evidence/{evidenceId}` | SECURITY_MANAGE |
| GET | `/api/v1/security/incidents/{id}/pdf` | SECURITY_READ |

### LiveOperationsController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/live/guards` | SECURITY_READ |
| GET | `/api/v1/security/sites/{siteId}/guards/locations` | SECURITY_READ |

### PatrolController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/patrols` | SECURITY_READ |
| GET | `/api/v1/security/patrols/{id}` | SECURITY_READ |
| POST | `/api/v1/security/patrols/{id}/acknowledge` | SECURITY_MANAGE |

### PatrolRouteController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/patrol-routes` | SECURITY_READ |
| POST | `/api/v1/security/patrol-routes` | SECURITY_MANAGE |
| POST | `/api/v1/security/patrol-routes/{routeId}/checkpoints` | SECURITY_MANAGE |
| GET | `/api/v1/security/patrol-routes/rounds` | SECURITY_READ |

### PayrollController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/payroll/periods` | SECURITY_READ |
| GET | `/api/v1/security/payroll/periods/{id}` | SECURITY_READ |
| POST | `/api/v1/security/payroll/periods` | SECURITY_MANAGE |
| POST | `/api/v1/security/payroll/periods/{id}/approve` | SECURITY_MANAGE |
| POST | `/api/v1/security/payroll/periods/{id}/mark-paid` | SECURITY_MANAGE |
| GET | `/api/v1/security/payroll/periods/{id}/lines` | SECURITY_READ |
| GET | `/api/v1/security/payroll/periods/{id}/export/csv` | SECURITY_READ |
| GET | `/api/v1/security/payroll/periods/{id}/export/json` | SECURITY_READ |
| GET | `/api/v1/security/payroll/periods/{id}/guards/{guardId}/pdf` | SECURITY_READ |
| GET | `/api/v1/security/payroll/grade-rates` | SECURITY_READ |
| POST | `/api/v1/security/payroll/grade-rates` | SECURITY_MANAGE |

### PostOrderController

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/security/contacts` | SECURITY_MANAGE |
| PUT | `/api/v1/security/contacts/{id}` | SECURITY_MANAGE |
| POST | `/api/v1/security/contacts/{id}/deactivate` | SECURITY_MANAGE |
| GET | `/api/v1/security/sites/{siteId}/contacts` | SECURITY_READ |
| POST | `/api/v1/security/sites/{siteId}/posts` | SECURITY_MANAGE |
| PUT | `/api/v1/security/posts/{id}` | SECURITY_MANAGE |
| POST | `/api/v1/security/posts/{id}/deactivate` | SECURITY_MANAGE |
| GET | `/api/v1/security/sites/{siteId}/posts` | SECURITY_READ |
| POST | `/api/v1/security/sites/{siteId}/post-orders` | SECURITY_MANAGE |
| PUT | `/api/v1/security/post-orders/{id}` | SECURITY_MANAGE |
| POST | `/api/v1/security/post-orders/{id}/publish` | SECURITY_MANAGE |
| POST | `/api/v1/security/post-orders/{id}/attachments` | SECURITY_MANAGE |
| GET | `/api/v1/security/sites/{siteId}/post-orders/history` | SECURITY_READ |
| GET | `/api/v1/security/post-orders/{id}/acknowledgements` | SECURITY_READ |

### PublicApiController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/public-api/keys` | SECURITY_READ |
| POST | `/api/v1/security/public-api/keys` | SECURITY_MANAGE |
| DELETE | `/api/v1/security/public-api/keys/{id}` | SECURITY_ADMIN |
| GET | `/api/v1/security/public-api/webhooks` | SECURITY_READ |
| POST | `/api/v1/security/public-api/webhooks` | SECURITY_MANAGE |
| DELETE | `/api/v1/security/public-api/webhooks/{id}` | SECURITY_MANAGE |
| POST | `/api/v1/security/public-api/webhooks/{id}/reactivate` | SECURITY_MANAGE |
| GET | `/api/v1/security/public-api/webhooks/{id}/deliveries` | SECURITY_READ |

### ReportCatalogueController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/reports/catalogue` | SECURITY_READ |

### ReportingController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/reports/site-coverage` | SECURITY_READ |
| GET | `/api/v1/security/reports/site-coverage/pdf` | SECURITY_READ |
| GET | `/api/v1/security/reports/guard-attendance` | SECURITY_READ |
| GET | `/api/v1/security/reports/guard-attendance/pdf` | SECURITY_READ |
| GET | `/api/v1/security/reports/monthly-summary` | SECURITY_READ |
| GET | `/api/v1/security/reports/monthly-summary/pdf` | SECURITY_READ |

### RotationController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/rotations` | SECURITY_READ |
| POST | `/api/v1/security/rotations` | SECURITY_MANAGE |
| PUT | `/api/v1/security/rotations/{id}` | SECURITY_MANAGE |
| DELETE | `/api/v1/security/rotations/{id}` | SECURITY_MANAGE |
| POST | `/api/v1/security/rotations/assignments` | SECURITY_MANAGE |
| GET | `/api/v1/security/rotations/{patternId}/assignments` | SECURITY_READ |
| DELETE | `/api/v1/security/rotations/assignments/{assignmentId}` | SECURITY_MANAGE |
| POST | `/api/v1/security/rotations/generate` | SECURITY_MANAGE |

### ScanLogController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/shifts/{id}/scans` | SECURITY_READ |

### SecurityClientPortalController.java

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/portal/{token}` |  |
| POST | `/api/v1/security/sites/{id}/portal/generate` | SECURITY_MANAGE |
| DELETE | `/api/v1/security/sites/{id}/portal` | SECURITY_MANAGE |
| POST | `/api/v1/security/sites/{id}/portal/send` | SECURITY_MANAGE |

### ShiftController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/shifts` | SECURITY_READ |
| GET | `/api/v1/security/shifts/range` | SECURITY_READ |
| POST | `/api/v1/security/shifts` | SECURITY_MANAGE |
| PUT | `/api/v1/security/shifts/{id}` | SECURITY_MANAGE |
| POST | `/api/v1/security/shifts/{id}/start` | SECURITY_MANAGE |
| POST | `/api/v1/security/shifts/{id}/complete` | SECURITY_MANAGE |
| POST | `/api/v1/security/shifts/{id}/dismiss-no-show` | SECURITY_MANAGE |
| POST | `/api/v1/security/shifts/{id}/close-overtime` | SECURITY_MANAGE |
| POST | `/api/v1/security/shifts/{id}/pull` | SECURITY_MANAGE |

### ShiftSwapController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/shifts/swaps` | SECURITY_READ |
| GET | `/api/v1/security/shifts/swaps/guard/{guardId}` | SECURITY_READ |
| POST | `/api/v1/security/shifts/swaps` | SECURITY_GUARD,SECURITY_MANAGE |
| POST | `/api/v1/security/shifts/swaps/{id}/accept` | SECURITY_GUARD,SECURITY_MANAGE |
| POST | `/api/v1/security/shifts/swaps/{id}/approve` | SECURITY_MANAGE |
| POST | `/api/v1/security/shifts/swaps/{id}/reject` | SECURITY_MANAGE |
| DELETE | `/api/v1/security/shifts/swaps/{id}` | SECURITY_GUARD,SECURITY_MANAGE |

### SiteController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/sites` | SECURITY_READ |
| GET | `/api/v1/security/sites/{id}` | SECURITY_READ |
| POST | `/api/v1/security/sites` | SECURITY_MANAGE |
| POST | `/api/v1/security/sites/{id}/checkpoints` | SECURITY_MANAGE |
| DELETE | `/api/v1/security/sites/{id}` | SECURITY_ADMIN |
| POST | `/api/v1/security/sites/{id}/terminate` | SECURITY_MANAGE |
| GET | `/api/v1/security/sites/{siteId}/checkpoints/{checkpointId}/qr-payload` | SECURITY_MANAGE |
| PATCH | `/api/v1/security/sites/{id}/branch` | SECURITY_MANAGE |
| PATCH | `/api/v1/security/sites/{id}/qr-enforcement` | SECURITY_MANAGE |
| POST | `/api/v1/security/sites/{siteId}/checkpoints/{checkpointId}/qr-secret/regenerate` | SECURITY_MANAGE |
| GET | `/api/v1/security/sites/{siteId}/checkpoints/{checkpointId}/qr-image` | SECURITY_MANAGE |
| GET | `/api/v1/security/sites/{siteId}/checkpoints/{checkpointId}/qr-pdf` | SECURITY_MANAGE |
| GET | `/api/v1/security/sites/{siteId}/checkpoints/qr-sheet` | SECURITY_MANAGE |

### SiteOverviewController

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/security/sites/{id}/overview` | SECURITY_READ |

### VettingController

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/security/cp/vetting/officers/{guardId}/tier` |  |
| GET | `/api/v1/security/cp/vetting/principals/{principalId}` |  |
| POST | `/api/v1/security/cp/vetting/principals/{principalId}` |  |
| POST | `/api/v1/security/cp/vetting/checks/{checkId}/result` |  |
| GET | `/api/v1/security/cp/vetting/declined` |  |
| POST | `/api/v1/security/cp/vetting/principals/{principalId}/decline` |  |

## 7. Known issues and findings

1. **Fixed in 0044: two active patrol routes at one site.** Round numbers now run per route (migration V331), so two active routes at a site each get rounds 1, 2, 3 and a shift starts normally. The demo data still uses one active route per site, which is fine; add a second active route to a site to see both.
2. **Rotation assignments** are unique on `(guard_id, ends_at)` with nulls treated as equal, so a guard can have only one open-ended assignment. This is intended: a guard cannot be on two open-ended rotations at once.
3. **Evidence storage.** With local file storage instead of database storage, seeded evidence rows will list but cannot be downloaded because the files only exist in the database. Use database storage when demoing.
4. **Complaint numbers.** The app generates them through the numbering service. Demo ones are `CMP-DEMO-nn` so they never collide with real numbers.
5. **Document links** are capped at 500 characters, which is why the seeded documents are tiny PDFs.
6. **Existing test data.** Migrations V105, V106, V119 and V120 already seed the Zeta Earthmoving tenant. The demo script is separate and works on any tenant.
7. **Backend tests not yet run here.** The services added across slice 7 (shift range, patrol rounds, patrol overview, checkpoint admin, gate dashboard, report runs) have unit tests, and the site overview has none. Run the module tests and `ArchitectureVerificationTest` before release.
8. **Switching off a checkpoint on an active route** (fixed in 0044). Open rounds that use it stop waiting for it, and rounds generated later leave it out; switching it back on restores both. Rounds that already finished are not changed.
9. **Screening gate** (fixed in 0044). The pre-shift gate and the guard's screening status now look at the newest record of each screening type, the same rule as Deployment Readiness, so a passed renewal clears an older failure.
10. **NFC tags and Bluetooth beacons** (fixed in 0044) only have to be unique within a tenant, not across all tenants.
