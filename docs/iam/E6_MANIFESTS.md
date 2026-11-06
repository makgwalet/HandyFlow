# E6 manifests (final proposal, 2026-11-06)

Generated from V590 (393 permissions) plus one new permission. Files: `E6_OWNER_MANIFEST.csv` (all 394) and `E6_ADMINISTRATOR_MANIFEST.csv` (every permission with YES/NO and the reason). Preview of the role conversion: `E6_CONVERSION_PREVIEW.sql` (read-only).

**Counts:** Owner 394. Administrator holds 260; does not hold 134 (non-clinic: 37, clinic: 97).

## Permissions that need refining

- `EXPENSES_MANAGE`: verified in `ExpensesController` (lines 103, 113) to be **approve/reject claims only**. Submitting and editing your own claim needs no permission, so Administrators keep ordinary expense creation. It stays Owner-only; no split needed.
- `EXPENSES_ADMIN`: **combines two jobs**: paying out reimbursements (`ExpensesController` line 137, maker-checker enforced in `ExpensesService`) and editing expense policy (`ExpensePolicyController` lines 36 to 79). Split: new `EXPENSES_POLICY_ADMIN` for policy (Administrator holds it); `EXPENSES_ADMIN` keeps payout (Owner-only). The controller annotations change in the same patch as the migration, and the migration copies the new permission to every role that holds `EXPENSES_ADMIN` today.
- Generic exports: classified by data. `CATALOGUE_EXPORT` stays with Administrators. `POPIA_EXPORT`, `PAYROLL_STATUTORY_EXPORT`, `CLINIC_BREAK_GLASS_EXPORT`, `CLINIC_GROWTH_EXPORT`, `CLINIC_DOCUMENT_DOWNLOAD`, `CLINIC_SICK_NOTE_DOWNLOAD` are restricted. `EXTERNAL_AUDIT_INVITE` is restricted (audit evidence is separately authorised).

## Judgement calls to confirm (my defaults)

1. **Clinical record content (66)**: Administrators do not hold them by default (clinical history, consultations, prescriptions, results, vitals, notes, referrals, sick-note drafting). They are granted through clinical roles. Administrators keep clinic operations: appointments, queue, rooms, patients (demographics, contacts), practitioners, billing preparation, dashboards. The Owner holds everything, so a single-doctor practice is unaffected.
2. **Clinical security (9)**: break-glass, restricted records, access log, private notes, step override, patient merge: Owner only.
3. **`PAYROLL_BANK_FILE_GENERATE`**: Owner only (produces the payment instrument). Administrators keep `PAYROLL_PREPARE`, `PAYROLL_RUN`, `PAYROLL_CONFIGURATION_ADMIN`, `HR_COMPENSATION_MANAGE` (pay changes already need separate approval).
4. **Grant ceiling**: your approval says Administrators manage users and roles 'within the grant ceiling'. I therefore **withdraw** my earlier idea that they cannot hand on `USER_*`/`ROLE_*`/`SETTINGS_MANAGE`. They can grant anything they hold, but only an Owner can assign the OWNER role or appoint another Owner.

## Not held by Administrator, by reason

- **clinical record content: granted through clinical roles, not by default** (66): CLINIC_ALLERGY_READ, CLINIC_ALLERGY_WRITE, CLINIC_CLINICAL_HISTORY_READ, CLINIC_CLINICAL_HISTORY_WRITE, CLINIC_CLINICAL_MATRIX_READ, CLINIC_CLINICAL_MATRIX_WRITE ... (see CSV)
- **R2 maker-checker** (16): ACCOUNTING_ADMIN, ACCOUNTING_PERIOD_REOPEN, ACCOUNTING_RECON_SIGNOFF, AP_APPROVE, AP_PAY, AP_SUPPLIER_BANKING, CLINIC_CLAIM_REVERSE, CLINIC_PAYMENT_REVERSE, CLINIC_WRITE_OFF, CREDIT_NOTE_APPROVE, HR_COMPENSATION_APPROVE, INVOICE_REFUND, LEAVE_APPROVE, PAYROLL_APPROVE, PAYROLL_FINALISE, PM_APPROVE
- **R3 approval** (10): CATALOGUE_APPROVE, CATALOGUE_PRICE_APPROVE, CLINIC_CONTENT_APPROVE, FACILITIESMANAGEMENT_BILLING_APPROVE, FACILITIESMANAGEMENT_VENDOR_APPROVE, FACILITIES_VENDOR_APPROVE, FACILITIES_WORKORDER_APPROVE, HR_OVERTIME_APPROVE, RECORDS_APPROVE, RECRUITER_APPROVE
- **clinical security/override/restricted-record** (9): CLINIC_ACCESS_LOG_READ, CLINIC_BREAK_GLASS_PRINT, CLINIC_BREAK_GLASS_REVIEW, CLINIC_BREAK_GLASS_VIEW, CLINIC_CONSULTATION_OVERRIDE_REQUIRED_STEP, CLINIC_PATIENT_MERGE, CLINIC_PRIVATE_NOTE_READ, CLINIC_RESTRICTED_RECORD_ACCESS, CLINIC_RESTRICTED_RECORD_MANAGE
- **R3 signing** (6): ACCOUNTANT_SIGNOFF, CLINIC_CLINICAL_SIGN, CLINIC_CONSULTATION_SIGN, CLINIC_PRESCRIPTION_SIGN, CLINIC_REFERRAL_SIGN, CLINIC_SICK_NOTE_SIGN
- **R4 sensitive export/download** (6): CLINIC_BREAK_GLASS_EXPORT, CLINIC_DOCUMENT_DOWNLOAD, CLINIC_GROWTH_EXPORT, CLINIC_SICK_NOTE_DOWNLOAD, PAYROLL_STATUTORY_EXPORT, POPIA_EXPORT
- **R3 voiding** (5): CLINIC_DOCUMENT_VOID, CLINIC_PRESCRIPTION_VOID, CLINIC_REFERRAL_VOID, CLINIC_SICK_NOTE_VOID, CLINIC_VITALS_VOID
- **R3 issuing certificate/letter/credit note** (3): CREDIT_NOTE_ISSUE, HR_LETTER_ISSUE, PAYROLL_CERTIFICATE_ISSUE
- **R1 billing and subscription** (2): BILLING_MANAGE, BILLING_READ
- **R3 revocation of consent/compliance** (2): CLINIC_CONSENT_REVOKE, FACILITIES_COMPLIANCE_REVOKE
- **R3 locking** (2): CLINIC_CONSULTATION_LOCK, CLINIC_NOTE_LOCK
- **approval / payout** (2): EXPENSES_ADMIN, EXPENSES_MANAGE
- **R3 signing on behalf of another** (1): CLINIC_DOCUMENT_SIGN_AS_OTHER
- **audit evidence access is separately authorised** (1): EXTERNAL_AUDIT_INVITE
- **R3 sensitive personal data** (1): HR_SENSITIVE_READ
- **money-movement instrument** (1): PAYROLL_BANK_FILE_GENERATE
- **R3 financial finalise/refund/write-off** (1): POS_REFUND

## Not verified
Classification is by rule and name pattern with the exceptions above; the 66 clinical permissions and the Owner-only signing/void/lock set have not been checked controller by controller. Nothing is compiled or run. The preview SQL was tested on scratch data in PostgreSQL (oldest-active rule, tie-break, inactive never promoted, tenant without active admin fails).
