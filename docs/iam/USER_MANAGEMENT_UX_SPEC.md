# User Management – UX Specification (proposal)

Status: proposal. Today's UI is one tab, `handyflow-web/src/pages/settings/TeamTab.tsx`. Terms follow `IAM_TARGET_ARCHITECTURE.md`. Nothing is built.

## 1. Principles

1. **Answer six questions on every user page:** what modules, what can they do, who granted it, when, until when, who can revoke.
2. **Two modes, one data model.** *Simple* (default for tenants with ≤ 5 users or no Owner opt-in): People, Invite, Roles in plain words, no scopes, no reviews. *Advanced* (switch in Settings, auto-suggested at > 15 users): adds departments, scopes, delegations, access reviews, toxic-combination enforcement, matrix.
3. **Preview before save.** Every access change shows "what this person will be able to do" and "what they will lose" before confirmation.
4. **Never two sources of truth.** Module-first and user-first screens read and write the same assignment records; a change on either appears on the other at once.
5. **Entitlement is visible.** Modules the tenant does not own show as locked with an upgrade link, never as selectable.

## 2. Navigation

`Settings → People & Access` (permission `USER_READ` or higher; see sections below)

```
People & Access
 ├─ Overview                (1)
 ├─ People                  (2, 15)   tabs: Active · Invited · Suspended · Former
 ├─ Invite                  (3)       wizard
 ├─ Roles                   (10, 11)  system templates · custom roles
 ├─ Modules                 (9)       module-first view
 ├─ Administrators          (12, 13)  owners, administrators, module administrators
 ├─ Teams & Departments     (14)      advanced
 ├─ External                (19)      portal and external grants
 ├─ Reviews                 (16)      advanced
 ├─ Activity                (17)      audit and security events
 └─ Temporary access        (18)      delegations, time-limited grants
My profile → My access      (20)
```

## 3. Screens

| # | Screen | Purpose | Key content and actions | Empty state | Mobile |
|---|---|---|---|---|---|
| 1 | **Overview** | one-glance health | counts: people, pending invites, suspended, administrators; modules with user counts; alerts: "2 invitations expire in 24 h", "Owner count: 1", "3 people unused for 90 days", "toxic combination: 1"; shortcuts: Invite, Review access | "You are the only person here. Invite your first colleague." | cards stack, alerts first |
| 2 | **All users register** | find and act | table: name, email, status, modules (chips), roles, last active, joined; search, filters (module, role, status, department); bulk: suspend, change role, export (permission `TENANT_AUDIT_READ`); row opens (5) | "No people match" with clear-filters | card list, filters in a sheet |
| 3 | **Invite wizard** | invite safely | steps: 1 Who (email, name; detects existing HandyFlow account); 2 Modules (only entitled, locked ones shown greyed); 3 Role per selected module (templates plus custom), optional department/scope (advanced), optional expiry; 4 Review (resulting permissions diff, warnings: toxic pair, financial permission needing Owner confirmation); 5 Send (shows expiry, who can see/resend). Validates the inviter's own ceiling server-side; a role above the inviter's authority is not selectable | n/a | one step per screen |
| 4 | **Pending invitations** | follow up | email, invited by, sent, expires, status (Pending, Expired, Accepted, Revoked, Declined), resend, revoke, copy-link disabled by default; shows expired ones separately so they stop counting as seats | "No pending invitations" | list |
| 5 | **User profile and access overview** | the core page | header: name, status, last active, member since; panels: *Modules and roles* (each with source, granted by, date, expires), *Effective permissions* (grouped, searchable, each with "from role X"), *Scope*, *Sessions* (sign out everywhere), *History* (audit slice), *Warnings* (toxic pair, unused); actions: change access, suspend, restore, remove, sign out everywhere | n/a | panels become accordions |
| 6 | **Assign modules to user** | user-first | list of entitled modules with a role picker each; locked modules disabled; summary of change; preview (see 8) | "Your plan has no modules assigned yet. Add modules in Billing." | full-screen sheet |
| 7 | **Role assignment** | granular | add/remove a role, set expiry, scope; reason field mandatory for financial/sensitive roles | n/a | sheet |
| 8 | **Permission matrix** | review and bulk | rows = permissions grouped by module, columns = roles (or users); cells: granted / inherited from role / not allowed by plan / sensitive (marker); read-only unless editing a custom role; export | "No custom roles yet" | horizontal scroll with sticky first column; also a per-role list view |
| 9 | **Module access management** | module-first | pick module → list of users with access, role, last used; add person, change role, revoke; same preview and audit; also embedded as the "Access" tab inside each module for module administrators | "Nobody uses this module yet. Assign people." | list |
| 10 | **Create custom role** | tailored job titles | start from template or blank; name; module (or tenant); permission picker grouped by module with the standard verbs and sensitive markers; shows which permissions the creator may not delegate (disabled with reason); toxic-pair check live; save shows who currently could receive it | n/a | stepper |
| 11 | **Edit role and permissions** | maintain | same as 10; shows "affects N people"; diff of added/removed permissions; reason required for sensitive; system templates are read-only (clone to edit); rename/delete (delete blocked while assigned) | n/a | stepper |
| 12 | **Tenant administrators** | highest risk list | owners and administrators with last sign-in, MFA status (when available); add/remove (Owner only for owners); transfer ownership (two-step); warning if only one Owner | "Only you. Add a second Owner to avoid lock-out." | list |
| 13 | **Module administrators** | delegation list | per module: who administers it, delegation limits (which roles they may grant, approval-delegable on/off); add/remove | "No module administrators" | list |
| 14 | **Departments and teams** (advanced) | structure | tree of departments, teams, branches, sites; members; default scope; manager link (reports-to); create/rename/move | "Skip this unless you run branches or departments." | tree collapses |
| 15 | **Suspended and deactivated** | lifecycle | list with reason, by, date; restore (re-confirm roles), remove permanently (anonymise per POPIA policy), what happens to their open work (reassign wizard: tasks, approvals, assigned records) | "Nobody is suspended" | list |
| 16 | **Access review dashboard** (advanced) | governance | campaigns (quarterly default): reviewers, progress, items (user × role) with Keep / Revoke / Change, exceptions with reason; overdue; sign-off; export for auditors | "Start your first review" | list |
| 17 | **Audit and security activity** | evidence | filterable event stream: invited, accepted, role granted/revoked, permission changes, suspended/restored, admin changes, support sessions, failed sign-ins; actor, target, module, before/after, reason, time, source IP; export; tenant-visible support sessions | "No events in this period" | list, detail drawer |
| 18 | **Temporary access and delegation** | covering absences | grant a role until a date; delegate my approvals to X from–to; list active and ending soon; auto-expires; always shows who granted | "No temporary access" | list |
| 19 | **External users and portal access** | one view | tabs by kind (customers, suppliers, accountants, auditors, recruitment clients, project clients, attendees); who has access to what subject, invited by, expires, last used; revoke, extend; invite via the module's own flow | "No external users" | list |
| 20 | **My profile – my roles and permissions** | self-service transparency | what I can do per module in plain words, which role gives it, who granted, expiry; "Request access" (sends a request to the right administrator); security: password, sessions | "You have no module access yet. Ask an administrator." | page |

## 4. Permission management: which interface?

Both, on one set of records.

- **User-first** (screens 5–7): best for onboarding, role changes, leavers. Journey: *open user → Modules → pick Accounting → pick "Accounting Viewer" → Review (diff) → Save*.
- **Module-first** (screen 9): best for a module owner ("who has Accounting?") and for entitlement changes. Journey: *open Accounting → Users → add person or revoke → Review → Save*.
- **Role-first** (10, 11): maintain definitions.
- **Matrix** (8): audit and teaching; read-mostly.

**Conflict rule:** both write `role_assignments`; save is a single transaction; the second editor of the same person within one page load gets a conflict message with a refresh (version check on the membership's access version).

**Preview content (every change):** (1) permissions gained, (2) permissions lost, (3) modules gained/lost, (4) warnings (toxic pair, financial permission, approver without limit), (5) who will be notified, (6) effect timing ("applies within seconds; they stay signed in"). **Explanations shown in the permission list:** *inherited* (from role X), *direct* (not allowed in this design, shown only for legacy data), *denied by plan* (module not entitled), *dormant* (module cancelled), *expired*.

## 5. Key journeys

**Invite and first login.** Admin completes the wizard → email (resend available; link opens `/invite/accept`) → invitee: new account (set password) or sign in to existing account → joins tenant → lands on the dashboard for their first granted module (or "Welcome, you have access to HR and Fleet" with two tiles) → optional checklist (set profile, verify email, enable two-step when available) which never blocks work.

**Role change.** Open user → Change access → preview → Save → person is notified ("Your access changed") and sees new menus within seconds.

**Leaver.** Open user → Suspend (reason) → Sign out everywhere (automatic) → reassign wizard → later Remove. Access ends in seconds (see target architecture section 7).

**Module cancelled / resubscribed.** Cancel: module disappears from menus; People shows "dormant" chips. Resubscribe: banner "Restore access for N people?" listing them (restore all / review).

**Wrong account accepts an invitation.** The accept page shows the invited email; accepting while signed in as a different email is blocked with an explanation and a "sign out and continue" action.

## 6. Cross-cutting UI states

Loading skeletons on tables; per-row error with retry; empty states as in section 3; permission-denied states show what is missing and who can grant ("Ask your administrator: <names>"); long operations (bulk) show progress and a result list; forms keep the user's input on failure; accessible names on all buttons; keyboard operation of the matrix; colour is never the only signal (icon plus label).

## 7. Simple vs advanced

| Capability | Simple | Advanced |
|---|---|---|
| Roles per person | pick a template per module | + custom roles, scopes, expiry |
| Departments/branches | hidden | shown |
| Toxic combinations | warning, owner acknowledges once | blocked unless exception approved |
| Reviews | hidden (reminder in Overview yearly) | campaigns |
| Matrix | hidden | shown |
| Audit | last 90 days list | filters, export |

## 8. Frontend rules that go with it

Route guards check permission **and** module (fixes F12); a shared `<Can permission module>` component; the UI never infers access from a role name; all permission checks mirror the server through `/identity/me/access` (permissions, modules, versions) so UI and API cannot disagree.
