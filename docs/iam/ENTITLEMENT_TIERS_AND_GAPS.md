# Entitlement, free tier and what does not exist yet

Date: 2026-11-04. Status: **record of gaps, nothing in sections 2 to 4 is built.** Section 1 says what Phase 0 changed.
**Update 5 Nov:** Free Core is now *conditional* on at least one paid module (see `docs/commercial/`). Earlier decision (4 Nov): CRM, Invoicing (with capped quotations and invoices) and Catalogue are the free core;
upgrades inside them and every other module are paid; Owner/Administrator split approved; sole-user businesses must
work; the product is not in production, so no compatibility layers are needed.

## 1. Delivered in Phase 0 (patch 0606), not yet run on a real database

| Item | What changed |
|---|---|
| 0.1 | Grantor ceiling in invite, role change, role create, role permission edit. No self role change. A user with more access than the caller cannot be edited. |
| 0.2 | Dev OTP endpoint needs `DEV_OTP_ENDPOINT=true`; previously on whenever `SMS_ENABLED` was unset. Local developers must now set it. |
| 0.3 | `access_audit_events` (append-only) plus `GET /api/v1/identity/access-audit` (needs `ROLE_MANAGE`). No screen yet. |
| 0.4 | Deactivate revokes refresh sessions. The access token still works until it expires (15 minutes). |
| 0.5 | Hashed invitation tokens, EXPIRED status, seats freed, configurable link base (`app.frontend.url`), email failure reported, resend. |
| 0.6 | Other people's permission sets hidden unless the caller has `ROLE_READ`. `USER_READ` stays on EMPLOYEE (see section 5). |
| 0.7 | Role editor lists all permissions grouped by name prefix. |
| 0.8 | Source-based test: new controllers need `@PreAuthorize` and a module check. 229 controllers are on a known-debt list. |

## 2. Entitlement: what does not exist

The present state is one on/off check per module (`FeatureGuard.requireModule`) over four overlapping sources, a 60 day
trial and then a lockout. Missing:

1. **Tier model.** No concept of Free Core, Starter, Growth, Enterprise as data. `plans` exists; tiers per module do not.
2. **Free core after trial.** A tenant whose trial ends is locked out (HTTP 402) of everything. The decided behaviour
   (keep CRM, Invoicing, Catalogue; drop to read-only for paid modules) is not implemented.
3. **Feature entitlements.** `requireFeature` exists and has zero callers. No list of features per tier, no admin screen
   to edit it, no frontend lock-and-explain component.
4. **Usage limits and counters.** Nothing caps quotations or invoices. No counter table, no billing-period boundaries,
   no concurrency-safe increment. Proposed rules (from the product owner, unvalidated): Free 25 quotations / 15 invoices
   a month, Starter 100/50, Growth 500/250, Enterprise custom; issue or finalise counts, drafts, edits, credit notes
   and re-sends do not; deletes and voids do not restore the allowance; quotation to invoice conversion uses one of each;
   period and time zone Africa/Johannesburg; at the limit the user can still view, download and draft, with an upgrade message.
5. **Seat limits per tier.** `plans.max_users` is enforced on invite only; per-tier values undefined.
6. **Downgrade handling.** No defined behaviour. Required: keep all data, block new use.
7. **Entitlement on the token.** Tokens carry permissions of modules the tenant may not own. Phase 1 filters at mint.
8. **Audit of entitlement changes.** None. `access_audit_events` can hold them once the writers exist.
9. **Platform admin editing** of tiers, features and limits without a code change.

### Module checks
`FeatureGuard` is called from 244 of the 473 controller source files (which include 2 exception-advice classes; about 1,700 call sites). **229 files have no module check**
(`platform/src/test/resources/security/controllers-without-module-guard.txt`). Per the product owner these are P0
commercial and access-control defects, not free offerings. The new test stops the list from growing; removing names
from it is Phase 1 work (`@RequiresModule` applied per package).

## 3. Paid features that are not built (needed before they can be sold)

From the product owner's list. "Exists?" is from a quick search of the code, to be verified when each is scheduled.

| Area | Feature | Exists? |
|---|---|---|
| CRM | multiple pipelines, automation and follow-ups, lead scoring, custom fields, forecasting, bulk import, territories | not checked |
| Invoicing | recurring documents, batch invoicing, multi-currency, customer self-service portal, payment gateway collection, multiple numbering sequences, approval workflows, automated reminders | not checked |
| Catalogue | variants, price lists, bundles, barcodes, bulk management, profitability analytics | not checked |

Nothing is gated today, so "paid upgrade" currently has no technical meaning. Each needs a feature key before it can
be switched.

## 4. IAM: what does not exist

Accounts and memberships (one login, several companies); Owner and Administrator roles (ADMIN still holds everything,
including every approval); module admins; separation of duties and the sole-operator exception; permission catalogue
metadata; entitlement-filtered tokens; access-version claims and immediate revocation (a deactivated user keeps a
working access token up to 15 minutes); invitations with several grants; role templates; People & Access screens;
scopes by branch or department; delegation and expiry; access reviews; support sessions v2; MFA. See `IAM_IMPLEMENTATION_ROADMAP.md`.

## 5. Known deviations and open points from Phase 0

* **EMPLOYEE keeps `USER_READ`** (roadmap 0.6 said remove it). Pickers need names; instead the list now hides permission
  sets. Staff still see every colleague's name, email and phone.
* **Permission "group" is the name prefix**, not a real module key. The real mapping arrives with the Phase 1 catalogue.
* **Pending invitations from before V589** keep working (hash computed in SQL). Their emailed links stay valid.
* **Resending an expired invitation does not re-check the seat limit.**
* **Audit events are written in the same transaction**; refused attempts (403) are not recorded yet.
* **Not compiled or run:** all Java in patch 0606 and V589. Run `mvn test` and start the app once against a database
  (Flyway applies V589).
