# HandyFlow Guard: what exists, what is missing, what to build first

Assessed 2026-11-12 on branch `fuel-stage0`. Discovery only: nothing was installed, built or changed. Status words follow the mobile documents: `[Verified]` seen in code, `[Proposed]` recommendation, `[Requires approval]` needs a product-owner decision.

Not run: no Java was compiled or executed for this assessment, and no guard flow was exercised on a device. Everything below is from reading code and the earlier audits (22 Oct readiness audit, 12 Nov mobile documents).

## 1. Where we are

- `[Verified]` **There is no Guard app in the repository.** Code comments call it "HandyFlow Shield"; nothing by that name exists under `HandyFlow-Apps/`. The four folders there are a Fuel scaffold, a Fuel prototype (named "handyfarm"), the Agriculture app and a desktop clinic app. Guard starts from nothing, so it can follow the mobile standards from day one.
- `[Verified]` The backend is well ahead. Security is the largest module (about 300 endpoints). The guard-facing part is already separated onto its own token and path (`/api/v1/guard/**`, filter `GuardJwtFilter`, tokens stored and revocable in `security_guard_tokens`).

## 2. What a guard can already do through the API

| Need | Endpoint | State |
|---|---|---|
| Sign in with phone or employee code + 6-digit PIN | `POST /api/v1/auth/guard/login` | `[Verified]` 13 h token, lockout after 5 failures for 30 min, forced PIN change flag |
| Move to a new phone | `POST /api/v1/auth/guard/activate-device` (6-digit supervisor code, 15 min, rate limited) | `[Verified]` |
| Change PIN, log out | `POST /api/v1/guard/auth/change-pin`, `/logout` | `[Verified]` |
| Start and end a shift | `POST /api/v1/guard/sessions/open`, `/{id}/close`, `GET /current` | `[Verified]` see risk R1 |
| Location pings | `POST /api/v1/guard/sessions/{id}/location` | `[Verified]` |
| Equipment (radio, torch, firearm) out and back | `.../resources/checkout`, `/resources/{id}/return` | `[Verified]` |
| Scan a checkpoint (QR, NFC, BLE, GPS, manual) | `POST /api/v1/guard/checkpoints/scan` | `[Verified]` guard taken from the token, not the body |
| Duress / panic | `POST /api/v1/guard/duress` | `[Verified]` |
| Report an incident | `POST /api/v1/guard/incidents` | `[Verified]` text only, see gap G2 |
| Post orders for my post, acknowledge | `GET /api/v1/guard/my-post`, `/posts/{id}`, `/orders/{id}/acknowledge` | `[Verified]` |
| Gate log: who is on site, sign people in and out, attach photo | `/api/v1/guard/gate/**` | `[Verified]` the one place photos can be sent today |
| Shift swaps: mine, offer, accept, cancel | `/api/v1/guard/shifts/swaps/**` | `[Verified]` |

## 3. Risks to close before any guard uses a phone

| # | Risk | Evidence | Why it matters |
|---|---|---|---|
| R1 | **Clock-in trusts the phone.** The open-session request carries `guardId`, `pinVerified`, `faceMatchConfidence` and `geofenceOk` from the client, and the service stores them as given. The guard is picked from the body, not the token. | `OpenSessionRequest`, `DeviceSessionService.openSession` | Anyone holding a valid guard token on a registered device can clock in as another guard, or report a passed PIN/face/geofence that never happened. Attendance and pay evidence is only as good as this. This is audit item SEC-02 and it is **still open**. `[Proposed]` take the guard from the token, verify the PIN on the server, compute geofence on the server from the site's saved position, treat the face score as a recorded signal, not proof. |
| R2 | **Duress names who triggered it in the body** (`triggeredByGuardId`). | `TriggerDuressRequest` | A false or misattributed duress alert. `[Proposed]` use the token's guard and ignore the body field. |
| R3 | **Two identity modes for devices.** Guard endpoints under `/api/v1/security/sessions/**` use a tenant JWT; the same actions under `/api/v1/guard/**` use the guard token. Duplicates can drift. | `DeviceSessionController`, `GuardSessionController` | `[Proposed]` the app uses only `/api/v1/guard/**`. |
| R4 | **Revoked tokens on staff paths** (audit PLT-01, untyped tokens on one key). Not re-checked in this pass. | 22 Oct audit | Needs a quick re-test before launch. |
| R5 | **Entitlement check**: about 200 Security endpoints had no subscription check in the October audit; not re-checked here. | audit | Re-test. |

## 4. Gaps the app needs (backend work)

| # | Gap | Why |
|---|---|---|
| G1 | **No guard-side "my shifts" and "my rounds".** Rounds are listed only for staff (`/api/v1/security/patrols`), shifts only by staff. A guard cannot see tonight's roster, route or checkpoint order. | Core screen |
| G2 | **Incident photos and video cannot be uploaded by a guard.** Evidence upload exists only on the staff incident path. | Core screen |
| G3 | **No offline-safe writes.** Scan, incident, duress and clock-in requests have no client-generated id, so a retry after a dropped signal can create duplicates, and a late scan has no "recorded at" time. | Patrols happen in basements and remote sites. Fuel already uses this pattern. |
| G4 | **No push token registry** for the guard device, so alerts (shift change, missed round, supervisor message) cannot reach a locked phone. | Needed for supervisor-to-guard alerts |
| G5 | **No "my attendance and pay summary"** for the guard. | Common first request from guards; payslip PDF exists on the staff side |
| G6 | **No supervisor view on a phone**: live guards, missed rounds, approve swaps, close a session. Exists on the web only. | Role the roadmap calls "supervisors" |

## 5. Recommended first release (Guard v1)

Scope is a single-purpose guard app, not a general HR app.

1. Sign in and activate device (PIN, forced change, lock after failures).
2. Clock in and out with server-side checks (closes R1).
3. Tonight's shift and patrol rounds, checkpoints in order.
4. Scan QR/NFC, with GPS recorded, working offline with a sync queue.
5. Panic button, always one tap, works with poor signal and falls back to SMS where the backend supports it `[Requires approval]`.
6. Report an incident with photos.
7. Post orders and acknowledgement.
8. Shift swaps.
9. Equipment checkout.

Later: gate log on the phone, supervisor mode, attendance summary, face check done on the server, push alerts, check calls (no backend yet).

Technology follows `MOBILE_TECHNOLOGY_STANDARDS.md`: Expo SDK per the decision still open there, TypeScript, secure storage for the token, SQLite write queue (same as the Agriculture app), TanStack Query.

## 6. Order of work

| Step | What | Size |
|---|---|---|
| A | Backend hardening: R1, R2, R3 decisions, plus tests proving a guard cannot act as another guard | S to M |
| B | Backend additions: G1 (my shifts, my rounds), G2 (incident evidence), G3 (client id and recorded-at on scan, incident, duress, session), G4 (device and push token) | M |
| C | App scaffold in the agreed monorepo layout, auth, secure storage, API client, offline queue, design tokens | M |
| D | Screens: shift, patrol, scan, panic, incident, post orders, swaps | L |
| E | Test script for security staff and a pilot on one site; then supervisor mode | M |

A and B ship as patches the same way Fuel did. C onward is blocked on the repository decisions below.

## 7. Decisions needed `[Requires approval]`

1. **Repository layout and Expo SDK** (the open items 1 and 2 in the mobile overview): monorepo `HandyFlow-Apps/{apps,packages}`, current stable SDK, fresh scaffold. Guard should not be the first app on the old SDK 52.
2. **Clock-in proof**: is a server-checked PIN plus geofence enough, or must a face check be required? A face score computed on the phone cannot be trusted by itself. Options: keep it as a recorded signal; run matching on the server; or drop it.
3. **Shared tablets**: do some posts use one shared device for several guards (the demo data does)? If yes, the guard signs in on the shared device with their own PIN each time, and the token, not the device, decides who is clocked in. This changes R1's fix.
4. **Panic with no data signal**: allow an SMS fallback to the control room? It has a cost and needs a number per tenant.
5. **Should Guard be its own store app or part of a shared HandyFlow Work app?** The assessment recommends a separate field app for guards (own icon, locked-down, kiosk friendly).
6. **Pilot site and a named person to test** before general release.

## 8. Not covered

Close protection, armoury inventory, control-room camera screens, client portal and payroll are not guard-app scope. The competitor comparison in the performance backlog was not re-checked.

## 9. Status: step A (patch 0815, 2026-11-12)

Written, not compiled or run here (Maven Central is not reachable from this workspace; only a syntax check was done). Run `mvn -q test "-Dtest=SessionGeofenceTest,DeviceSessionServiceGuardTest,ControllerAccessCoverageTest"` with the app stopped.

| Item | Change |
|---|---|
| R1 clock-in | `POST /api/v1/guard/sessions/open` now takes `{deviceHardwareId, latitude?, longitude?, accuracyMetres?}`. The guard is the token's guard. The PIN flag is set by the server (the PIN was checked at login). The geofence is computed from the phone's position and the site's saved position (300 m, plus up to 50 m for GPS error; a poor fix or missing position is recorded as unknown, not passed). The phone's face score is no longer stored. The staff route (`/api/v1/security/sessions/open`, supervisor on behalf of a guard) is unchanged. |
| R2 panic | `triggeredByGuardId` in the body is ignored; the token's guard is used. |
| New hole found and closed | On the guard routes, close, location ping, equipment checkout and equipment return did not check the session or item belonged to the calling guard. They now do (another guard's session reads as not found), and the guard route's close sets the PIN flag from the token and drops the face score. `GET /current` shows only the caller's own session. |
| Works for shared tablets | Identity is the signed-in guard's token, so each guard signing in on a shared device is handled the same way. Decision 3 now only affects the app screens. |

Not changed: a per-site geofence radius (needs a column and screen; 300 m is a constant), the face check (decision 2), and whether a clock-in with an unknown or failed geofence should be refused or just flagged for the supervisor (today it is recorded only).

## 10. Status: step B1 (patch 0816, 2026-11-12)

Written, not compiled or run here (same limit as step A). Run `mvn -q test "-Dtest=GuardMyWorkServiceTest,IncidentCaseServiceTest,ControllerAccessCoverageTest"` with the app stopped.

| Gap | Change |
|---|---|
| G1 my shifts, my rounds | `GET /api/v1/guard/me/shifts` (12 hours back to 7 days ahead, with site names). `GET /api/v1/guard/me/rounds`: rounds of the shift on the guard's open session, each with its checkpoints in route order and which are scanned. 409 `NO_OPEN_SESSION` until the guard clocks in. The guard is always the token's guard. Checks module entitlement. |
| G2 incident photos | `POST /api/v1/guard/incidents/{id}/evidence` (multipart `file`, optional `label`). Only the guard who reported the incident; another guard's incident is a 404. At most 20 files per incident from the field. File type and size limits are the evidence store's. |
| New hole closed | The guard incident route took the reporting guard from the request body, so a guard could file as someone else or as nobody. It now uses the token's guard. |

Still open from section 4: G3 (client ids and recorded-at for scan, incident, panic, clock-in, needs a migration), G4 (push token), G5, G6. The other guard controllers are still on the "no entitlement check" debt list; only the new controller checks it.

## 11. Status: step B2 (patch 0817, 2026-11-12)

Written, not compiled or run here. Adds migration **V687** (`security_client_requests`). Run `mvn -q test "-Dtest=OfflineTimestampTest,GuardOfflineWriteServiceTest,ControllerAccessCoverageTest"` with the app stopped, then start the app once so V687 runs.

| Gap | Change |
|---|---|
| G3 duplicates | The scan, incident and panic endpoints on the guard routes accept an optional `clientRef` (a UUID the phone makes once per action). The first call does the work and remembers the id; a retry with the same id returns the first result and creates nothing. Two retries racing each other cannot both win (unique key; the loser gets 409 `REQUEST_IN_PROGRESS` and retries). An id first used by another guard is refused (409 `CLIENT_REF_IN_USE`). With no `clientRef` everything behaves as before. |
| G3 late time | The same three accept an optional `recordedAt`. A scan is stamped with it (kept if it is from the last 48 hours, up to 5 minutes ahead; further ahead it is replaced by server time; older than 48 hours is refused 400 `RECORDED_AT_TOO_OLD`). An incident or panic alarm that arrives more than 2 minutes late gets a line added to its description saying when the phone recorded it and when it was received. |
| Clock-in | Stays online-only on purpose: it has server checks and conflicts that cannot be replayed safely. |

Open: (1) a scan that arrives after its round was already marked missed does not count towards a round, because round routing only looks at open rounds; a grace window for late syncs is a rule decision. (2) Incident photo uploads have no client id, so a retried upload can attach twice (cap 20). (3) The ledger is never pruned; a cleanup after 90 days is easy to add. (4) The staff routes accept the new fields and ignore them.

## 12. Status: incident photos (patches 0819 backend, 0820 app, 2026-11-12)

- Backend: `POST /api/v1/guard/incidents/by-ref/{incidentRef}/evidence` (multipart `file`, required `photoRef`, optional `label`). The incident is found through the idempotency ledger by the clientRef the phone made, so a photo can be sent before the phone knows the server id; until the report has arrived the answer is 409 `INCIDENT_NOT_SENT_YET` and the phone retries. `photoRef` is recorded as kind EVIDENCE (migration V688 widens the kind check), so a retried upload returns the first result. Only the guard who reported the incident can attach (existing rule), 20 files per incident, type and size limits are the evidence store's.
- App: up to 5 photos per report (`expo-image-picker`, quality 0.6), copied into app storage, queued as EVIDENCE items after the report, scans and panic; multipart upload with a 60 s timeout; the local copy is deleted after success.
- Written, not compiled or run (Java). Tests added to `GuardOfflineWriteServiceTest` (photo before report, first upload, retry). App: `tsc` clean, 19 jest tests, `expo export` succeeds; the camera flow has not been run on a device.
- Open: a failed report leaves its photos retrying until parked; photos of a dismissed failed item stay in storage; no compression beyond quality 0.6, so a very large photo could still exceed 5 MB and be refused.

## 13. Status: post orders and shift swaps (patches 0821 backend, 0822 app, 2026-11-12)

- Backend (0821, written not compiled): the guard endpoints already existed. Acknowledging an order now (a) refuses an order that is not ACTIVE (409 `ORDER_NOT_CURRENT`), (b) takes the version the guard read and refuses a newer one (409 `ORDER_VERSION_CHANGED`) so an acknowledgement cannot claim text the guard never saw, (c) returns the first record on a repeat instead of adding a duplicate, (d) uses `OfflineTimestamp` for an offline time (future clamped, older than 48 h refused). A post's order is only readable for a post at the guard's own site. New `PostOrderServiceGuardTest`.
- App (0822): My post orders screen (site order, post list, per-post order, contacts, acknowledge) and Shift swaps screen (request an open swap, accept one proposed to you, cancel yours). `tsc` clean, 19 jest tests, `expo export` ok; not run on a device, and the two screens have no unit tests.
- Open: acknowledgement needs signal (reading needs it anyway); an order from another site can still be acknowledged by id (it only creates the guard's own record) until the order's site is checked against the guard's shifts; no colleague picker for swaps; open swaps are not visible to other guards, so they are decided by the supervisor; whether a swap request should be allowed close to shift start is a rule decision.

## 14. Status: equipment (patches 0823 backend, 0824 app, 2026-11-12)

- Backend (0823, written not compiled): `GET /api/v1/guard/sessions/resources/mine` (the caller's own unreturned items, no guard id parameter); checkout and return answer with a small `GuardCustodyView` instead of the raw entity; an unknown resource type or condition is a 400 and returning an item twice is a 409 (both were 500s); item names are trimmed and capped at 100 characters. Four tests added to `DeviceSessionServiceGuardTest`.
- App (0824): Equipment screen (items with you, return as good/damaged/missing, sign out RADIO/KEY/VEHICLE/OTHER). The clock-out "I returned all equipment" switch is gone: the End shift button stays disabled while the server shows items out. `tsc` clean, 19 jest tests, `expo export` ok; not run on a device.
- Open: the server still accepts a close with resourcesReturned=true while items are out (existing rule, the app no longer sends a claim it cannot back); no supervisor alert on a MISSING return; the guard route still accepts FIREARM (the app does not offer it); no witness picker; equipment actions need signal.

## 15. Status: gate log (patches 0825 backend, 0826 app, 2026-11-12)

- Backend (0825, written not compiled): the guard gate endpoints took "who is logging" from a `deviceHardwareId` in the body, so a guard could name another device and have the entry attributed to someone else, and any gate, entry or photo target in the tenant could be used from any site. Now the logger is the signed-in guard, the gate/entry must be at the site of the guard's open shift (anything else is 404), a repeat departure is a 409 (was a 500), and the photo is attributed to the signed-in guard. Arrivals take an optional `clientRef` (migration V689 adds kind GATE_ENTRY), so a repeat returns the first entry. The old `deviceHardwareId` fields are still accepted and ignored. Staff routes untouched. Tests: `GateAccessServiceGuardTest` (4), two more in `GuardOfflineWriteServiceTest`.
- App (0826): Gate log screen (on-site list with Log departure, arrival form). `tsc` clean, 19 jest tests, `expo export` ok; not run on a device.
- Open: gate photos (ID, disc) not in the app; no offline queue for gate entries; ID numbers are collected, so how long they are kept and who may see them is a POPIA decision for the tenant; no overstay alert in the app.
