# IAM and entitlement backlog (2026-11-04)

Companion to `docs/iam/ENTITLEMENT_TIERS_AND_GAPS.md`. Priority: P0 security or commercial leak, P1 needed for launch, P2 later.

## Open items

| ID | Pri | Item | Depends on |
|---|---|---|---|
| E1 | P0 | Add module entitlement to the 229 controllers on the debt list (shrink `controllers-without-module-guard.txt`) | none |
| E2 | P0 | Run Phase 0 on a real database: `mvn test`, start the app (Flyway V589), invite, accept, resend, deactivate | patch 0606 applied |
| E3 | P1 | Tier/feature/limit/usage-counter tables; free core after trial instead of 402 lockout | answers to the four open questions |
| E4 | P1 | Quotation and invoice caps with transactional counting | E3 |
| E5 | P1 | Feature keys for each paid upgrade; frontend lock-and-explain component; platform admin screen | E3 |
| E6 | P1 | Owner/Administrator roles; approvals out of the administrator template; sole-user self-approval flagged `self_approved` | IAM Phase 1 |
| E7 | P1 | Entitlement-filtered tokens (`tcv`), `@RequiresModule` | E1, E3 |
| E8 | P1 | Screen for `access_audit_events`; also record refused (403) attempts | patch 0606 |
| E9 | P2 | Accounts and memberships, access-version claims, immediate revocation | Phase 1 |
| E10 | P2 | Scopes, delegation, access reviews, support sessions v2, MFA | Phase 3 |

## Open questions for the product owner
1. Which platform services stay free for every tenant (approvals, audit trail, notifications, documents, secure access, contracting)?
2. Soft cap (warn, allow) or hard cap (block) for invoices; hard for quotations?
3. Seat limits per tier.
4. A downgraded tenant: read-only access to paid modules, or none?

## Delivery status of numbered patches (from commit history)
Applied order on the user's machine must be 0593 onwards, then 0602 to 0606. Delivered, all committed, none pushed:

| Patch | Content |
|---|---|
| 0575 to 0582 | Legal Practice screens; Facilities Management provider screens and dashboard |
| 0583 | CRM customer search fix |
| 0584 | Bookkeeping bank import messages; agreement end-date fix |
| 0585 | HR dashboard shows approved payroll only |
| 0593 to 0601 | Earthmoving, HR/Bookkeeping form messages, FM site rules (A10, A11), FM portal step 1, Company & billing, FM fixes, HR registration and drafts, medical scheme contributions (commit subjects in `git log`; per-number mapping not recorded here) |
| 0596 | Projects client portal bean name fix (app starts) |
| 0602 | FM client portal step 2: progress, photos, equipment, reports, map links |
| 0603 | Internal recruiter: Clients and Placements removed |
| 0604 | IAM audit and design documents |
| 0605 | IAM decisions recorded |
| 0606 | IAM Phase 0 |

Unverified: backend Java in 0602 and 0606 was not compiled or run in the sandbox (no Maven repository there).
Known failing before these changes: 8 HR tests; `RecApproval.test.tsx` needs `@testing-library/user-event` in the sandbox.
