# Mobile offline strategy

## 1. Existing implementations `[Verified]`
| Where | What | Weakness |
|---|---|---|
| Fuel prototype `src/store/transactionQueueStore.ts` | In-memory queue with the right envelope (`clientRequestId`, `operation`, `createdAt`, `userId`, `payload`, `status`) | Lost on restart; sync is simulated |
| Agriculture `src/offline/` | SQLite `pending_mutations`; replays POST/PATCH/PUT after network errors | No idempotency key, no attachments, no conflict handling, ids from `Date.now()` and `Math.random()` |
| Fuel web (C05, `handyflow-web`) | Queue for start trip, delivery, GPS point, photo and signature, with retry and discard | Browser storage only; the contract is the useful part |
| Backend | `clientRequestId` on complete, evidence, failed attempt, returns; `Idempotency-Key` filter | Missing on dispense, dip, receive, shift close, stays |

## 2. Levels
1. Offline read access. 2. Offline data capture. 3. Offline workflow execution. 4. Full offline operations.

| App | Level | Notes |
|---|---|---|
| Fuel driver | 3 | Start, arrive, complete, photo, signature, failed attempt, returns |
| Fuel forecourt | 3 | Dip, dispense, receive, shift; **not** approvals |
| Fleet | 2 to 3 | Inspections, trip log, accident reports |
| Earthmoving | 3 | Remote sites |
| Warehousing | 2, reads cached | Counts and receipts capture offline; allocation stays online |
| Security | 3 | Patrol scans, incidents, duress queued |
| Agriculture | 2 | Records and photos |
| HR employee | 1 | Cached payslips and leave balances; clock-in captured with a timestamp |
| Clinic | 1 at most | No patient data offline until a privacy review |
| Finance apps | none | Approvals need a live backend |

## 3. Engine rules (one shared package, `packages/offline`)
1. Every queued write gets a **UUID `clientRequestId` created once** with `expo-crypto`, kept with the item, sent as `Idempotency-Key` and, where the DTO has it, as `clientRequestId`.
2. Queue in SQLite; order per entity (a completion must not run before its start). Attachments upload **before** the dependent record, or after with a reference, never silently dropped.
3. Statuses: PENDING, SYNCING, CONFIRMED, FAILED (retryable), CONFLICT (needs a person), DISCARDED (by user, with audit note).
4. Retry with backoff on network errors and 5xx; **no automatic retry on 4xx** (validation, permission, "already moved on"). Show the server message.
5. A replay that returns the first record is a success.
6. **Never auto-resolve conflicts for money or stock.** Stock movements, fuel deliveries, patient records, approvals: show the item and let the user decide.
7. Visible sync state: a persistent bar with pending, failed, last sync time (the prototype has `SyncStatusBar` and `useQueueCounts`).
8. Logout never deletes unsent items without an explicit warning.
9. Time: store device time and let the server decide; flag large clock drift.
10. Storage limits: cap queue size and photo resolution; purge confirmed items after 7 days.

## 4. Backend prerequisites `[Requires approval]`
B5 from `MOBILE_ARCHITECTURE.md`: `clientRequestId` on dispense, dip, receive, shift close and stay actions, with unique indexes. Known issue from the evidence work: two replays racing at the same instant can hit the unique index and the second gets a server error; the phone's retry then succeeds. Acceptable, but test it.

## 5. Open questions
- Retention and encryption of queued payloads.
- Maximum offline period before forcing re-login.
- Whether a tank dip taken offline is still valid when stock moved in the meantime (policy: record both, flag variance).
