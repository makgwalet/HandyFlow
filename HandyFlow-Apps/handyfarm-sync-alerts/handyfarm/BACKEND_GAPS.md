# HandyFlow Fuel — Backend Gaps Log

Gaps found while building the mobile foundation and the Dispense Fuel flow,
cross-referenced against the report's own "Phase 0" list (`handyfuel.txt`,
section 26). Each entry says which mobile screen hit the gap, what's
missing, and what shape would unblock it. This file grows as later phases
(Dip, Receive, Deliveries) are built.

Legend: 🔴 blocks going live · 🟡 workable with a mobile-side stopgap (noted)

---

## Phase 1 — Foundation (auth shell)

### 🔴 No Identity/OTP service
**Hit in:** `LoginScreen`, `OTPScreen`
**Gap:** There's no `POST /auth/otp` / `POST /auth/otp/verify` pair, and no
concept of a `Role` (`FUEL_MANAGER` / `OPERATOR` / `DRIVER`) attached to a
user record — matches the report's own point in section 2 that Fuel
currently identifies people via free-text (`driverName`, `authorisedBy`,
`receivedBy`) rather than real identities.
**Stopgap:** mocked in `authStore.ts` — a local directory of 3 users, any
6-digit code accepted, dev-only role switcher on the login screen.
**Needed shape:** `POST /auth/otp {phone}` → OTP sent; `POST /auth/otp/verify
{phone, code}` → `{accessToken, refreshToken, user: {id, name, role, site}}`.

### 🔴 No device registration endpoint
**Hit in:** `authStore.restoreSession` (conceptually — not yet wired)
**Gap:** Spec section 27/28 calls for a `MobileDevice` resource
(`deviceId`, `platform`, `appVersion`, `status: ACTIVE|REVOKED|BLOCKED|EXPIRED`)
so a lost phone can be revoked. Nothing like this exists yet. Right now the
mobile session is just a SecureStore blob with no server-side handle to kill it.
**Needed shape:** `POST /devices/register` on first login, returning a
`deviceId` the app stores; server needs a way to reject refresh-token use
from a revoked device.

---

## Phase 2 — Dispense Fuel flow

### 🔴 No Fleet/Asset vehicle search endpoint
**Hit in:** `SelectVehicleScreen`
**Gap:** Report section 8 explicitly says vehicle selection "should consume
Fleet/Asset data rather than asking the operator to type a name" — but no
such endpoint exists yet. Currently mocked in `data/mockVehicles.ts`.
**Needed shape:** `GET /fleet/vehicles?search=&siteId=` → paginated list of
`{id, registration, label, fuelType, lastOdometer}`.

### 🔴 Vehicles have no per-vehicle fuel allowance
**Hit in:** `QuantityScreen` (the "Maximum allowed: 500 L" cap shown in the
mockup)
**Gap:** The current Fleet/Asset model (per the report) has no field for a
configurable max-litres-per-fill cap. Mobile can't enforce a limit the
backend doesn't know about — right now it's a hardcoded `maxAllowedLitres`
in the mock, which is not authoritative and would need to be re-validated
server-side anyway.
**Needed shape:** either a field on the vehicle/asset record, or a
transaction-policy rule keyed by vehicle type — see next entry.

### 🔴 No transaction policy engine
**Hit in:** `QuantityScreen` → `AuthorisationScreen` gate
**Gap:** Report section 9 describes tiered rules ("> X litres → require
PIN, photo, odometer, approval") but there's no backend resource for this
today. Mobile currently hardcodes a `HIGH_RISK_LITRES` threshold
client-side, which is a stopgap only — a threshold enforced solely on the
device can be bypassed by a modified client, and a tenant can't tune it
without an app release.
**Needed shape:** `GET /fuel/transaction-policies` → thresholds and
required-evidence rules per tenant/fuel-type, fetched at device-registration
time and re-validated server-side on submit regardless of what the client sent.

### 🔴 No operator PIN verification
**Hit in:** `AuthorisationScreen`
**Gap:** Ties directly into the Identity gap above — there's no
`operatorPin` credential type, so "Authorised by" is currently just
whichever user is logged in, with a locally-accepted 4-digit PIN. This
does not produce a real audit trail.
**Needed shape:** `POST /auth/operator-pin/verify {userId, pin}` — separate
from account login, per report section 10, so a shared-device operator can
re-confirm identity without a full re-login.

### 🔴 Dispatch endpoint has no idempotency contract
**Hit in:** `EvidenceScreen` submit handler
**Gap:** Report sections 22 and 26 flag this as Phase-0-critical: every
offline write needs a client-generated UUID the server can deduplicate on.
Right now `transactionQueueStore.ts` generates a `clientRequestId` via
`expo-crypto` and queues it locally, but there is no `POST /fuel/dispatches`
that accepts an `Idempotency-Key` (or an equivalent field) and returns
`PENDING → CONFIRMED`/`CONFLICT` — so nothing after "saved locally" can be
built yet.
**Needed shape:** `POST /fuel/dispatches` header `Idempotency-Key:
{clientRequestId}`; response `{status: CONFIRMED}` or `{status: CONFLICT,
reason, currentTankLevel}`.

### 🔴 Fuel domain has no GPS fields at all
**Hit in:** `EvidenceScreen` (GPS auto-capture via `expo-location`)
**Gap:** Confirmed while wiring this screen — the mobile side can capture
`{lat, lng, accuracy, capturedAt}` today (real device GPS, not mocked), but
there's nowhere on the backend dispatch/receipt/delivery models to send it.
Report section 18 flags this as a complete gap, not a partial one.
**Needed shape:** add `location {lat, lng, accuracy, capturedAt}` to the
fuel dispatch, dip, receipt, and delivery-event schemas.

### 🔴 No evidence/attachment upload pipeline
**Hit in:** `CameraCaptureScreen` → `EvidenceScreen`
**Gap:** Photo is captured on-device (`expo-camera`) and held as a local
URI, exactly as the report's section 29 recommends ("photos shouldn't block
the transaction"). But per section 19/29, the actual upload path doesn't
exist yet, so there is nowhere to POST the file or any way to associate it
with a dispatch after the fact.
**Needed shape:** `POST /evidence` (multipart) → `{evidenceId, url}`, plus
a way to attach `evidenceId[]` to a dispatch either in the same call or a
follow-up `PATCH /fuel/dispatches/{id}`.

### 🟡 No sync/queue status API
**Hit in:** `syncStore.ts` (`pendingCount`/`failedCount` are still fully
client-local)
**Gap:** Nothing to confirm "has my transaction actually reached
HandyFlow?" (report section 23's framing) beyond the immediate dispatch
response. Workable short-term because the immediate response covers the
common case, but a real Sync Centre (a later phase) needs a status list.
**Needed shape:** `GET /sync/status?since=` → list of `{clientRequestId,
status, serverId?, error?}` for everything the device has queued.

---

_Next phase (Record Dip / Reconciliation) will likely surface: variance
acknowledgement workflow (section 13 — doesn't exist on the backend at
all today), and book-level vs physical-dip comparison at read time._

---

## Phase 3 — Record Dip / Reconciliation

### 🔴 No variance acknowledgment/investigation workflow
**Hit in:** `VarianceDetailScreen` (Add Note / Add Evidence / Resolve)
**Gap:** Confirmed exactly as the report predicted in section 13 — there is
no `OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED` state machine on the
backend. Mobile currently creates a variance record entirely client-side
(`varianceStore.ts`, in-memory, lost on app restart) and the three action
buttons on the detail screen are explicit "coming later" no-ops rather than
faked success, since faking a resolution would misrepresent what's
actually persisted.
**Needed shape:** `POST /fuel/variances` (created automatically when a
flagged dip is submitted, not called directly by mobile), `PATCH
/fuel/variances/{id}` for status transitions + notes, `POST
/fuel/variances/{id}/evidence` for follow-up attachments.

### 🔴 No dip endpoint / no variance-policy thresholds
**Hit in:** `ReconciliationScreen`, `VarianceReasonScreen`
**Gap:** Same shape as the dispatch idempotency gap, but for dips —
there's no `POST /fuel/dips` to sync `TANK_DIP` queue items to at all. The
warning/critical variance thresholds (`VARIANCE_WARNING_LITRES` /
`VARIANCE_CRITICAL_LITRES` in `dipFlowStore.ts`) are hardcoded
client-side; a real deployment needs these configurable per tenant/tank
(a 50 L variance means something very different on a 2,000 L AdBlue tank
vs. a 20,000 L diesel tank, and a flat litre threshold doesn't account
for that).
**Needed shape:** `POST /fuel/dips` with the same idempotency contract as
dispatches; `GET /fuel/variance-policies` returning thresholds as a
percentage of tank capacity, not a flat litre figure.

### 🟡 Book level is read from the same tank snapshot as everything else
**Hit in:** `ReconciliationScreen` (`bookLevel = tank.currentLitres`)
**Gap:** Not a new gap, but worth flagging precisely: reconciliation is
only meaningful if "book level" reflects every dispatch/receipt up to the
moment of the dip, including anything still sitting in another operator's
offline queue. The conflict-handling gap from Phase 2 (section 25 — server
must remain authoritative on tank balance) applies here too, arguably more
so, since a stale book level produces a false variance reading rather than
just a rejected transaction.

---

## Phase 4 — Driver Deliveries

### 🔴 Delivery lifecycle endpoints don't exist
**Hit in:** `DeliveryDetailScreen` (Start), `ArrivalScreen` (Confirm),
`DeliveryNotePhotoScreen` (Complete)
**Gap:** Confirmed exactly as the report already flagged in section 16/26
— starting a delivery (`SCHEDULED → IN_TRANSIT`) "is currently impossible
through the API despite the domain model supporting it." The same is true
for every other transition here: no `GET /deliveries/{id}`, no `PATCH
/deliveries/{id}/start`, `/arrive`, `/complete`. Mobile currently manages
the entire lifecycle in local Zustand state (`deliveryStore.ts`), which
means a driver's delivery progress is lost if the app is reinstalled and
two devices can't share a driver's delivery list at all.
**Needed shape:** at minimum `GET /deliveries?driverId=&status=`, `PATCH
/deliveries/{id}/start`, `PATCH /deliveries/{id}/arrive`, `PATCH
/deliveries/{id}/complete` — each idempotent per the same contract as
Phase 2/3.

### 🔴 No receiver signature upload path
**Hit in:** `SignatureScreen`
**Gap:** Matches report section 19 exactly: "the actual
receiverSignatureUrl isn't currently populated because the upload path
doesn't exist." Mobile captures a real signature as vector strokes
(`SignatureStroke[]`) rather than an image — partly a deliberate choice
(see `SignaturePad.tsx` comment: no `react-native-view-shot` dependency
for a PNG snapshot, and nowhere to upload one anyway), but the underlying
blocker is the same evidence-pipeline gap from Phase 2.
**Needed shape:** either accept a vector signature format directly, or add
image-export + the same `POST /evidence` pipeline other photos need.

### 🔴 No problem-report endpoint
**Hit in:** `ReportProblemScreen`
**Gap:** Spec section 35 describes this flow in detail but there's nothing
to sync it to. `reportProblem()` only updates local state today.
**Needed shape:** `POST /deliveries/{id}/problems {reason, notes, photoUri,
gps, reportedAt}`.

### 🟡 Geofence radius and receipt numbering are both client-side guesses
**Hit in:** `ArrivalScreen` (`GEOFENCE_RADIUS_METERS = 150`),
`DeliveryNotePhotoScreen` (`completeDelivery()` generates
`FDR-2026-XXXX` locally)
**Gap:** Neither is safe as a permanent client-side value. A fixed 150 m
radius doesn't account for large sites (a mine site's "arrival" point is a
very different geofence than a small farm gate), and a client-generated
receipt number can collide across drivers/devices — it must be
server-assigned like any other authoritative identifier.
**Needed shape:** geofence radius as a per-site field (ties into the same
Fleet/Asset gap from Phase 2); receipt number returned by the `complete`
endpoint response, not generated on-device.

---

## Phase 5 — Receive Fuel

### 🔴 No supplier/vendor master data endpoint
**Hit in:** `SupplierDetailsScreen`
**Gap:** Same shape as the Fleet/Asset gap from Phase 2, but for
suppliers — mocked in `data/mockSuppliers.ts`. There's also no
waybill-number uniqueness check, so nothing stops the same delivery note
being captured twice by two different operators.
**Needed shape:** `GET /suppliers?search=`; ideally `POST
/fuel/receipts` validates waybill number uniqueness per supplier
server-side rather than relying on operators not to double-enter.

### 🔴 No receipt endpoint (same idempotency gap as everywhere else)
**Hit in:** `ReceiveReconciliationScreen` submit handler
**Gap:** No `POST /fuel/receipts` to sync `FUEL_RECEIPT` queue items to.
Same contract needed as dispatches/dips/deliveries.

### 🟢 Variance investigation entity should span Dip AND Receive, not be dip-specific
**Hit in:** `ReceiveVarianceReasonScreen` — reuses `useVarianceStore` and
`VarianceDetailScreen` from the Dip phase directly, unchanged.
**Note (design suggestion, not strictly a gap):** building this flow
confirmed that a receiving-quantity variance (waybill vs. dip-measured
actual) and a dip variance (book vs. physical) are the same kind of
investigation from an operator's point of view — same evidence
requirements, same OPEN→RESOLVED lifecycle. Worth designing the backend's
variance/investigation resource as source-agnostic (`sourceType: 'DIP' |
'RECEIPT'`) rather than modeling it only off fuel dispatches, so the
mobile app isn't the only place this gets unified.

### 🟡 Three-way match (waybill vs. dip) has no server-side equivalent
**Hit in:** `DipReadingsScreen` / `ReceiveReconciliationScreen`
**Gap:** Mobile computes "actual received" from pre/post dip readings and
compares it to the waybill quantity entirely on-device. This is a
reasonable capture-time UX, but the backend needs to persist all three
numbers (waybill quantity, pre-dip, post-dip) rather than just a single
"received" figure, or the three-way match can never be re-verified or
audited later — only the mobile app's one-time comparison would exist.

---

## Phase 6 — Shift Reconciliation

### 🔴 No shift lifecycle endpoints at all
**Hit in:** `ShiftDashboardScreen` (start), `ShiftCloseScreen` (close)
**Gap:** Nothing like `POST /shifts/start` or `PATCH /shifts/{id}/close`
exists — this is a completely new resource, not a variant of a gap
already flagged. `shiftStore.ts` holds the entire shift lifecycle
client-side (single active shift, lost on app restart), which also means
two operators can't be prevented from both starting a shift on the same
tank, and a manager has no way to see who currently has a tank "checked
out."
**Needed shape:** `POST /shifts` `{tankId, operatorId, openingLevel}` →
`{shiftId, status: OPEN}`; `PATCH /shifts/{id}/close`
`{physicalReading, explanation?}` → server computes and returns the
authoritative variance, rather than trusting the client's math.

### 🟢 Shift Received/Dispensed totals are only as good as the local queue
**Hit in:** `useShiftTotals.ts`
**Gap (design note, not new):** This hook is the nicest piece of
integration in the app so far — it sums the exact same
`FUEL_RECEIPT`/`FUEL_DISPATCH` queue items the Dispense and Receive Fuel
flows created — but it's built entirely on the in-memory queue from
Phase 2, so it only reflects transactions made *in this app session on
this device*. The moment a real dispatch/receipt endpoint exists (Phase 2
gap), shift totals should be recomputed from the server's transaction
history for that tank/window, not the client's local queue, or a shift
that spans an app restart — or involves a second device on the same
tank — will under-report.

### 🟢 Variance entity reused a third time — now three sources deep
**Hit in:** `ShiftCloseScreen` — reuses `useVarianceStore` and
`VarianceDetailScreen` exactly as Dip and Receive Fuel do, adding a third
source (`shift close` variance) to the same shared entity without any
special-casing.
**Note:** Reinforces the Phase 5 suggestion: the variance/investigation
resource is clearly a first-class concept independent of which flow
detected it (dip vs. receipt vs. shift close), and should be modeled that
way server-side rather than being bolted onto fuel dispatches.

---

## Phase 7 — Sync Centre & Alerts

### 🔴 No sync status API (confirmed, not just anticipated)
**Hit in:** `SyncCentreScreen`
**Gap:** Flagged as a maybe in Phase 2; now confirmed directly. The
Completed/Pending/Failed counters shown here are 100% derived from the
in-memory `transactionQueueStore` (`useQueueCounts.ts`) — there is no
`GET /sync/status` to ask the server "did my transactions actually
arrive?", which is the exact question the spec's Sync Centre section says
a driver should always be able to answer. "Sync Now" currently just marks
local PENDING items CONFIRMED; it doesn't talk to a server at all because
there's nothing to talk to.
**Needed shape:** as noted in Phase 2 — `GET /sync/status?since=` →
`[{clientRequestId, status, serverId?, error?}]`, ideally pushed via
websocket/polling so Sync Centre updates without a manual pull.

### 🔴 "Photos" metric is a proxy, not a real upload count
**Hit in:** `SyncCentreScreen` (`photosPending`)
**Gap:** Counts queue items whose payload happens to carry a
`photoUri`/`signature`/etc. field — it's counting *transactions with
attached evidence*, not *evidence uploads in flight*, because there's no
separate evidence-upload pipeline to track (see Phase 2's evidence-upload
gap). Once that pipeline exists, this metric should count actual
attachment upload states, not infer them from transaction payloads.

### 🔴 No conflict ever occurs in this build — by design, not oversight
**Hit in:** `SyncCentreScreen`, `AlertsScreen` ("Failed Sync")
**Note:** The UI fully supports rendering `CONFLICT` status (color, icon,
"needs attention" copy), but nothing in this app ever produces one,
because faking a server rejection with no server would be dishonest about
what's actually been verified. This will only become exercisable once a
real endpoint exists to actually reject a transaction (e.g. the
tank-balance conflict scenario from Phase 2's section 25 note).

### 🔴 No approvals resource exists anywhere in the domain
**Hit in:** `AlertsScreen` ("Approvals" section)
**Gap:** Unlike every other Alerts category, this one has no local data
to even fake — no approval request is ever created by any flow built so
far (the Fuel Manager's "Approvals" quick action from Phase 1 is still
just a "coming later" stub too). This is a genuinely unbuilt domain
concept, not a sync/integration gap.
**Needed shape:** depends entirely on what needs approving in this
tenant's workflow (high-value dispatches? shift close variances above a
threshold? supplier changes?) — needs product definition before an API
shape makes sense.

### 🟡 Alerts is computed by the client from other resources' local caches
**Hit in:** `AlertsScreen`
**Gap:** Low Stock reads `MOCK_TANKS`, Variance reads the local
`varianceStore`, Delivery Issues reads the local `deliveryStore` — all
three reset when the app restarts, and none of them reflect what's
happening on *other* devices or from the backend's point of view. A
production Alerts tab needs its own queryable resource (`GET
/alerts?role=&siteId=`) that the backend maintains as state changes
happen server-side, rather than the mobile app reconstructing "what's
wrong" by filtering caches of unrelated resources every time the tab
opens.





