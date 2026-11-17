# Fuel mobile app: first build (2026-11-18)

`HandyFlow-Apps/handyflow-fuel` was an untouched Expo SDK 52 template. It is now an Expo SDK 58 app (RN 0.88.1, React 19.3.0), on the same versions as `handyflow-guard`, built from the Guard foundation as the mobile plan recommends. Guard stays parked and was not changed.

## What it does

- **Sign in** with company code, email and password (`POST /api/v1/auth/login`). The token is kept in SecureStore only.
- **Modes from permissions**: `FUEL_DRIVER`, `FUEL_MANAGE` or `FUEL_TRANSPORT_MANAGE` opens My loads. Forecourt, Truck stop, Depot and Overview tiles show for the matching permissions and say "Coming soon". The server still checks every call.
- **Driver Mode** (`packages/fuel-driver`), on the existing endpoints:
  - `GET /api/v1/fuel/driver/loads`: the list. The last list is saved on the phone and shown with its time when there is no signal.
  - `POST .../dispatch` (start trip), `.../complete` (delivery), `.../failed-attempt`, `.../evidence/file` (photo, with position when permitted).
- **Offline outbox** (SQLite): each action is saved first, then sent. Order: start trip, then delivery or failed delivery, then photos. The outbox item id is sent as `clientRequestId`. No signal, expired sign-in (401) or inactive subscription (402) stops the run and keeps everything. 400, 403, 404, 409 park the item as refused with the server's words, and the driver can retry or discard it in the Sync centre. 429, 408, 5xx retry up to 8 times.
- Screens read the phone's own queue, so a trip started offline is not offered again and a delivery saved offline cannot be closed twice.

## App-side rules (not on the server)

- The delivery form requires "received by". The server does not. Change `validateComplete` if you disagree.
- A pump meter that differs from the litres typed by more than 1% shows a warning, not a block.
- Litres delivered cannot exceed litres loaded when a loading is recorded.

## Not built, and why

| Item | Reason |
|---|---|
| Silent token refresh (B1) | Refresh uses an httpOnly cookie, which the app cannot read. When the sign-in expires the app asks for sign-in again and keeps the queue. Needs a native refresh token on the backend. |
| Access summary screen (B9), 401/402/403 explanations | Needs the access-summary endpoint. |
| Eligibility check before loading, emergency contact, push, signature capture | Need endpoints or decisions in the mobile plan (section 11). |
| Forecourt, Truck stop, Depot, Overview | Placeholders only. |
| Component tests of the driver screens | Logic is tested in plain functions; only Home is rendered in tests. |

## Checks run

`npx tsc --noEmit` clean; `npx jest` 56 tests passing. Not run on a device or emulator. `package-lock.json` is not included: run `npm install` once.

## To apply

`git am -3` the patch, then `npm install` in `HandyFlow-Apps/handyflow-fuel`. For a local backend: `EXPO_PUBLIC_API_BASE_URL=http://<ip>:8080 npm start`. The driver's permission is `FUEL_DRIVER` and the load must be assigned to that user's employee record.
