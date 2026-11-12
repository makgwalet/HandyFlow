# Mobile security standards

`[Proposed]` unless marked. Backend authorisation stays authoritative; the app only improves usability.

## 1. Authentication
| Topic | Standard |
|---|---|
| Login | Email, password, company slug (existing). OTP-by-phone in the Fuel prototype is **not** supported by the backend; drop it or build it `[Requires approval]` |
| Token storage | Access token in memory; refresh token in `expo-secure-store` only (Keychain/Keystore). Never AsyncStorage, never logs |
| Refresh | Single in-flight refresh; on 401 refresh once, then sign out. Rotation means the old token dies; never retry a refresh with the old value |
| Reuse detection | Backend revokes all sessions if a rotated token is replayed (`RefreshTokenService`). The app must therefore never run two refreshes in parallel |
| Logout | Revoke the server token, wipe SecureStore, wipe local caches and the queue prompt (see offline) |
| Session expiry | Show a re-login screen that keeps unsent queued work; do not delete it |
| Biometrics | Optional local unlock of the stored session (`expo-local-authentication`); never a replacement for the server login |
| MFA | None for tenant users today; required for Clinic and finance roles if the owner approves `[Requires approval]` |
| Device registration | Needs backend B3; until then use `expo-application` installation id as a fingerprint hint only |

## 2. Tenant and authorisation
- One tenant per session. Never send a tenant id from the client; the server takes it from the token.
- Handle 401 (re-login), 402/403 module not entitled (explain, do not crash), tenant suspended (sign out with message).
- Re-read permissions on app resume and after login.
- Division-gated areas (Fuel) must tolerate a refusal.

## 3. Data on the device
| Data | Rule |
|---|---|
| Tokens | SecureStore only |
| Offline queue | SQLite; sensitive payloads encrypted with a key held in SecureStore `[Proposed]`; clinical data not stored offline until reviewed |
| Photos and signatures | Stored in app-private storage, deleted after confirmed upload |
| Logs | No tokens, no personal data, no full payloads |
| Clipboard, backups | Exclude from OS backup (`allowBackup=false` on Android for apps with queues) |

## 4. Transport
HTTPS only; production builds refuse cleartext; certificate pinning only for high-risk apps (Clinic) because pinning complicates rotation `[Deferred]`. Send `Idempotency-Key` on every queued write.

## 5. Screens and devices
- Block screenshots on signature, payslip and clinical screens (`expo-screen-capture`).
- Auto-lock after inactivity (configurable per tenant, default 5 minutes for Clinic and HR).
- Root/jailbreak detection `[Deferred]`; prefer server-side controls.
- Lost or stolen device: admin revokes the device (B3) and `revokeAllForUser`; the next refresh fails. Fuel forecourt shared devices need per-operator PIN (B6).

## 6. Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Cookie-only refresh forces insecure workarounds | High | Backend B1 before any build |
| Suspended tenant keeps working until access token expiry | Medium | B2 |
| Offline replay duplicates stock movements | High | B5 |
| Prototype ships mock auth by accident | High | Remove `MOCK_DIRECTORY` and the dev role switcher from production builds; CI grep gate |
| `.env` and `node_modules` tracked in git | Medium | Untrack; secret scan in CI |
| Clinical data on device | High | Review before Phase 3; POPIA special personal information |
