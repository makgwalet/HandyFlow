# Mobile testing standards

Current state `[Verified]`: no tests, no test scripts, no Jest config in any Expo project. The web app has Vitest suites (445 Fuel and navigation web tests pass) and the backend has JUnit suites; the mobile apps have none.

## 1. Test types
| Type | Tooling | Scope |
|---|---|---|
| Unit | Jest (`jest-expo`) | Pure logic: queue ordering, idempotency ids, geo, validation schemas, permission filters |
| Component | React Native Testing Library | Forms, wizards, empty/error/offline states |
| Hook / API | RNTL + mocked `fetch` (MSW) | Query hooks, error mapping, envelope unwrap |
| Contract | Generate types or fixtures from backend DTOs; CI job compares to `openapi` output `[Requires approval]` | Prevents drift (the Agriculture client hand-copies DTOs) |
| Navigation | RNTL | Role and entitlement based menus |
| Auth | Unit + integration | Refresh once, reuse safety, logout wipes, expired session keeps queue |
| Offline | Unit on SQLite (in-memory) | Replay order, idempotent retry, conflict and failure states, attachments |
| End-to-end | Maestro on a device or emulator | Login, deliver with signature offline then sync, dip, dispense |
| Accessibility | RNTL role queries, manual screen reader pass | Labels, focus, font scale |
| Performance | Release-build profiling on a low-end Android | Startup, list scroll, queue of 500 items |
| Security | Secret scan, dependency audit, manual checklist | See security standards |
| Device matrix | Two Android (one low end, API 24+ per SDK minimum) and two iOS versions | Camera, GPS, SecureStore |

## 2. Cross-stack rule
Any backend change in the change register (`MOBILE_ARCHITECTURE.md` section 4) ships with backend tests (JUnit) **and** a mobile contract fixture.

## 3. Observability
Crash reporting and release health in production builds; structured logging without personal data; a "send diagnostics" action in the sync centre.

## 4. Production-ready checklist
- Real auth, tenant and permission handling; no mock code paths in the release bundle.
- All writes idempotent end to end; offline suite green.
- No `localhost` URLs; environment profiles configured.
- Typecheck, lint, unit, component, navigation, auth, offline suites pass in CI.
- Maestro smoke test passes on both platforms.
- Accessibility pass; store listing assets and privacy answers complete.
- Crash reporting verified with a test crash.
- Rollback procedure rehearsed.
