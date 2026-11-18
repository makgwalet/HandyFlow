# Fuel arrival check policy (Batch A)

Status: implemented, backend not compiled or run in the build sandbox (Maven unavailable). Not production-ready until the tests below pass and the rollout steps are done.

## Freshness
- Default window 12 hours (`FuelArrivalCheckPolicy.DEFAULT_FRESH_HOURS`). A company may set 1 to 72 hours: `PUT /api/v1/fuel/settings/arrival-check` (FUEL_CONFIGURATION_MANAGE). `GET` of the same path returns the window, the default and the enforcement switch.
- The server decides, on its own clock. Every check view carries `status`, `freshHours`, `expiresAt`, `releasedAt`. The phone's `FRESH_HOURS` is now only a fallback for an old cached check; it uses the server's window and expiry.
- Boundary: exactly the window old is FRESH; one second older is STALE.
- Statuses: MISSING, BLOCKED, FRESH, STALE, RELEASED. The stored check and its findings are never changed.

## Release of a stale check
- `POST /api/v1/fuel/deliveries/{id}/arrival-check/release`, permission `FUEL_ARRIVAL_CHECK_OVERRIDE` (migration V725). **Granted to no role.** The company grants it explicitly; ADMIN does not hold it by default.
- Needs a reason code (DRIVER_DELAYED, SITE_QUEUE, OFFLINE_AT_SITE, RECHECK_NOT_POSSIBLE, OTHER) and an explanation of at least 10 characters. Stored in `fuel_arrival_check_releases` with the original check id, approver, and server time. Audit event `FUEL_ARRIVAL_CHECK_RELEASED` is written in the same transaction.
- Only a STALE check can be released. Refused when: the check is BLOCKED, MISSING, still fresh or already released; the approver made the check; the live eligibility rules now block; a statutory certificate has expired (`FuelComplianceService.requireCompliantForAllocation`). So a safety-critical failure is never released past.
- A release counts for one window from the time it was made, then the check is stale again.

## Server enforcement
- `FuelArrivalCheckService.requireClear` runs on the driver's delivery completion (`myComplete`), the offloading-complete and final-record step. It needs a FRESH or RELEASED check and re-runs the live rules, so a licence that lapsed after the check also stops the step.
- Not blocked: viewing loads and details, route instructions, recording arrival, the check itself.
- **Switch:** `fuel_settings.arrival_check_enforced`, default **off**, so existing companies and the web driver screen (which has no arrival check step) keep working. Turn it on per company only once drivers use the app. This is a rollout decision, not a default.

## Not yet covered
- Other protected steps (restricted site operations other than completion, failed-attempt recording) still need the same gate; completion is the only enforced step in this batch.
- The blocked-check push to driver and operations is Batch C.

## Tests
Backend: `FuelArrivalCheckServiceTest` (boundary, company window, bounds, release, expiry, reason validation, only-stale, safety-critical blocks, licence, self-release, tenant isolation, gate, revalidation, off by default), `FuelTransportServiceTest` (completion blocked). Revoked permission is enforced by `@PreAuthorize` on the release endpoint; there is no controller-level test for it yet. Mobile: `arrival.test.ts`, `releaseCheck.test.tsx` (218 mobile tests pass).
