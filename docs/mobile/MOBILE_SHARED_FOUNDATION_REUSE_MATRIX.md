# Shared mobile foundation: reuse matrix (first cut)

2026-11-18, read-only listing of `HandyFlow-Apps/` on `fuel-stage0`. The planning papers require a real audit of the Fuel app before any shared package is extracted. This is only the starting inventory, with what was seen from folder listings and `package.json` files. No source was read in depth, so every decision column says what to decide, not what was decided.

Used by: HandyFarm (`docs/handyfarm/`), Supply Chain mobile (`SUPPLY_CHAIN_MOBILE_PLAN.md`), Guard, Fleet.

## Apps present

| Folder | What it is | Expo / RN / React |
|---|---|---|
| `handyflow-fuel/` | Fuel app; `app.config.ts`, `eas.json`, Jest, five feature packages, `src/{access,api,auth,config,navigation,offline,screens,ui}`, `push.ts`, `pushPrefs.ts`, `PushBridge.tsx`, `files.ts` | `~58.0.24` / `0.88.1` / `19.3.0` |
| `handyflow-guard/` | Guard app; `app.config.ts`, `eas.json`, tests | not checked |
| `handyfarm-offline-increment/handyfarm/` | The Agriculture (HandyFarm) app, 16 screens, no tests | `52.0.46` / `0.76.7` / `18.3.1` |
| `handyfarm-sync-alerts/handyfarm/` | Early Fuel prototype, mocked data, named `handyfarm` | `52.0.46` family (per 12 Nov inventory) |
| `clinic-desktop/` | Vite, React, Tauri desktop app; not Expo | n/a |

## Capability matrix

| Capability | Existing implementation seen | Reuse, upgrade or build |
|---|---|---|
| Authentication, sessions | Fuel: `src/auth/AuthContext.tsx`; HandyFarm: axios bearer token with SecureStore | Decide after audit; two approaches exist |
| Device registration | Fuel: `src/auth/deviceStore.ts`; Guard has a device-binding backend flow | Audit what the backend registry supports |
| Tenant context | Fuel `src/access`, `src/config` | Audit; HandyFarm needs farm and entity scope on top |
| API client | Fuel `src/api`; HandyFarm `src/api/client.ts` | Audit; unify |
| Offline queue and sync | Fuel: `offline/{outbox,sqliteStore,send,OutboxProvider}`; HandyFarm: `offline/{db,queue,useOfflineSync}` with `pending_mutations` | Two separate implementations. Audit Fuel's for idempotency, ordering and conflicts; likely base for a shared package. Not safe to extract before the audit |
| Notifications and push | Fuel: `push.ts`, `pushPrefs.ts`, `PushBridge.tsx` | Audit, then share |
| Document and photo capture | Fuel `files.ts`, evidence flows; HandyFarm has camera dependency only | Audit |
| Barcode scanning | not checked | Build or reuse after audit (needed by HandyFarm tags and Supply Chain receiving) |
| Location | `expo-location` in HandyFarm | Audit |
| Design system and UI | Fuel `src/ui`; HandyFarm `src/design-system`; web tokens `--hf-*` | Decide shared package scope |
| Build and release | Fuel and Guard have `eas.json` and `app.config.ts`; HandyFarm has `app.json` only | Align |
| Tests | Fuel and Guard have Jest setups; HandyFarm none | Add to HandyFarm |
| Backend APIs | Agriculture `/api/v1/agriculture/**`; Supply Chain `/api/v1/supply-chain/**`; Fuel driver APIs | Reuse and extend; proposed `/api/v1/mobile/...` groupings only where needed |

## Risks to resolve before extracting shared packages
1. SDK split (52 versus 58) blocks sharing; one SDK must be chosen `[Requires approval]`.
2. Two offline engines will drift; one should become the shared engine.
3. Naming collision: the Fuel prototype folder is called `handyfarm` while HandyFarm is the agriculture product `[Requires approval]`.
4. Earlier findings from 12 Nov that the mobile auth backend lacked body-returned refresh tokens, a device registry and push tokens need re-checking against the current backend.
5. Repo hygiene items from the 12 Nov inventory (tracked `node_modules` and `.env` under `clinic-desktop`) are not re-checked here.
6. Proposed target layout `handyflow-mobile/{apps,packages}` is not the current layout (`HandyFlow-Apps/`); reconcile before moving files.
