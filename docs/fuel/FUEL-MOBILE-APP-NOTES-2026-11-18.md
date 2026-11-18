# Fuel mobile app (2026-11-18)

`HandyFlow-Apps/handyflow-fuel` was an untouched Expo SDK 52 template. It is now an Expo SDK 58 app (RN 0.88.1, React 19.3.0), on the same versions as `handyflow-guard`, built from the Guard foundation as the mobile plan recommends. Guard stays parked and was not changed.

## What it does

- **Sign in** with company code, email and password (`POST /api/v1/auth/login`). The token is kept in SecureStore only.
- **Modes from permissions**: `FUEL_DRIVER`, `FUEL_MANAGE` or `FUEL_TRANSPORT_MANAGE` opens My loads. Every mode now opens a real screen (the "Coming soon" screen is kept only as a guard for a future mode). The server still checks every call.
- **Driver Mode** (`packages/fuel-driver`), on the existing endpoints:
  - `GET /api/v1/fuel/driver/loads`: the list. The last list is saved on the phone and shown with its time when there is no signal.
  - `POST .../dispatch` (start trip), `.../complete` (delivery), `.../failed-attempt`, `.../evidence/file` (photo, with position when permitted).
- **Offline outbox** (SQLite): each action is saved first, then sent. Order: start trip, then delivery or failed delivery, then photos. The outbox item id is sent as `clientRequestId`. No signal, expired sign-in (401) or inactive subscription (402) stops the run and keeps everything. 400, 403, 404, 409 park the item as refused with the server's words, and the driver can retry or discard it in the Sync centre. 429, 408, 5xx retry up to 8 times.
- Screens read the phone's own queue, so a trip started offline is not offered again and a delivery saved offline cannot be closed twice.

## Depot mode (`packages/fuel-depot`)

- **Tanks**: `GET /api/v1/fuel/tanks` (the server already limits it by the person's scope). Low tanks first. The last list is saved and shown with its time when there is no signal.
- **Tank detail**: fill level, recent dip readings with variance, **Save dip reading** and **Receive fuel** for `FUEL_MANAGE` / `FUEL_OPERATIONS_MANAGE`. A reading above the tank's capacity is refused on the phone.
- **Dip readings and receipts are queued offline** (outbox kinds `DIP_READING`, `RECEIVE_FUEL`). The outbox item id is sent as `clientRequestId`; backend patch 0888 (V719) keeps a unique index on it, so a resend after a lost reply returns the first record and never adds stock or a reading twice. The time of the dip or receipt is when it was recorded on the phone, not when it was sent.
- **Receive fuel** takes litres, price per litre, delivery note, supplier invoice number and freight. It refuses more litres than the room left in the tank. The **supplier** list (approved suppliers only) and the **purchase order** list (placed orders for that supplier and that tank's fuel) are kept on the phone, so they can be picked with no signal. The server checks the order again when the receipt arrives (supplier, fuel type, litres still open); a refusal parks the receipt in the Sync centre with the server's reason.
- A user with only `FUEL_STOCK_MANAGE` (no `FUEL_READ`) sees the Depot tile but the server will refuse the tank list.

## Overview mode (`packages/fuel-overview`)

`GET /api/v1/fuel/dashboard-summary` (needs `FUEL_READ`): litres dispatched this month, dispatch and receipt counts, open and on-the-road deliveries, the next deliveries, and an attention list (overdue deliveries, dip readings more than 5 L short). Last copy is kept and labelled when there is no signal.

## Forecourt mode (`packages/fuel-forecourt`)

Shifts list (open first), **Open a shift** (attendant, opening meters pre-filled from each pump's last meter, only free active pumps), **Close shift** (closing meters, optional test litres, cash / card / account / fleet card / other, reason for a difference, and a litres-and-value preview worked out from the meters), shift detail (readings, takings, variance read against the shift's tolerance, review status, refunds/voids/discounts) and **Record refund, void or discount** (a supervisor approves it on the web, as before).
- **Online only, on purpose.** Open and close have no client request id. The server refuses a second open ("Already on an open shift") and a second close ("already closed"), so a resend after a lost reply is refused, not doubled. A close queued for hours would also be closed on stale pump prices, so with no signal the screen says nothing was saved. The list of shifts is kept and labelled when there is no signal.
- Not in the app: reversing a shift, supervisor approval of adjustments and shift review (web, `FUEL_SHIFT_SUPERVISE`), pump set-up, tank reconciliation.

## Truck stop mode (`packages/fuel-truckstop`)

The yard (in the yard, free bays, arrivals and departures today; stays grouped as overstaying, in the yard, arriving today, upcoming, recent), **Stay detail** (check in, check out, "Did not arrive", assign or clear a bay, call the driver; check-out raises the account invoice on the server as before) and **Stay requests** (approve, or decline with a reason of 5 to 300 characters; a refused approval, for example no free bay, shows the server's reason and the request stays pending). Online only for the same reason as Forecourt. "Today" is South African time, as on the server.
- Not in the app: booking a stay at the desk, cancelling a stay with a reason, bay set-up, parking settings, **driver tabs and tab settlement** (the point-of-sale side, left for the web).

## Push notifications (patch 0893, migration V722)

- **Backend.** `PUT/DELETE /api/v1/fuel/me/push-token` (the caller's own token only), table `fuel_push_tokens`, `FuelPushService` and a small Expo client, a copy of the Guard design kept inside Fuel so the modules do not reach into each other. A token moves to whoever signs in on that phone last; a token Expo reports dead is revoked, not deleted. Sending happens after the action commits, on its own thread, and a failure is only logged, so it can never undo an allocation or a stay request. **Off unless `handyflow.fuel.push.enabled=true`.** Optional: `handyflow.fuel.push.expo-access-token`.
- **Two alerts, nothing else yet.** (1) A driver with a login is alerted when a load is allocated to them ("New load", the fuel and the day; opens the load). (2) The people who get the web notification of a new customer stay request (tenant administrators) are alerted on their phones (opens Stay requests). Lock screens show no litres, prices or customer names.
- **App.** `expo-notifications` and `expo-device` added (same versions as Guard). The phone registers after sign-in once the person allows alerts, and unregisters on sign-out. A tap opens only a screen the app lists (My loads, a load with a valid id, Stay requests, Overview); anything else is ignored. Needs an EAS project id in the build (`eas init`), and does nothing on an emulator.

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
4. **Which events should push.** Only new loads and new stay requests do now. Candidates: an overdue delivery, a dip variance, a shift closed outside tolerance, a driver's arrival check blocked. The alert-recipient settings (`FuelAlertRecipientService`) already say who wants variance and shift alerts; wiring push to them is small once you choose.
5. Whether the Forecourt and Truck stop screens should ever queue offline. That needs a client request id on shift open/close and stay check-in/out, as dips and receipts got in V719.

## App-side rules (not on the server)

- The delivery form requires "received by". The server does not. Change `validateComplete` if you disagree.
- A pump meter that differs from the litres typed by more than 1% shows a warning, not a block.
- Litres delivered cannot exceed litres loaded when a loading is recorded.

## Not built, and why

| Item | State |
|---|---|
| Driver tabs and tab settlement, desk booking and cancelling of stays | Web only for now. |
| Shift reversal, adjustment approval, shift review | Web, supervisor permission. |
| Push for variances, overdue deliveries, shift exceptions | Decision 4 above. |
| Supervisor release of a stale arrival check | Decision 1 above. |
| Offline Forecourt and Truck stop writes | Decision 5 above. |

## Checks run

`npx tsc --noEmit` clean; `npx jest` 208 tests in 21 suites passing, including rendered tests of Load detail, My loads, Tank detail, Receive fuel, Overview, Open and Close shift, Stay detail and Stay requests. Java was only syntax-checked (no Maven here). Not run on a device or emulator. `package-lock.json` is not included: run `npm install` once.

## To apply

`git am -3` patches 0885 to 0894 in order (Fuel patches 0877 onwards must already be applied), then `npm install` in `HandyFlow-Apps/handyflow-fuel`. For a local backend: `EXPO_PUBLIC_API_BASE_URL=http://<ip>:8080 npm start`. The driver's permission is `FUEL_DRIVER` and the load must be assigned to that user's employee record.

New migrations in this series: V719, V720, V721, V722. If the Supply Chain branch also adds migrations numbered from V717, renumber one side before merging; Flyway refuses two files with the same version.
