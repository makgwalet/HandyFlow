# HandyFlow Guard

Field app for security guards (patrol, checkpoint scans, panic, incidents, post orders, shift swaps). Backend: the `security` module, guard endpoints under `/api/v1/guard/**` and `/api/v1/auth/guard/**`. Design and risks: `docs/security/GUARD-APP-ASSESSMENT-2026-11-12.md`.

## Stack
Expo SDK 58, React Native 0.88, React 19.3, TypeScript 6 (strict, `noUncheckedIndexedAccess`). Node 22.13 or newer. Chosen over SDK 55 because 55 is three releases old; 58 went final on 2026-10-15.

## Run
```
npm install
set EXPO_PUBLIC_API_BASE_URL=http://<your-pc-lan-ip>:8080   # PowerShell: $env:EXPO_PUBLIC_API_BASE_URL="..."
npx expo start
npm run typecheck
npm test
```
Android emulator reaches the host at `10.0.2.2` (the default). A real phone needs your PC's LAN address.
Always add packages with `npx expo install <pkg>` on a machine that can reach api.expo.dev (it picks the SDK-matched version). The cloud workspace this was scaffolded in could not, so versions came from `node_modules/expo/bundledNativeModules.json`; run `npx expo install --check` once on your PC.

## State
Done: scaffold, strict config, API client, sign-in (phone or employee code + 6-digit PIN), forced PIN change, device id in secure storage.
Done (screens, patch 0818): shift and clock in/out, patrol rounds with checkpoint ticks, QR scan with camera and GPS, incident report, panic (hold 1.5 s), sync banner.
Offline: every scan, incident and panic is saved in SQLite first, then sent. Each carries a `clientRef` and `recordedAt`. Panic sends first, then incidents, then scans. No signal or an expired sign-in stops sending and keeps everything; a request the server refuses for good is parked as failed and shown. Each item is only ever sent under its own guard's sign-in (shared tablets), and Sign out is blocked while items are waiting.
Incident photos: up to 5 per report, taken in the report screen, kept in the app's private storage, queued behind the report and uploaded by the report's id (a retry never attaches twice); the phone's copy is deleted after a successful upload. A photo whose report was refused for good stays parked as failed.

My post orders (read the site and post orders, acknowledge the version read; needs signal, not queued offline) and shift swaps (ask for an open swap of an upcoming shift, accept one proposed to you, cancel yours; the supervisor decides).

Equipment: sign out a radio, key, vehicle or other item, return it as good, damaged or missing, and ending the shift is blocked until the server shows nothing signed out to the guard (no more "I returned everything" switch). Firearms are not offered; they go through the armoury process. Needs signal, not queued offline.

Gate log: who is on site, log an arrival (visitor, contractor, delivery, staff vehicle, other; ID number optional and masked once saved) and a departure. Needs signal; a double tap or timed-out retry logs the visitor once. Photos (ID, vehicle disc, other) are taken from the visitor's card and uploaded straight away.

Site overview (guards a manager made site supervisors only): who is on duty at the site they are on shift at, round counts, last scan, and unresolved incidents from the last 24 hours. Read only. The button appears on Home only when the server allows it.

Push alerts: the app registers the phone's token after sign-in, removes it on sign-out, and opens the right screen when an alert is tapped. Needs a real phone with an EAS build (not an emulator, not Expo Go) and `eas init` so the project id exists. The backend sends only when `handyflow.guard.push.enabled=true`.

Swap colleague picker: a swap is now always sent to a named colleague (the server cannot approve a request that names nobody). The list is guards rostered at a site you are rostered at within 30 days, names only.
Offline post orders: the last orders you loaded are kept on the phone (one file per guard, removed at sign-out). With no signal you can read that copy (it says when it was saved) and acknowledge it; the acknowledgement names the version you read and the time, is saved in the queue, and is refused by the server (shown as failed, not retried) if a newer version was published meanwhile.

Not done: offline gate log, real icon and splash, translations, privacy policy.
Never run on a phone or emulator: checked only by type check, unit tests and an Android bundle build.

## Rules for this app
- The phone never states who the guard is, whether a PIN passed, or whether they are inside the geofence. The server decides all three (assessment R1, R2). Clock-in sends only `{deviceHardwareId, latitude, longitude, accuracyMetres}` to `POST /api/v1/guard/sessions/open` (backend patch 0815); it never sends a guard id or check results.
- Writes that can be repeated (scan, incident, panic) get a client-generated `clientRef` (UUID, made once per action and reused on every retry) and a `recordedAt` before they go on the wire. Backend support is in patch 0817. Clock-in is online-only.
- Token only in secure storage. No guard data in logs.


## Building for a real phone (EAS)

1. `npm install -g eas-cli`, `eas login`, then `eas init` in this folder (adds the project id to the Expo config).
2. Edit `eas.json`: replace the two `REPLACE-WITH-...-API-URL` placeholders with the real https address of your staging and production backends. The build stops with a clear error if a placeholder or a plain `http://` address is left in (phones block plain http, so a local backend cannot be used from an installed build; use the emulator for that).
3. `eas build --profile preview --platform android` produces an APK you can install on pilot phones from the link EAS prints. `--profile production` produces an app bundle for Play.
4. Still missing before a wider release: a real icon and splash (the ones here are the Expo template), a privacy policy URL for the store listing (the app collects location, photos and, at the gate, ID numbers), and the translations.
