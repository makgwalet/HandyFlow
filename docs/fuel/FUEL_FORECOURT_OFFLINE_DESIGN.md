# Fuel Forecourt: offline operations design

Status: implemented in Batch F (server V728, `FuelOfflineService`, Fuel app outbox). **Not tested against a running server or a device.** See "Tested and not tested".

## What was audited first

- Shifts: `FuelForecourtService.openShift` / `closeShift` (pump readings are part of these two calls; there is no separate mid-shift pump-reading endpoint). Cash-up is the close (`cash, card, fleetCard, account, other, varianceReason`). Tank dips are separate (`DIP_READING`, already idempotent since V719).
- Existing server rules kept untouched: pumps unique and active, a pump on one open shift only, closing meter per pump, pending refunds/voids block a close, variance against tolerance (company, depot) with a reason and supervisor review outside it, optional separate closer, stock posted to the ledger on close.
- Outbox (SQLite, item id = client request id) already existed for driver and depot writes.

## What can be captured offline

| Capture | Queue kind | Payload | Server call |
|---|---|---|---|
| Opening readings (and who opens) | `SHIFT_OPEN` | attendant, pumps and opening meters, notes | `POST /forecourt/shifts` |
| Closing readings and cash-up | `SHIFT_CLOSE` | closing meters, test litres, cash, card, fleet card, account, other, reason | `POST /forecourt/shifts/{id}/close`, or `/shifts/by-request/{openRequestId}/close` |
| Tank readings | `DIP_READING` (existing) | | `POST /tanks/{id}/dip-readings` |

Not offline, by design: refunds, voids and discounts (adjustments), supervisor approve/reject/review/reverse. These need a supervisor and the server's current state.

## Idempotency (V719 pattern)

V728 adds `fuel_shifts.open_request_id` and `close_request_id` with partial unique indexes on `(tenant_id, …) WHERE … IS NOT NULL`. The phone sends the outbox item id as `clientRequestId` and the capture time as `capturedAt`.

- Same open id again: the first shift is returned; nothing is opened.
- Same close id again for the same shift: the closed shift is returned; stock is not posted twice.
- Same close id for a different shift: refused (a bug on the phone, not a replay).
- No id (web, older apps): unchanged, straight to the existing service.
- A truly concurrent pair with the same id hits the unique index and one gets a 409/500. The outbox retries, and the retry is a replay. Not tested against a database.

## What the server validates at sync

| Check | How |
|---|---|
| Attendant / permission | The controller checks `FUEL_FORECOURT` / `FUEL_MANAGE` / `FUEL_FORECOURT_MANAGE` on the request that arrives, so a revoked permission refuses a queued write when it syncs (403, parked as FAILED). The submitting user is recorded as opener/closer. |
| Site / tenant | All lookups, including the request-id lookups, are tenant-scoped. Another company's identical id is not a replay. |
| Shift state | Existing: only an OPEN shift closes. A close for a shift someone else already closed is a conflict. |
| Active shifts | Existing: a pump already on an open shift refuses the open. |
| Pump / nozzle config | Existing: pumps exist, are active, unique in the request, linked to a tank. Every pump on the shift needs a closing meter. |
| Reading sequence | New, for requests that carry an id (offline): an opening meter below the pump's last recorded meter is refused ("a meter does not run backwards"). Closing below opening is refused by the existing close rule. Online opens keep their existing gap handling. |
| Capture age | New: `capturedAt` more than 5 minutes in the future, or older than 72 hours, is refused. |
| Duplicates | The id rules above. |
| Variance tolerance / approvals | Existing, run at sync time on the server's current tolerance: outside tolerance needs a reason and sends the shift to supervisor review; separate-closer policy applies. |

The server's `opened_at` / `closed_at` are the sync time. The phone's time is stored in `captured_opened_at` / `captured_closed_at` for the supervisor to read; it never replaces the system time.

## Queued financial records are not final

- The Close shift screen says the takings are not final until the server accepts them, and that the server works out expected takings and variance. After saving it shows "saved on this phone and waiting to send" and offers no second close.
- The app never computes or shows a variance as a result. The preview on screen is "from the meters" only.
- The shift stays OPEN in the list until the server has accepted the close and the list is refreshed.

## Refusals (conflicts)

A refused queued write is parked FAILED in the app (visible in the Sync centre with the server's message) and recorded in `fuel_offline_conflicts` (own transaction, one row per request id) with an audit event `FUEL_OFFLINE_CONFLICT`. Supervisors read it at `GET /api/v1/fuel/offline/conflicts` and mark it resolved with `POST …/{id}/resolve`. Resolving changes the list only. Accepted syncs write `FUEL_SHIFT_OPENED_OFFLINE_SYNC` / `FUEL_SHIFT_CLOSED_OFFLINE_SYNC`.

## Since Batch F

- The capture limits are company settings (V729): `offline_max_age_hours` (default 72) and `offline_clock_skew_minutes` (default 5), edited under Operations policy on the web (`GET/PUT /fuel/offline/conflicts/policy`).
- A shift opened on the phone is listed as "opened on this phone, waiting to send" and can be closed from the app. The close is queued under the id of the open and sent after it, through `/shifts/by-request/{id}/close`. If the open is refused, the close is refused with it and both are kept as conflicts.
- The server's conflict list is shown in the app (Refused offline writes, managers and supervisors) and on the web (Operations policy).

## Known gaps (not hidden)

1. The close's expected takings use the pump prices, tolerance and tank cost at sync time, not at capture time. Judging at capture time would need prices snapshotted when the shift opens. This is a design choice to confirm, not an oversight.
2. Mid-shift pump readings do not exist in the product, so none were added.
3. None of this has run against a database or a device.
