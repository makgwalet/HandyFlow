# IAM Implementation Roadmap (proposal)

Status: **Phase 0 items 0.1 to 0.8 delivered in patch 0606 (untested on a database; see ENTITLEMENT_TIERS_AND_GAPS.md section 1 and 5). Later phases not started.** Phase 0 is the part I recommend approving first because it closes concrete findings from `USER_MANAGEMENT_AUDIT.md` without schema redesign. Sizes: S ≤ 3 days, M ≤ 2 weeks, L > 2 weeks of one developer, rough and unvalidated. Each phase ships behind per-tenant flags where behaviour changes, and has a rollback.

## Update 2026-11-04: not yet in production

Because there are no live tenants, these changes apply to the plan below:

- **No shadow mode, dual write or report-only release.** Entitlement is enforced from the first release of Phase 1; `iam.v2` flags are unnecessary. Replace "dual write with `user_roles`" and "reconciliation report" by a single migration that converts data and drops the old table.
- **Direct renames are allowed:** `USER_INVITE`, `USER_UPDATE`, `USER_DEACTIVATE`, `ROLE_MANAGE`, `SETTINGS_MANAGE` become the `TENANT_*` names (API contract section 13) in one migration, with the controllers updated together. No accepted-both transition.
- **`ADMIN` becomes Owner** (first user of each tenant) **and Administrator** (others) in one migration; new permissions are never auto-granted to Administrators.
- **Accounts and memberships (Phase 3) can be done first-class,** with no verified-email merge problem: there is no legacy duplicate data to protect. Phase 3 can move before Phase 2 if you prefer one UI built once on the final model.
- **Test data:** reseed development and test tenants rather than backfilling. Keep one small script that creates a sole-user tenant, a two-person tenant and an enterprise-shaped tenant for tests.
- **Still required even without production:** the grantor ceiling, audit table, hashed invitation tokens, the controller coverage test and the build rules. They are cheapest to add now.
- **Free tier work (new):** express CRM, Invoicing and Catalogue entry tiers as plan data and build the missing quote/invoice cap (nothing limits them today). Needs your list of which features are "upgrades".
- **Sole-user behaviour (new):** self-approval exception and flagging as described in `IAM_TARGET_ARCHITECTURE.md` section 15.3, built together with `SeparationOfDutiesPolicy` in Phase 1, tested with the sole-user and two-person tenants above.

## Dependency overview

```
P0 hardening ──► P1 catalogue+entitlement+SoD ──► P2 invitations v2 + People & Access UI + templates
                                 │                              │
                                 └────────────► P3 accounts & memberships + access version
                                                              │
                                                              ▼
                                 P4 scopes, org units, delegation, reviews, module-admin, support sessions v2, external view
```

## Phase 0 – Close the open findings (M)

| Item | Closes | Change | Acceptance criteria |
|---|---|---|---|
| 0.1 Grantor ceiling | F1 | In `inviteUser`, `updateUser`, `createRole`, `updateRolePermissions`: compare requested role/permissions with the caller's own permissions; refuse otherwise (`403 GRANT_ABOVE_AUTHORITY`); block changing one's own role | test: caller with only `USER_INVITE` cannot invite as `ADMIN`; caller with `USER_UPDATE` cannot set own role to `ADMIN`; caller with `ROLE_MANAGE` cannot add a permission they lack |
| 0.2 Dev OTP endpoint | F3 | require explicit property to enable, default off; add `@PreAuthorize("hasAuthority('PLATFORM_DEV')")` or remove from non-local profiles; confirm `SMS_ENABLED=true` in production | endpoint returns 404 in default and production profiles; startup logs a warning if enabled |
| 0.3 Access audit | F4 | new append-only `access_audit_events` (same trigger approach as V409); write on invite, accept, cancel, role create/edit, role assignment, deactivate, reactivate | each action produces one event with actor, target, before/after |
| 0.4 Immediate sign-out on deactivate | F10 | call the existing session sweep in `setUserStatus` | after deactivation the refresh token is rejected at once; unit test |
| 0.5 Invitations | F8, F9 | expiry sweep sets `EXPIRED`; seat check ignores expired; store `token_hash`; link base URL from configuration; surface email failure to the admin; add resend | pending count excludes expired; plaintext token not in DB; resend works |
| 0.6 Directory leak | F6 | `GET /users` returns permission sets only to holders of an access-review permission; remove `USER_READ` from default `EMPLOYEE`, replace with a name-only lookup endpoint where UIs need it (e.g. assignee pickers) | employee-token test: no permission data of others |
| 0.7 Role editor | F7 | `PermissionResponse` gains `moduleKey`; UI groups by module from the API instead of the six hard-coded groups | custom role can include any catalogue permission the creator may delegate |
| 0.8 Controller coverage test | F2 | ArchUnit/test that lists controllers without `FeatureGuard`/`@RequiresModule` and fails on any not on an explicit allow-list (the allow-list itself is the product decision about free modules) | list reviewed and agreed |

Dependencies: none. Risks: 0.6 can break screens that call `/identity/users` for pickers (`facilities.api.ts` does) → add lookup endpoint first. 0.2 may break a staging contract-signing demo → communicate.

## Phase 1 – Catalogue, entitlement, separation of duties (L)

1. **Permission metadata** (`module_key`, `resource`, `verb`, `sensitivity`, `requires_second_person`, `grantable_by`) added by expand migration; backfill from a prefix map reviewed by a person; test that every `hasAuthority` name exists in the catalogue (fails the build otherwise).
2. **Entitlement on token**: drop non-entitled modules' permissions at mint (login, refresh, invite accept); `tcv` claim; module-entitlement event bumps a tenant version.
3. **`@RequiresModule`** annotation plus interceptor, applied by package; Phase 0.8 test becomes strict.
4. **`SeparationOfDutiesPolicy.assertDifferentActor`** and toxic-pair table; wire into AP pay, payroll approve/run, journal review, credit-note approval, refunds first; then the rest. Build rule: methods guarded by `*_APPROVE`, `PAY`, `ISSUE`, `SIGN` call it.
5. **Reclassify approval permissions out of administrative templates** (matrix doc).

**Status 2026-11-05:** the build rule in item 1 ("every `hasAuthority` name exists in the catalogue") is in place as `AuthorityCatalogueCoverageTest` (patch 0628). On the current source all 4,185 guard uses resolve to the 404 catalogue names; the only exception is `PORTAL_USER`, issued by `PortalJwtFilter`. Still to do in item 1: the metadata columns (`module_key`, `resource`, `verb`, `sensitivity`, `requires_second_person`, `grantable_by`) and the reviewed prefix map that fills them.

**Status of item 4, 2026-11-05 (patch 0629).** The code is further along than this section assumed. Checked against the sole-user rule in `IAM_TARGET_ARCHITECTURE.md` 15.3:

| Area | Before | Now |
|---|---|---|
| AP bills, second approval, payment, batch release, supplier banking | Different person unless nobody else can; sole exception logged (`ApDutyChecks`) | unchanged |
| AP supplier credit notes | Creator excluded from approvers through the approvals engine when another approver exists | unchanged |
| Manual journals (post) | Maker could never post, even as the only person able to: a sole-user business could not post its own journals | `SeparationOfDutiesChecker`: refused only when another `ACCOUNTING_ADMIN` holder exists |
| Expense claims (approve, pay out) | Submitter or payee could never approve or pay, same lock-out | Same checker (`EXPENSES_MANAGE`, `EXPENSES_ADMIN`) |
| Payroll | Tenant setting `allowSelfApproval` | unchanged (a different, explicit mechanism) |
| Sales refunds and credit notes (invoicing) | No maker/checker at all; a refund is issued in one step | **open**: needs a pending-approval state, so a design decision |
| Persisting `self_approved` for the accountant report | AP and the new checker only log a warning | **open**: needs columns on each approvable table |

New shared pieces: `shared/SeparationOfDuties` (pure rule) and `shared/SeparationOfDutiesChecker` (counts other holders of the permission). AP keeps its own `ApDutyChecks` for now; moving it onto the shared checker is a no-behaviour-change follow-up.


Acceptance: a tenant without HR cannot call any HR endpoint even as `ADMIN` (integration test per module); no controller uses an unregistered authority; approving one's own AP bill, payroll run, journal, credit note, expense is refused for every role including `ADMIN`. Risks: tenants that today use a module without a subscription row (trials, internal tenants, demo tenant) lose access → run in **report mode** for one release (log what would have been blocked) before enforcing; data from `tenant_modules` must be verified clean first. Dependencies: product decision on which modules are free.

## Phase 2 – Invitations v2, templates, People & Access UI (L)

1. Invitations with several grants, per-module roles, states, resend, revoke, preview, decline, email binding (API contract section 2).
2. Role templates per module, shipped and versioned, adopted into the tenant (start with the core modules: CRM, Invoicing, Accounting, AP, HR & Payroll, Fleet, POS; Clinic's existing templates moved into the common mechanism).
3. Multi-role assignments (`role_assignments`) with grantor, time, expiry, reason; dual write with `user_roles`.
4. UI screens 1–8, 10–12, 20 (UX spec), simple mode first.
5. Per-tenant flag `iam.v2`; legacy `TeamTab` stays for tenants not switched.

Acceptance: invite with two modules and two roles → accept → correct menus and 403 elsewhere; resend rotates token; expired link rejected; reconciliation report shows 0 differences between `role_assignments` and `user_roles` for switched tenants. Risks: dual-write drift (compare job nightly); UI scope (cut screens 13–19 to later).

## Phase 3 – Accounts, memberships, immediate revocation (L)

1. `accounts` (global login) and `tenant_memberships`; `users.id` stays as membership id; backfill accounts only by **verified** email; users with unverified or duplicate emails remain separate until verified.
2. Login: email + password, then tenant picker (slug login kept for compatibility); switch tenant without re-login.
3. Invitations accept into existing account.
4. `acv`/`tcv` claims, filter cache, `401 ACCESS_CHANGED`, refresh flow, suspension revokes sessions.
5. Owner role, ownership transfer, last-owner guard; map legacy `ADMIN`.

Acceptance: one person, two tenants, one login; revoke on one tenant takes effect within the cache TTL while the other is unaffected; load test shows no more than one extra cache lookup per request. Risks: account merge mistakes (never auto-merge unverified), password policy differences between tenants, support burden → staged rollout and an undo (split) tool. Dependencies: Phase 2 tables.

## Phase 4 – Enterprise features (L, incremental)

Org units and data scopes (module by module, starting with HR and Fleet); delegations and expiry sweeps; module-admin delegation limits and Access tabs in modules; access review campaigns and reports; support sessions v2 (ticket, tenant notification, approval for writes); central external-user view and eventually unified `external_access_grants`; MFA for owners and administrators.

Acceptance and risk per item to be written when scheduled; data-scope work carries the highest regression risk and gets its own design note.

## Test strategy

**Unit:** grantor ceiling matrix; last-owner and last-admin guards; toxic-pair detection; role assignment merge (union); invitation state machine; `SeparationOfDutiesPolicy`; version bump rules; token claim filtering by entitlement.

**Service/controller security (MockMvc):** every identity endpoint with no token, wrong-tenant token, insufficient permission, sufficient permission (expect 401/404/403/200).

**Integration (Testcontainers Postgres, real Flyway):**
1. Tenant A admin requests tenant B's user, role, invitation, assignment by id → 404 (IDOR) on every identity endpoint.
2. Role id from tenant B used in tenant A invite → rejected.
3. Mass assignment: extra fields (`tenantId`, `status`, `roles`) in update bodies are ignored.
4. Invite with only `USER_INVITE` as `ADMIN` → 403; own role escalation → 403.
5. Invitation: expired, reused, revoked, wrong email, token guessing (rate limit), double accept race (two threads: one wins).
6. Suspension: refresh rejected immediately; old access token rejected within the cache TTL; other tenant's membership unaffected.
7. Permission revoke while signed in: next call after TTL returns `ACCESS_CHANGED`, then works with reduced permissions.
8. Entitlement: cancel module → module endpoints 403 and tokens re-minted without the module's permissions; resubscribe → assignments `pending review`, not active.
9. Last owner: demote, suspend, remove, transfer → refused; with two owners allowed.
10. Maker-checker: creator of an AP bill / payroll run / journal / credit note / expense cannot approve it; `ADMIN` cannot either; a different user with the approve permission can; approval above limit refused.
11. Toxic combination: assigning `AP_MANAGE` + `AP_PAY` blocked in enterprise mode, warned and acknowledged in simple mode.
12. Platform: support session without reason refused; token read-only (write endpoints 403); expires; tenant sees it in its log; super admin token cannot read business data without a session.
13. Data scope (Phase 4): user scoped to branch X cannot read branch Y records by id.
14. Every controller either has `@PreAuthorize` or is on the public allow-list (build-time).
15. Every `hasAuthority` name exists in the catalogue (build-time).
16. `findById` without tenant check: ArchUnit rule forbidding plain `JpaRepository.findById` in `*Service` classes except on an allow-list that must be justified (starting from the 168 candidates in audit F13).

**Frontend (vitest):** route guard blocks missing permission and missing module; `<Can>` hides actions; invite wizard shows only entitled modules and only grantable roles; preview shows gained/lost; conflict message on concurrent edit; empty, loading, error states of each screen; matrix keyboard navigation.

**End-to-end (Playwright):** admin invites → email captured → invitee accepts → lands on correct dashboard → can use granted module, gets "no access" elsewhere; admin revokes → invitee's open page is redirected after the next call.

## Global acceptance for the programme

No existing tenant, user, role or subscription is broken at any phase (reconciliation reports clean, shadow mode before switch); every access change appears in `access_audit_events`; no permission is grantable above the grantor's authority; no module is reachable without entitlement; a revocation is effective within seconds; simple mode is usable without reading documentation.

## Decisions needed before Phase 1

1. ~~Which modules are free for all tenants.~~ **Answered:** CRM, Invoicing (capped quotes and invoices) and Catalogue; upgrades and other modules paid. Still needed: the list of features that count as upgrades and the cap values.
2. ~~Approval of the Owner/Administrator split.~~ **Approved.** `ADMIN` converts directly (no live data).
3. Whether to ship simple-mode only first and hold the enterprise features (my suggestion: yes).
4. Cache TTL acceptable for revocation (proposed 15 s) and whether a distributed cache or pub/sub exists in production.
5. MFA ambitions (not covered by this plan except for owners/administrators in Phase 4).
