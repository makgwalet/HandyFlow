# HandyFlow Fuel (mobile)

One role-based app for Fuel staff. Expo SDK 58, React Native 0.88, React 19: the same versions as `handyflow-guard`.

## Run

```
npm install
EXPO_PUBLIC_API_BASE_URL=http://<your-machine>:8080 npm start
npm run typecheck && npm test
```

## Layout

- `src/access/modes.ts` decides which modes a person sees, from the permissions on their sign-in.
- `src/offline/` is the outbox: every write is saved in SQLite first, then sent. The outbox item id is the `clientRequestId`, so a retry never double-records.
- `src/auth/` keeps the token in SecureStore only.
- `packages/fuel-driver/` is Driver Mode (my loads, start trip, record delivery, could not deliver, photos). It does not import from any other mode.
- `__tests__/` covers modes, load rules and forms, the outbox, the senders and sign-in.

## Built / not built

Built: sign-in, modes, My loads, load detail, start trip, record delivery, could not deliver, photo evidence with position, saved load list when offline, sync centre.
Shown as "coming soon": Forecourt, Truck stop, Depot, Overview.
See `docs/fuel/FUEL-MOBILE-APP-NOTES-2026-11-18.md` for what the backend still needs.
