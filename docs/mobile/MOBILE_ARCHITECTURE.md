# Mobile architecture

Separates **confirmed existing** backend facts from **proposed** mobile design. Every confirmed item cites a file under `platform/src/main/java/za/co/handyflow/platform/`.

## 1. Confirmed backend facts
| Area | Fact | Source |
|---|---|---|
| Login | `POST /api/v1/auth/login` takes **email, password and company slug**; one user belongs to one tenant | `identity/api/AuthController.java` |
| Access token | JWT (`JwtService`) with `userId`, `tenantId`, `email`, names, `permissions`; expiry from `app.security.jwt.expiration-ms`; a `tcv` token-version claim exists | `shared/JwtService.java` |
| Refresh | `POST /auth/refresh` reads an **httpOnly cookie**; rotates; reuse of a rotated token revokes all sessions for the user; stores a device fingerprint, IP, user agent | `AuthController`, `identity/application/internal/RefreshTokenService.java` |
| Refresh gap | Refresh returns `subscriptionStatus = null`; it does not re-check tenant status | comment in `AuthController.refresh` |
| Logout | Revokes one refresh token (single device) | `AuthController.logout`, `RefreshTokenService` |
| Revoke all | `revokeAllForUser(userId)` | `RefreshTokenService` |
| Tenant | `TenantContext` set from the JWT; endpoints never accept a client tenant id (enforced by tests in Fuel) | `shared/TenantContext.java`, `JwtAuthFilter` |
| Module entitlements | `FeatureGuard.requireModule`, plus an interceptor config | `billing/config/ModuleEntitlementInterceptorConfig.java` |
| MFA | Platform **admin** TOTP only (`AdminTotp*`); none for tenant users | `admin/dto` |
| Device binding | Guards only: enrol with PIN, face, device hardware id; activate replacement; revoke tokens; device history | `security/api/GuardAuthController.java` |
| Idempotency | Opt-in `Idempotency-Key` header filter; Fuel DTOs carry `clientRequestId` for complete, evidence, failed attempt, returns | `config/IdempotencyKeyFilter.java`, `shared/IdempotencyKeyService.java`, Fuel DTOs |
| Notifications | `/api/v1/notifications` list, unread-count, read, preferences; channels IN_APP, EMAIL, SMS. **No push token model** | `notifications` |
| Employee self-service | `HrEmployeePortalController` under `/api/v1/hr/portal` has its **own login** (`/auth/login`, `HrEmployeePortalAuthService`, portal JWT via `PortalJwtFilter`) and employee-scoped paths (profile, payslips, tax certificates, leave); `AttendanceController` uses normal tenant auth | `hr` |
| Division gating | `FuelDivisionRules` blocks routes by tenant divisions | `fuel` |

## 2. Proposed mobile layers
```
Screens (per app)
  -> feature hooks (TanStack Query, per module)
    -> api-client (fetch, envelope unwrap, Idempotency-Key, error mapping)
      -> auth (session, refresh, device id)  -> tenant/entitlement context
    -> offline (SQLite queue, sync engine) for writes
```
Envelope: backend responses are `ApiResponse{success, message, data}`; the Agriculture client already unwraps `data`. Keep that in `api-client`.

## 3. Answers to the architectural questions
| # | Question | Recommendation | Reason | Advantages | Disadvantages / Risks | Depends on |
|---|---|---|---|---|---|---|
| 6 | Integrate with HandyFlow auth | Keep the existing login (email, password, slug) and add a **mobile token mode**: refresh token returned in the body, stored in SecureStore, bound to a registered device. Browser cookie flow stays unchanged | Cookie-only refresh does not suit native clients | Reuses IAM, rotation, reuse detection | Backend change; token theft risk moves to the device | Backend work (section 4) |
| 7 | Tenant switching | v1: one tenant per login, sign out to change. v2: backend `POST /auth/switch-tenant` and a multi-tenant user model | No switch endpoint exists; a user has one tenant | Simple and safe now | Consultants or group users must log in again | Product decision on multi-tenant users |
| 8 | Permissions and entitlements in navigation | Build the menu from the JWT `permissions` plus a module-entitlement call; hide, never rely on it; handle 401/402/403 centrally (re-login, "upgrade" screen, "no access") | Backend remains authoritative | Clean UI, safe | Permission cache can go stale; refresh on resume | Entitlement endpoint for current user (verify; not found in this review) |
| 9 | Offline | Per-app level (see offline strategy) with one shared engine | Avoid five queue implementations | Consistent behaviour | Engine must handle attachments | Idempotency gaps closed |
| 10 | Unified employee app vs separate | **Both:** one `work` app for employee self-service and approvals; separate field apps for role-specific operations | A driver and an accountant need different apps; a leave request should not require installing five apps | Right-sized apps | Two kinds of app to maintain | Decision 5 |
| 11 | Customer-facing apps | Fuel portal, FM portal, property portal, client portal | Portals already exist on web | Reuse portal APIs | Separate JWT (`PortalJwtService`) | Demand validation |
| 12 | Internal enterprise apps | All field apps (Fuel, Fleet, Earthmoving, Warehousing, Security, Clinic) | Staff-only | Private distribution option | MDM needed for managed fleets | Release strategy |
| 13 | Independent versioning | App version per app; shared packages versioned in the workspace | Release cadence differs | Safe rollouts | Compat matrix | CI |
| 15 | Backend compatibility | API version header and `minSupportedAppVersion` endpoint; additive-only changes within `/api/v1`; contract tests in CI | Old installs live for months | Controlled breaking changes | Governance effort | Backend contract tests |

## 4. Backend change register (none implemented) `[Requires approval]`
| # | File / area | Current | Problem | Proposed change | Why | Depends | Risk |
|---|---|---|---|---|---|---|---|
| B1 | `AuthController.refresh`, `RefreshTokenService` | Cookie only | Native client cannot reliably use httpOnly cookies | Accept and return refresh token in body when header `X-Client: mobile` is set | Mobile auth | Device registry | Token exposure on device |
| B2 | `AuthController.refresh` | `subscriptionStatus` null; no tenant check | Suspended tenant keeps access until token expiry | Re-check tenant status on refresh | Suspension handling | Tenant repo | Extra lookup per refresh |
| B3 | New `identity` device registry | Only guards have devices | No remote revoke for staff devices | `POST /devices/register`, list, revoke; link refresh tokens to device | Lost phone | B1 | Schema change |
| B4 | `notifications` | No push tokens | No push | Token table + Expo push sender, new `PUSH` channel | Alerts | B3 | Provider dependency |
| B5 | Fuel `DispatchFuelRequest`, `DipReadingRequest`, `ReceiveFuelRequest`, shift close, stays | No `clientRequestId` | Offline replay may duplicate stock movements | Add `clientRequestId` with unique index, as done for evidence | Safe offline | Fuel stage | Migration on live tables |
| B6 | Fuel operator PIN | None | Shared forecourt devices | PIN verify endpoint like guards | Accountability | B3 | Lockout rules |
| B7 | Min app version endpoint | None | Cannot force upgrades | `GET /app/config` | Compat | none | Low |
| B8 | Tenant-user MFA | None | Higher-risk roles | TOTP like admin | Optional | Product | UX |
| B9 | Entitlement summary for current user | Not found | Mobile menu needs modules and permissions in one call | `GET /me/access` | Navigation | none | Low |

## 5. Two identity modes (confirmed)
Staff apps use tenant-user login (`/auth/login`). Employee self-service, and every web portal (fuel, FM, property, warehousing, bookkeeping, legal and others), use a **separate portal JWT** (`PortalJwtService`, `PortalJwtFilter`) with their own login endpoints. The proposed `HandyFlow Work` app therefore has to either (a) sign in through the HR portal login for payslips and leave and through tenant login for approvals and tasks, or (b) get a backend decision to unify employee identity `[Requires approval]`. Customer apps in Phase 4 will use portal auth by design, so `packages/auth` must support both modes behind one interface.
