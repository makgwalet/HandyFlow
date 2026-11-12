# Mobile release strategy

Nothing below exists yet: no `eas.json`, no bundle identifiers, no CI for mobile `[Verified]`. All items `[Proposed]` unless noted.

## 1. Identity and accounts
| Decision | Recommendation |
|---|---|
| App identifiers | One per app, reverse-domain, immutable: `za.co.handyflow.fuel`, `.work`, `.fleet`, `.agriculture`, ... (confirm the owned domain `[Requires approval]`) |
| Expo organisation | One EAS organisation, one EAS project per app |
| Developer accounts | One Apple and one Google Play organisation account owned by the company, not a person |
| Environments | `development`, `preview`, `production` EAS profiles, each with its own API base URL via `app.config.ts` and EAS environment variables (no `localhost` defaults in release builds) |
| Variants | Different bundle suffix per environment so all three install side by side |

## 2. Distribution
| App type | Channel |
|---|---|
| Employee and field apps | Private: TestFlight and Apple Business Manager / custom apps; Google Play closed or managed Google Play; internal APK for sites without store access |
| Customer or supplier apps | Public stores (Phase 4) |
| Clinic | Private, MDM-friendly |

## 3. Versioning
- `version` per app (semantic); build number auto-incremented by EAS (`appVersionSource: remote`).
- Backend sends `minSupportedAppVersion` (B7); below it, the app shows "update required".
- Shared packages versioned in the workspace; apps pin exact versions.

## 4. OTA updates
EAS Update for JavaScript-only fixes, on channels `preview` and `production`, with runtime version tied to the SDK and native dependencies so an OTA never reaches a binary that cannot run it. Rollback: republish the previous update to the channel; for native problems, expedite a store build. Never OTA a change that alters the queue schema without a migration.

## 5. CI/CD
Shared pipeline per app: install, typecheck, lint, unit tests, `expo-doctor`, dependency catalogue check, secret scan, EAS build for `preview` on merge, `production` on tag. Path filters so a change in `apps/fuel` does not build `apps/fleet`; a change in `packages/*` builds all.

## 6. Release gate
Production release requires the checklist in `MOBILE_TESTING_STANDARDS.md` section 4 and a rollback plan.
