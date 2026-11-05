# Permission metadata: what was decided and what to review

Status: draft for review, 5 Nov 2026 (patch 0631, migration `V590__permission_metadata.sql`). Nothing yet drops a permission based on this data; it only records it. Read it before the next step (dropping non-entitled modules' permissions when a token is minted, Phase 1 item 2).

## How it was made

1. Every one of the 397 permissions in the database (checked by running all 590 migrations on a real PostgreSQL 16) got one explicit row in V590: owning module, resource, verb, sensitivity, second-person flag, who may grant it.
2. The owning module comes from the name prefix. It was cross-checked against which controller packages actually use each name: no disagreements.
3. `unassigned` is the default for any permission added later without metadata. The build test `everyPermissionInTheCatalogueHasMetadata` fails until a migration numbered V590 or higher gives it metadata.

## Permissions per owning module

| module_key | permissions |
|---|---|
| clinic | 154 |
| hr | 30 |
| core | 21 |
| facilities | 15 |
| facilitiesmanagement | 15 |
| catalogue | 11 |
| recruiter | 9 |
| invoicing | 8 |
| supply_chain | 7 |
| earthmoving | 6 |
| pos | 6 |
| accountant | 5 |
| accounting | 5 |
| ap | 5 |
| crm | 5 |
| security | 5 |
| training | 5 |
| agriculture | 4 |
| internal-audit | 4 |
| projects | 4 |
| bookingagency | 3 |
| bookings | 3 |
| bookkeeping | 3 |
| collectionsagency | 3 |
| complianceservices | 3 |
| compliancetender | 3 |
| debtcollection | 3 |
| desk | 3 |
| expenses | 3 |
| fleet | 3 |
| fuel | 3 |
| insurance | 3 |
| insurancebrokerage | 3 |
| legalcompliance | 3 |
| legalpractice | 3 |
| marketing | 3 |
| payrollbureau | 3 |
| property | 3 |
| recruitmentagency | 3 |
| tasks | 3 |
| trainingprovider | 3 |
| warehousing | 3 |
| contracting | 2 |
| creative | 2 |
| events | 2 |
| auditor | 1 |

## Please review these decisions

**1. `core` (21 permissions, always available whatever the plan).** User, role, settings, billing and report permissions, plus the shared platform services: approvals, evidence, records, control exceptions. `AUDIT_ADMIN` is also `core` on purpose: the core audit-trail endpoints, including the sole-approver report from patch 0630, need it, so a tenant without Internal Audit must keep it. Decision D-05 (which platform services are free) is still open; if any of these should be paid, change them here.

**2. Free core.** `CUSTOMER_*` and `POPIA_EXPORT` are `crm`, `INVOICE_*` and `CREDIT_NOTE_*` are `invoicing`, `CATALOGUE_*` is `catalogue`. They carry their own module key so the entitlement layer, not this table, decides they are free (pending D-01).

**3. Modules with no `module_catalogue` row** still get their own key (`projects`, `ap`, `accountant`, `auditor`, `payrollbureau`, `recruitmentagency`, `bookingagency`, and others). Until a catalogue row exists they stay report-only, as in `ModuleEntitlementPaths.REPORT_ONLY`.

**4. `requires_second_person` is true for 18 permissions:** `AP_APPROVE`, `AP_PAY`, `AP_SUPPLIER_BANKING`, `ACCOUNTING_ADMIN`, `ACCOUNTING_RECON_SIGNOFF`, `ACCOUNTING_PERIOD_REOPEN`, `CREDIT_NOTE_APPROVE`, `EXPENSES_MANAGE`, `EXPENSES_ADMIN`, `INVOICE_REFUND`, `PAYROLL_APPROVE`, `PAYROLL_FINALISE`, `HR_COMPENSATION_APPROVE`, `LEAVE_APPROVE`, `PM_APPROVE`, `CLINIC_PAYMENT_REVERSE`, `CLINIC_CLAIM_REVERSE`, `CLINIC_WRITE_OFF`. It is a statement of intent: `SeparationOfDutiesChecker` enforces it only where already wired (AP, manual journals, expenses). Refunds, payroll and the rest are not enforced yet. Add or remove names here as you see fit.

**5. `grantable_by`.** `OWNER` for user, role, settings and billing permissions; `MODULE_ADMIN` for any permission whose verb is `ADMIN`; `ADMIN` for everything else. The Owner/Administrator split you approved means an Administrator can never hand out `OWNER` permissions.

**6. Sensitivity.** `FINANCIAL` (54): money-moving or money-viewing actions in accounting, AP, invoicing and expenses, plus payroll, claims, bills, payments, compensation and margins. `SENSITIVE` (256): all HR and Clinic permissions that are not financial, user/role/settings, and any export, delete, sign, approve, admin, reverse, void, reopen, revoke or break-glass action. `NORMAL` (87): the rest. The Clinic and HR blanket rule is deliberately cautious.

**7. Awkward verbs.** Where a name has a compound verb (`SIGN_AS_OTHER`, `WRITE_OFF`, `CHECK_IN`) it was fixed by hand; a few others (for example `ACCOUNTANT_ALL_CLIENTS`, `RECRUITER_AUDIT`) have a verb that is really a noun. These fields feed the People & Access matrix labels only.

## What this does not do yet

It changes no behaviour. The next step uses `module_key` to drop non-entitled modules' permissions when a login token is issued, which needs your answers on the open entitlement questions first.
