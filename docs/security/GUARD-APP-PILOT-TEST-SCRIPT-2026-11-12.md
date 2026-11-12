# HandyFlow Guard — first run test script (2026-11-12)

Purpose: run the Guard app against your local backend for the first time, key in the data it needs, and check every feature built in patches 0814 to 0829. Nothing in these patches has been run yet, so expect to find faults. Write what you see next to each step and send it back.

Needs: backend running locally with the patches applied (0815 to 0828) and the app started once so migrations V687, V688 and V689 run; an Android emulator (or phone) with the app; Swagger or any API client logged in as a supervisor with SECURITY_MANAGE; a second emulator or a second sign-in for the cross-guard checks (section E).

## A. Test data to key in (supervisor, through Swagger)

Use real-looking values; change the phone numbers to ones you control. `<site>`, `<guard>` and similar are ids returned by the earlier call.

| # | Call | Body |
|---|------|------|
| A1 | `POST /api/v1/security/sites` | `{"name":"Pilot Test Site","latitude":-26.2041,"longitude":28.0473,"contactName":"Site Manager","contactPhone":"0821234567","instructions":"Test site"}` (put the latitude and longitude of where your emulator location will be set; the app's geofence is 300 m) |
| A2 | `POST /api/v1/security/sites/<site>/checkpoints` three times | `{"name":"Main Gate","description":"Front gate"}`, `{"name":"Plant Room"}`, `{"name":"Back Fence"}` |
| A3 | `POST /api/v1/security/patrol-routes` | `{"siteId":"<site>","name":"Night route","intervalMinutes":60,"toleranceMinutes":10}` |
| A4 | `POST /api/v1/security/patrol-routes/<route>/checkpoints` three times | `{"checkpointId":"<cp1>","sequence":0}`, then sequence 1 and 2 |
| A5 | `POST /api/v1/security/guards` | `{"firstName":"Lerato","lastName":"Mokoena","psiraNumber":"TEST-0001","idNumber":"<a test id number>","phone":"<your test phone>","grade":"C"}` — note the guard id and employee code. A second guard (Sipho Dlamini, PSIRA TEST-0002) is needed for section E |
| A6 | `POST /api/v1/security/guards/<guard>/enrol` | `{"pin":"123456","deviceHardwareId":"<the Phone ID shown at the bottom of the app's sign-in screen>"}` — the PIN will be forced to change on first sign-in only if the guard is flagged so; enrolment clears that flag |
| A7 | `POST /api/v1/security/shifts` | `{"siteId":"<site>","guardId":"<guard>","startAt":"<now minus 5 minutes, ISO UTC>","endAt":"<now plus 3 hours>"}`. The shift must start within 30 minutes either side of the moment you tap Start shift, or no shift is linked and the site screens will say so |
| A8 | Print or show the QR codes: `GET /api/v1/security/sites/<site>/checkpoints/<cp>/qr-image` (or `/qr-sheet`) | open the image on your monitor and point the emulator camera at it, or paste the `qr-payload` text where the app lets you |
| A9 | Post orders: `POST /api/v1/security/sites/<site>/posts` `{"name":"Main Gate post"}`; `POST /api/v1/security/sites/<site>/post-orders` with `{"instructions":"Check every vehicle.","duties":"Log all visitors.","emergencyProcedures":"Press panic, call control room.","restrictedAreas":"Plant room: staff only.","accessRules":"No entry after 22:00 without host."}` (site-wide, no postId) and a second with `"postId":"<post>"`; then `POST /api/v1/security/post-orders/<id>/publish` for each |
| A10 | `POST /api/v1/security/access-points` | `{"siteId":"<site>","name":"Main Gate","description":"Vehicle gate"}` |
| A11 | Emulator location | Extended controls, Location: set to the site's latitude and longitude from A1 |

## B. Sign-in and shift

| # | Do | Expect |
|---|----|--------|
| B1 | Open the app, read the Phone ID on the sign-in screen | An id is shown; you used it in A6 |
| B2 | Sign in with the guard's phone and PIN 123456 | Home screen "Hello, Lerato". Wrong PIN: a clear message, no crash |
| B3 | Home before starting | "Next shift: Pilot Test Site", times shown |
| B4 | Tap Start shift (allow location) | "On duty at Pilot Test Site". Check in Swagger `GET /api/v1/security/sessions/current` that the session has the shift and `startGeofenceOk` true |
| B5 | Sign out, set the emulator location 5 km away, sign in, Start shift | Session opens, `startGeofenceOk` false (not refused: that decision is yours). Close the session in Swagger before continuing |

## C. Patrol, scan, incident, panic

| # | Do | Expect |
|---|----|--------|
| C1 | Patrol rounds | Rounds listed with the three checkpoints in order, none scanned |
| C2 | Scan checkpoint 1 | "Scanned"; round shows 1 of 3 |
| C3 | Scan the same code twice quickly | One scan recorded, no error to the guard (retry safe) |
| C4 | Report an incident with title, details, severity High, two photos | Saved; in Swagger `GET /api/v1/security/incidents` shows it reported by Lerato, and both photos are attached to it |
| C5 | Airplane mode on. Scan checkpoint 2, report an incident with one photo, press panic | Each says it is saved and waiting; the banner shows items waiting; Sign out is disabled |
| C6 | Airplane mode off, wait up to 30 s or tap the banner | Banner clears. Server shows the scan, the incident with its photo, and the panic alarm. Panic is sent first. Times are when it happened on the phone, with a "raised on the guard's phone" note if more than 2 minutes late |
| C7 | Turn airplane mode on, scan, then force-close and reopen the app before turning it off | The waiting item survives and is sent later |

## D. Post orders, swaps, equipment, gate, end shift

| # | Do | Expect |
|---|----|--------|
| D1 | My post orders | Site order text shown; the post "Main Gate post" listed |
| D2 | Acknowledge the site order, then acknowledge again | "You have acknowledged this version". Swagger acknowledgements for that order list one row for Lerato |
| D3 | In Swagger publish a new version of the site order, then tap acknowledge in the app without reopening | Refused with a message to read the newer version; screen reloads |
| D4 | Shift swaps: create a second shift tomorrow (Swagger A7 with later times), then in the app ask to swap it | Appears under Your requests as waiting. Cancel it; shows cancelled |
| D5 | Equipment: sign out Radio "R-014" | Listed under With you now |
| D6 | End shift while the radio is out | End shift button disabled with the list of what is still out |
| D7 | Return the radio as Good | Disappears from the list |
| D8 | Gate log: log an arrival (Visitor, Thabo Nkosi, company ACME, vehicle CA123456) | Appears under On site now. Double-tap Log arrival fast: only one entry |
| D9 | Add an ID photo and a vehicle disc photo from the visitor card | "Photo saved". Swagger `GET /api/v1/security/gate-entries/<id>/attachments` lists two |
| D10 | Log departure | Removed from On site. A second attempt (stale screen) gives "already logged as departed" |
| D11 | End shift with handover notes | Shift closes; Home shows the next shift |

## E. Things that must be refused (security checks)

Use a second guard, enrolled the same way, with a shift at a second site (create a second site, gate and post for this).

| # | Do | Expect |
|---|----|--------|
| E1 | With guard 1's token call `GET /api/v1/guard/me/shifts` | Only guard 1's shifts |
| E2 | Guard 1's token: `POST /api/v1/guard/sessions/open` with guard 2's device id | Refused or opens only guard 1's own session; never a session for guard 2 |
| E3 | Guard 1's token: `POST /api/v1/guard/gate/entries` with an access point of site 2 | 404 |
| E4 | Guard 1's token: `POST /api/v1/guard/gate/entries/<site 2 entry>/exit` | 404 |
| E5 | Guard 1's token: `GET /api/v1/guard/my-post/posts/<site 2 post>` | 404 |
| E6 | Guard 1's token: `POST /api/v1/guard/incidents/<guard 2 incident>/evidence` | 404 |
| E7 | Guard 1's token: close guard 2's session id | 404 |
| E8 | Revoke guard 1's tokens in Swagger (`/guards/<id>/revoke-tokens`) while the app is open | App returns to sign-in on its next call; nothing is lost from the queue |

## F. What to send back

For every row that did not behave as expected: the row number, what you saw, a screenshot, and the backend log lines around the time. Also tell me: how long Start shift took, whether the camera scan worked from the monitor, and anything a guard would find confusing.
