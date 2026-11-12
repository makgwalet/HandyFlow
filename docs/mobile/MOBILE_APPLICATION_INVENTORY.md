# Mobile application inventory

Evidence gathered by reading files only. Build and typecheck were **not run**: no project has `node_modules`, and installing would change the project, which the brief forbids. Status: `[Verified]` unless stated.

## 1. Fuel scaffold: `HandyFlow-Apps/handyflow-fuel/`
| Area | Finding |
|---|---|
| Business module | Fuel (intended) |
| Expo / RN / React / TS | `expo 52.0.46`, `react-native 0.76.7`, `react 18.3.1`, `typescript 5.3.3` (`package.json`) |
| Navigation | `@react-navigation` v6 declared, **no code uses it** |
| Auth / tenant / API | None |
| Screens | One: the template `App.tsx` |
| Declared but unused | zustand, react-hook-form, zod, expo-sqlite, expo-camera, expo-location, expo-secure-store, nfc-manager, date-fns |
| Config | `app.json`: `newArchEnabled`, `edgeToEdgeEnabled`; no bundle id, no permissions plugins |
| Tests / build / CI | None |
| Docs | `AGENTS.md` (points at Expo v54 docs, wrong); `CLAUDE.md` is just `@AGENTS.md` |
| Readiness | 1 (scaffold) |
| Gaps | Everything. Also a name clash with the real Fuel prototype below |

## 2. Fuel prototype: `HandyFlow-Apps/handyfarm-sync-alerts/handyfarm/`
| Area | Finding |
|---|---|
| Business module | Fuel (permission strings and screens say "fuel transactions") |
| Versions | Same as above, with `^` ranges for react, react-native, typescript |
| Navigation | React Navigation v6: `RootNavigator`, `AuthNavigator`, `MainNavigator`, flow navigators for Delivery, Dip, Dispense, Receive, Shift; **role tabs** in `navigation/roleTabs.tsx` (FUEL_MANAGER, OPERATOR, DRIVER) |
| Authentication | **Mocked.** `store/authStore.ts` has a `MOCK_DIRECTORY` of three users, accepts any 6-digit OTP, has a dev role switcher; session blob in SecureStore |
| Tenant | None |
| API integration | **None.** No `fetch` or `axios` in `src/`; data from `src/data/mock*.ts` |
| Screens | About 60 across auth, home (Today), delivery (arrival, in transit, checklist, receiver, signature, proof, camera, problem report), dispense (vehicle, tank, quantity, meter, authorisation, evidence), dip (tank, physical reading, reconciliation, variance reason), receive, shift, alerts |
| Offline | `transactionQueueStore.ts` is **in memory**; envelope `{clientRequestId, operation, createdAt, userId, payload, status}`; `syncStore.simulateSync()` fakes success after 900 ms |
| Stores | zustand: auth, delivery, dip, dispense, receive, shift, variance, queue, sync |
| Design | `theme/tokens.ts`; shared `Card`, `StatusPill`, `SignaturePad`, `TankRow` etc. |
| Tests | None |
| Config | `app.json` has camera and location permission strings; no bundle id |
| Docs | `BACKEND_GAPS.md` (369 lines, 7 phases). Written against `handyfuel.txt`, a spec file that is **not in the repository** |
| Readiness | UI prototype, about 21/70 on the scorecard |
| Gaps | Real auth, API, persistent queue, tenant, permissions, tests, build config, re-baseline of `BACKEND_GAPS.md` |

## 3. Agriculture app: `HandyFlow-Apps/handyfarm-offline-increment/handyfarm/`
| Area | Finding |
|---|---|
| Business module | Agriculture |
| Versions | Expo 52.0.46, RN `^0.76.7`; adds `@tanstack/react-query ^5.62`, `axios ^1.7.9` |
| Navigation | React Navigation v6 with a `ProductionStackNavigator` |
| Authentication | `api/client.ts` reads `handyflow_auth_token` from SecureStore and sends a Bearer header. **No login screen.** The file says the real auth mechanism "was not inspected" |
| Tenant | Assumed server-side via token; no tenant UI |
| API | Typed clients for farms, animals, groups, crop cycles matching backend DTOs; `API_BASE_URL` from `EXPO_PUBLIC_API_BASE_URL`, default `http://localhost:8080/api/v1` |
| Screens | Home, farm hub, farm/animal/group lists and profiles, record weight/treatment/mortality, crop cycles, record planting/input/scouting/harvest |
| Offline | `offline/db.ts` SQLite table `pending_mutations`; `offline/queue.ts` replays POST/PATCH/PUT after network errors. **No idempotency key, no attachments, no conflict handling** (stated in `12-OFFLINE-ARCHITECTURE.md`) |
| Design | `design-system/tokens.ts`, `ThemeProvider.tsx` (separate from the Fuel prototype's `theme/tokens.ts`) |
| Tests / build / CI | None |
| Docs | `docs/agriculture/00-OVERVIEW`, `10-API-MAP`, `11-API-GAPS`, `12-OFFLINE-ARCHITECTURE` (676 lines): the best documented project |
| Readiness | About 24/70 |

## 4. Desktop (out of scope): `HandyFlow-Apps/clinic-desktop/`
Vite 5, React 18, Tauri 1 CLI, Dexie, jsPDF. `.env` holds `VITE_API_URL`. `node_modules/` is **tracked in git**. `[N/A]` for the Expo standard; it needs its own desktop strategy `[Deferred]`.

## 5. Cross-project problems
| # | Problem | Evidence | Proposed fix | Approval |
|---|---|---|---|---|
| 1 | Two apps share the name `handyfarm` | both `package.json`, `app.json` | Rename Fuel prototype to `handyflow-fuel`, Agriculture to `handyflow-agriculture` | Yes |
| 2 | Empty Fuel scaffold beside the real Fuel prototype | `handyflow-fuel/` vs `handyfarm-sync-alerts/` | Port prototype into the scaffold name and delete the empty one | Yes |
| 3 | Duplicated stores, tokens, components, no shared package | `theme/tokens.ts` vs `design-system/tokens.ts` | Shared `packages/` | Yes |
| 4 | `AGENTS.md` says Expo v54 | all three | Correct to the chosen SDK | Yes |
| 5 | Version ranges differ (`^` vs pinned) | `package.json` files | One version catalogue | Yes |
| 6 | `node_modules` and `.env` tracked | `git ls-files HandyFlow-Apps` | `git rm --cached`, add `.gitignore` | Yes |
| 7 | No bundle ids, no env profiles | `app.json` | `app.config.ts` per app | Yes |
| 8 | Fuel gap list references a spec not in the repo | `BACKEND_GAPS.md` header | Re-baseline against the real backend (see `MOBILE_MODULE_ASSESSMENT.md` section 5) | No |
