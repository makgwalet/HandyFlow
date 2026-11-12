# FUEL C05: Offline queue and driver evidence (2026-11-12)

## 1. Implemented
- A generic driver-phone queue (`offlineQueue.ts`, key `fuel-driver-queue-v2`) for three actions: start trip, record delivery, record location (GPS evidence).
- Each item keeps its own request id, sent in the order made. Status per item: PENDING or FAILED.
- Network failure keeps everything and stops the run. A server refusal marks only that item FAILED, shows the reason, and offers Retry and Discard. Later items for the same load wait behind a failed one, so actions never run out of order. Other loads carry on.
- A refused start is treated as done if the load is no longer SCHEDULED (repeat, or the office dispatched it).
- Server idempotency for driver evidence: a repeated `clientRequestId` returns the first record and saves nothing new.
- New "Record location" button on the driver page (uses the phone's location, accuracy rounded to metres).
- Completions saved by the previous app version (key v1) are migrated into the new queue once.

## 2. Preserved
- Completion already carried a `clientRequestId`; unchanged. `EvidenceRequest` keeps its 6-arg constructor. Evidence without an id behaves as before. Office evidence endpoints unchanged.

## 3. Files
- DB: `V671__fuel_evidence_client_request_id.sql`.
- Java: `FuelDeliveryEvidence` (`clientRequestId`, `withClientRequestId`), `FuelDeliveryEvidenceRepository.findByClientRequest`, `FuelEvidenceService.add`, `FuelDistributionDtos.EvidenceRequest`.
- Web: new `offlineQueue.ts`; `DriverTab.tsx` rewritten around it; old queue removed from `transportOps.ts`.

## 4. DB
- `fuel_delivery_evidence.client_request_id UUID` (nullable) and a partial unique index on (tenant_id, delivery_id, client_request_id) where not null. Checked on a scratch PostgreSQL 16: runs once, a duplicate id is rejected, rows without an id are unaffected.

## 5. API
- `POST /api/v1/fuel/driver/loads/{id}/evidence` accepts an optional `clientRequestId`. No new endpoints.

## 6. Security
- Unchanged: driver endpoints still only allow the driver's own load; tenant scoped; the new query filters on tenant.

## 7. Tests
- Java (not run here, parse-checked only): 2 new tests in `FuelDistributionControlsServiceTest` (replay returns first record with no second save; no id means no lookup).
- Web (run): new `offlineQueue.test.ts` (8), `DriverTab.test.tsx` (+3, 8 total), old queue tests removed from `transportOps.test.ts`. Fuel and navigation suites: 296 tests pass.

## 8. Gaps
- Photo and signature capture offline is not built: evidence only takes a `fileRef`, and there is no file-upload service or blob storage. Until one exists the driver app records GPS only.
- The queue is per phone and per browser profile. Clearing site data loses unsent items.
- No automatic expiry of stale queued items.

## 9. Risks
- Two replays racing at the same instant could hit the unique index; the second gets a server error and the phone retries, which then finds the first record. Not specially handled.
- Start and Complete have no server-side client id; a replayed start is covered by the "already moved on" check.

## 10. Next
- A file-upload service, then photo and signature capture in the queue.
- C04 routes and multi-stop.
