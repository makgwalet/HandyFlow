# Owner decisions, 5 November 2026 (16:12)

Status words used below:
- **DECIDED**: the owner gave a specific answer. Still reversible until built and released.
- **PROVISIONAL**: approved as a working default only. Not final. The owner has asked to see the exact decision, the recommended value and the consequence before any irreversible or commercially significant item is treated as final.
- **UNRESOLVED**: nothing to approve yet; recorded so it is not lost.

The register itself is `claude/COMMERCIAL-04-GAPS-ROADMAP-DECISIONS-2026-11-05.md` (section 3). Question, options, recommendation and "blocks" below are copied from it. "What it means" is my reading, not the register's.

## 1. Answers given

| # | Topic | Answer | Status | Notes |
|---|---|---|---|---|
| 1 | Refunds | Every refund needs a second independent approver. One-person businesses are exempt, and the exemption is recorded and audited. The person who asked cannot approve it when a second authorised person exists. | DECIDED, built (patch 0642) | Recorded as audit action `SOLE_APPROVER_SELF_APPROVAL`, visible on the Owner report. |
| 2 | Zeta seed migrations | New corrective migrations only. V106, V107, V108, V119 and V120 are not edited. Test on a fresh and an existing database. | DECIDED, built (patch 0641) | Migration `V105_1`. Follow-up not done: move QA seed data out of `db/migration` so production never gets a demo tenant. |
| 3 | HR approvals | Require approval for employment-impacting changes: pay changes, leave and overtime, employee status, disciplinary, termination, other sensitive decisions. Routine profile and contact corrections need none. No self-approval. Audit trail. | DECIDED, not built yet | Scope still to be matched to what already exists (see section 4). |
| 4 | Free platform services | Authentication; tenant creation and isolation; basic tenant settings; password and security management; basic user invitations and administration; central role and permission management; subscription management; module marketplace access; essential notifications; security audit events; basic usage visibility; essential document generation; access to and export of existing business records. Storage, messaging and API usage may have transparent quotas. Security and tenant isolation are never premium. | DECIDED (recommendation accepted) | Feeds D-05. |
| 5 | Invoice cap | **Hard** (blocks issuing new invoices). Warnings at 80% and 90%. Allowance is the tier's monthly allowance. Drafts do not count. Credit notes do not use allowance. Voiding or deleting an issued invoice does not give usage back. Existing invoices and financial records stay accessible. Limits **provisional**: Starter 50, Growth 250, Business 1,000, Enterprise negotiated. | DECIDED (hard); limits PROVISIONAL | **Changes D-11**, whose recommendation was Soft (110%). See section 3. |
| 6 | Seats | Active internal users per tier, **provisional**: Starter 5, Growth 20, Business 50, Enterprise negotiated. The owner and internal administrators count. Suspended users do not. Pending invitations hold a seat until accepted, revoked or expired. External client and portal users are metered separately or excluded. A downgrade never deletes users. | PROVISIONAL | This is D-13. |
| 7 | After a downgrade | Read-only for 30 days, then restricted with no new operational access, keeping historical records and controlled export. No data deleted. A voluntary downgrade, a payment failure and a suspension are treated as different. Legal, financial and compliance records stay accessible to authorised users. | DECIDED (30 days matches D-04); payment-failure and suspension rules UNRESOLVED | |
| 8 | Catalogue SQL outputs | Skipped. Nothing invented. Enforcement for Projects, Auditor, Accountant and AP stays off until the real query results are supplied and the catalogue rows and entitlements are verified. | DECIDED | The code already treats these four as report-only (`ModuleEntitlementPaths.REPORT_ONLY`). Nothing to change. |
| 9 | D-01..D-05, D-23..D-26 | Recommended defaults approved provisionally, provided the exact decisions are shown first, and nothing irreversible or commercially significant is treated as final. Do not guess the IDs. Record unresolved items. | See section 2 | |

## 2. Decision register items covered by answer 9

| ID | Question (register wording) | Options | Recommendation | Blocks | Status now | What it means |
|---|---|---|---|---|---|---|
| D-01 | Free Core conditional on a paid module (today's brief) or unconditional (4 Nov)? | conditional / unconditional / free tier with low caps | **Conditional**, with a visible read-only transition | Phase B | PROVISIONAL. Commercially significant: needs explicit final confirmation. | CRM, Invoicing and Catalogue are free only while the business holds at least one paid module. When the last paid module ends, they go read-only (D-04). |
| D-02 | Is a workspace plan priced? | Option 1 / 2 / 3 | **Hybrid**: Starter included with first paid module; Growth/Business priced | Phase B | PROVISIONAL. Commercially significant: needs explicit final confirmation. | Your tier names (Starter, Growth, Business, Enterprise) line up with this. Starter is not charged separately. Growth and Business carry a price that is not set yet. |
| D-03 | Trial length | 14 / 30 / 60 days | 30 days, extendable by platform admin | A config only | PROVISIONAL. Reversible (one setting). | Today it is 60 days, hard-coded in three places (gap G-08). |
| D-04 | Transition period after last paid module ends | 0 / 30 / 60 days | 30 days read-only | Phase D | DECIDED by answer 7. | Matches your 30 days. |
| D-05 | Which of the 16 uncovered modules are paid vs shared services | per module | Paid: projects, recruiter, pos, desk, creative, marketing, accountant, auditor, ap, supplychain, tasks (R149 in the catalogue). Shared services needing no module check: notifications, approvals, audittrail, documents, secureaccess, capture | Phase A | PROVISIONAL. Commercially significant: needs explicit final confirmation. | Your free-services list (answer 4) agrees with the shared-services side. Marketing is still undecided (D-08). Four of the paid keys have no catalogue row, so they stay report-only (answer 8). |
| D-23 | Professional free-module credit | keep (one module, cap R499, no Clinic) / replace by Free Core and bundles | "Decide; if kept, meter it and enforce `included_module_count`" | Phase B | **UNRESOLVED.** The register gives no concrete recommendation to approve. | Needs your choice. |
| D-24 | Annual contract terms | June 2026 terms / revise | Legal review (auto-renew, no refund, early-termination fee, Consumer Protection Act) before any use | Phase E | PROVISIONAL, **cannot be final until legal review**. | Nothing is built on annual terms until then. |
| D-25 | Pause clause for seasonal tenants | yes / no | Defer | none | PROVISIONAL (deferred). | No pause feature is built. |
| D-26 | Difficult-tenant playbook | adopt / revise | Correct the CCMA reference, legal review, then use as an operations document | none | PROVISIONAL, **cannot be final until the CCMA reference is corrected and legal review is done**. | Documentation only. No code. |

Other items touched by your answers:
- **D-11 (invoice cap type).** Register recommendation: Soft (110%), lifted by a Finance bundle. Your answer 5 is **Hard**. Recorded as DECIDED (hard); register recommendation overridden by the owner.
- **D-13 (seat definition and limits per tier).** Register recommendation: Approve section 5 of document 02. Your answer 6 sets the tiers. PROVISIONAL until the commercial approval.
- **D-10 (quote cap type).** Register recommendation: Hard. You did not answer. Kept as recommended (hard), no limit set. UNRESOLVED for the quantity.

## 3. Things your answers change or that need a further decision

1. **A hard invoice cap can stop a paying business from invoicing.** Allowed, as decided. Open questions:
   - Do **recurring and quote-converted** invoices count, and what happens when a scheduled recurring invoice would exceed the cap: skip it, delay it, or issue it anyway?
   - What counts as "issued": the moment an invoice gets its number?
   - The register says gap **G-15 / INV-01** (quote-converted and recurring invoices are not posted to the ledger) should be fixed before invoices are metered, otherwise the count and the books disagree.
2. **Payment failure and suspension** are to be treated differently from a voluntary downgrade. The durations and what stays accessible are not set. UNRESOLVED.
3. **Seats:** "internal administrators count" is stated; how Owner versus Administrator (approved earlier) are counted when one person holds both is not stated. Assumed: one person counts once.
4. **Provisional limits are not to be shown to customers or hard-coded into a price book** until final commercial approval. They will be stored as configuration so they can change without a release.
5. **Free services versus Free Core (D-01):** the free list in answer 4 is platform services and applies to everyone. Free Core (CRM, Invoicing, Catalogue) stays conditional on a paid module unless D-01 is changed.

## 4. Plan for what is not built yet

| Item | State | Next step |
|---|---|---|
| HR approval step | Not built | Survey which of the listed changes already have an approval flow (leave, overtime, pay adjustments, employee changes exist in some form). Build only the gaps, with self-approval blocked and an audit trail. |
| Invoice and seat metering (Phase C) | Not built. The register says it depends on the entitlement engine (Phase B) and the INV-01 fix. | First slice: count issued invoices per tenant per month and active-plus-invited seats, in **report mode only**. No blocking until the limits are confirmed. |
| Downgrade transition (Phase D) | Not built | Needs the payment-failure and suspension rules (item 2 above). |

## 5. Unresolved items

- D-23 (credit scheme), D-10 quantity, D-24 and D-26 pending legal review.
- Payment-failure and suspension handling.
- Recurring invoices against the hard cap.
- Final commercial approval of the tier limits (invoice allowance and seats).
- The five catalogue query results (answer 8).
- The remaining register items not covered by answer 9: D-06 to D-09, D-12, D-14 to D-22.
