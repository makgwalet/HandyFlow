# Module audit — `identity` (tenants, users, roles/permissions, auth, invitations)

Status: **Complete for this pass.** First module modernized per `CLAUDE.md` /
`MODULE-MODERNIZATION-CHECKLIST.md`.

## 1. Discover

Backend: `platform/src/main/java/za/co/handyflow/platform/identity/**`
(entities, repositories, services, controllers, DTOs), plus the
cross-cutting security wiring in `platform/.../config/SecurityConfig.java`
and `RateLimitFilter.java`.

Frontend: `handyflow-web/src/pages/settings/{SettingsPage,TeamTab}.tsx`,
`handyflow-web/src/pages/auth/**`, `handyflow-web/src/types/auth.types.ts`.

Tests before this pass: **zero** under `za.co.handyflow.platform.identity`
(confirmed: 97 test files existed repo-wide, none in this module) despite
`identity` being the tenancy/auth foundation every other module depends on.

## 2. Map — capability/coverage gaps found

| Capability | Backend | Frontend | Gap found |
|---|---|---|---|
| Accept team invitation | `UserController.acceptInvitation()` / `validateToken()` | `AcceptInvitePage.tsx` | **Critical**: neither endpoint was in `SecurityConfig`'s `permitAll()` — both are documented as public/no-auth but were rejected by `.anyRequest().authenticated()`. Flow was completely broken end to end. |
| Billing contact | `TenantController.updateBillingContact()` (write) | none | Write path worked; `TenantDetails` never returned the three fields, and Settings had no UI at all — endpoint had zero frontend consumer. |
| Remove company logo | `TenantController.uploadLogo()` | Settings "Remove" button | `UploadLogoRequest.logoBase64` was `@NotBlank`, but the Remove button sends `""` — every removal attempt 400'd. Also: even if it hadn't 400'd, the service wrapped `""` into a garbage `data:image/png;base64,` placeholder instead of truly clearing the logo. |
| Edit user (name fields) | `UserManagementService.updateUser()` | `TeamTab.tsx` Edit modal | No validation either side — a cleared name field saved as `""`. |
| Company profile form | `TenantService.updateProfile()` | `SettingsPage.tsx` | Zero validation either side — unbounded strings, no required-field enforcement. |
| Deactivate / cancel-invite failures | `UserManagementService.setUserStatus()` / `cancelInvitation()` | `TeamTab.tsx` confirm modal | Mutations had no `onError` — any rejection (including the new last-admin guard) failed silently with no message shown. |

## 3. Understand — real user workflows affected

- **Team onboarding was fully broken**: nobody invited to a tenant could
  actually join.
- **Admin lockout risk**: nothing prevented a tenant ending up with zero
  ADMIN users (via role reassignment or deactivation), which would have
  been an unrecoverable support case (no user left with `ROLE_MANAGE`).
- Settings page couldn't manage a billing contact or reliably remove a
  logo.

## 4. Backend changes

- `SecurityConfig.java` — added the two missing `permitAll()` entries for
  invitation accept/validate.
- `RateLimitFilter.java` — added a matching rate limit for the
  now-reachable public accept-invitation endpoint.
- `TenantDetails.java` / `TenantService.java` / `TenantFacadeImpl.java` —
  billing-contact fields now round-trip; both call sites of the record's
  constructor updated to match.
- `TenantService.uploadLogo()` — blank now correctly clears the logo.
- `UpdateUserRequest.java` — `@NotBlank`/`@Size` on name fields.
- `UpdateTenantProfileRequest.java` — length caps added.
- `UploadLogoRequest.java` — removed the `@NotBlank` that broke logo
  removal; added a size cap (`@Size`) as the real security backstop
  (frontend's 200KB check is client-side only, not a security boundary).
- `UserManagementService.java` — last-remaining-admin guard added to
  `updateUser()` (role reassignment) and `setUserStatus()` (deactivation);
  `cancelInvitation()` now rejects non-pending invitations.
- `UserRepository.java` — new `countByTenantIdAndRoleNameAndStatus()`
  query backing the admin guard.

## 5. Frontend changes

- `types/auth.types.ts` — added missing `subscriptionStatus` (fixes a
  real pre-existing `tsc` error in `LoginPage.tsx`).
- `LoginPage.tsx` — surfaces the real backend error message (suspended
  tenant / deactivated user / rate-limited) instead of a hardcoded
  "Invalid credentials" for every failure.
- `TeamTab.tsx` — fixed the 2 pre-existing real `tsc` errors in this
  file; added `onError` to `deactivateMutation`/`cancelInviteMutation`;
  converted the Edit User modal to controlled state with required-field
  validation matching the new backend rule.
- `SettingsPage.tsx` — added the "Billing Contact" section (wired to the
  now-working backend endpoint) and real client-side validation on the
  Company form before Save.

## 6. Verify

- `npx tsc -b --noEmit`: baseline had 314 pre-existing errors repo-wide.
  After this change: **314 total, 0 new, 4 fixed** (the 2 `LoginPage.tsx`
  `subscriptionStatus` errors, the 2 `TeamTab.tsx` errors).
- `npx vite build`: succeeds cleanly.
- `npx eslint` on the 4 touched frontend files: baseline 15 pre-existing
  errors on these exact files (all `@typescript-eslint/no-explicit-any`,
  matching this codebase's pervasive existing `(e: any) =>` convention in
  every mutation's `onError`, plus 1 unused-var and 2 pre-existing
  `no-unused-expressions` I didn't touch). After: 18 — the unused-var one
  is now fixed; the net new 4 are `any` usages in the 2 new mutations,
  matching the exact same convention every sibling mutation in these
  files already uses. Not a new deviation; fixing the convention
  repo-wide is out of scope for a single-module pass.
- **Backend (`mvn test` / `mvn compile`) could not be executed in this
  environment** — Maven Central (`repo.maven.apache.org`) is not on this
  sandbox's allowed network list, confirmed by direct attempt, and no
  dependency cache pre-existed. All Java changes were written against the
  real, directly-read source (not assumed), manually re-verified for
  brace/paren balance and signature correctness, and 3 new test files
  added (890 lines) matching this codebase's own established Mockito/
  AssertJ/JUnit5 conventions (see `ClinicServiceTest` /
  `PayrollEngineParityTest` for the precedent followed). **Run `mvn test`
  in a normal environment before merging** — this has not been confirmed
  green by an actual test run.

## 7. Remaining gaps (documented, not fixed this pass — out of scope)

- `Tenant.status` has no reachable path to `CANCELLED` anywhere in the
  codebase (confirmed by search) — the guard in `Tenant.activate()`
  checking for it is currently dead code. Likely intentional (the real
  cancellation source of truth is `billing.Subscription.status`, per
  `AuthService`'s own extensive comment on this exact distinction), but
  worth a deliberate product decision rather than leaving unconfirmed.
- No "resend invitation" action — an admin must cancel + re-invite if an
  invite email is lost.
- No delete-role endpoint/UI (soft-delete-only via deactivate is the
  existing pattern for users; roles have no equivalent).
- IP-based rate limiting only (not per-account) — a distributed attacker
  could still brute-force a single account's password across many IPs.
  `User.UserStatus.LOCKED` is defined but never set anywhere in the
  codebase.
- Repo-wide `@typescript-eslint/no-explicit-any` convention (see
  Verify section) — a lint-hygiene initiative, not this module's scope.

## 8. Next recommended module

Per the earlier repo-wide audit: **`billing`/`FeatureGuard`** (module
catalogue, subscription, tenant-module activation) — the other half of
the auth/tenancy foundation, directly coupled to `identity` via the
already-documented accepted circular dependency
(`AuthService.subscriptionQueryFacade` ↔ `BillingEventHandlers` listening
to `TenantCreatedEvent`), and the gate every other module's `FeatureGuard.
requireModule()` call depends on.
