# Final commercial and HR decisions, 5 November 2026 (18:07)

Source: the owner's message of 18:07. Nothing here switches an enforcement flag on. Where a decision is recorded but not yet built, the status says so.

Status words: **DECIDED** (owner gave the answer), **BUILT** (code delivered, flag off), **NOT BUILT**, **BLOCKED** (waiting on inputs).

| # | Topic | Decision | Status |
|---|---|---|---|
| 1 | Tier limits | Starter 5 seats / 100 quotes / 50 invoices per month. Growth 20 / 500 / 250. Business 50 / 2,000 / 1,000. Enterprise negotiated. Limits are configurable and versioned. Warn at 80% and 90%, block at 100%. **Approving the limits does not authorise activating the hard cap.** Enforcement stays disabled until usage reports are validated and a staged rollout is approved. | DECIDED. Limits no longer provisional. Versioning, quote metering: see patch notes. Enforcement OFF. |
| 2 | Payment failure | Failed payment sets the subscription PAST_DUE. 7-day grace with normal access and payment retries. Then paid-module write access is suspended, read-only kept. A further 30-day recovery period. Then operational access is restricted, data and authorised exports preserved. A successful payment restores access. Authentication, security, billing and data export always stay available. Voluntary cancellation is separate, with its own 30-day read-only transition. | DECIDED. Access rules built in report mode only. Today's scheduler still suspends fully after the 7-day grace; that is **not changed** until rollout is approved. |
| 3 | D-01 | CRM, Invoicing and Catalogue are free only while the tenant has at least one active paid specialist module. Not a standalone permanent free subscription. | CONFIRMED (was provisional). |
| 4 | D-02 | Starter has included platform capabilities. Growth and Business are priced platform tiers. Enterprise is negotiated. Platform tiers are separate from specialist module subscriptions. | CONFIRMED. Prices for Growth and Business are still not set. |
| 5 | D-05 | All specialist modules are paid. CRM, Invoicing and Catalogue are the only Free Core modules. Bundles and multi-module discounts discount existing subscriptions and must never create duplicate entitlements. | CONFIRMED. |
| 6 | D-23 | Retire the Professional free-specialist-module credit from the new commercial model. Honour existing contractual commitments until their agreed expiry or migration date. Never silently remove an existing benefit. | DECIDED. NOT BUILT: needs a list of current customers holding the credit and their expiry dates. |
| 7 | HR employee status | Employment-impacting status changes need independent approval. Termination keeps the two-person HR manager approval. Emergency security or safety suspension takes effect at once but needs independent review within one business day. No self-approval. All status changes audited. | DECIDED. See patch notes. |
| 8 | Catalogue SQL | BLOCKED. Projects, Auditor, Accountant and AP enforcement stay off. The five real SQL outputs and the matching catalogue and entitlement records are needed. Nothing is invented and no speculative catalogue mapping is made. | BLOCKED on the owner. |

## What this closes in the register
- D-01, D-02, D-05: confirmed (were provisional).
- D-04: already decided (30 days).
- D-11 (invoice cap type): hard, as decided earlier; D-10 quote cap type: hard, quantities now set (100 / 500 / 2,000).
- D-13 (seats): limits approved (5 / 20 / 50).
- D-23: decided (retire, honour existing).
- Still open: D-06 to D-09, D-12, D-14 to D-22, D-24 (legal review), D-25, D-26; Growth and Business prices; the five catalogue query results.

## Enforcement switches (all remain OFF)
- `handyflow.usage.invoice-hard-cap.enabled` = false
- Payment-failure restriction: not wired to anything yet.
- Module entitlement enforcement: unchanged (see the enforcement runbook).

## What was built for these decisions (patches 0657 and 0658)
- **Limits (0657, migration V597):** `usage_limits` is versioned (version, effective date, who approved). Version 1 is the old provisional set; version 2 is the approved set above and adds quotes. A change is a new row with an effective date. Quotes are counted by a trigger the first time a quote leaves DRAFT (sent). 80% and 90% warnings are in the usage report; the block at 100% exists for invoices (0654) and for sending quotes, **both OFF**: `handyflow.usage.invoice-hard-cap.enabled`, `handyflow.usage.quote-hard-cap.enabled`. A staged rollout list `handyflow.usage.hard-cap.tenants` (comma-separated tenant ids) limits a switched-on cap to those businesses first; empty means everyone.
- **Employee status (0658, migration V598):** `hr_status_change_requests`. Suspension and reinstatement are asked for by one HR manager and approved by another. Nobody approves their own; a sole HR manager may act only as an audited exception with a written reason. An emergency suspension takes effect at once and must be reviewed by a different HR manager by the end of the next business day (Monday to Friday, South African time; public holidays are not modelled); an overdue review is flagged on the screen. Reversing a suspension reinstates the employee. Termination is unchanged. Every step is written to the employee audit trail.
- **Payment failure (0658):** the rules are in code (`PaymentFailureRules`): 7 days full access, then 30 days read-only for paid modules, then restricted; sign-in, security, billing and data export always available; payment restores everything. **Report mode only.** A daily `PAYMENT-FAILURE-REPORT` log line shows, per business with a failed payment, the stage the rules give and what happens today. Today's behaviour is unchanged: after the 7-day grace the scheduler suspends the business and every module answers 402. There are also no automatic payment retries in the code yet, which the decision assumes during the grace period.
- **Not built, waiting:** D-23 (needs the list of customers holding the Professional credit and their expiry dates); the catalogue SQL outputs (blocked on the owner); Growth and Business prices.
