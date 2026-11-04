# Module Role & Permission Matrix

Status: proposal built from the code. Date: 2026-11-04.

## How to read this

- **Implemented** = found in the repository by search: the authority names in `hasAuthority(...)` and `hasAnyAuthority(...)` across `platform/src/main/java`. There are **332 distinct authority names** in 58 prefix groups.
- **Implemented roles**: only two stored roles exist in code: `ADMIN` (every permission, created at registration) and `EMPLOYEE` (`USER_READ, CUSTOMER_READ, INVOICE_READ, BILLING_READ, REPORT_VIEW`, created on first invite). Clinic additionally ships **role templates in Java** (`ClinicRoleTemplates`), which are not stored roles until a tenant creates one. An `OWNER` role is referenced by migration V383 but nothing creates it.
- **Proposed** = everything else in this file: the role names, the permission bundles, and any permission written as "proposed". Do not read a proposed role as existing.
- Permissions named here that are **not** in the code list are marked *proposed*. I did not invent any other.
- Every module needs an **Access tab** inside the module (a view of central access, section 1 of `IAM_TARGET_ARCHITECTURE.md`); none needs its own user store. "Own user admin screen" below means more than that.

## Common roles in every module (proposed template pattern)

| Template | Holds |
|---|---|
| `<Module> Viewer` | `_READ` |
| `<Module> Operator` | `_READ`, `_MANAGE` |
| `<Module> Approver` | `_READ`, the module's approval permission(s); never `_MANAGE` |
| `<Module> Administrator` | `_READ`, `_ADMIN` (configure module, assign module roles within delegation limits); **no approvals** |

---

## CRM

- **Implemented permissions** (4): `CUSTOMER_CREATE`, `CUSTOMER_DELETE`, `CUSTOMER_READ`, `CUSTOMER_UPDATE`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: CRM Viewer (`CUSTOMER_READ`); CRM Agent (+`CUSTOMER_CREATE`, `CUSTOMER_UPDATE`); CRM Manager (+`CUSTOMER_DELETE`)
- **Sensitive actions needing separate authority**: delete; bulk export (no `CUSTOMER_EXPORT` exists, proposed); POPIA export (`POPIA_EXPORT`, tenant-level, exists)
- **Approval roles**: none needed
- **Read-only role**: CRM Viewer
- **External access**: public lead capture endpoint (`/api/v1/public/crm/leads`); no customer login
- **Needs its own user administration screen?** No

## Catalogue

- **Implemented permissions** (11): `CATALOGUE_ADMIN`, `CATALOGUE_APPROVE`, `CATALOGUE_ARCHIVE`, `CATALOGUE_EXPORT`, `CATALOGUE_IMPORT`, `CATALOGUE_PRICE_APPROVE`, `CATALOGUE_PRICE_READ`, `CATALOGUE_PRICE_WRITE`, `CATALOGUE_PUBLISH`, `CATALOGUE_READ`, `CATALOGUE_WRITE`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Catalogue Viewer (`CATALOGUE_READ`, `CATALOGUE_PRICE_READ`); Catalogue Editor (+`CATALOGUE_WRITE`, `CATALOGUE_IMPORT`); Price Maker (`CATALOGUE_PRICE_WRITE`); Publisher (`CATALOGUE_PUBLISH`); Catalogue Admin
- **Sensitive actions needing separate authority**: price changes (`CATALOGUE_PRICE_WRITE` vs `CATALOGUE_PRICE_APPROVE` already separate), publish, export, archive
- **Approval roles**: Price Approver (`CATALOGUE_PRICE_APPROVE`), Content Approver (`CATALOGUE_APPROVE`)
- **Read-only role**: Catalogue Viewer
- **External access**: none
- **Needs its own user administration screen?** No

## Invoicing

- **Implemented permissions** (8): `INVOICE_CREATE`, `INVOICE_DELETE`, `INVOICE_PAYMENT`, `INVOICE_READ`, `INVOICE_REFUND`, `INVOICE_SEND`, `CREDIT_NOTE_APPROVE`, `CREDIT_NOTE_ISSUE`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Invoice Viewer (`INVOICE_READ`); Biller (+`INVOICE_CREATE`, `INVOICE_SEND`); Collections Clerk (`INVOICE_PAYMENT`); Credit Note Issuer (`CREDIT_NOTE_ISSUE`); Billing Admin
- **Sensitive actions needing separate authority**: payments, refunds (`INVOICE_REFUND`), delete, credit notes
- **Approval roles**: Credit Note Approver (`CREDIT_NOTE_APPROVE`), Refund Approver (`INVOICE_REFUND`)
- **Read-only role**: Invoice Viewer
- **External access**: none found (no invoicing portal in the `SecurityConfig` public list)
- **Needs its own user administration screen?** No

## Accounting

- **Implemented permissions** (5): `ACCOUNTING_ADMIN`, `ACCOUNTING_MANAGE`, `ACCOUNTING_PERIOD_REOPEN`, `ACCOUNTING_READ`, `ACCOUNTING_RECON_SIGNOFF`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Accounting Viewer (`ACCOUNTING_READ`); Bookkeeper (`ACCOUNTING_MANAGE`); Reconciliation Reviewer (`ACCOUNTING_RECON_SIGNOFF`); Period Controller (`ACCOUNTING_PERIOD_REOPEN`); Accounting Admin (`ACCOUNTING_ADMIN`)
- **Sensitive actions needing separate authority**: journal posting, period reopen, reconciliation sign-off
- **Approval roles**: Journal Reviewer (rule exists in `JournalReviewRules`), Reconciliation Reviewer, Period Controller
- **Read-only role**: Accounting Viewer
- **External access**: accountant and auditor portals (below)
- **Needs its own user administration screen?** No

## Accountant (external accountant)

- **Implemented permissions** (4): `ACCOUNTANT_ASSIGN`, `ACCOUNTANT_READ`, `ACCOUNTANT_SIGNOFF`, `ACCOUNTANT_WRITE`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Accountant Reader (`ACCOUNTANT_READ`); Accountant Preparer (+`ACCOUNTANT_WRITE`); Accountant Sign-off (`ACCOUNTANT_SIGNOFF`); Assigner (`ACCOUNTANT_ASSIGN`)
- **Sensitive actions needing separate authority**: sign-off
- **Approval roles**: Sign-off
- **Read-only role**: Accountant Reader
- **External access**: **Yes**: accountant portal (`/api/v1/accountant/portal/**`) with `PortalUser` plus grants
- **Needs its own user administration screen?** Grants view only

## Accounts Payable

- **Implemented permissions** (5): `AP_APPROVE`, `AP_MANAGE`, `AP_PAY`, `AP_READ`, `AP_SUPPLIER_BANKING`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: AP Viewer (`AP_READ`); AP Clerk (`AP_MANAGE`); Bill Approver (`AP_APPROVE`); Payment Releaser (`AP_PAY`); Supplier Banking Maintainer (`AP_SUPPLIER_BANKING`)
- **Sensitive actions needing separate authority**: supplier bank changes, payment release
- **Approval roles**: Bill Approver, Payment Releaser (V388 `ap_separation_of_duties` already splits these)
- **Read-only role**: AP Viewer
- **External access**: supplier portal (none today)
- **Needs its own user administration screen?** No

## Expenses

- **Implemented permissions** (3): `EXPENSES_ADMIN`, `EXPENSES_MANAGE`, `EXPENSES_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Expense Submitter (own claims, any employee); Expense Approver (**proposed** `EXPENSES_APPROVE`, not in code); Expense Admin (`EXPENSES_ADMIN`)
- **Sensitive actions needing separate authority**: approving others' claims; export
- **Approval roles**: Expense Approver
- **Read-only role**: Expenses Viewer (`EXPENSES_READ`)
- **External access**: none
- **Needs its own user administration screen?** No

## HR & Payroll

- **Implemented permissions** (25): `HR_ATTENDANCE_MANAGE`, `HR_COMPENSATION_APPROVE`, `HR_COMPENSATION_MANAGE`, `HR_COMPENSATION_READ`, `HR_ER_DECIDE`, `HR_ER_MANAGE`, `HR_ER_READ`, `HR_LETTER_ISSUE`, `HR_LETTER_MANAGE`, `HR_LETTER_READ`, `HR_MANAGE`, `HR_ONBOARDING_MANAGE`, … (+13 more)
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: HR Viewer (`HR_READ`); HR Officer (+`HR_MANAGE`, `HR_ONBOARDING_MANAGE`, `HR_ATTENDANCE_MANAGE`, `HR_LETTER_*`); Employee Relations Officer (`HR_ER_MANAGE`); ER Decision Maker (`HR_ER_DECIDE`); Payroll Preparer (`PAYROLL_PREPARE`); Payroll Approver (`PAYROLL_APPROVE`); Payroll Releaser (`PAYROLL_RUN`); Compensation Manager (`HR_COMPENSATION_MANAGE`); Compensation Approver (`HR_COMPENSATION_APPROVE`); Team Lead (`HR_TEAM_READ`); Leave Approver (`LEAVE_APPROVE`); HR Admin (`HR_MANAGE`)
- **Sensitive actions needing separate authority**: `HR_SENSITIVE_READ`, compensation, payroll run, certificates (`PAYROLL_CERTIFICATE_ISSUE`), ER decisions, `LEAVE_ADJUST`
- **Approval roles**: Payroll Approver, Compensation Approver, Overtime Approver (`HR_OVERTIME_APPROVE`), Leave Approver
- **Read-only role**: HR Viewer
- **External access**: employee self-service and HR portal (`/api/v1/hr/portal/**`)
- **Needs its own user administration screen?** No

## Recruitment (internal)

- **Implemented permissions** (9): `RECRUITER_ADMIN`, `RECRUITER_APPROVE`, `RECRUITER_AUDIT`, `RECRUITER_CV`, `RECRUITER_HIRE`, `RECRUITER_MANAGE`, `RECRUITER_OFFER`, `RECRUITER_READ`, `RECRUITER_REPORTS`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Recruiter (`RECRUITER_READ`, `RECRUITER_MANAGE`, `RECRUITER_CV`); Hiring Manager (read plus `RECRUITER_APPROVE` for own requisitions); Offer Maker (`RECRUITER_OFFER`); Hirer (`RECRUITER_HIRE`); Recruitment Auditor (`RECRUITER_AUDIT`, `RECRUITER_REPORTS`); Admin (`RECRUITER_ADMIN`)
- **Sensitive actions needing separate authority**: CV and applicant data (POPIA), offers, hire (creates HR record)
- **Approval roles**: Requisition/Offer Approver (`RECRUITER_APPROVE`)
- **Read-only role**: Recruitment Auditor
- **External access**: public careers portal (`/api/v1/recruiter/careers/**`, `/portal/**`)
- **Needs its own user administration screen?** No. Internal module has **no clients or placements** (removed in patch 0603; those belong to Recruitment Agency)

## Payroll Bureau

- **Implemented permissions** (3): `PAYROLLBUREAU_ADMIN`, `PAYROLLBUREAU_MANAGE`, `PAYROLLBUREAU_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Bureau Viewer; Bureau Clerk (`PAYROLLBUREAU_MANAGE`); Bureau Admin
- **Sensitive actions needing separate authority**: payroll runs for client companies
- **Approval roles**: (module-owned; see HR payroll roles)
- **Read-only role**: Bureau Viewer
- **External access**: **Yes**: bureau client portal
- **Needs its own user administration screen?** No

## Fleet

- **Implemented permissions** (3): `FLEET_ADMIN`, `FLEET_MANAGE`, `FLEET_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Fleet Viewer (`FLEET_READ`); Fleet Supervisor (`FLEET_MANAGE`); Fleet Admin (`FLEET_ADMIN`); Driver (mobile, assigned vehicle only)
- **Sensitive actions needing separate authority**: telematics data, vehicle disposal, maintenance cost over limit (proposed)
- **Approval roles**: Maintenance Approver (proposed)
- **Read-only role**: Fleet Viewer
- **External access**: none
- **Needs its own user administration screen?** No

## Fuel

- **Implemented permissions** (3): `FUEL_MANAGE`, `FUEL_MARGIN_READ`, `FUEL_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Fuel Viewer (`FUEL_READ`); Fuel Clerk (`FUEL_MANAGE`); Margin Analyst (`FUEL_MARGIN_READ`); proposed: Fuel Requisition Approver (`FUEL_REQUISITION_APPROVE`), Fuel Delivery Confirmer (`FUEL_DELIVERY_CONFIRM`)
- **Sensitive actions needing separate authority**: margins (`FUEL_MARGIN_READ` exists), requisition approval, delivery confirmation
- **Approval roles**: Requisition Approver; requester ≠ delivery confirmer
- **Read-only role**: Fuel Viewer
- **External access**: none
- **Needs its own user administration screen?** No

## Earthmoving

- **Implemented permissions** (6): `EARTHMOVING_ADMIN`, `EARTHMOVING_DEPLOY`, `EARTHMOVING_MAINTAIN`, `EARTHMOVING_READ`, `EARTHMOVING_RELEASE`, `EARTHMOVING_WRITE`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Earthmoving Viewer (`EARTHMOVING_READ`); Planner (`EARTHMOVING_WRITE`); Deployer (`EARTHMOVING_DEPLOY`); Maintenance Lead (`EARTHMOVING_MAINTAIN`); Release Authoriser (`EARTHMOVING_RELEASE`); Admin
- **Sensitive actions needing separate authority**: machine release
- **Approval roles**: Release Authoriser (deploy ≠ release)
- **Read-only role**: Viewer
- **External access**: none
- **Needs its own user administration screen?** No

## Security (guarding)

- **Implemented permissions** (5): `SECURITY_ADMIN`, `SECURITY_GUARD`, `SECURITY_MANAGE`, `SECURITY_READ`, `VIP_DETAIL_ACCESS`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Security Viewer (`SECURITY_READ`); Control Room Operator (`SECURITY_MANAGE`); Guard (`SECURITY_GUARD`, uses separate guard JWT); VIP Detail Reader (`VIP_DETAIL_ACCESS`); Security Admin
- **Sensitive actions needing separate authority**: VIP details, armoury, guard screening, payroll
- **Approval roles**: Incident closure (module rule)
- **Read-only role**: Security Viewer
- **External access**: security client portal (`SecurityClientPortalController`), guards via guard login
- **Needs its own user administration screen?** No

## Clinic

- **Implemented permissions** (99): `CLINIC_ACCESS_LOG_READ`, `CLINIC_ADMIN`, `CLINIC_ALLERGY_READ`, `CLINIC_ALLERGY_WRITE`, `CLINIC_APPOINTMENT_CREATE`, `CLINIC_APPOINTMENT_READ`, `CLINIC_APPOINTMENT_RESCHEDULE`, `CLINIC_APPOINTMENT_UPDATE`, `CLINIC_BILL_READ`, `CLINIC_BREAK_GLASS_PRINT`, `CLINIC_BREAK_GLASS_REVIEW`, `CLINIC_BREAK_GLASS_VIEW`, … (+87 more)
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE` (Clinic templates in Java, see below)
- **Proposed standard roles**: **Implemented as code templates** (not stored roles): Doctor, Clinical associate, Professional nurse, Enrolled nurse, Reception, Billing, Pharmacy, Lab, Records, Auditor, Practice manager, Clinic admin (see `docs/CLINIC-ACCESS-MATRIX.md`, `ClinicRoleTemplates`)
- **Sensitive actions needing separate authority**: clinical notes, signing, restricted records, break-glass (`CLINIC_BREAK_GLASS_*`), claims
- **Approval roles**: Signing (`CLINIC_CONSULTATION_SIGN`), break-glass review
- **Read-only role**: Auditor, Records
- **External access**: public document verification only (`ClinicDocumentVerificationController`); no patient portal found
- **Needs its own user administration screen?** No (templates already exist; will move into the central engine)

## Bookings

- **Implemented permissions** (3): `BOOKINGS_ADMIN`, `BOOKINGS_MANAGE`, `BOOKINGS_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Bookings Viewer; Bookings Operator (`BOOKINGS_MANAGE`); Bookings Admin
- **Sensitive actions needing separate authority**: refunds/cancellations over limit (proposed)
- **Approval roles**: (module-owned)
- **Read-only role**: Viewer
- **External access**: none found
- **Needs its own user administration screen?** No

## Booking Agency

- **Implemented permissions** (3): `BOOKINGAGENCY_ADMIN`, `BOOKINGAGENCY_MANAGE`, `BOOKINGAGENCY_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Viewer; Agent (`BOOKINGAGENCY_MANAGE`); Admin
- **Sensitive actions needing separate authority**: commissions (proposed)
- **Approval roles**: (module-owned)
- **Read-only role**: Viewer
- **External access**: **Yes**: booking agency portal
- **Needs its own user administration screen?** No

## Events

- **Implemented permissions** (2): `EVENTS_MANAGE`, `EVENTS_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Events Viewer (`EVENTS_READ`); Events Coordinator (`EVENTS_MANAGE`); proposed: Ticketing Manager (`EVENTS_TICKETING_MANAGE`), Events Admin
- **Sensitive actions needing separate authority**: ticket pricing, refunds (proposed)
- **Approval roles**: Refund Approver (proposed)
- **Read-only role**: Viewer
- **External access**: none found (attendees are a target external group in the brief; nothing exists yet)
- **Needs its own user administration screen?** No

## Creative

- **Implemented permissions** (2): `CREATIVE_MANAGE`, `CREATIVE_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Creative Viewer (`CREATIVE_READ`); Creative Contributor (`CREATIVE_MANAGE`); proposed: Project Manager (`CREATIVE_PROJECT_MANAGE`)
- **Sensitive actions needing separate authority**: client proof approval (public link)
- **Approval roles**: Client proof approval is external
- **Read-only role**: Viewer
- **External access**: **Yes**: public proof approval (`/api/v1/creative/approve/**`)
- **Needs its own user administration screen?** No

## Marketing

- **Implemented permissions** (3): `MARKETING_ADMIN`, `MARKETING_MANAGE`, `MARKETING_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Marketing Viewer; Marketer (`MARKETING_MANAGE`); Marketing Admin
- **Sensitive actions needing separate authority**: bulk send and list export (proposed `MARKETING_SEND`, `MARKETING_EXPORT`)
- **Approval roles**: Campaign Approver (proposed)
- **Read-only role**: Viewer
- **External access**: unsubscribe link (public)
- **Needs its own user administration screen?** No

## Desk Support

- **Implemented permissions** (3): `DESK_ADMIN`, `DESK_MANAGE`, `DESK_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Desk Viewer; Agent (`DESK_MANAGE`); Desk Admin
- **Sensitive actions needing separate authority**: customer data, ticket merge
- **Approval roles**: (module-owned)
- **Read-only role**: Viewer
- **External access**: customers via desk portal (`/api/v1/desk/portal/**`)
- **Needs its own user administration screen?** No

## Tasks

- **Implemented permissions** (3): `TASKS_ADMIN`, `TASKS_MANAGE`, `TASKS_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Tasks Viewer; Task Contributor (`TASKS_MANAGE`); Tasks Admin
- **Sensitive actions needing separate authority**: none significant
- **Approval roles**: none
- **Read-only role**: Viewer
- **External access**: none
- **Needs its own user administration screen?** No

## Projects

- **Implemented permissions** (4): `PM_ADMIN`, `PM_APPROVE`, `PM_READ`, `PM_WRITE`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Project Viewer (`PM_READ`); Project Contributor (`PM_WRITE`); Project Approver (`PM_APPROVE`); Project Admin (`PM_ADMIN`)
- **Sensitive actions needing separate authority**: budget approval
- **Approval roles**: Project Approver
- **Read-only role**: Viewer
- **External access**: **Yes**: project client portal
- **Needs its own user administration screen?** No

## Supply Chain

- **Implemented permissions** (6): `SCM_ADMIN`, `SCM_INVENTORY`, `SCM_INVOICE`, `SCM_ORDER`, `SCM_READ`, `SCM_RECEIVE`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: SCM Viewer (`SCM_READ`); Buyer (`SCM_ORDER`); Goods Receiver (`SCM_RECEIVE`); Invoice Matcher (`SCM_INVOICE`); Inventory Controller (`SCM_INVENTORY`); SCM Admin (`SCM_ADMIN`)
- **Sensitive actions needing separate authority**: order ≠ receive ≠ invoice (three-way match)
- **Approval roles**: Order approval (module rule in `ScmService`)
- **Read-only role**: SCM Viewer
- **External access**: supplier portal (none today)
- **Needs its own user administration screen?** No

## Warehousing

- **Implemented permissions** (3): `WAREHOUSING_ADMIN`, `WAREHOUSING_MANAGE`, `WAREHOUSING_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Warehouse Viewer; Warehouse Operator (`WAREHOUSING_MANAGE`); Warehouse Admin
- **Sensitive actions needing separate authority**: stock adjustment (proposed separate approve)
- **Approval roles**: Stock Adjustment Approver (proposed)
- **Read-only role**: Viewer
- **External access**: **Yes**: warehousing portal
- **Needs its own user administration screen?** No

## Property

- **Implemented permissions** (3): `PROPERTY_ADMIN`, `PROPERTY_MANAGE`, `PROPERTY_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Property Viewer; Property Manager (`PROPERTY_MANAGE`); Property Admin
- **Sensitive actions needing separate authority**: deposits, lease terms
- **Approval roles**: (module-owned)
- **Read-only role**: Viewer
- **External access**: **Yes**: tenants/owners portal
- **Needs its own user administration screen?** No

## Legal Practice

- **Implemented permissions** (3): `LEGALPRACTICE_ADMIN`, `LEGALPRACTICE_MANAGE`, `LEGALPRACTICE_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Legal Viewer; Fee Earner (`LEGALPRACTICE_MANAGE`); Legal Practice Admin
- **Sensitive actions needing separate authority**: trust funds, privileged files
- **Approval roles**: Partner Sign-off (proposed)
- **Read-only role**: Viewer
- **External access**: **Yes**: legal client portal
- **Needs its own user administration screen?** No

## Legal Compliance

- **Implemented permissions** (3): `LEGALCOMPLIANCE_ADMIN`, `LEGALCOMPLIANCE_MANAGE`, `LEGALCOMPLIANCE_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Compliance Viewer; Compliance Officer (`LEGALCOMPLIANCE_MANAGE`); Admin
- **Sensitive actions needing separate authority**: register changes
- **Approval roles**: (module-owned)
- **Read-only role**: Viewer
- **External access**: none
- **Needs its own user administration screen?** No

## Debt Collection

- **Implemented permissions** (3): `DEBTCOLLECTION_ADMIN`, `DEBTCOLLECTION_MANAGE`, `DEBTCOLLECTION_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Collections Viewer; Collector (`DEBTCOLLECTION_MANAGE`); Collections Admin
- **Sensitive actions needing separate authority**: write-offs, settlements (proposed separate approve)
- **Approval roles**: Write-off Approver (proposed)
- **Read-only role**: Viewer
- **External access**: none found; the separate Collections Agency module has its own portal (`CollAgencyPortalAuthController`)
- **Needs its own user administration screen?** No

## Agriculture

- **Implemented permissions** (4): `AGRICULTURE_ADMIN`, `AGRICULTURE_FINANCE`, `AGRICULTURE_MANAGE`, `AGRICULTURE_READ`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Farm Viewer (`AGRICULTURE_READ`); Farm Manager (`AGRICULTURE_MANAGE`); Farm Finance (`AGRICULTURE_FINANCE`); Admin
- **Sensitive actions needing separate authority**: farm finance
- **Approval roles**: Finance approval
- **Read-only role**: Viewer
- **External access**: none
- **Needs its own user administration screen?** No

## POS & Stock

- **Implemented permissions** (4): `POS_ADMIN`, `POS_MANAGE`, `POS_READ`, `POS_SELL`
- **Implemented roles**: none beyond `ADMIN`/`EMPLOYEE`
- **Proposed standard roles**: Cashier (`POS_SELL`); Supervisor (`POS_MANAGE`); POS Viewer (`POS_READ`); POS Admin (`POS_ADMIN`)
- **Sensitive actions needing separate authority**: voids, refunds, stock adjustment, cash-up variance (proposed `POS_REFUND_APPROVE`, `STOCK_ADJUST_APPROVE`)
- **Approval roles**: Supervisor approval of cashier refunds
- **Read-only role**: POS Viewer
- **External access**: none
- **Needs its own user administration screen?** No

## Modules present in the code but not in your list

| Module | Implemented permissions |
|---|---|
| Facilities (internal) | `FACILITIES_ADMIN`, `FACILITIES_AUDIT_READ`, `FACILITIES_COMPLIANCE_MANAGE`, `FACILITIES_COMPLIANCE_REVOKE`, `FACILITIES_INSPECT`, `FACILITIES_MANAGE`, `FACILITIES_PPM_MANAGE`, `FACILITIES_READ`, `FACILITIES_TECHNICIAN`, `FACILITIES_VENDOR_APPROVE`, `FACILITIES_WORKORDER_APPROVE`, `FACILITIES_WORKORDER_ASSIGN`, `FACILITIES_WORKORDER_CREATE`, `FACILITIES_WORKORDER_REQUEST`, `FACILITIES_WORKORDER_VERIFY` |
| Facilities Management (provider) | `FACILITIESMANAGEMENT_ADMIN`, `FACILITIESMANAGEMENT_AUDIT_READ`, `FACILITIESMANAGEMENT_BILLING_APPROVE`, `FACILITIESMANAGEMENT_BILLING_MANAGE`, `FACILITIESMANAGEMENT_CONTRACT_MANAGE`, `FACILITIESMANAGEMENT_COST_READ`, `FACILITIESMANAGEMENT_MANAGE`, `FACILITIESMANAGEMENT_PORTAL_MANAGE`, `FACILITIESMANAGEMENT_PPM_MANAGE`, `FACILITIESMANAGEMENT_READ`, `FACILITIESMANAGEMENT_SLA_MANAGE`, `FACILITIESMANAGEMENT_TECHNICIAN`, `FACILITIESMANAGEMENT_VENDOR_APPROVE`, `FACILITIESMANAGEMENT_WORKORDER_ASSIGN`, `FACILITIESMANAGEMENT_WORKORDER_CREATE` |
| Insurance | `INSURANCE_ADMIN`, `INSURANCE_MANAGE`, `INSURANCE_READ` |
| Insurance Brokerage | `INSURANCEBROKERAGE_ADMIN`, `INSURANCEBROKERAGE_MANAGE`, `INSURANCEBROKERAGE_READ` |
| Bookkeeping practice | `BOOKKEEPING_ADMIN`, `BOOKKEEPING_MANAGE`, `BOOKKEEPING_READ` |
| Collections Agency | `COLLECTIONSAGENCY_ADMIN`, `COLLECTIONSAGENCY_MANAGE`, `COLLECTIONSAGENCY_READ` |
| Recruitment Agency | `RECRUITMENTAGENCY_ADMIN`, `RECRUITMENTAGENCY_MANAGE`, `RECRUITMENTAGENCY_READ` |
| Training | `TRAINING_ADMIN`, `TRAINING_AUDIT`, `TRAINING_MANAGE`, `TRAINING_READ`, `TRAINING_REPORTS` |
| Training Provider | `TRAININGPROVIDER_ADMIN`, `TRAININGPROVIDER_MANAGE`, `TRAININGPROVIDER_READ` |
| Compliance / Compliance Services / Tender | `COMPLIANCE_ADMIN`, `COMPLIANCE_MANAGE`, `COMPLIANCE_READ`, `COMPLIANCE_SERVICES_ADMIN`, `COMPLIANCE_SERVICES_MANAGE`, `COMPLIANCE_SERVICES_READ` |
| Internal Audit | `AUDIT_ADMIN`, `AUDIT_COMMITTEE_READ`, `AUDIT_INVESTIGATE`, `AUDIT_MANAGE`, `AUDIT_READ`, `CONTROL_EXCEPTION_READ`, `CONTROL_EXCEPTION_RESOLVE` |
| Platform / tenant level | `USER_CREATE`, `USER_DEACTIVATE`, `USER_INVITE`, `USER_READ`, `USER_UPDATE`, `ROLE_MANAGE`, `ROLE_READ`, `SETTINGS_MANAGE`, `BILLING_MANAGE`, `BILLING_READ`, `POPIA_EXPORT`, `APPROVALS_MANAGE`, … (+9 more) |

These need the same exercise (three baseline permissions, extras only for approval, financial movement, sensitive data or export) once the first group is done. `FACILITIES_*` and `FACILITIESMANAGEMENT_*` already have work-order, vendor and billing approval permissions and are the closest to the target standard together with Clinic.

## Cross-module permissions that must be reclassified

| Permission | Today | Proposed class |
|---|---|---|
| `USER_INVITE`, `USER_UPDATE`, `USER_DEACTIVATE`, `ROLE_MANAGE` | normal | **tenant administration**, grantor ceiling applies |
| `SETTINGS_MANAGE`, `BILLING_MANAGE` | normal | **Owner** |
| `POPIA_EXPORT`, `HR_SENSITIVE_READ`, `AP_SUPPLIER_BANKING`, `VIP_DETAIL_ACCESS`, `FUEL_MARGIN_READ` | normal | **sensitive** |
| `PAYROLL_*`, `AP_PAY`, `INVOICE_REFUND`, `CREDIT_NOTE_APPROVE`, `ACCOUNTING_PERIOD_REOPEN`, `*_APPROVE` | normal | **financial / approval**: never in an administrative template |

## Open questions for you

1. Which modules are included free for every tenant (the README names `crm`, `invoicing`, `catalogue` as core)? This decides which unguarded modules in audit finding F2 are bugs and which are by design.
2. Is `EXPENSES_APPROVE` meant to exist? The code only has `EXPENSES_READ/MANAGE/ADMIN`; `POST /expenses/{id}/approve` is guarded by `EXPENSES_MANAGE` (`expenses/api/ExpensesController.java`), so anyone who can manage claims can approve them. Also, several employee-facing expense endpoints in the same controller require `USER_CREATE` (an administrative permission); I did not check whether that blocks ordinary employees from submitting claims, but it looks wrong and should be tested.
