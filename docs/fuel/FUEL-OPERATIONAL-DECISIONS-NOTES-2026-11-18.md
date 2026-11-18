# Fuel operational decisions: implementation notes (2026-11-18)

Spec: "HANDYFLOW FUEL — OPERATIONAL DECISIONS IMPLEMENTATION". Delivered in six batches, patches 0911–0915 (0914 and 0915 are the last two). **This is not a production-readiness statement.** The backend has not been compiled or run by me; see below.

## Batches

| Batch | Patch | What |
|---|---|---|
| A | 0911 | Arrival check freshness (server, tenant setting, default 12 h), STALE, override permission `FUEL_ARRIVAL_CHECK_OVERRIDE` (granted to no role), release with reason/explanation/approver/audit, server enforcement at completion |
| B | 0912 | POD receiver name mandatory, authorised exceptions (unattended, after-hours approved, automated receiving, contactless authorised) with reason, evidence, policy and audit; `FUEL_POD_EXCEPTION_APPROVE` |
| C | 0913 | Push: overdue delivery, dip variance, shift outside tolerance, arrival check blocked; dedup, preferences, deep links, delivery tracking, retry, escalation, audit. Fix: HR facade dropped the login link so the existing "new load" push never fired |
| D+E | 0914 | App: supervisor adjustment approval, shift review and reversal; desk booking and cancelling of stays; driver tabs with settle, charge, void (all online) |
| F | 0915 | Offline Forecourt and Truck stop writes: V728, `FuelOfflineService`, conflict log, outbox kinds and screens |
| G | 0916 | Offline limits as company settings (V729); an offline-opened shift can be closed from the app; app screens Alerts I get and Refused offline writes |
| H | 0917 | Web: Operations policy tab (arrival check, POD, overdue, offline limits, recent alerts, refused offline writes); Arrival check and POD exception dialogs on delivery rows |

Docs: `FUEL_ARRIVAL_CHECK_POLICY.md`, `FUEL_POD_EXCEPTION_POLICY.md`, `FUEL_PUSH_NOTIFICATION_MATRIX.md`, `FUEL_FORECOURT_OFFLINE_DESIGN.md`, `FUEL_TRUCKSTOP_OFFLINE_DESIGN.md`.

## Tests: what was actually run

| Suite | Result |
|---|---|
| Fuel app, `npm run typecheck` | clean (after Batch F) |
| Fuel app, `npx jest` | **27 suites, 281 tests, all passed** (run after Batch G) |
| Backend (Maven) | **NOT RUN.** The sandbox has no Maven. Java was only syntax-checked with `javac -proc:none`, which cannot resolve Lombok and cannot prove the code compiles. |
| Web (vitest, Fuel pages and navigation) | **102 files, 747 tests, all passed** (run after Batch H) |

## Spec test list against what exists

| Required test | Where | Ran? |
|---|---|---|
| Freshness boundary at 12 h | `FuelArrivalCheckServiceTest` / policy tests | written, backend not run |
| Stale check release; unauthorised release; safety-critical blocked | `FuelArrivalCheckServiceTest` (+ `@PreAuthorize`, security scan test) | written, not run |
| Delivery blocked without fresh check | `FuelTransportServiceTest` | written, not run |
| Approved POD exception; missing receiver details | `FuelPodPolicyServiceTest`; app `loads.test.ts` (ran, passes) | server not run |
| Duplicate notification event; correct recipients | `FuelPushEventServiceTest`, `FuelOverdueDeliveryAlertsTest`, `FuelArrivalCheckServiceTest` | written, not run |
| Shift open/close duplicate submissions | `FuelOfflineServiceTest` | written, not run |
| Offline shift reconciliation | partly: replay, sequence, age, conflict logging (`FuelOfflineServiceTest`). **No end-to-end test against a database.** | not run |
| Duplicate stay check-in / check-out | `FuelOfflineServiceTest` | written, not run |
| Offline transaction conflicts | `FuelOfflineServiceTest` (conflict recorded and rethrown); app: refusal parks in outbox (existing outbox tests, ran) | partly run |
| Tenant isolation | `FuelOfflineServiceTest` (request-id lookups only in the caller's tenant) | written, not run |
| Revoked permissions | Structural only: the endpoints are guarded by `@PreAuthorize` on the request that arrives, checked by `FuelEndpointSecurityTest`; app tests for hidden actions without permission (ran). **No test that a queued write is refused after a role change.** | not run |

## Skipped or missing

- No integration or database tests (unique indexes, concurrent duplicate ids, migration V725–V728 on real data).
- No test of the HTTP controllers for the new offline paths.
- No tests for the shift and dip push hooks (Batch C).
- No device or Expo build test of the new screens or the outbox on a real phone.
- Web screens now exist for the new APIs, but the customer-site POD designation (unattended / automated) has no web control yet: `PUT /customer-sites/{id}/pod-exception` is API-only. The web push-preference screen does not exist (people set theirs in the app).

## Unresolved risks

1. **Backend uncompiled.** Expect a first Maven build to find small errors (Lombok-dependent code, test constructor arities).
2. **Migration numbering.** Fuel V725–V728 may collide with SCM V717–V724 on the shared Flyway path; renumber before applying. Run V717–V728 on a copy of production data first.
3. **Enforcement switches default OFF** (`arrival_check_enforced`, `pod_policy_enforced`) so the existing web screens keep working. Until a company turns them on, nothing is enforced. A rollout decision is needed.
4. An offline-opened shift is closed by the id of its open; if that open is refused, the close is refused too. Not run on a device.
5. **Concurrent identical ids** rely on the unique index; the losing request errors and the phone's retry is a replay. Untested.
6. **Captured-time limits** (72 h, 5 min skew) are constants.
7. **Close takings are judged at sync time** (prices, tolerance, tank cost), not at capture time.
8. "Acknowledgement" for POD was read as the receiver name. A signature is a separate queued evidence step and is not enforced on completion. Needs your decision.
9. The dashboard overdue threshold (24 h) differs from the push grace (120 min). Dip variance reuses the `VARIANCE` recipient topic. No app UI for push preferences.
10. Push depends on `handyflow.fuel.push.enabled=true`, an EAS project id and real devices; none verified.

## Owed by you

Apply patches 0877–0917 (SCM 0900–0902) with `git am -3`; run `mvn test` and the web build; `npm install` in the Fuel app; set the EAS project id; enable push; renumber and run migrations on a copy of production data; decide risks 3 and 8.
