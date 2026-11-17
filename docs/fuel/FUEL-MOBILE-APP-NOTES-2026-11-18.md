# Fuel mobile app: first build (2026-11-18)

`HandyFlow-Apps/handyflow-fuel` was an untouched Expo SDK 52 template. It is now an Expo SDK 58 app (RN 0.88.1, React 19.3.0), on the same versions as `handyflow-guard`, built from the Guard foundation as the mobile plan recommends. Guard stays parked and was not changed.

## What it does

- **Sign in** with company code, email and password (`POST /api/v1/auth/login`). The token is kept in SecureStore only.
- **Modes from permissions**: `FUEL_DRIVER`, `FUEL_MANAGE` or `FUEL_TRANSPORT_MANAGE` opens My loads. Forecourt and Truck stop tiles show for the matching permissions and say "Coming soon". The server still checks every call.
- **Driver Mode** (`packages/fuel-driver`), on the existing endpoints:
  - `GET /api/v1/fuel/driver/loads`: the list. The last list is saved on the phone and shown with its time when there is no signal.
  - `POST .../dispatch` (start trip), `.../complete` (delivery), `.../failed-attempt`, `.../evidence/file` (photo, with position when permitted).
- **Offline outbox** (SQLite): each action is saved first, then sent. Order: start trip, then delivery or failed delivery, then photos. The outbox item id is sent as `clientRequestId`. No signal, expired sign-in (401) or inactive subscription (402) stops the run and keeps everything. 400, 403, 404, 409 park the item as refused with the server's words, and the driver can retry or discard it in the Sync centre. 429, 408, 5xx retry up to 8 times.
- Screens read the phone's own queue, so a trip started offline is not offered again and a delivery saved offline cannot be closed twice.

## Depot mode (`packages/fuel-depot`)

- **Tanks**: `GET /api/v1/fuel/tanks` (the server already limits it by the person's scope). Low tanks first. The last list is saved and shown with its time when there is no signal.
- **Tank detail**: fill level, recent dip readings with variance, **Save dip reading** and **Receive fuel** for `FUEL_MANAGE` / `FUEL_OPERATIONS_MANAGE`. A reading above the tank's capacity is refused on the phone.
- **Dip readings and receipts are queued offline** (outbox kinds `DIP_READING`, `RECEIVE_FUEL`). The outbox item id is sent as `clientRequestId`; backend patch 0888 (V719) keeps a unique index on it, so a resend after a lost reply returns the first record and never adds stock or a reading twice. The time of the dip or receipt is when it was recorded on the phone, not when it was sent.
- **Receive fuel** takes litres, price per litre, delivery note, supplier invoice number and freight. It refuses more litres than the room left in the tank. **Not in the app yet:** supplier picker and purchase order link (both optional on the server).
- A user with only `FUEL_STOCK_MANAGE` (no `FUEL_READ`) sees the Depot tile but the server will refuse the tank list.

## Overview mode (`packages/fuel-overview`)

`GET /api/v1/fuel/dashboard-summary` (needs `FUEL_READ`): litres dispatched this month, dispatch and receipt counts, open and on-the-road deliveries, the next deliveries, and an attention list (overdue deliveries, dip readings more than 5 L short). Last copy is kept and labelled when there is no signal.

## Sign-in, access, arrival check, signature (patches 0888 to 0890)

- **Silent refresh.** Backend `POST /api/v1/auth/mobile/login|refresh|logout` return the refresh token in the body (web keeps its httpOnly cookie, untouched). The app refreshes once, however many calls fail together, and saves the new refresh token before using it, because the server treats reuse of an old one as theft and ends every session. No signal during a refresh keeps the session and the queue; only a refused refresh signs out.
- **My access** screen from `GET /api/v1/fuel/me/access` (permissions, divisions, depots, subscription). Home hides modes the company or the person's scope does not include. A 402 shows the subscription card and keeps the queue.
- **Arrival check.** `POST/GET /api/v1/fuel/driver/loads/{id}/arrival-check` runs the same eligibility rules as dispatch, read-only, and records the result and time (V721). The app shows "Record delivery" and the signature only when the latest check allows the load and is **under 12 hours old**. Could not deliver is always offered. The server does not enforce the check yet.
- **Emergency contact.** Set by the company (`PUT /api/v1/fuel/settings/emergency-contact`, V720); drivers get a call button on My loads.
- **Receiver signature.** Drawn on screen, turned into a 1-bit PNG in JavaScript (no native drawing library), uploaded as SIGNATURE evidence. Photos and signatures are copied into the app's own folder first, because the OS can empty the camera cache, and removed once uploaded.

## Decisions still open

1. The 12-hour arrival check window (an app constant, `FRESH_HOURS`) and who may release a stale or blocked check. No supervisor release is built.
2. Whether the server should refuse delivery without a fresh arrival check.
3. Whether to keep the app-side "received by" requirement.

## App-side rules (not on the server)

- The delivery form requires "received by". The server does not. Change `validateComplete` if you disagree.
- A pump meter that differs from the litres typed by more than 1% shows a warning, not a block.
- Litres delivered cannot exceed litres loaded when a loading is recorded.

## Not built, and why

| Item | State |
|---|---|
| Forecourt mode (shift, pump readings, takings) | Placeholder tile. Needs its endpoints and shift rules read first. |
| Truck stop mode (bays, stays, driver tabs) | Placeholder tile. |
| Push notifications | Not started. Needs a device-token table and a send path on the backend, plus app registration and a decision on which events notify. |
| Supplier and purchase order on Receive fuel | Optional on the server; not in the app. |
| Supervisor release of a stale arrival check | Decision open (above). |

## Checks run

`npx tsc --noEmit` clean; `npx jest` 152 tests in 16 suites passing, including rendered tests of Load detail, My loads, Tank detail, Receive fuel and Overview. Java was only syntax-checked (no Maven here). Not run on a device or emulator. `package-lock.json` is not included: run `npm install` once.

## To apply

`git am -3` the patch, then `npm install` in `HandyFlow-Apps/handyflow-fuel`. For a local backend: `EXPO_PUBLIC_API_BASE_URL=http://<ip>:8080 npm start`. The driver's permission is `FUEL_DRIVER` and the load must be assigned to that user's employee record.
