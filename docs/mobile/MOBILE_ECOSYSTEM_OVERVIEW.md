# Mobile ecosystem overview

Assessed 2026-11-12 on branch `fuel-stage0`. Discovery only: nothing was installed, upgraded, built or changed in `HandyFlow-Apps/` or the backend.

## Status legend (used in every document)
`[Verified]` seen in the repository. `[Proposed]` recommendation. `[Requires approval]` needs a product-owner decision before work. `[Deferred]` valid but later. `[N/A]` does not apply.

## 1. Where we are
- `[Verified]` The mobile folder is `HandyFlow-Apps/` (not `mobile/`). It holds four projects, only three of them Expo:

| Folder | Package / slug | What it really is | Maturity |
|---|---|---|---|
| `handyflow-fuel/` | `handyflow-fuel` | Untouched `create-expo-app` template (`App.tsx` is 20 lines: "Open up App.tsx") | Scaffold (1) |
| `handyfarm-sync-alerts/handyfarm/` | `handyfarm` | **A Fuel app.** 86 source files, about 7,900 lines: dispense, dip, receive, deliveries, shifts, sync centre, alerts. All data is mocked; no network calls | UI prototype |
| `handyfarm-offline-increment/handyfarm/` | `handyfarm` | **The Agriculture app.** Real typed API client, TanStack Query, SQLite write queue, livestock and crops screens | Partial, connected |
| `clinic-desktop/` | `clinic-desktop` | Vite + React + Tauri **desktop** app with Dexie offline store. Not Expo | Out of mobile scope |

- `[Verified]` All three Expo projects pin **Expo 52.0.46 / React Native 0.76.7 / React 18.3.1**. Expo's current stable is **SDK 58 (React Native 0.88, React 19.3)** per docs.expo.dev on 2026-11-12, so the projects are six SDKs behind.
- `[Verified]` No project has tests, an `eas.json`, bundle identifiers, an `app.config.ts`, environment profiles, CI, or installed `node_modules` (except the desktop app).
- `[Verified]` The backend is much further ahead than the apps: 63 backend packages (56 with controllers), about 4,100 mapped endpoints, a driver-facing Fuel API with idempotency fields, and a working Security guard device-binding flow.

## 2. Headline findings
1. **Naming is wrong and confusing.** The Fuel prototype is called "handyfarm"; the real Fuel folder is empty; two projects share the package name `handyfarm`. They look like zip snapshots ("-sync-alerts", "-offline-increment"), not branches, so nothing is shared and code will drift.
2. **Auth is the biggest platform gap.** The backend refresh token is httpOnly-cookie only (`AuthController.refresh`), there is no tenant-user MFA, no generic device registry, no push token model, and login needs a company slug. A native app cannot use this as it stands.
3. **The Fuel prototype was designed against a hypothetical spec (`handyfuel.txt`)** and lists about 25 backend gaps. Many are now closed by the Fuel backend (delivery lifecycle, evidence upload, shifts, dips, receipts, variances, approvals). It needs re-baselining before any build.
4. **Repository hygiene:** `HandyFlow-Apps/clinic-desktop/node_modules` is tracked in git (17,156 files) and a `.env` is tracked.
5. **`AGENTS.md` in all three Expo projects tells agents to read Expo v54 docs** while SDK 52 is installed. That will mislead any AI-assisted work.
6. **Cheapest moment to standardise is now.** Nothing has shipped, so the SDK jump can be a re-scaffold plus screen port rather than six incremental upgrades `[Requires approval]`.

## 3. Document set
| # | File | Purpose |
|---|---|---|
| 1 | this file | Summary, legend, decisions |
| 2 | `MOBILE_APPLICATION_INVENTORY.md` | Per-project evidence |
| 3 | `MOBILE_MODULE_ASSESSMENT.md` | All modules classified A to E; Fuel and other deep dives |
| 4 | `MOBILE_TECHNOLOGY_STANDARDS.md` | SDK, dependencies, repo structure |
| 5 | `MOBILE_ARCHITECTURE.md` | Integration with IAM, tenancy, entitlements; backend change register |
| 6 | `MOBILE_SECURITY_STANDARDS.md` | Threats and controls |
| 7 | `MOBILE_OFFLINE_STRATEGY.md` | Offline levels and sync rules |
| 8 | `MOBILE_DESIGN_SYSTEM.md` | Tokens, components, role experiences |
| 9 | `MOBILE_RELEASE_STRATEGY.md` | EAS, identifiers, channels |
| 10 | `MOBILE_TESTING_STANDARDS.md` | Quality gates |
| 11 | `MOBILE_IMPLEMENTATION_ROADMAP.md` | Phases 0 to 4 |
| 12 | `MOBILE_APPLICATION_READINESS.md` | 0 to 5 scorecards |

## 4. Decisions needed from the product owner (all `[Requires approval]`)
1. Adopt a monorepo layout `HandyFlow-Apps/{apps,packages}` with npm workspaces.
2. Choose the standard SDK (recommendation: current stable, 58) and re-scaffold rather than upgrade step by step.
3. Rename and reorganise the four folders (details in the inventory).
4. Approve mobile auth backend work: body-returned refresh token for native clients, device registry, push tokens, tenant re-check on refresh.
5. One shared "HandyFlow Work" employee app plus separate field apps, or one app per module only.
6. First build target: Fuel driver and forecourt operator app.
7. Whether tenant users get MFA (the backend has it only for platform admins).
8. Employee identity: employee self-service uses a separate HR portal login, not tenant login. Keep two identity modes in the Work app, or unify them in the backend.
9. Untrack `node_modules` and `.env` under `HandyFlow-Apps/` (history rewrite not required).
