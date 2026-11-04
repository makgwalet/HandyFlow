# HandyFlow – User Management & Access Control Audit

Date: 2026-11-04 · Scope: `platform/` (Spring Boot) and `handyflow-web/` (React) at branch `feat/theme-token-phase0`
Status: **audit only. Nothing was changed, no migration was written.** Approval is needed before anything in `IAM_IMPLEMENTATION_ROADMAP.md` starts.

## How to read this document

- **Verified** means I read the code path end to end. **Not verified** means I saw the code but did not trace or run everything. Nothing here was exercised against a running system; there is no live test of any exploit. Findings marked "code reading only" should be reproduced by a test before being treated as proven.
- The earlier `MODULE-AUDIT-identity.md` covered fixes inside the identity module (last-admin guards, seat limits). This audit goes wider: it follows access from invitation to every module and to the frontend.

---

## 1. Current architecture (what actually exists)

### 1.1 Components

| Concern | Where | Notes |
|---|---|---|
| Tenant user, role, permission entities | `identity/domain/model/User.java`, `Role.java`, `Permission.java` | `users` has `tenant_id`; unique `(tenant_id, email)` (migration V2). |
| Invitations | `identity/domain/model/UserInvitation.java`, `UserManagementService.inviteUser / acceptInvitation` | One role per invitation. 72 h expiry. |
| Login | `AuthService.login` | Needs **tenant slug + email + password**. |
| Tokens | `shared/JwtService.java`, `RefreshTokenService.java` | Access token 15 min (`application.yaml`, `expiration-ms: 900000`); refresh 7 days, rotated, reuse detection. |
| Request authentication | `shared/JwtAuthFilter.java` | Trusts the token's permission claim (`pz`, packed by `PermissionCodec`). No database read per request. |
| Endpoint authorization | `@PreAuthorize("hasAuthority('X')")`, `@EnableMethodSecurity` in `config/SecurityConfig.java` | 451 of 471 controllers carry `@PreAuthorize`. |
| Module entitlement | `billing/FeatureGuard.java` called as `featureGuard.requireModule("x")` | Per controller, hand-written. See finding F2. |
| Platform admin | `admin/` (`AdminUser`, `AdminJwtFilter`, TOTP login, `AdminImpersonationSession`) | Separate user table, separate JWT filter. |
| External users | `shared/PortalUser.java`, `PortalJwtService` (separate secret) plus one access-grant table per portal (e.g. `FmPortalAccessGrant`) | About ten portals. |
| Audit | `audittrail/` (append-only, trigger from V409) | **Not used by identity.** |
| Approvals | `approvals/` (rules, steps, delegation) and module-specific maker-checker rules | See section 8. |
| Frontend | `handyflow-web/src/pages/settings/TeamTab.tsx` (702 lines), `hooks/usePermission.ts`, `navigation/moduleSections.ts` | One screen for users, roles, invitations. |

### 1.2 Permission and role model

- `permissions`: global catalogue, unique `name` (V2). The code references **332 distinct authority names** (`hasAuthority/hasAnyAuthority`); every one of them also appears in the migrations.
- `roles`: per tenant, unique `(tenant_id, name)`. Role names are upper-cased.
- `role_permissions`, `user_roles`: many-to-many. The schema supports several roles per user; the API and UI assign **one** (`User.clearRoles()` then `assignRole()` in `updateUser`; `UserInvitation.role_id` is a single column).
- Roles created by code: `ADMIN` (at registration, `RoleService.createDefaultAdminRole`, gets *every* permission that exists at that moment) and `EMPLOYEE` (created on demand in `createDefaultEmployeeRole`: `USER_READ, CUSTOMER_READ, INVOICE_READ, BILLING_READ, REPORT_VIEW`).
- When a later migration adds permissions it grants them to all existing `ADMIN` roles with SQL (pattern in `V230__seed_agency_permissions.sql`; 43 migrations reference `name = 'ADMIN'`). So ADMIN silently accumulates every new capability.
- `OWNER` appears in `V383__finance_read_and_expense_permissions.sql` (`WHERE r.name IN ('ADMIN','OWNER')`), but **no code or migration creates an OWNER role**. That grant matches nothing.
- Permission name convention in use: `<MODULE>_<ACTION>` (e.g. `HR_READ`, `ACCOUNTING_MANAGE`, `AP_PAY`), but with no standard verb list. 58 different prefixes. `CLINIC_` alone has 99.

### 1.3 Answers to the 13 questions

1. **How does a user become a HandyFlow user?** Two ways. (a) `AuthService.register` creates a tenant plus its first user with `ADMIN`. (b) `UserManagementService.acceptInvitation` creates a new `users` row from an invitation. *Verified.*
2. **How does a user join a tenant after an invitation?** The accept endpoint (`POST /api/v1/identity/invitations/accept`, public) takes only the token and a new password. It always creates a **new** user row in the inviting tenant. There is no "sign in with an existing account and join". *Verified.*
3. **Can one user belong to multiple tenants?** Not as one identity. The same email can exist in several tenants, but each is a separate `users` row with its own password, and login requires the tenant slug. `PersonIdentity` (shared) only matches people across modules for HR-style data; it is not a login identity. *Verified.*
4. **Different roles in different tenants?** Yes, but only because they are different accounts.
5. **How are roles assigned?** At invitation (`roleId`, default `EMPLOYEE`) or later through `PUT /identity/users/{id}` with `roleId`, which replaces all roles with that one.
6. **How are permissions granted and revoked?** Only by editing a role's permission set (`PUT /identity/roles/{id}/permissions`) or changing the user's role. No direct per-user grants, no expiry, no "granted by".
7. **Are roles global, tenant, or module scoped?** Tenant scoped, module-blind. A role is a flat bag of permission names.
8. **Does inviting a user grant module access?** No module concept in the invitation at all. Access comes only from the role's permissions.
9. **How are subscribed modules connected to user permissions?** **They are not connected.** Entitlement is checked separately by `FeatureGuard.requireModule` inside controllers. A permission never depends on a subscription.
10. **What happens when a tenant removes a user's access?** There is no removal; there is deactivate. See 11.
11. **Suspended or deactivated?** `UserStatus.ACTIVE/INACTIVE/LOCKED`. `setUserStatus` sets INACTIVE. Login and refresh refuse inactive users (`AuthService.login`, `RefreshTokenService.refresh`, which also revokes all sessions at that point). `LOCKED` is defined but never set anywhere. There is no "suspended" state and no reason or date recorded.
12. **JWT after a permission change?** The old token keeps its old permissions until it expires (max 15 min). The next refresh re-reads the user and issues a fresh token. Deactivation does **not** revoke the current access token or sessions at the moment of deactivation (`setUserStatus` does not call the session sweep); the user is blocked at their next refresh, at most 15 minutes later.
13. **Can users call backend endpoints directly when the UI hides them?** Mostly no for permissions (451/471 controllers use `@PreAuthorize`); but **module entitlement is not enforced in many modules** (F2), and the frontend route guard only checks that a token exists (F12).

### 1.4 Invitation flow as built

1. `POST /identity/users/invite` needs `USER_INVITE`. Checks: disposable email, seat limit (`plans.max_users`, counts active users plus pending invites), email already a user in the tenant. Cancels any older pending invite for the same email.
2. Role: given `roleId` or default `EMPLOYEE`. **No check that the inviter may grant that role.**
3. Token: two random UUIDs concatenated (64 hex), stored **as plain text** in `user_invitations.token` (unique). Link is built with the hard-coded host `https://app.handyflow.co.za`.
4. Email is sent in a `try/catch`; failure is only logged, the API still reports success.
5. Accept: validates status and expiry, creates the user, returns a logged-in `AuthResponse`.
6. Missing: resend, change role of a pending invite, revoke of one specific module, expiry state (`InvitationStatus.EXPIRED` is **never set**), onboarding checklist, wrong-email protection (anyone holding the link can accept).

---

## 2. Findings

Severity is my judgement of exploitability and impact. "CR" = code reading only.

### F1 – Privilege escalation through invite, role change and role edit (High, CR)
- `inviteUser` accepts any `roleId` of the tenant. A user holding only `USER_INVITE` can invite an account into `ADMIN`, accept it and obtain every permission.
- `updateUser` with `roleId` is guarded only by `USER_UPDATE` and the last-admin rule. The comment in the code itself says there is "no self-edit restriction", so a user with `USER_UPDATE` can set their own role to `ADMIN`.
- `updateRolePermissions` with `ROLE_MANAGE` can add *any* permission, including ones the caller lacks, to any role except `ADMIN`, including their own.
- `createRole` has the same property.
- Evidence: `identity/application/internal/UserManagementService.java` (`inviteUser`, `updateUser`, `createRole`, `updateRolePermissions`); controller guards in `identity/api/UserController.java`.
- No test covers this (`UserManagementServiceTest` has 15 tests, none about escalation).

### F2 – Module entitlement is not enforced in many modules (High, verified by search)
`requireModule` is used for 25 module keys. Controllers in these packages have **no** `FeatureGuard` call and no alternative check anywhere in the package: `hr` (18 controllers), `accounting` (10), `projects` (23), `recruiter` (10), `auditor` (10), `accountant` (7), `contracting` (4), `secureaccess` (3), `documents`, `expenses` (2 each), and one each in `ap`, `approvals`, `audittrail`, `bookings`, `capture`, `catalogue`, `creative`, `desk`, `events`, `marketing`, `notifications`, `pos`, `supplychain`, `tasks`.
**Update 2026-11-04:** the product owner confirmed that CRM, Invoicing (capped) and Catalogue are the free entry tier; everything else is paid, so the other unguarded modules listed here are bugs, not design. Original note: some of these may be intended as included-for-everyone (the README names `crm`, `invoicing`, `catalogue` as core), but `hr` is a selectable product module (`OnboardingModuleSelection` lists `"hr"`) and is never enforced. Because `ADMIN` holds every permission, a tenant that never bought HR can call the HR API directly. Which of the other modules are meant to be free is a product decision I could not find written down.

### F3 – Development OTP endpoint reachable by any logged-in user when SMS is "off" (High if the setting is wrong in production; CR)
`contracting/api/OtpDevController` (`GET /api/v1/dev/otp/{partyId}`) is active when `sms.enabled=false`. `application.yaml` defaults `sms.enabled` to `${SMS_ENABLED:false}`, so any environment that does not set the variable exposes it. The controller has no `@PreAuthorize`; `anyRequest().authenticated()` is the only gate, so any tenant user can read the stored signing OTP of any party id. Needs: confirm `SMS_ENABLED=true` in production and make the default the safe one.

### F4 – No persisted audit of access changes (Medium, verified)
Invite, accept, role change, role permission change, deactivate, reactivate are only `log.info` lines. The `audittrail` module (append-only, DB trigger) is not called from `identity`. No before/after, no reason, no source IP. Platform impersonation *is* recorded (`AdminImpersonationSession` with reason, IP, start/end).

### F5 – ADMIN is the same as business authority (Medium, verified)
`ADMIN` has every permission, cannot be edited (`updateRolePermissions` refuses it), and receives every new permission automatically. That includes `PAYROLL_APPROVE`, `AP_PAY`, `INVOICE_REFUND`, `CREDIT_NOTE_APPROVE`, `ACCOUNTING_PERIOD_REOPEN`. Your stated preference (administration is not approval) is not achievable with the current model. Some modules do enforce maker-checker on user identity (see section 8), but an ADMIN can still be both sides in modules that do not.

### F6 – Default EMPLOYEE role leaks the user directory (Medium, verified)
`EMPLOYEE` includes `USER_READ`; `GET /identity/users` returns every tenant user with email, job title and the **full permission set** (`UserResponse.permissions`). Any employee can see who holds which powers.

### F7 – Role editor can only grant 18 of 332 permissions (Medium, verified)
`TeamTab.tsx` (`PERMISSION_GROUPS`, lines 37–44) lists six groups: users, roles, CRM, invoicing, billing, system. The API returns the whole catalogue but the UI renders only those. In practice custom roles cannot include HR, accounting, fleet, clinic or any other module permission through the UI; module access exists only for `ADMIN`, or via SQL.

### F8 – Seat limit counts expired invitations (Low, verified)
The seat check counts invitations with `status == PENDING`. Status never becomes `EXPIRED`, so expired, unaccepted invitations consume seats until someone cancels them.

### F9 – Invitation token and link handling (Medium/Low)
- Token stored in clear (refresh tokens, by contrast, are hashed in `RefreshTokenService.hash`). A database read exposes live invite links.
- Link host hard-coded (`https://app.handyflow.co.za`), same in the verify-email link. Breaks staging and any white-label domain.
- Email failure is swallowed: the admin sees "invited" even if no email left.
- `GET /invitations/validate/{token}` is public; protection is the IP-based `RateLimitFilter` only.

### F10 – Deactivation and role changes are not immediate (Medium, verified)
Maximum stale window is the access-token lifetime, 15 minutes, which is a reasonable default but is neither documented nor controllable (no "force sign-out", no short path for sensitive revocations).

### F11 – No account lockout (Low/Medium, known)
`UserStatus.LOCKED` is never set; rate limiting is by IP (already recorded in `MODULE-AUDIT-identity.md`).

### F12 – Frontend guards are cosmetic (Medium, verified)
- `App.tsx` `ProtectedRoute` checks only that a token exists. No route checks a permission or a module.
- Sidebar visibility comes from `navigation/moduleSections.ts` `visibleGroups(config, permissions)`; typing a URL still renders the page and relies on the API returning 403.
- Because of F2, for unguarded modules the API will not return 403 either.

### F13 – Tenant isolation relies on convention (Medium, needs triage)
759 service calls use tenant-scoped finders (`findByIdAndTenantId`, `findActiveById`), but **222** use plain `findById`. A mechanical scan found 168 of those with no "tenant" word in the following lines; clusters: `recruiter` 25, `security` 25, `hr` 17, `recruitmentagency` 13, `bookingagency` 11, `creative` 10, `marketing` 10, `accountant` 9. I read 14 of the request-driven ones in `recruiter`; each was either filtered by tenant afterwards, or chained from a tenant-scoped parent, so **I found no confirmed cross-tenant read**. This is a candidate list, not a list of bugs. It needs a systematic review and an automated rule (section 7 of the roadmap).

### F14 – Controllers without `@PreAuthorize` (Info, partially verified)
20 controllers. Most are public login or portal-auth controllers by design. To confirm individually: `secureaccess/api/SecureShareController`, `SecureCollaboratorPortalController`, `projects/api/ClientPortalController`, `security/api/GuardCpController` (guard JWT, comment says intentional), `clinic/api/ClinicDocumentVerificationController` (public), `contracting/api/OtpDevController` (F3). Also: `/api/v1/auditor/portal/auth/**` is missing from the `permitAll` list in `SecurityConfig` (already known).

### F15 – Other observations
- `SecurityConfig` CORS allows only `http://localhost:5173/5174`. Production presumably sits behind a same-origin proxy; worth confirming.
- `JwtAuthFilter` catches any exception, logs, and continues the chain as unauthenticated; safe but hides token errors.
- Platform impersonation is read-only, 15 minutes, with reason and IP (`AdminAuthService`, `Permission.readOnly` from V287). Good base for support access.
- Tests: 54 test files use MockMvc/SpringBootTest out of roughly 827 test sources; no test of tenant isolation or privilege escalation at the HTTP level for identity.

---

## 3. Frontend and backend parity (identity screens)

All calls in `TeamTab.tsx` hit real endpoints (`/identity/users`, `/roles`, `/permissions`, `/invitations`, invite, update, deactivate, reactivate, cancel invitation, create role, update role permissions). I found no mock data and no dead buttons in that file. Gaps:

| Gap | Evidence |
|---|---|
| No resend invitation | no endpoint, no button |
| No delete or rename role | no endpoint |
| No module concept in invite or user edit | `TeamTab.tsx` forms: name, email, title, department (free text), one role |
| Department is free text on the user, not a structure | `User.department` string |
| Cannot see who granted what or when | not stored |
| Role editor hides module permissions | F7 |
| Role dropdown lists all roles, even ones the signed-in user could not safely grant | F1 |
| External (portal) users are managed per module with no central view | ten portal grant tables |

Not verified: loading, error and empty states in every tab of `TeamTab.tsx` (I did not run the UI).

---

## 4. Existing structures worth keeping

- Short access token plus rotating refresh token with reuse detection (`RefreshTokenService`).
- Packed permission claim (`PermissionCodec`).
- `(tenant_id, …)` uniqueness and `findByIdAndTenantId` pattern.
- `approvals/` engine (rules, steps, delegation) and `audittrail/` (append-only).
- Platform admin separation, TOTP and read-only impersonation with reason.
- Portal separation (separate secret and filter) for external users.
- `is_read_only` flag on permissions (V287).

## 5. Technical debt that shapes the redesign

1. Permissions are strings with no metadata (module, verb, sensitivity, read-only is the only flag).
2. ADMIN is a snapshot that must be re-granted by SQL on every release.
3. Entitlement is scattered across hundreds of controller methods.
4. One account per tenant per person.
5. Portal grant tables repeat the same shape ten times.
6. No access-change audit.
