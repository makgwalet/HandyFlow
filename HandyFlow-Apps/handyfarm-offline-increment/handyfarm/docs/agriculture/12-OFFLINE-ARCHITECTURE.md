# HandyFlow Agriculture — Offline Architecture (v1)

This is the first real increment of the offline-first requirement from the
product brief. It is deliberately narrow — one write path, one queue table
— rather than a full design for every capture screen, sync conflict, and
attachment type at once. See "What this deliberately does not cover" below
before treating this as complete.

## What exists

**Local storage:** a single SQLite table, `pending_mutations`
(`src/offline/db.ts`), opened via `expo-sqlite`'s async API. One row per
queued write:

```
id, method, url, body (JSON), entity_label, created_at, status, attempt_count, last_error
```

**Enqueue path:** `src/api/client.ts` exports `writeOrQueue<T>(config, entityLabel)`.
Every write that should be offline-safe calls this instead of
`apiClient.post/patch` directly:

```
UI → mutation (TanStack Query) → service function → writeOrQueue → apiClient
                                                          │
                                              network error (no response)?
                                                          │
                                                     enqueueMutation()
                                                          │
                                        WriteResult = { status: 'queued' }
```

`writeOrQueue` only queues on a **genuine network failure** — no response
came back at all (offline, DNS failure, timeout). A server-returned error
(validation 400, permission 403, etc.) is rethrown as a normal `ApiError`
instead of queued, because that's a real problem the person needs to see
and fix, not a connectivity issue. This distinction matters: queuing a
guaranteed-to-fail validation error would just mean discovering the same
failure later, further from the moment the person could fix it.

**Replay:** `src/offline/queue.ts`'s `syncPendingMutations()` walks the
queue in creation order and replays each request through the same
`apiClient`. On success the row is deleted. On failure the row is marked
`FAILED` with the error message and attempt count incremented; if the
failure looks like another network error, the loop stops entirely (no
point burning through the rest of the queue while still offline).

**UI:** `src/offline/useOfflineSync.ts` exposes `pendingCount`, `isSyncing`,
and `syncNow()`. The Home screen's `SyncStatusBar` now shows the real
count (not a placeholder) and is tappable to trigger a manual sync, with
an honest result message either way ("N synced" vs "N synced, M still
waiting").

**Wired into one flow today:** Record Weight (`RecordWeightScreen`, both
the animal and group case). Tell the person plainly when their weight was
saved on-device vs. confirmed by the server (per the product brief's rule:
never say "network error," say "saved on this device, will sync
automatically").

## What this deliberately does NOT cover yet

- **Every other capture screen.** Treatment, Mortality, Input Application,
  Scouting, Harvest, and Record Planting still call the plain
  `apiClient.post`/`unwrap` path and will throw a normal error if offline,
  losing the person's input. Migrating each to `writeOrQueue` is
  mechanical (see the diff to `animals.ts`/`groups.ts`'s
  `recordAnimalWeight`/`recordGroupWeight` for the pattern) but hasn't been
  done yet — noted so nobody assumes "offline" means the whole app.
- **Operation IDs / idempotency.** If a request actually reached the
  server and the response was lost in transit (rather than the request
  never arriving), replaying it could create a duplicate record. The
  backend's DTOs don't currently expose a client-supplied idempotency key
  to dedupe against — this needs a backend conversation before it's safe
  to rely on for real farm data.
- **Conflict resolution.** There's exactly one kind of conflict handled:
  none. If the same animal's weight was also recorded from another device
  while this one was offline, both writes succeed independently — no
  detection, no merge.
- **Attachments/photos.** No photo capture is wired up anywhere yet (see
  API GAP 5's mobile-app note), so there's nothing to queue.
- **GPS-tagged records.** Same — scouting's location field isn't captured
  yet.
- **Background sync.** Sync only runs when the person taps the sync pill,
  or (implicitly) the next time any write succeeds. There's no
  connectivity-change listener (`NetInfo` or similar isn't a dependency
  yet) that triggers a sync automatically when the device comes back
  online, and no background task registration.
- **A dedicated Sync Center screen.** The product brief describes a full
  screen breaking down pending records by type ("Egg collections 3,
  Animal weights 2..."). What exists today is the Home screen's pill —
  functional, not the full designed experience.
- **Schema migrations.** The `pending_mutations` table is created with
  `CREATE TABLE IF NOT EXISTS` and nothing else — there's no migration
  runner for when this schema needs to change later.
- **Auth token expiry while offline.** If the stored bearer token expires
  during an offline period, replay will fail with a 401 — surfaced through
  the normal `stillFailed`/`last_error` path, but there's no explicit
  re-auth flow triggered by it yet.

## Design principle this preserves

From the product brief: *"Never make the farmer wonder whether their work
was saved."* Every write that goes through `writeOrQueue` gets an honest
answer — synced, or saved locally and pending — never a bare "network
error" with no indication of whether the data survived.
