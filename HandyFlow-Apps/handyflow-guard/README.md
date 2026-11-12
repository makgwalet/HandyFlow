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
Done: scaffold, strict config, API client (envelope, timeout, 401 sign-out, network = status 0), guard sign-in (phone or employee code + 6-digit PIN), device id in secure storage, forced PIN change, sign-out that always clears the device.
Not done: shift (clock in/out), patrol rounds, checkpoint scan, panic, incident with photos, post orders, swaps, offline queue, push, Android/iOS builds (EAS), real icon and splash.

## Rules for this app
- The phone never states who the guard is, whether a PIN passed, or whether they are inside the geofence. The server decides all three (assessment R1, R2). Until the backend is fixed, the clock-in screen must not be built against the current `OpenSessionRequest`.
- Writes that can be repeated (scan, incident, panic) get a client-generated id and a recorded-at time before they go on the wire (assessment G3).
- Token only in secure storage. No guard data in logs.
