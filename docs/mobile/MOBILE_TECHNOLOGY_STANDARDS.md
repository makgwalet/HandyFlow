# Mobile technology standards

## 1. Current state `[Verified]`
| Item | All three Expo projects |
|---|---|
| Expo SDK | 52.0.46 (pinned) |
| React Native | 0.76.7 (exact in Fuel scaffold, `^` in the others) |
| React | 18.3.1 |
| TypeScript | 5.3.3 / `^5.3.3`, `strict: true`, `extends expo/tsconfig.base` |
| Navigation | React Navigation v6 (not Expo Router) |
| State | zustand 4 |
| Forms | react-hook-form 7 + zod 3 |
| Data | TanStack Query 5 + axios (Agriculture only) |
| Storage | expo-secure-store, expo-sqlite |
| Device | expo-camera, expo-location, react-native-nfc-manager 3.14.14 |
| Architecture flag | `newArchEnabled: true` in `app.json` |

Reference for "current": docs.expo.dev on 2026-11-12 lists **SDK 58 = React Native 0.88, React 19.3, Node 22.13+, iOS 16.4+, Android 7+, Xcode 26.4+**, and says each SDK targets a single React Native version, following React Native's six releases a year.

## 2. SDK strategy
1. `[Proposed]` **One SDK for the whole ecosystem at any time**, held in a single version catalogue.
2. `[Requires approval]` **Target SDK 58** (current stable) for all new work. Because no app has shipped, re-scaffold each app on the target SDK and port its screens instead of stepping through 53, 54, 55, 56, 57. Risk: the Fuel prototype uses React 18 and React Navigation 6; React 19 and library majors need a compatibility pass first (a spike, not code in the apps).
3. `[Proposed]` **Upgrade policy:** adopt a new SDK at most every second release; stay within N-1 of Expo's current stable; upgrade all apps in one release train, with one lead app first (Fuel). Each upgrade is a ticket with the Expo changelog, a `npx expo-doctor` run, and a regression run.
4. `[Proposed]` Enforce in CI: `npx expo install --check` and `npx expo-doctor` must pass; a root script fails if any app's `expo` version differs from the catalogue.
5. `[Proposed]` Node version pinned (`.nvmrc`, `engines`) to the SDK's minimum (22.13 for SDK 58).
6. `[Deferred]` Do not upgrade `handyfarm-*` in place. Doing so would spend effort on SDK 52 code that will be replaced.

## 3. Dependency standard
| Concern | Standard | Shared or on demand |
|---|---|---|
| Navigation | **Expo Router** for new apps `[Requires approval]`; the prototypes use React Navigation 6, so porting needs a decision. Alternative: stay on React Navigation (current). Choose once for all apps | Shared |
| Server state | TanStack Query | Shared |
| Client state | zustand, small stores only | Shared |
| HTTP | One `fetch`-based client in `packages/api-client` (replace axios; fewer dependencies, native `AbortController`) | Shared |
| Forms | react-hook-form + zod | Shared |
| Auth storage | expo-secure-store | Shared |
| Offline store | expo-sqlite (one queue implementation) | Apps that need offline |
| Notifications | expo-notifications | On demand, after backend push tokens exist |
| Camera, location | expo-camera, expo-location | On demand |
| Files | expo-file-system, expo-image-picker | On demand |
| NFC | react-native-nfc-manager | Only apps that scan NFC (Security, maybe Warehousing). Remove from the Fuel scaffold unless used |
| Biometrics | expo-local-authentication | On demand |
| Crash reporting | One provider (Sentry has Expo guides) | Shared `[Requires approval]` |
| Testing | Jest (`jest-expo`), React Native Testing Library, Maestro for end-to-end | Shared |
| Unit libs | date-fns (already used) | Shared |

Rule `[Proposed]`: a dependency enters an app only when a screen uses it; the Fuel scaffold currently declares ten unused packages.

## 4. Repository structure `[Requires approval]`
```
HandyFlow-Apps/
  package.json            # npm workspaces, shared scripts, version catalogue check
  tsconfig.base.json
  apps/
    fuel/                 # id: za.co.handyflow.fuel
    agriculture/
    work/                 # shared employee app
    fleet/ earthmoving/ warehousing/ security/ ...
  packages/
    config/               # eslint, tsconfig, jest presets
    design-tokens/        # colours, type, spacing (no React)
    ui/                   # components built on tokens
    api-client/           # fetch wrapper, envelope unwrap, error mapping, Idempotency-Key
    auth/                 # login, refresh, SecureStore, session
    tenant/               # tenant + entitlement context
    permissions/          # permission checks, nav filtering helpers
    offline/              # SQLite queue, sync engine, status hooks
    testing/              # mocks, fixtures
  clinic-desktop/         # unchanged until desktop strategy is decided
```
- Independent `package.json` per app: **yes** (own dependencies, own `app.config.ts`, own EAS project). A root workspace gives one lockfile and hoisting; Expo supports monorepos from SDK 52.
- Shared packages live in `packages/` **beside** `apps/`, not inside an app folder, so an app can never import another app's code.
- Packages hold infrastructure only. No business screens go in packages, so apps stay independent.
- Copy rules: a module screen stays in its app until a second app needs it.

## 4a. Answers to the architectural questions (technology side)
| # | Question | Recommendation | Reason | Advantages | Disadvantages / Risks | Depends on |
|---|---|---|---|---|---|---|
| 1 | Own Expo project per module? | Yes, where a dedicated app is justified (Category A/B) | Independent releases and store listings | Small bundles, isolated failures | More pipelines | Roadmap |
| 2 | What is shared? | auth, tenant, permissions, api-client, offline, ui, tokens, config | Prevents duplicated security code | One fix, all apps | Package versioning | Monorepo decision |
| 3 | Where do packages live? | `packages/` next to `apps/` | Clear boundaries | Easy lint rule against cross-app imports | None notable | Layout approval |
| 4 | Independent `package.json`? | Yes | Own deps and EAS config | Independence | Version drift if not catalogued | CI version check |
| 5 | Enforce one SDK? | Catalogue + CI check (`expo-doctor`, version script) | Machine-enforced | No silent drift | Upgrades are coordinated | CI |
| 14 | Avoid duplicate code? | Shared packages, import-boundary lint, code owners on `packages/` | Two token files already exist | Consistency | Review overhead | Monorepo |
