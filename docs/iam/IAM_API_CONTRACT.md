# IAM API Contract (proposal)

Status: proposal. Existing endpoints stay and keep their behaviour; new ones are additive under `/api/v1/identity`. All responses use the existing `ApiResponse` envelope. Tenant always comes from the token (`TenantContext`), never from the path or body. Authorization column: `P:` permission, `G:` grantor rule, `M:` module entitlement.

## 0. Existing endpoints (kept)

From `identity/api/UserController.java`: `GET/PUT /identity/users`, `POST /users/{id}/deactivate|reactivate`, `POST /users/invite`, `GET /invitations`, `DELETE /invitations/{id}`, public `POST /invitations/accept`, `GET /invitations/validate/{token}`, `GET/POST /roles`, `PUT /roles/{id}/permissions`, `GET /permissions`, `GET/PUT /me`, `POST /me/password`. Auth: `AuthController` (`/api/v1/auth/*`).

**Hardening on existing endpoints (no shape change, release 1):** invite, user update, role create and role permission update gain the **grantor ceiling** check (G below) and write audit events; `GET /users` stops returning other users' permission sets unless caller has `TENANT_ACCESS_REVIEW`; accept validates token hash and email binding.

## 1. Conventions

- Errors: `{ "success": false, "code": "…", "message": "…" }`. Codes below.
- Pagination: `page`, `size` (max 100), same as the rest of the API.
- Idempotency: `Idempotency-Key` header supported on invite, assign, revoke (existing `IdempotencyKeyFilter`).
- Concurrency: membership has `accessVersion`; write requests may send `If-Match: <accessVersion>`; mismatch returns `409 ACCESS_VERSION_CONFLICT`.
- **Grantor ceiling (G):** a caller may grant or edit only roles whose permissions are a subset of the permissions the caller currently holds **and** that are marked grantable at the caller's level; `FINANCIAL`/`SENSITIVE` permissions additionally need an Owner confirmation token. Violation: `403 GRANT_ABOVE_AUTHORITY`.
- Entitlement: every module-scoped grant is rejected with `409 MODULE_NOT_ENTITLED` if the tenant does not own the module.

## 2. Invitations

| Method, path | Authz | Request | Response | Validation / errors |
|---|---|---|---|---|
| `POST /invitations` | P: `TENANT_USERS_INVITE`; G; M | `{ email, firstName, lastName, grants:[{roleId, scope?:{type,ids[]}, expiresAt?, reason?}], orgUnitIds?[], message? }` | `InvitationResponse` (no token) | email format, not disposable, not already active member, seat limit counts **non-expired** pending only (`409 USER_LIMIT_EXCEEDED`), grants non-empty, each grant valid (`GRANT_ABOVE_AUTHORITY`, `MODULE_NOT_ENTITLED`, `TOXIC_COMBINATION` in enterprise mode) |
| `GET /invitations?status=` | P: `TENANT_USERS_INVITE` or module admin for own module | – | page of `InvitationResponse{id,email,status,invitedBy,sentAt,expiresAt,resendCount,grants[]}` | statuses: PENDING, ACCEPTED, EXPIRED, REVOKED, DECLINED |
| `POST /invitations/{id}/resend` | P: invite; same G | `{}` | `InvitationResponse` | rotates token, resets expiry, max 5 resends, `409 INVITATION_NOT_PENDING` |
| `POST /invitations/{id}/revoke` | P: invite | `{ reason? }` | `InvitationResponse` | only PENDING |
| `PUT /invitations/{id}/grants` | P: invite; G | grants list | `InvitationResponse` | only PENDING |
| `GET /invitations/preview?token=` (public) | none, rate limited | – | `{ tenantName, email, inviterName, grantsSummary, expiresAt, accountExists }` | token hashed lookup; generic error for unknown/expired to avoid enumeration |
| `POST /invitations/accept` (public) | token | `{ token, password?, fullName? }` for new account; or authenticated session for existing | `AuthResponse` | single use; email of signed-in account must equal invitation email (`403 INVITATION_EMAIL_MISMATCH`); password policy |
| `POST /invitations/decline` (public) | token | `{ token }` | – | marks DECLINED |

## 3. Members (people)

| Method, path | Authz | Notes |
|---|---|---|
| `GET /members?search&status&module&roleId&orgUnitId` | P: `TENANT_USERS_MANAGE` or `TENANT_ACCESS_REVIEW` | list, filters as UX register |
| `GET /members/{id}` | same, or self | profile, effective access summary |
| `GET /members/{id}/access` | same, or self | `{ memberId, accessVersion, modules:[{key, entitled, roles:[{roleId,name,grantedBy,grantedAt,expiresAt,scope}] , permissions:[{name, viaRoleId, sensitivity}]}], warnings:[] }` |
| `POST /members/{id}/suspend` | P: `TENANT_USERS_MANAGE` | `{ reason }` mandatory; cannot target last Owner; revokes refresh tokens; bumps accessVersion; `409 LAST_OWNER` |
| `POST /members/{id}/restore` | same | `{ reason? }`; assignments return as they were unless expired |
| `POST /members/{id}/remove` | same, Owner for administrators | `{ reason, reassignTo? }` |
| `POST /members/{id}/sign-out` | same | revoke all sessions |
| `PUT /members/{id}/profile` | self or P | name, phone, title |
| `GET /members/{id}/history` | P: `TENANT_AUDIT_READ` | audit slice |

## 4. Access assignment

| Method, path | Authz | Request | Response |
|---|---|---|---|
| `POST /members/{id}/assignments/preview` | P: `TENANT_ROLES_MANAGE` or module admin | `{ add:[{roleId, scope?, expiresAt?, reason?}], remove:[assignmentId] }` | `{ gained[], lost[], modulesGained[], modulesLost[], warnings[{code,message}], requiresOwnerConfirmation }` (no change made) |
| `POST /members/{id}/assignments` | same, G, M | `{ add[], remove[], confirmationToken?, ifMatch }` | `MemberAccess` | atomic; writes audit; bumps `accessVersion`; `409 TOXIC_COMBINATION` (enterprise), `403 GRANT_ABOVE_AUTHORITY`, `409 MODULE_NOT_ENTITLED`, `409 LAST_OWNER` |
| `DELETE /assignments/{id}` | same | `{ reason? }` | – |
| `GET /modules/{moduleKey}/members` | P: module admin or `TENANT_USERS_MANAGE` | module-first listing |
| `POST /modules/{moduleKey}/members/{memberId}/assignments` | module admin; G limited to module roles | same body | same |

## 5. Roles and permissions

| Method, path | Authz | Notes |
|---|---|---|
| `GET /roles?module=` | P: `TENANT_ROLES_MANAGE` or module admin | includes `system`, `moduleKey`, `memberCount`, `templateVersion` |
| `POST /roles` | P: `TENANT_ROLES_MANAGE`; G | `{ name, description, moduleKey?, cloneFromRoleId?, permissionIds[] }`; `409 ROLE_NAME_TAKEN`; `403 GRANT_ABOVE_AUTHORITY` |
| `PUT /roles/{id}` | same; G | name, description, permissions; system roles `403 ROLE_IS_SYSTEM` (clone instead); returns `affectedMembers`; bumps `accessVersion` of affected members |
| `DELETE /roles/{id}` | same | `409 ROLE_IN_USE` |
| `GET /role-templates?module=` | P: roles manage | shipped templates with version |
| `POST /role-templates/{key}/adopt` | same | copies into tenant |
| `GET /permissions?module=` | P: roles manage | catalogue with `{name, moduleKey, resource, verb, sensitivity, readOnly, grantableBy, description}`; **all modules**, not the six hard-coded UI groups (fixes F7) |
| `GET /sod/conflicts` | P: access review | list of toxic pairs configured |

## 6. Administrators and ownership

| Method, path | Authz | Notes |
|---|---|---|
| `GET /administrators` | P: `TENANT_USERS_MANAGE` | owners, administrators, module administrators |
| `POST /ownership/transfer` | Owner | `{ toMemberId }` → pending; target accepts via `POST /ownership/accept`; `409 LAST_OWNER` rules |
| `POST /administrators` / `DELETE /administrators/{id}` | Owner (tenant admins), Administrator (module admins) | audited |
| `PUT /module-delegations/{moduleKey}` | Owner | `{ grantableRoleIds[], approvalDelegable }` limits for module administrators |

## 7. Organisation, temporary access, external

| Method, path | Authz |
|---|---|
| `GET/POST/PUT/DELETE /org-units` | P: `TENANT_USERS_MANAGE` |
| `PUT /members/{id}/org-units` | same |
| `POST /delegations` `{ fromMemberId, toMemberId, assignmentIds[], from, to, reason }` | self for own, else P |
| `GET /delegations?active=` | P or self |
| `GET /external-access?kind=&subject=` | P: `TENANT_ACCESS_REVIEW` |
| `DELETE /external-access/{id}` | P: `TENANT_USERS_MANAGE`; module admin for own module |

## 8. Access reviews and audit

| Method, path | Authz | Notes |
|---|---|---|
| `POST /access-reviews` | P: `TENANT_ACCESS_REVIEW` | `{ name, scope:{modules?, roles?, units?}, dueDate, reviewerIds[] }` |
| `GET /access-reviews`, `GET /access-reviews/{id}` | same | progress, items |
| `POST /access-reviews/{id}/items/{itemId}/decision` | reviewer | `{ decision: KEEP|REVOKE|CHANGE, newRoleId?, reason }`; REVOKE creates the revocation and audit |
| `POST /access-reviews/{id}/complete` | reviewer owner | locks the review; export |
| `GET /audit/access-events?memberId&module&type&from&to` | P: `TENANT_AUDIT_READ` | `{ id, at, actor, target, module, type, before, after, reason, sourceIp, sessionId }`; append-only |
| `GET /reports/access/{users-by-module|users-by-role|administrators|unused|pending-invitations|suspended|excessive|sensitive-financial|recent-changes|review-exceptions}` | P: `TENANT_ACCESS_REVIEW` | CSV or JSON |

## 9. Me

| Method, path | Notes |
|---|---|
| `GET /me/access` | `{ permissions[], modules[], accessVersion, tenantVersion, roles[] }`; the UI uses this for guards so it equals what the server checks |
| `POST /me/access-requests` | `{ moduleKey, roleId?, reason }` → notifies module admins/administrators |

## 10. Platform (admin console, separate JWT)

| Method, path | Authz | Notes |
|---|---|---|
| `POST /api/v1/admin/support-sessions` | `PLATFORM_SUPPORT_ACCESS` | `{ tenantId, reason, ticketRef, durationMinutes≤60, mode: READ_ONLY|WRITE_REQUEST }` → creates session, emails the tenant Owner; returns session token (read-only permission set) |
| `POST /api/v1/admin/support-sessions/{id}/end` | same | |
| `GET /api/v1/identity/support-sessions` | tenant `TENANT_AUDIT_READ` | tenant-visible log |
| `POST /api/v1/identity/support-sessions/{id}/approve-write` | Owner | one-time, scoped, time-limited |

## 11. Token and filter contract

JWT keeps `sub`, `tenantId`, `pz`; adds `acv` (member access version), `tcv` (tenant entitlement version). `JwtAuthFilter` rejects a token whose `acv`/`tcv` is lower than the cached current value with `401 ACCESS_CHANGED`; clients call `/auth/refresh` and retry once. Suspension revokes refresh tokens immediately.

## 12. Error codes

`GRANT_ABOVE_AUTHORITY`, `MODULE_NOT_ENTITLED`, `TOXIC_COMBINATION`, `LAST_OWNER`, `ROLE_IS_SYSTEM`, `ROLE_IN_USE`, `ROLE_NAME_TAKEN`, `INVITATION_NOT_PENDING`, `INVITATION_EMAIL_MISMATCH`, `USER_LIMIT_EXCEEDED`, `ACCESS_VERSION_CONFLICT`, `ACCESS_CHANGED`, `OWNER_CONFIRMATION_REQUIRED`.

## 13. New tenant-level permissions (names proposed)

`TENANT_READ`, `TENANT_SETTINGS_MANAGE`, `TENANT_USERS_INVITE`, `TENANT_USERS_MANAGE`, `TENANT_ROLES_MANAGE`, `TENANT_ACCESS_REVIEW`, `TENANT_AUDIT_READ`. Mapping from today's names, so no tenant loses access in release 1: `USER_INVITE → TENANT_USERS_INVITE`; `USER_UPDATE`, `USER_DEACTIVATE → TENANT_USERS_MANAGE`; `ROLE_MANAGE → TENANT_ROLES_MANAGE`; `SETTINGS_MANAGE → TENANT_SETTINGS_MANAGE`. Both old and new names are accepted by the guards until the contract phase. Platform permissions (`PLATFORM_*`) live in the admin console's own token and are not tenant permissions.
