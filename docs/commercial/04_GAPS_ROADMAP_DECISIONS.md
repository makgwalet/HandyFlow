# Gap register, roadmap, decision register, verification

## 1. Gap and risk register
"Evidence" marks: **V** verified in this session from source or build output; **I** inherited from the 22 or 29 Oct audits (not re-verified); **U** unverified.

| ID | Area | Finding | Evidence | Sev | Business impact | Recommendation | Pri |
|---|---|---|---|---|---|---|---|
| G-01 | Entitlement | 229 controller files had no module check; now 8 modules covered in report mode only (0608) | V (list in `platform/src/test/resources/security/`) | P0 | Paid modules reachable without payment | Finish mapping, turn enforcement on after reading the log | P0 |
| G-02 | Entitlement | Four overlapping sources can each grant access | V (`EntitlementService`) | P0 | A cancelled module can still be granted by plan JSON | One source (`tenant_modules`), calculator | P0 |
| G-03 | Catalogue | Modules built but not sellable: no catalogue row for at least Auditor, Booking Agency, Recruitment Agency, Projects, Accountant, AP, Payroll Bureau | V (migration parse) / U (live DB) | P1 | Cannot sell; guards reject unknown keys | Reconcile with the live table, add rows | P1 |
| G-04 | Catalogue | Key mismatch `supply_chain` / `supplychain`; `internal-audit` vs package `internalaudit` | V | P1 | Wrong module checked | Canonical `app_code`, test | P1 |
| G-05 | Pricing | Seven catalogue rows priced R0 | V (migrations) | P1 | Free by accident | Decide price or hide (D-06) | P1 |
| G-06 | Metering | No quote or invoice cap anywhere; plan JSON limits unread | V + I | P1 | Free Core unlimited | Build meters (phase C) | P1 |
| G-07 | Lifecycle | Module cancel and account suspend are the only transitions; no read-only transition or export prompt | V | P1 | Lockout surprises; record-access risk | Section 7 of `02` | P1 |
| G-08 | Trial | 60 days hard-coded in three places | V | P2 | Cannot configure per tenant | One setting; 30 if approved | P2 |
| G-09 | Billing | No payment gateway; invoices marked paid manually | I | P1 | Revenue leakage, manual work | Gateway decision (D-15) | P1 |
| G-10 | Tests | No unit tests for entitlement, subscription, scheduler | V | P1 | Regression risk on money logic | Write before refactor (phase B) | P1 |
| G-11 | Tests | 484 failures in other modules in the 5 Nov full run (210 context-load errors, rest unit tests); cause of context errors not yet seen | V | P1 | CI cannot be green | Triage (see test review) | P1 |
| G-12 | Frontend | No usage dashboard, plan comparison, upgrade/downgrade preview, trial management, payment history | V | P2 | Customers cannot self-serve | Screens in `04` section 5 | P2 |
| G-13 | Security | Default ADMIN holds all permissions, so entitlement is the only barrier to modules | I (22 Oct) + IAM audit | P0 | Combined with G-01 | IAM Phase 1 | P0 |
| G-14 | Integrity | `invoicing` posts to `accounting` regardless of an Accounting subscription | I | P2 | Ledger data created for tenants not paying for Accounting | Decide: allowed (system of record) or gated (D-16) | P2 |
| G-15 | Data | INV-01: quote-converted and recurring invoices not posted to the ledger | I (29 Oct) | P0 | Understated revenue; wrong debtors | Fix before metering invoices | P0 |
| G-16 | Pricing | Prices edited in place, no history | I | P2 | Cannot audit or grandfather | Price book versions | P2 |
| G-17 | Scheduling | 109 scheduled jobs with no distributed lock | I | P2 | Duplicate billing runs if scaled out | Lock before second instance | P2 |
| G-18 | Marketing | Module exists, not in business catalogue | V | P3 | Unclear whether sold | D-08 | P3 |
| G-19 | Compliance | Retention periods, POPIA and e-record duties not documented in product | U | P1 | Legal exposure on closure/deletion | Professional advice (D-18) | P1 |
| G-20 | Payroll Bureau | Not ready, should be withdrawn from sale | I (29 Oct) | P1 | Selling an unfit product | D-14 | P1 |

## 2. Roadmap
Because HandyFlow is not in production, migrations may convert data directly; no dual running is required. Report mode is still needed for **entitlement** because internal, demo and test tenants use modules without rows.

| Phase | Objective | Backend | Database | Frontend / API | Tests | Depends on | Risks | Acceptance |
|---|---|---|---|---|---|---|---|---|
| A. Stop the leaks (2-3 weeks) | Every module checks entitlement; catalogue is sellable | Finish prefix map to all packages; unify keys; run report mode; then enforce | `app_code`, status, `qualifies_free_core`; add missing rows; fix R0 | Show a 403/402 upgrade message page | Per-package entitlement integration tests (direct API call without module = 403); coverage test debt list shrinks to 0 | Decisions D-05..D-08, live catalogue query | Locking out demo tenants | Debt list empty; each module has a test; log shows no unexpected ENTITLEMENT-REPORT lines |
| B. One entitlement engine (3-4 weeks) | Single source, calculator, derived Free Core, 30-day trial | `EntitlementCalculator`, `tenant_entitlements`, version, `billing.api` interfaces; remove plan-JSON module checks | new tables; drop `module_subscriptions` use | `GET /billing/entitlements`; token `tcv` | Calculator unit tests (the 16 scenarios in your brief); cache invalidation test | Phase A, D-01..D-04 | Behaviour change for existing tenants | Scenarios 1-8, 13, 15, 16 pass |
| C. Metering (3 weeks) | Quote and invoice caps, seat rules | `UsageGate`, counters, events; hook into issuing | `usage_*` tables | Usage dashboard; limit messages | Concurrency test (N threads, one slot); idempotency test; period boundary test | B, INV-01 fixed | Hard cap blocking legitimate work | Scenario 11 passes; caps documented |
| D. Lifecycle (3 weeks) | Transition, export prompt, distinct events | Status model split; transition job; events | tenant status, `entitlement_events` | Cancellation flow, downgrade preview, trial management | Scenarios 5-8, 12 | B | Legal retention rules | No data removed; read-only works |
| E. Commercial (4 weeks) | Bundles, price book, promotions, gateway | bundle tables, price book, gateway integration | new tables | Marketplace, plan comparison, billing history | Discount and bundle tests (13, 14) | D-09, D-15 | Gateway choice and fees | Bundle never double-charges |
| F. Identity tie-in (with IAM Phase 1) | Token filtering, Owner/Administrator | per IAM roadmap | per IAM | People & Access | per IAM | B | | Downgrade removes access on next call |
| G. Enterprise (later) | Contracts, negotiated limits | contract entities | | admin screens | | E | | |

Rollback: each phase ships behind a configuration flag where behaviour changes (`ENTITLEMENT_ENFORCE`, then per-meter `enforcement=REPORT`); migrations are additive until the last step of a phase.

## 3. Decision register
| ID | Question | Options | Recommendation | Blocks |
|---|---|---|---|---|
| D-01 | Free Core conditional on a paid module (today's brief) or unconditional (4 Nov)? | conditional / unconditional / free tier with low caps | **Conditional**, with a visible read-only transition | Phase B |
| D-02 | Is a workspace plan priced? | Option 1 / 2 / 3 | **Hybrid**: Starter included with first paid module; Growth/Business priced | Phase B |
| D-03 | Trial length | 14 / 30 / 60 days | 30 days, extendable by platform admin | A config only |
| D-04 | Transition period after last paid module ends | 0 / 30 / 60 days | 30 days read-only | Phase D |
| D-05 | Which of the 16 uncovered modules are paid vs shared services | per module | Paid: projects, recruiter, pos, desk, creative, marketing, accountant, auditor, ap, supplychain, tasks (R149 in the catalogue). Shared services needing no module check: notifications, approvals, audittrail, documents, secureaccess, capture | Phase A |
| D-06 | R0 modules | price now / hide / bundle | Hide until priced; do not let R0 qualify for Free Core | Phase A |
| D-07 | Module keys | rename to canonical | Canonical lower-case, no separators | Phase A |
| D-08 | Marketing: sell or retire | | Decide with owner | none |
| D-09 | Discount schedule | live ladder / proposal / bundles only | Bundles plus a modest ladder after margin model | Phase E |
| D-10 | Quote cap type | hard / soft | Hard | Phase C |
| D-11 | Invoice cap type | hard / soft / none for paid | **Soft** (110%) and lifted by Finance bundle | Phase C |
| D-12 | Full feature matrix for 38 modules | per-module audits | Scripted draft then review | none |
| D-13 | Seat definition and limits per tier | section 5 of `02` | Approve | Phase C |
| D-14 | Withdraw Payroll Bureau from sale | yes/no | Yes until rebuilt | none |
| D-15 | Payment gateway | PayFast / Peach / Paystack / Netcash / Ozow | Evaluate fees and recurring support | Phase E |
| D-16 | Invoicing posting to Accounting without a subscription | allow / gate | Allow (accounting engine is the system of record), but show a paywall to *view* ledger reports | Phase B |
| D-17 | Annual billing | now / later | Later | none |
| D-18 | Retention and closure policy | | Needs tax/legal advice | Phase D |
| D-19 | Top-up packs for usage | yes/no | No at launch | none |
| D-20 | Enterprise future modules | auto-include / contract | Contract | none |
| D-21 | Sole-user self-approval policy | per IAM doc | As approved (flag `self_approved`) | IAM Phase 1 |
| D-22 | Bundle composition | per proposal | Validate with pilot customers | Phase E |

## 4. Verification query for the live catalogue (read-only)
Run against your database and send me the output:
```sql
SELECT key, name, monthly_price, active FROM module_catalogue ORDER BY key;
SELECT name, price_cents, max_users, included_module_count FROM plans ORDER BY sort_order;
SELECT module_key, status, count(*) FROM tenant_modules GROUP BY 1,2 ORDER BY 1,2;
SELECT status, count(*) FROM subscriptions GROUP BY 1;
SELECT count(*) AS tenants_with_module_but_no_catalogue_row
FROM tenant_modules tm LEFT JOIN module_catalogue c ON c.key = tm.module_key WHERE c.key IS NULL;
```

## 5. Screens (target)
Exists: `BillingPage` (subscription, My Modules, add and cancel with cancel preview). Needed: Marketplace with dependency display; Module details; Plan comparison and workspace plan selection; Tier selection per module; Billing overview (VAT, next renewal, payment history, downloadable invoices); Usage dashboard (current, remaining, trend, 80/90/100% thresholds, history); Upgrade and downgrade preview (limits, features gained and lost, effective date, proration, data implications); Cancellation flow with affected workflows; Trial management; People & Access (IAM UX spec). Every locked feature shows an explanation and an upgrade action, but the backend decides.

## 6. Mapping of the 16 required test scenarios
None of the 16 exist today (no billing tests besides the path test from 0608). They map to phases A (9, 16), B (1-4, 13, 15), C (11), D (5-8, 12), E (13, 14), and tenant isolation (10) to the IAM integration tests.
