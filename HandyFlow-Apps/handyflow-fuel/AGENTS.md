# HandyFlow Fuel (mobile)

Expo SDK 58, React Native 0.88, React 19. Same versions as `handyflow-guard`: read the Expo 58 docs before changing native config.

- `src/` is the shared shell: sign-in, access, modes, offline outbox, UI kit, navigation.
- `packages/fuel-driver/` is Driver Mode. It must not import from other modes. Import paths: `@/` for `src/`, `@driver/` for the driver package.
- Writes go through the outbox (SQLite) with a `clientRequestId` equal to the outbox item id. The backend is authoritative.
- Run `npm run typecheck` and `npm test` before every change.
