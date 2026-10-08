# Clinic access matrix

Generated for patch 0148. Three parts: the permission catalogue, the role templates, and the API authorization matrix. The Java source of truth is `ClinicPermission` and `ClinicRoleTemplates`; `ClinicEndpointAuthorizationTest` fails the build if an endpoint has no `@PreAuthorize`, names a permission that is not in the catalogue, or still relies on one of the old coarse permissions.

## How access is decided

```
user -> role -> permissions -> (later) patient and context rules
```

- A permission is one capability, not a module. Signing is never implied by writing; sending is never implied by signing; delivering a document is never implied by signing it (DEC-CLINIC-002).
- Roles are per tenant. The templates below are starting sets a practice picks when it creates a role; nothing here creates a role by itself.
- `Auto from` is the old coarse permission whose holders were given the new one automatically (V363). Nobody lost access. `never` means it is only ever granted on purpose.
- `Live` means at least one endpoint requires it today. `Reserved` names are in the catalogue so roles can be designed now; the endpoint that will use them does not exist yet or is not split yet.
- Context rules (care relationship, assigned practitioner, restricted record, break-glass) are the next layer and are not enforced yet. `CLINIC_PATIENT_ACCESS` is reserved for that.


## 1. Permission catalogue

### Patient registration

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_PATIENT_ACCESS` | read | Open a patient's record (context rules such as care relationship and restricted records apply) | `CLINIC_READ` | Reserved |
| `CLINIC_PATIENT_READ` | read | Search and view patients | `CLINIC_READ` | Live |
| `CLINIC_PATIENT_CREATE` | write | Register a patient | `CLINIC_WRITE` | Live |
| `CLINIC_PATIENT_UPDATE` | write | Edit a patient's registration details | `CLINIC_WRITE` | Live |
| `CLINIC_PATIENT_ARCHIVE` | manage | Archive a patient record | `CLINIC_ADMIN` | Reserved |
| `CLINIC_PATIENT_MERGE` | manage | Merge duplicate patient records | `CLINIC_ADMIN` | Reserved |
| `CLINIC_PATIENT_DUPLICATE_REVIEW` | write | Review and resolve possible duplicate patients | `CLINIC_WRITE` | Reserved |
| `CLINIC_PATIENT_DEMOGRAPHICS_READ` | read | View name, date of birth, sex and ID number | `CLINIC_READ` | Reserved |
| `CLINIC_PATIENT_DEMOGRAPHICS_WRITE` | write | Change name, date of birth, sex and ID number | `CLINIC_WRITE` | Reserved |
| `CLINIC_PATIENT_CONTACT_READ` | read | View phone and email | `CLINIC_READ` | Reserved |
| `CLINIC_PATIENT_CONTACT_WRITE` | write | Change phone and email | `CLINIC_WRITE` | Reserved |
| `CLINIC_PATIENT_ADDRESS_READ` | read | View addresses | `CLINIC_READ` | Reserved |
| `CLINIC_PATIENT_ADDRESS_WRITE` | write | Change addresses | `CLINIC_WRITE` | Reserved |
| `CLINIC_PATIENT_RELATIONSHIP_READ` | read | View family and guarantor relationships | `CLINIC_READ` | Live |
| `CLINIC_PATIENT_RELATIONSHIP_WRITE` | write | Change family and guarantor relationships | `CLINIC_WRITE` | Reserved |
| `CLINIC_PATIENT_EMERGENCY_CONTACT_READ` | read | View emergency contacts | `CLINIC_READ` | Reserved |
| `CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE` | write | Change emergency contacts | `CLINIC_WRITE` | Reserved |

### Clinical summary

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_CLINICAL_SUMMARY_READ` | read | See the clinical briefing on a patient overview (last visit, vitals, medicines, labs, alerts) | `CLINIC_READ` | Live |
| `CLINIC_ALLERGY_READ` | read | View allergies | `CLINIC_READ` | Live |
| `CLINIC_ALLERGY_WRITE` | write | Record and change allergies | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_CONDITION_READ` | read | View conditions | `CLINIC_READ` | Live |
| `CLINIC_CONDITION_WRITE` | write | Record and change conditions | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_MEDICATION_READ` | read | View the patient's current medicines | `CLINIC_READ` | Live |
| `CLINIC_MEDICATION_WRITE` | write | Record and change the patient's current medicines | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_CLINICAL_HISTORY_READ` | read | View clinical history | `CLINIC_READ` | Live |
| `CLINIC_CLINICAL_HISTORY_WRITE` | write | Record clinical history | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_VITALS_READ` | read | View vital signs and measurements | `CLINIC_READ` | Live |
| `CLINIC_VITALS_WRITE` | write | Record vital signs and measurements | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_VITALS_VOID` | write | Void a recorded measurement | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_CLINICAL_MATRIX_READ` | read | View the clinical matrix | `CLINIC_READ` | Reserved |
| `CLINIC_CLINICAL_MATRIX_WRITE` | write | Update the clinical matrix | `CLINIC_CLINICAL_WRITE` | Reserved |

### Notes

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_NOTE_READ` | read | Read patient notes | `CLINIC_READ` | Live |
| `CLINIC_NOTE_CREATE` | write | Add a patient note | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_NOTE_UPDATE` | write | Resolve or edit a patient note | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_NOTE_LOCK` | sign | Lock a note against further change | `CLINIC_CLINICAL_WRITE` | Reserved |
| `CLINIC_ADMIN_NOTE_READ` | read | Read administrative notes | `CLINIC_READ` | Reserved |
| `CLINIC_PRIVATE_NOTE_READ` | read | Read a clinician's private notes | `CLINIC_CLINICAL_SIGN` | Reserved |

### Scheduling, queue and operations

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_TASK_READ` | read | See clinic tasks (follow-ups, calls, results to chase) | `CLINIC_READ` | Live |
| `CLINIC_TASK_CREATE` | write | Create a clinic task for yourself or a colleague | `CLINIC_WRITE` | Live |
| `CLINIC_TASK_COMPLETE` | write | Complete or dismiss a clinic task | `CLINIC_WRITE` | Live |
| `CLINIC_QUEUE_READ` | read | View the handoff queue and today's queue | `CLINIC_READ` | Live |
| `CLINIC_APPOINTMENT_READ` | read | View appointments, rooms and the waitlist | `CLINIC_READ` | Live |
| `CLINIC_APPOINTMENT_CREATE` | write | Book an appointment or add to the waitlist | `CLINIC_WRITE` | Live |
| `CLINIC_APPOINTMENT_UPDATE` | write | Confirm, start, complete, no-show or cancel an appointment; send reminders; work the waitlist | `CLINIC_WRITE` | Live |
| `CLINIC_APPOINTMENT_CANCEL` | write | Cancel an appointment (split from update when the action endpoint is split) | `CLINIC_WRITE` | Reserved |
| `CLINIC_APPOINTMENT_RESCHEDULE` | write | Reschedule an appointment | `CLINIC_WRITE` | Live |
| `CLINIC_APPOINTMENT_CHECK_IN` | write | Check a patient in (split from update when the action endpoint is split) | `CLINIC_WRITE` | Reserved |
| `CLINIC_QUEUE_MANAGE` | write | Manage today's queue | `CLINIC_WRITE` | Reserved |
| `CLINIC_PRACTITIONER_READ` | read | View practitioners | `CLINIC_READ` | Live |
| `CLINIC_PRACTITIONER_MANAGE` | manage | Register a practitioner | `CLINIC_WRITE` | Live |
| `CLINIC_WORKING_HOURS_WRITE` | write | Set a practitioner's working hours | `CLINIC_WRITE` | Live |
| `CLINIC_TIME_OFF_WRITE` | write | Add or cancel practitioner time off | `CLINIC_WRITE` | Live |
| `CLINIC_CLOSURE_MANAGE` | manage | Create and cancel practice closures | `CLINIC_ADMIN` | Live |
| `CLINIC_ROOM_MANAGE` | manage | Create and edit rooms | `CLINIC_ADMIN` | Live |
| `CLINIC_TELEHEALTH_ROOM_CREATE` | write | Create a video room for an appointment | `CLINIC_READ` | Live |
| `CLINIC_RECALL_READ` | read | View the recall worklist | `CLINIC_READ` | Live |
| `CLINIC_RECALL_MANAGE` | write | Log a recall action (call, snooze, dismiss) | `CLINIC_WRITE` | Live |
| `CLINIC_DASHBOARD_READ` | read | View the clinic dashboard | `CLINIC_READ` | Live |
| `CLINIC_TIMELINE_READ` | read | View a patient timeline | `CLINIC_READ` | Live |
| `CLINIC_CATALOGUE_READ` | read | Search the medicine and procedure catalogues | `CLINIC_READ` | Live |
| `CLINIC_QUESTIONNAIRE_READ` | read | Open questionnaires and visit-type settings used in a consultation | `CLINIC_READ` | Live |
| `CLINIC_QUESTIONNAIRE_ANSWER` | write | Save questionnaire answers on a consultation | `CLINIC_CLINICAL_WRITE` | Live |

### Consultation

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_CONSULTATION_READ` | read | View consultations, drafts, edit history and summaries | `CLINIC_READ` | Live |
| `CLINIC_CONSULTATION_CREATE` | write | Start a consultation or draft | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_CONSULTATION_UPDATE` | write | Edit or abandon an unsigned consultation | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_CONSULTATION_SIGN` | sign | Sign a consultation | `CLINIC_CLINICAL_SIGN` | Live |
| `CLINIC_CONSULTATION_LOCK` | sign | Lock a signed consultation | `CLINIC_CLINICAL_SIGN` | Reserved |
| `CLINIC_CONSULTATION_AMEND` | write | Add an addendum to a signed consultation | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_CONSULTATION_OVERRIDE_REQUIRED_STEP` | sign | Sign with a required stage overridden (typed reason, audited) | `CLINIC_CLINICAL_SIGN` | Reserved |

### Nurse workflow and handoff

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_NURSE_INTAKE_CREATE` | write | Start nurse intake on a consultation | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_NURSE_INTAKE_UPDATE` | write | Resume or edit nurse intake | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_NURSE_HANDOFF` | write | Send a consultation to the doctor | `CLINIC_CLINICAL_WRITE` | Live |
| `CLINIC_NURSE_HANDOFF_ACCEPT` | sign | Accept a handoff as the doctor | `CLINIC_CLINICAL_SIGN` | Live |
| `CLINIC_NURSE_HANDOFF_RETURN` | sign | Return a handoff to the nurse | `CLINIC_CLINICAL_SIGN` | Live |
| `CLINIC_NURSE_HANDOFF_COMPLETE` | sign | Complete the doctor's review of a handoff | `CLINIC_CLINICAL_SIGN` | Live |

### Prescriptions

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_PRESCRIPTION_READ` | read | View prescriptions and fills | `CLINIC_READ` | Live |
| `CLINIC_PRESCRIPTION_CREATE` | write | Write a prescription (includes the allergy check) | `CLINIC_PRESCRIPTION_WRITE` | Live |
| `CLINIC_PRESCRIPTION_UPDATE` | write | Change a draft prescription | `CLINIC_PRESCRIPTION_WRITE` | Reserved |
| `CLINIC_PRESCRIPTION_SIGN` | sign | Sign a prescription | `CLINIC_PRESCRIPTION_WRITE` | Reserved |
| `CLINIC_PRESCRIPTION_VOID` | sign | Void a prescription | `CLINIC_PRESCRIPTION_WRITE` | Reserved |
| `CLINIC_PRESCRIPTION_DISPENSE` | write | Record a dispense / fill | `CLINIC_PRESCRIPTION_WRITE` | Live |

### Sick notes

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_SICK_NOTE_READ` | read | View sick notes | `CLINIC_READ` | Reserved |
| `CLINIC_SICK_NOTE_CREATE` | write | Create a sick note draft | `CLINIC_CLINICAL_WRITE` | Reserved |
| `CLINIC_SICK_NOTE_UPDATE_DRAFT` | write | Edit a draft sick note | `CLINIC_CLINICAL_WRITE` | Reserved |
| `CLINIC_SICK_NOTE_SIGN` | sign | Issue (sign) a medical certificate | `CLINIC_CLINICAL_SIGN` | Live |
| `CLINIC_SICK_NOTE_VOID` | sign | Void a sick note | `CLINIC_CLINICAL_SIGN` | Reserved |
| `CLINIC_SICK_NOTE_DOWNLOAD` | read | Download a sick note | `CLINIC_READ` | Reserved |
| `CLINIC_SICK_NOTE_PRINT` | read | Print a sick note | `CLINIC_READ` | Reserved |

### Referrals

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_REFERRAL_READ` | read | View referrals | `CLINIC_READ` | Reserved |
| `CLINIC_REFERRAL_CREATE` | write | Create a referral draft | `CLINIC_CLINICAL_WRITE` | Reserved |
| `CLINIC_REFERRAL_UPDATE` | write | Edit a draft referral | `CLINIC_CLINICAL_WRITE` | Reserved |
| `CLINIC_REFERRAL_SIGN` | sign | Issue (sign) a referral letter | `CLINIC_CLINICAL_SIGN` | Live |
| `CLINIC_REFERRAL_VOID` | sign | Void a referral | `CLINIC_CLINICAL_SIGN` | Reserved |
| `CLINIC_REFERRAL_SEND` | send | Send a referral (separate from signing) | `CLINIC_CLINICAL_SIGN` | Reserved |

### Growth

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_GROWTH_READ` | read | View growth measurements and charts | `CLINIC_READ` | Live |
| `CLINIC_GROWTH_RECORD` | write | Record a growth measurement | `CLINIC_CLINICAL_WRITE` | Reserved |
| `CLINIC_GROWTH_UPDATE` | write | Correct a growth measurement | `CLINIC_CLINICAL_WRITE` | Reserved |
| `CLINIC_GROWTH_INTERPRET` | sign | Record a clinical interpretation of growth | `CLINIC_CLINICAL_SIGN` | Reserved |
| `CLINIC_GROWTH_EXPORT` | read | Export or print a growth chart | `CLINIC_READ` | Reserved |

### Results

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_RESULT_READ` | read | View lab results and critical flags | `CLINIC_READ` | Live |
| `CLINIC_RESULT_CREATE` | write | Upload a lab result | `CLINIC_LAB_WRITE` | Live |
| `CLINIC_RESULT_UPDATE` | write | Correct result markers | `CLINIC_LAB_WRITE` | Live |
| `CLINIC_RESULT_MATCH` | write | Match a result to a patient | `CLINIC_LAB_WRITE` | Live |
| `CLINIC_RESULT_INTERPRET` | write | Interpret a result (including AI assistance) | `CLINIC_LAB_WRITE` | Live |
| `CLINIC_RESULT_REVIEW` | sign | Mark a result reviewed | `CLINIC_LAB_WRITE` | Live |
| `CLINIC_RESULT_FILE` | write | File a result to the patient record | `CLINIC_LAB_WRITE` | Live |
| `CLINIC_RESULT_ACKNOWLEDGE` | sign | Acknowledge a result in the results inbox | `CLINIC_LAB_WRITE` | Reserved |

### Documents

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_DOCUMENT_READ` | read | View a document list | `CLINIC_READ` | Live |
| `CLINIC_DOCUMENT_CREATE` | write | Create a document | `CLINIC_WRITE` | Live |
| `CLINIC_DOCUMENT_DOWNLOAD` | read | Download a document | `CLINIC_READ` | Live |
| `CLINIC_DOCUMENT_PRINT` | read | Print a document | `CLINIC_READ` | Reserved |
| `CLINIC_DOCUMENT_EMAIL` | send | Email a document to a patient (delivery is separate from signing) | `CLINIC_BILLING_WRITE` | Live |
| `CLINIC_DOCUMENT_VOID` | manage | Void a document | `CLINIC_ADMIN` | Live |

### Consent

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_CONSENT_READ` | read | View consent status | `CLINIC_READ` | Live |
| `CLINIC_CONSENT_HISTORY_READ` | read | View consent history | `CLINIC_READ` | Live |
| `CLINIC_CONSENT_RECORD` | write | Record consent | `CLINIC_WRITE` | Live |
| `CLINIC_CONSENT_UPDATE` | write | Change a consent | `CLINIC_WRITE` | Reserved |
| `CLINIC_CONSENT_REVOKE` | write | Revoke a consent | `CLINIC_WRITE` | Reserved |

### Restricted records and break-glass

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_RESTRICTED_RECORD_MANAGE` | manage | Flag a record or document as restricted | **never** | Live |
| `CLINIC_RESTRICTED_RECORD_REQUEST` | write | Ask for access to a restricted record | **never** | Reserved |
| `CLINIC_RESTRICTED_RECORD_ACCESS` | read | Open a restricted record once access is granted | **never** | Live |
| `CLINIC_BREAK_GLASS_VIEW` | read | Break the glass to view a restricted record (reason required, audited, alerts the practice manager) | **never** | Live |
| `CLINIC_BREAK_GLASS_PRINT` | read | Print or download a document from a restricted record opened by break-glass | **never** | Live |
| `CLINIC_BREAK_GLASS_REVIEW` | manage | Review and acknowledge break-glass alerts (practice manager / compliance) | **never** | Live |
| `CLINIC_BREAK_GLASS_EXPORT` | read | Export a restricted record opened by break-glass | **never** | Live |

### Billing, claims and payments

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_BILL_READ` | read | View bills, statements, outstanding balances and revenue | `CLINIC_BILLING_READ` | Live |
| `CLINIC_BILL_CREATE` | write | Create a bill | `CLINIC_BILLING_WRITE` | Reserved |
| `CLINIC_BILL_ADJUST` | manage | Adjust a bill (credit, discount); never granted automatically | **never** | Reserved |
| `CLINIC_PAYMENT_READ` | read | View payments | `CLINIC_BILLING_READ` | Live |
| `CLINIC_PAYMENT_CREATE` | write | Record a payment | `CLINIC_BILLING_WRITE` | Live |
| `CLINIC_PAYMENT_ALLOCATE` | manage | Allocate a scheme payment across claims, including manual override (CLINIC-DEC-006) | **never** | Live |
| `CLINIC_PAYMENT_REVERSE` | manage | Reverse a payment | **never** | Reserved |
| `CLINIC_WRITE_OFF` | manage | Write off a shortfall as a controlled transaction (CLINIC-DEC-001) | **never** | Live |
| `CLINIC_CLAIM_READ` | read | View claims and claim documents | `CLINIC_BILLING_READ` | Live |
| `CLINIC_CLAIM_CREATE` | write | Create a claim from a consultation | `CLINIC_BILLING_WRITE` | Live |
| `CLINIC_CLAIM_SUBMIT` | write | Submit claims | `CLINIC_BILLING_WRITE` | Live |
| `CLINIC_CLAIM_PROGRESS` | write | Move a claim through accept, reject, pay (to be split by the claims-money work) | `CLINIC_BILLING_WRITE` | Live |
| `CLINIC_CLAIM_CORRECT` | manage | Correct a submitted claim through the correction workflow (CLINIC-DEC-004) | **never** | Reserved |
| `CLINIC_CLAIM_REVERSE` | manage | Reverse or credit a claim (CLINIC-DEC-002, 003) | **never** | Live |
| `CLINIC_CLAIM_RESUBMIT` | write | Resubmit a corrected claim | **never** | Reserved |

### Clinical content

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_CONTENT_ADMIN` | manage | Author question groups and visit-type settings | (existing) | Live |
| `CLINIC_CONTENT_APPROVE` | sign | Approve clinical content (qualified reviewer, DEC-019) | (existing) | Live |

### Audit

| Permission | Kind | What it allows | Auto from | State |
|---|---|---|---|---|
| `CLINIC_ACCESS_LOG_READ` | read | View the clinic access log | `CLINIC_ADMIN` | Live |

### The coarse permissions that remain

Still granted to the same roles and still meaningful as bundles, but **no endpoint requires them any more** (except `CLINIC_ADMIN` for the access catalogue screen).

| Permission | Meaning |
|---|---|
| `CLINIC_READ` | View patients, appointments, consultations, lab results |
| `CLINIC_WRITE` | Front desk: register patients, book appointments |
| `CLINIC_CLINICAL_WRITE` | Nurse and doctor: record consultations, vitals, allergies, conditions, medicines |
| `CLINIC_CLINICAL_SIGN` | Doctor: accept handoffs, sign, certificates, referrals |
| `CLINIC_PRESCRIPTION_WRITE` | Issue prescriptions |
| `CLINIC_LAB_WRITE` | Upload, interpret, review and file lab results |
| `CLINIC_BILLING_READ` | View claims and billing |
| `CLINIC_BILLING_WRITE` | Create and submit claims, record payments |
| `CLINIC_ADMIN` | Manage practitioners and clinic settings |

## 2. Role templates

Cell = what the template holds in that area: **R** read only, **C** read and change, **A** also signs or approves, **S** also sends or manages, **-** nothing.

| Area | Doctor | Clin. assoc. | Prof. nurse | Enrolled nurse | Reception | Billing | Pharmacy | Lab | Records | Auditor | Practice mgr | Clinic admin |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Patient registration | C | C | C | R | C | R | R | R | R | R | C | S |
| Clinical summary | C | C | C | C | - | - | R | - | - | - | - | - |
| Notes | A | C | C | R | - | - | - | - | - | - | - | - |
| Scheduling, queue and operations | C | C | C | C | C | R | R | R | R | R | S | S |
| Consultation | A | A | C | R | - | - | - | - | - | - | - | - |
| Nurse workflow and handoff | A | A | C | C | - | - | - | - | - | - | - | - |
| Prescriptions | A | C | R | - | - | - | C | - | - | - | - | - |
| Sick notes | A | C | R | - | - | - | - | - | - | - | - | - |
| Referrals | A | C | R | - | - | - | - | - | - | - | - | - |
| Growth | A | A | C | C | - | - | - | - | - | - | - | - |
| Results | A | A | C | R | - | - | - | C | - | - | - | - |
| Documents | S | S | R | R | R | S | - | - | C | - | S | S |
| Consent | C | C | C | - | C | - | - | - | - | - | C | C |
| Restricted records and break-glass | C | C | - | - | - | - | - | - | - | S | S | S |
| Billing, claims and payments | R | R | - | - | - | S | - | - | - | - | S | S |
| Audit | - | - | - | - | - | - | - | - | - | R | R | R |

The Practice Manager and Clinic Administrator hold **no** clinical-note, consultation, result or prescription permissions by default. Read-only oversight of clinical content is a tenant governance choice (the `R*` in the proposal), so it is added deliberately, not inherited from "manager".

### What each template holds

<details><summary><b>Medical Practitioner / Doctor</b> (108 permissions)</summary>

Full clinical authority within their scope; signs consultations, prescriptions, sick notes and referrals.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_CREATE`, `CLINIC_PATIENT_UPDATE`, `CLINIC_PATIENT_DUPLICATE_REVIEW`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_PATIENT_DEMOGRAPHICS_WRITE`, `CLINIC_PATIENT_CONTACT_READ`, `CLINIC_PATIENT_CONTACT_WRITE`, `CLINIC_PATIENT_ADDRESS_READ`, `CLINIC_PATIENT_ADDRESS_WRITE`, `CLINIC_PATIENT_RELATIONSHIP_READ`, `CLINIC_PATIENT_RELATIONSHIP_WRITE`, `CLINIC_PATIENT_EMERGENCY_CONTACT_READ`, `CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE`, `CLINIC_CLINICAL_SUMMARY_READ`, `CLINIC_ALLERGY_READ`, `CLINIC_ALLERGY_WRITE`, `CLINIC_CONDITION_READ`, `CLINIC_CONDITION_WRITE`, `CLINIC_MEDICATION_READ`, `CLINIC_MEDICATION_WRITE`, `CLINIC_CLINICAL_HISTORY_READ`, `CLINIC_CLINICAL_HISTORY_WRITE`, `CLINIC_VITALS_READ`, `CLINIC_VITALS_WRITE`, `CLINIC_VITALS_VOID`, `CLINIC_CLINICAL_MATRIX_READ`, `CLINIC_CLINICAL_MATRIX_WRITE`, `CLINIC_NOTE_READ`, `CLINIC_NOTE_CREATE`, `CLINIC_NOTE_UPDATE`, `CLINIC_NOTE_LOCK`, `CLINIC_ADMIN_NOTE_READ`, `CLINIC_PRIVATE_NOTE_READ`, `CLINIC_CONSULTATION_READ`, `CLINIC_CONSULTATION_CREATE`, `CLINIC_CONSULTATION_UPDATE`, `CLINIC_CONSULTATION_SIGN`, `CLINIC_CONSULTATION_LOCK`, `CLINIC_CONSULTATION_AMEND`, `CLINIC_CONSULTATION_OVERRIDE_REQUIRED_STEP`, `CLINIC_NURSE_INTAKE_CREATE`, `CLINIC_NURSE_INTAKE_UPDATE`, `CLINIC_NURSE_HANDOFF`, `CLINIC_NURSE_HANDOFF_ACCEPT`, `CLINIC_NURSE_HANDOFF_RETURN`, `CLINIC_NURSE_HANDOFF_COMPLETE`, `CLINIC_PRESCRIPTION_READ`, `CLINIC_PRESCRIPTION_CREATE`, `CLINIC_PRESCRIPTION_UPDATE`, `CLINIC_PRESCRIPTION_SIGN`, `CLINIC_PRESCRIPTION_VOID`, `CLINIC_PRESCRIPTION_DISPENSE`, `CLINIC_SICK_NOTE_READ`, `CLINIC_SICK_NOTE_CREATE`, `CLINIC_SICK_NOTE_UPDATE_DRAFT`, `CLINIC_SICK_NOTE_SIGN`, `CLINIC_SICK_NOTE_VOID`, `CLINIC_SICK_NOTE_DOWNLOAD`, `CLINIC_SICK_NOTE_PRINT`, `CLINIC_REFERRAL_READ`, `CLINIC_REFERRAL_CREATE`, `CLINIC_REFERRAL_UPDATE`, `CLINIC_REFERRAL_SIGN`, `CLINIC_REFERRAL_VOID`, `CLINIC_REFERRAL_SEND`, `CLINIC_GROWTH_READ`, `CLINIC_GROWTH_RECORD`, `CLINIC_GROWTH_UPDATE`, `CLINIC_GROWTH_INTERPRET`, `CLINIC_GROWTH_EXPORT`, `CLINIC_RESULT_READ`, `CLINIC_RESULT_CREATE`, `CLINIC_RESULT_UPDATE`, `CLINIC_RESULT_MATCH`, `CLINIC_RESULT_INTERPRET`, `CLINIC_RESULT_REVIEW`, `CLINIC_RESULT_FILE`, `CLINIC_RESULT_ACKNOWLEDGE`, `CLINIC_DOCUMENT_READ`, `CLINIC_DOCUMENT_CREATE`, `CLINIC_DOCUMENT_DOWNLOAD`, `CLINIC_DOCUMENT_PRINT`, `CLINIC_DOCUMENT_EMAIL`, `CLINIC_CONSENT_READ`, `CLINIC_CONSENT_HISTORY_READ`, `CLINIC_CONSENT_RECORD`, `CLINIC_CONSENT_UPDATE`, `CLINIC_APPOINTMENT_READ`, `CLINIC_APPOINTMENT_CHECK_IN`, `CLINIC_PRACTITIONER_READ`, `CLINIC_RECALL_READ`, `CLINIC_RECALL_MANAGE`, `CLINIC_TIMELINE_READ`, `CLINIC_QUESTIONNAIRE_READ`, `CLINIC_QUESTIONNAIRE_ANSWER`, `CLINIC_TELEHEALTH_ROOM_CREATE`, `CLINIC_BILL_READ`, `CLINIC_PAYMENT_READ`, `CLINIC_CLAIM_READ`, `CLINIC_RESTRICTED_RECORD_REQUEST`, `CLINIC_BREAK_GLASS_VIEW`, `CLINIC_TASK_READ`, `CLINIC_TASK_CREATE`, `CLINIC_TASK_COMPLETE`

</details>

<details><summary><b>Clinical Associate</b> (98 permissions)</summary>

Clinical practitioner; signing of prescriptions, sick notes and referrals depends on the tenant's scope configuration and is off by default.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_CREATE`, `CLINIC_PATIENT_UPDATE`, `CLINIC_PATIENT_DUPLICATE_REVIEW`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_PATIENT_DEMOGRAPHICS_WRITE`, `CLINIC_PATIENT_CONTACT_READ`, `CLINIC_PATIENT_CONTACT_WRITE`, `CLINIC_PATIENT_ADDRESS_READ`, `CLINIC_PATIENT_ADDRESS_WRITE`, `CLINIC_PATIENT_RELATIONSHIP_READ`, `CLINIC_PATIENT_RELATIONSHIP_WRITE`, `CLINIC_PATIENT_EMERGENCY_CONTACT_READ`, `CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE`, `CLINIC_CLINICAL_SUMMARY_READ`, `CLINIC_ALLERGY_READ`, `CLINIC_ALLERGY_WRITE`, `CLINIC_CONDITION_READ`, `CLINIC_CONDITION_WRITE`, `CLINIC_MEDICATION_READ`, `CLINIC_MEDICATION_WRITE`, `CLINIC_CLINICAL_HISTORY_READ`, `CLINIC_CLINICAL_HISTORY_WRITE`, `CLINIC_VITALS_READ`, `CLINIC_VITALS_WRITE`, `CLINIC_VITALS_VOID`, `CLINIC_CLINICAL_MATRIX_READ`, `CLINIC_CLINICAL_MATRIX_WRITE`, `CLINIC_NOTE_READ`, `CLINIC_NOTE_CREATE`, `CLINIC_NOTE_UPDATE`, `CLINIC_ADMIN_NOTE_READ`, `CLINIC_CONSULTATION_READ`, `CLINIC_CONSULTATION_CREATE`, `CLINIC_CONSULTATION_UPDATE`, `CLINIC_CONSULTATION_SIGN`, `CLINIC_CONSULTATION_AMEND`, `CLINIC_CONSULTATION_OVERRIDE_REQUIRED_STEP`, `CLINIC_NURSE_INTAKE_CREATE`, `CLINIC_NURSE_INTAKE_UPDATE`, `CLINIC_NURSE_HANDOFF`, `CLINIC_NURSE_HANDOFF_ACCEPT`, `CLINIC_NURSE_HANDOFF_RETURN`, `CLINIC_NURSE_HANDOFF_COMPLETE`, `CLINIC_PRESCRIPTION_READ`, `CLINIC_PRESCRIPTION_CREATE`, `CLINIC_PRESCRIPTION_UPDATE`, `CLINIC_PRESCRIPTION_DISPENSE`, `CLINIC_SICK_NOTE_READ`, `CLINIC_SICK_NOTE_CREATE`, `CLINIC_SICK_NOTE_UPDATE_DRAFT`, `CLINIC_SICK_NOTE_DOWNLOAD`, `CLINIC_SICK_NOTE_PRINT`, `CLINIC_REFERRAL_READ`, `CLINIC_REFERRAL_CREATE`, `CLINIC_REFERRAL_UPDATE`, `CLINIC_GROWTH_READ`, `CLINIC_GROWTH_RECORD`, `CLINIC_GROWTH_UPDATE`, `CLINIC_GROWTH_INTERPRET`, `CLINIC_GROWTH_EXPORT`, `CLINIC_RESULT_READ`, `CLINIC_RESULT_CREATE`, `CLINIC_RESULT_UPDATE`, `CLINIC_RESULT_MATCH`, `CLINIC_RESULT_INTERPRET`, `CLINIC_RESULT_REVIEW`, `CLINIC_RESULT_FILE`, `CLINIC_RESULT_ACKNOWLEDGE`, `CLINIC_DOCUMENT_READ`, `CLINIC_DOCUMENT_CREATE`, `CLINIC_DOCUMENT_DOWNLOAD`, `CLINIC_DOCUMENT_PRINT`, `CLINIC_DOCUMENT_EMAIL`, `CLINIC_CONSENT_READ`, `CLINIC_CONSENT_HISTORY_READ`, `CLINIC_CONSENT_RECORD`, `CLINIC_CONSENT_UPDATE`, `CLINIC_APPOINTMENT_READ`, `CLINIC_APPOINTMENT_CHECK_IN`, `CLINIC_PRACTITIONER_READ`, `CLINIC_RECALL_READ`, `CLINIC_RECALL_MANAGE`, `CLINIC_TIMELINE_READ`, `CLINIC_QUESTIONNAIRE_READ`, `CLINIC_QUESTIONNAIRE_ANSWER`, `CLINIC_TELEHEALTH_ROOM_CREATE`, `CLINIC_BILL_READ`, `CLINIC_PAYMENT_READ`, `CLINIC_CLAIM_READ`, `CLINIC_RESTRICTED_RECORD_REQUEST`, `CLINIC_BREAK_GLASS_VIEW`, `CLINIC_TASK_READ`, `CLINIC_TASK_CREATE`, `CLINIC_TASK_COMPLETE`

</details>

<details><summary><b>Professional Nurse</b> (65 permissions)</summary>

Nursing assessment, observations and the nurse side of the handoff; cannot sign.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_CREATE`, `CLINIC_PATIENT_UPDATE`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_PATIENT_DEMOGRAPHICS_WRITE`, `CLINIC_PATIENT_CONTACT_READ`, `CLINIC_PATIENT_CONTACT_WRITE`, `CLINIC_PATIENT_ADDRESS_READ`, `CLINIC_PATIENT_ADDRESS_WRITE`, `CLINIC_PATIENT_RELATIONSHIP_READ`, `CLINIC_PATIENT_RELATIONSHIP_WRITE`, `CLINIC_PATIENT_EMERGENCY_CONTACT_READ`, `CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE`, `CLINIC_CLINICAL_SUMMARY_READ`, `CLINIC_ALLERGY_READ`, `CLINIC_ALLERGY_WRITE`, `CLINIC_CONDITION_READ`, `CLINIC_CONDITION_WRITE`, `CLINIC_MEDICATION_READ`, `CLINIC_MEDICATION_WRITE`, `CLINIC_CLINICAL_HISTORY_READ`, `CLINIC_CLINICAL_HISTORY_WRITE`, `CLINIC_VITALS_READ`, `CLINIC_VITALS_WRITE`, `CLINIC_VITALS_VOID`, `CLINIC_CLINICAL_MATRIX_READ`, `CLINIC_CLINICAL_MATRIX_WRITE`, `CLINIC_NOTE_READ`, `CLINIC_NOTE_CREATE`, `CLINIC_NOTE_UPDATE`, `CLINIC_CONSULTATION_READ`, `CLINIC_CONSULTATION_CREATE`, `CLINIC_CONSULTATION_UPDATE`, `CLINIC_NURSE_INTAKE_CREATE`, `CLINIC_NURSE_INTAKE_UPDATE`, `CLINIC_NURSE_HANDOFF`, `CLINIC_QUEUE_READ`, `CLINIC_QUEUE_MANAGE`, `CLINIC_GROWTH_READ`, `CLINIC_GROWTH_RECORD`, `CLINIC_GROWTH_UPDATE`, `CLINIC_GROWTH_EXPORT`, `CLINIC_PRESCRIPTION_READ`, `CLINIC_SICK_NOTE_READ`, `CLINIC_REFERRAL_READ`, `CLINIC_RESULT_READ`, `CLINIC_RESULT_CREATE`, `CLINIC_RESULT_MATCH`, `CLINIC_RESULT_UPDATE`, `CLINIC_DOCUMENT_READ`, `CLINIC_CONSENT_READ`, `CLINIC_CONSENT_HISTORY_READ`, `CLINIC_CONSENT_RECORD`, `CLINIC_APPOINTMENT_READ`, `CLINIC_PRACTITIONER_READ`, `CLINIC_TIMELINE_READ`, `CLINIC_QUESTIONNAIRE_READ`, `CLINIC_QUESTIONNAIRE_ANSWER`, `CLINIC_RECALL_READ`, `CLINIC_TASK_READ`, `CLINIC_TASK_CREATE`, `CLINIC_TASK_COMPLETE`

</details>

<details><summary><b>Enrolled / Staff Nurse</b> (31 permissions)</summary>

More restricted clinical capture: vitals, growth measurements and intake; reads allergies, conditions and notes.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_PATIENT_CONTACT_READ`, `CLINIC_PATIENT_EMERGENCY_CONTACT_READ`, `CLINIC_PATIENT_RELATIONSHIP_READ`, `CLINIC_CLINICAL_SUMMARY_READ`, `CLINIC_ALLERGY_READ`, `CLINIC_CONDITION_READ`, `CLINIC_MEDICATION_READ`, `CLINIC_VITALS_READ`, `CLINIC_VITALS_WRITE`, `CLINIC_NOTE_READ`, `CLINIC_CONSULTATION_READ`, `CLINIC_NURSE_INTAKE_CREATE`, `CLINIC_NURSE_INTAKE_UPDATE`, `CLINIC_NURSE_HANDOFF`, `CLINIC_QUEUE_READ`, `CLINIC_GROWTH_READ`, `CLINIC_GROWTH_RECORD`, `CLINIC_RESULT_READ`, `CLINIC_APPOINTMENT_READ`, `CLINIC_PRACTITIONER_READ`, `CLINIC_QUESTIONNAIRE_READ`, `CLINIC_QUESTIONNAIRE_ANSWER`, `CLINIC_DOCUMENT_READ`, `CLINIC_TASK_READ`, `CLINIC_TASK_CREATE`, `CLINIC_TASK_COMPLETE`

</details>

<details><summary><b>Reception / Front Desk</b> (34 permissions)</summary>

Registration, appointments, queue, demographics and recalls. No clinical content.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_CREATE`, `CLINIC_PATIENT_UPDATE`, `CLINIC_PATIENT_DUPLICATE_REVIEW`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_PATIENT_DEMOGRAPHICS_WRITE`, `CLINIC_PATIENT_CONTACT_READ`, `CLINIC_PATIENT_CONTACT_WRITE`, `CLINIC_PATIENT_ADDRESS_READ`, `CLINIC_PATIENT_ADDRESS_WRITE`, `CLINIC_PATIENT_RELATIONSHIP_READ`, `CLINIC_PATIENT_RELATIONSHIP_WRITE`, `CLINIC_PATIENT_EMERGENCY_CONTACT_READ`, `CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE`, `CLINIC_APPOINTMENT_READ`, `CLINIC_APPOINTMENT_CREATE`, `CLINIC_APPOINTMENT_UPDATE`, `CLINIC_APPOINTMENT_CANCEL`, `CLINIC_APPOINTMENT_RESCHEDULE`, `CLINIC_APPOINTMENT_CHECK_IN`, `CLINIC_QUEUE_MANAGE`, `CLINIC_QUEUE_READ`, `CLINIC_PRACTITIONER_READ`, `CLINIC_RECALL_READ`, `CLINIC_RECALL_MANAGE`, `CLINIC_CONSENT_READ`, `CLINIC_CONSENT_RECORD`, `CLINIC_DOCUMENT_READ`, `CLINIC_TASK_READ`, `CLINIC_TASK_CREATE`, `CLINIC_TASK_COMPLETE`

</details>

<details><summary><b>Billing / Claims Officer</b> (24 permissions)</summary>

Claims, bills and payments. Reads patient identity only.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_PATIENT_CONTACT_READ`, `CLINIC_PATIENT_RELATIONSHIP_READ`, `CLINIC_BILL_READ`, `CLINIC_BILL_CREATE`, `CLINIC_PAYMENT_READ`, `CLINIC_PAYMENT_CREATE`, `CLINIC_PAYMENT_ALLOCATE`, `CLINIC_CLAIM_READ`, `CLINIC_CLAIM_CREATE`, `CLINIC_CLAIM_SUBMIT`, `CLINIC_CLAIM_PROGRESS`, `CLINIC_CLAIM_CORRECT`, `CLINIC_CLAIM_RESUBMIT`, `CLINIC_DOCUMENT_READ`, `CLINIC_DOCUMENT_DOWNLOAD`, `CLINIC_DOCUMENT_PRINT`, `CLINIC_DOCUMENT_EMAIL`, `CLINIC_APPOINTMENT_READ`, `CLINIC_TIMELINE_READ`

</details>

<details><summary><b>Pharmacy / Dispensary</b> (9 permissions)</summary>

Reads prescriptions and allergies and records dispensing.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_ALLERGY_READ`, `CLINIC_MEDICATION_READ`, `CLINIC_PRESCRIPTION_READ`, `CLINIC_PRESCRIPTION_DISPENSE`

</details>

<details><summary><b>Lab / Diagnostics</b> (9 permissions)</summary>

Receives, matches and updates results. Does not review or interpret.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_RESULT_READ`, `CLINIC_RESULT_CREATE`, `CLINIC_RESULT_MATCH`, `CLINIC_RESULT_UPDATE`

</details>

<details><summary><b>Practice Support / Records Clerk</b> (10 permissions)</summary>

Documents and records handling without clinical access.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_PATIENT_CONTACT_READ`, `CLINIC_DOCUMENT_READ`, `CLINIC_DOCUMENT_CREATE`, `CLINIC_DOCUMENT_DOWNLOAD`, `CLINIC_DOCUMENT_PRINT`

</details>

<details><summary><b>Tenant Auditor / Compliance</b> (6 permissions)</summary>

Audit and access-log review; no clinical modification and no clinical content.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_ACCESS_LOG_READ`, `CLINIC_BREAK_GLASS_REVIEW`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`

</details>

<details><summary><b>Practice Manager</b> (65 permissions)</summary>

Operations: front desk, scheduling, billing and documents. Clinical-note access is read-only and only where the tenant's governance allows it, so none is granted by default.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_CREATE`, `CLINIC_PATIENT_UPDATE`, `CLINIC_PATIENT_DUPLICATE_REVIEW`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_PATIENT_DEMOGRAPHICS_WRITE`, `CLINIC_PATIENT_CONTACT_READ`, `CLINIC_PATIENT_CONTACT_WRITE`, `CLINIC_PATIENT_ADDRESS_READ`, `CLINIC_PATIENT_ADDRESS_WRITE`, `CLINIC_PATIENT_RELATIONSHIP_READ`, `CLINIC_PATIENT_RELATIONSHIP_WRITE`, `CLINIC_PATIENT_EMERGENCY_CONTACT_READ`, `CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE`, `CLINIC_TASK_READ`, `CLINIC_TASK_CREATE`, `CLINIC_TASK_COMPLETE`, `CLINIC_QUEUE_READ`, `CLINIC_APPOINTMENT_READ`, `CLINIC_APPOINTMENT_CREATE`, `CLINIC_APPOINTMENT_UPDATE`, `CLINIC_APPOINTMENT_CANCEL`, `CLINIC_APPOINTMENT_RESCHEDULE`, `CLINIC_APPOINTMENT_CHECK_IN`, `CLINIC_QUEUE_MANAGE`, `CLINIC_PRACTITIONER_READ`, `CLINIC_PRACTITIONER_MANAGE`, `CLINIC_WORKING_HOURS_WRITE`, `CLINIC_TIME_OFF_WRITE`, `CLINIC_CLOSURE_MANAGE`, `CLINIC_ROOM_MANAGE`, `CLINIC_TELEHEALTH_ROOM_CREATE`, `CLINIC_RECALL_READ`, `CLINIC_RECALL_MANAGE`, `CLINIC_TIMELINE_READ`, `CLINIC_QUESTIONNAIRE_READ`, `CLINIC_QUESTIONNAIRE_ANSWER`, `CLINIC_BILL_READ`, `CLINIC_BILL_CREATE`, `CLINIC_BILL_ADJUST`, `CLINIC_PAYMENT_READ`, `CLINIC_PAYMENT_CREATE`, `CLINIC_PAYMENT_ALLOCATE`, `CLINIC_CLAIM_READ`, `CLINIC_CLAIM_CREATE`, `CLINIC_CLAIM_SUBMIT`, `CLINIC_CLAIM_PROGRESS`, `CLINIC_CLAIM_CORRECT`, `CLINIC_CLAIM_RESUBMIT`, `CLINIC_DOCUMENT_READ`, `CLINIC_DOCUMENT_CREATE`, `CLINIC_DOCUMENT_DOWNLOAD`, `CLINIC_DOCUMENT_PRINT`, `CLINIC_DOCUMENT_EMAIL`, `CLINIC_DOCUMENT_VOID`, `CLINIC_CONSENT_READ`, `CLINIC_CONSENT_HISTORY_READ`, `CLINIC_CONSENT_RECORD`, `CLINIC_CONSENT_UPDATE`, `CLINIC_CONSENT_REVOKE`, `CLINIC_ACCESS_LOG_READ`, `CLINIC_BREAK_GLASS_REVIEW`

</details>

<details><summary><b>Clinic Administrator</b> (71 permissions)</summary>

Practice configuration; everything the Practice Manager has, plus restricted-record flags, content authoring and archive/merge.

`CLINIC_DASHBOARD_READ`, `CLINIC_CATALOGUE_READ`, `CLINIC_PATIENT_ACCESS`, `CLINIC_PATIENT_READ`, `CLINIC_PATIENT_CREATE`, `CLINIC_PATIENT_UPDATE`, `CLINIC_PATIENT_DUPLICATE_REVIEW`, `CLINIC_PATIENT_DEMOGRAPHICS_READ`, `CLINIC_PATIENT_DEMOGRAPHICS_WRITE`, `CLINIC_PATIENT_CONTACT_READ`, `CLINIC_PATIENT_CONTACT_WRITE`, `CLINIC_PATIENT_ADDRESS_READ`, `CLINIC_PATIENT_ADDRESS_WRITE`, `CLINIC_PATIENT_RELATIONSHIP_READ`, `CLINIC_PATIENT_RELATIONSHIP_WRITE`, `CLINIC_PATIENT_EMERGENCY_CONTACT_READ`, `CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE`, `CLINIC_TASK_READ`, `CLINIC_TASK_CREATE`, `CLINIC_TASK_COMPLETE`, `CLINIC_QUEUE_READ`, `CLINIC_APPOINTMENT_READ`, `CLINIC_APPOINTMENT_CREATE`, `CLINIC_APPOINTMENT_UPDATE`, `CLINIC_APPOINTMENT_CANCEL`, `CLINIC_APPOINTMENT_RESCHEDULE`, `CLINIC_APPOINTMENT_CHECK_IN`, `CLINIC_QUEUE_MANAGE`, `CLINIC_PRACTITIONER_READ`, `CLINIC_PRACTITIONER_MANAGE`, `CLINIC_WORKING_HOURS_WRITE`, `CLINIC_TIME_OFF_WRITE`, `CLINIC_CLOSURE_MANAGE`, `CLINIC_ROOM_MANAGE`, `CLINIC_TELEHEALTH_ROOM_CREATE`, `CLINIC_RECALL_READ`, `CLINIC_RECALL_MANAGE`, `CLINIC_TIMELINE_READ`, `CLINIC_QUESTIONNAIRE_READ`, `CLINIC_QUESTIONNAIRE_ANSWER`, `CLINIC_BILL_READ`, `CLINIC_BILL_CREATE`, `CLINIC_BILL_ADJUST`, `CLINIC_PAYMENT_READ`, `CLINIC_PAYMENT_CREATE`, `CLINIC_PAYMENT_ALLOCATE`, `CLINIC_CLAIM_READ`, `CLINIC_CLAIM_CREATE`, `CLINIC_CLAIM_SUBMIT`, `CLINIC_CLAIM_PROGRESS`, `CLINIC_CLAIM_CORRECT`, `CLINIC_CLAIM_RESUBMIT`, `CLINIC_DOCUMENT_READ`, `CLINIC_DOCUMENT_CREATE`, `CLINIC_DOCUMENT_DOWNLOAD`, `CLINIC_DOCUMENT_PRINT`, `CLINIC_DOCUMENT_EMAIL`, `CLINIC_DOCUMENT_VOID`, `CLINIC_CONSENT_READ`, `CLINIC_CONSENT_HISTORY_READ`, `CLINIC_CONSENT_RECORD`, `CLINIC_CONSENT_UPDATE`, `CLINIC_CONSENT_REVOKE`, `CLINIC_ACCESS_LOG_READ`, `CLINIC_BREAK_GLASS_REVIEW`, `CLINIC_PATIENT_ARCHIVE`, `CLINIC_PATIENT_MERGE`, `CLINIC_RESTRICTED_RECORD_MANAGE`, `CLINIC_CONTENT_ADMIN`, `CLINIC_WRITE_OFF`, `CLINIC_CLAIM_REVERSE`

</details>

## 3. API authorization matrix

184 endpoints. Each requires exactly the permission shown. The access catalogue endpoint is `CLINIC_ADMIN`.

| Area | Method | Path | Requires |
|---|---|---|---|
| AccessCatalogue | GET | `/access/catalogue` | `CLINIC_ADMIN` |
| AccessLog | GET | `/access-log` | `CLINIC_ACCESS_LOG_READ` |
| Account | GET | `/patients/{patientId}/account` | `CLINIC_BILL_READ` |
| Addendum | GET | `/consultations/{consultationId}/addenda` | `CLINIC_CONSULTATION_READ` |
| Addendum | POST | `/consultations/{consultationId}/addenda` | `CLINIC_CONSULTATION_AMEND` |
| Billing | GET | `/billing/claims` | `CLINIC_CLAIM_READ` |
| Billing | GET | `/billing/consultations/{consultationId}/claim` | `CLINIC_CLAIM_READ` |
| Billing | POST | `/billing/consultations/{consultationId}/claim` | `CLINIC_CLAIM_CREATE` |
| Billing | POST | `/billing/claims/{id}/submit` | `CLINIC_CLAIM_SUBMIT` |
| Billing | POST | `/billing/claims/batch-submit` | `CLINIC_CLAIM_SUBMIT` |
| Billing | POST | `/billing/claims/{id}/{action}` | `CLINIC_CLAIM_PROGRESS` |
| Billing | GET | `/billing/claims/{id}/ledger` | `CLINIC_CLAIM_READ` |
| Billing | POST | `/billing/claims/{id}/write-off` | `CLINIC_WRITE_OFF` |
| Billing | POST | `/billing/claims/{id}/credit-note` | `CLINIC_CLAIM_REVERSE` |
| Billing | POST | `/billing/claims/{id}/void` | `CLINIC_CLAIM_REVERSE` |
| Billing | POST | `/billing/claims/scheme-payments` | `CLINIC_PAYMENT_ALLOCATE` |
| Billing | GET | `/billing/claims/{id}/patient-invoice-pdf` | `CLINIC_BILL_READ` |
| Billing | GET | `/billing/claims/{id}/submission-pdf` | `CLINIC_CLAIM_READ` |
| Billing | GET | `/billing/patients/{patientId}/statement-pdf` | `CLINIC_BILL_READ` |
| Billing | POST | `/billing/patients/{patientId}/statement/email` | `CLINIC_DOCUMENT_EMAIL` |
| Billing | GET | `/billing/outstanding` | `CLINIC_BILL_READ` |
| Billing | POST | `/billing/payments` | `CLINIC_PAYMENT_CREATE` |
| Billing | GET | `/billing/payments` | `CLINIC_PAYMENT_READ` |
| Billing | GET | `/billing/revenue` | `CLINIC_BILL_READ` |
| Billing | GET | `/billing/consultations` | `CLINIC_BILL_READ` |
| Briefing | GET | `/patients/{patientId}/briefing` | `CLINIC_CLINICAL_SUMMARY_READ` |
| Closure | GET | `/closures` | `CLINIC_APPOINTMENT_READ` |
| Closure | POST | `/closures` | `CLINIC_CLOSURE_MANAGE` |
| Closure | DELETE | `/closures/{id}` | `CLINIC_CLOSURE_MANAGE` |
| Consent | GET | `/patients/{patientId}/consent` | `CLINIC_CONSENT_READ` |
| Consent | GET | `/patients/{patientId}/consent/history` | `CLINIC_CONSENT_HISTORY_READ` |
| Consent | POST | `/patients/{patientId}/consent` | `CLINIC_CONSENT_RECORD` |
| Core | GET | `/patients` | `CLINIC_PATIENT_READ` |
| Core | GET | `/patients` | `CLINIC_PATIENT_READ` |
| Core | GET | `/patients/duplicate-check` | `CLINIC_PATIENT_READ` |
| Core | GET | `/patients/id-number/validate` | `CLINIC_PATIENT_READ` |
| Core | GET | `/patients/{id}` | `CLINIC_PATIENT_READ` |
| Core | POST | `/patients` | `CLINIC_PATIENT_CREATE` |
| Core | PATCH | `/patients/{id}` | `CLINIC_PATIENT_UPDATE` |
| Core | GET | `/patients/{id}/family` | `CLINIC_PATIENT_RELATIONSHIP_READ` |
| Core | GET | `/practitioners` | `CLINIC_PRACTITIONER_READ` |
| Core | GET | `/practitioners/list` | `CLINIC_PRACTITIONER_READ` |
| Core | POST | `/practitioners` | `CLINIC_PRACTITIONER_MANAGE` |
| Core | GET | `/appointments` | `CLINIC_APPOINTMENT_READ` |
| Core | GET | `/appointments/range` | `CLINIC_APPOINTMENT_READ` |
| Core | GET | `/appointments/{id}` | `CLINIC_APPOINTMENT_READ` |
| Core | GET | `/appointments/{id}/consultation` | `CLINIC_APPOINTMENT_READ` |
| Core | GET | `/patients/{patientId}/appointments` | `CLINIC_APPOINTMENT_READ` |
| Core | POST | `/appointments` | `CLINIC_APPOINTMENT_CREATE` |
| Core | POST | `/appointments/{id}/reschedule` | `CLINIC_APPOINTMENT_RESCHEDULE` |
| Core | POST | `/appointments/{id}/{action}` | `CLINIC_APPOINTMENT_UPDATE` |
| Core | POST | `/appointments/{id}/send-reminder` | `CLINIC_APPOINTMENT_UPDATE` |
| Core | POST | `/appointments/{id}/video-room` | `CLINIC_TELEHEALTH_ROOM_CREATE` |
| Core | GET | `/patients/{patientId}/consultations` | `CLINIC_CONSULTATION_READ` |
| Core | POST | `/patients/{patientId}/consultations` | `CLINIC_CONSULTATION_CREATE` |
| Core | PATCH | `/consultations/{id}` | `CLINIC_CONSULTATION_UPDATE` |
| Core | POST | `/patients/{patientId}/consultations/draft` | `CLINIC_CONSULTATION_CREATE` |
| Core | GET | `/consultations/drafts` | `CLINIC_CONSULTATION_READ` |
| Core | GET | `/consultations/{id}/edits` | `CLINIC_CONSULTATION_READ` |
| Core | POST | `/consultations/{id}/sign` | `CLINIC_CONSULTATION_SIGN` |
| Core | POST | `/consultations/{id}/abandon` | `CLINIC_CONSULTATION_UPDATE` |
| Core | GET | `/consultations/{consultationId}/prescriptions` | `CLINIC_PRESCRIPTION_READ` |
| Core | POST | `/prescriptions/{id}/fills` | `CLINIC_PRESCRIPTION_DISPENSE` |
| Core | GET | `/prescriptions/{id}/fills` | `CLINIC_PRESCRIPTION_READ` |
| Core | GET | `/consultations/{id}/allergy-snapshot` | `CLINIC_ALLERGY_READ` |
| Core | POST | `/consultations/{consultationId}/prescriptions/allergy-check` | `CLINIC_PRESCRIPTION_CREATE` |
| Core | POST | `/consultations/{consultationId}/prescriptions` | `CLINIC_PRESCRIPTION_CREATE` |
| Core | POST | `/consultations/{id}/medical-certificate` | `CLINIC_SICK_NOTE_SIGN` |
| Core | POST | `/consultations/{id}/referral-letter` | `CLINIC_REFERRAL_SIGN` |
| Core | GET | `/consultations/{id}/prescription-pdf` | `CLINIC_PRESCRIPTION_READ` |
| Core | GET | `/consultations/{id}/summary-pdf` | `CLINIC_CONSULTATION_READ` |
| Core | GET | `/medications` | `CLINIC_CATALOGUE_READ` |
| Core | GET | `/procedures` | `CLINIC_CATALOGUE_READ` |
| Dashboard | GET | `/dashboard/summary` | `CLINIC_DASHBOARD_READ` |
| Growth | GET | `/patients/{patientId}/growth` | `CLINIC_GROWTH_READ` |
| Growth | GET | `/growth-reference` | `CLINIC_CONTENT_ADMIN` or `CLINIC_CONTENT_APPROVE` |
| Growth | POST | `/growth-reference` | `CLINIC_CONTENT_ADMIN` |
| Growth | POST | `/growth-reference/{id}/submit` | `CLINIC_CONTENT_ADMIN` |
| Growth | POST | `/growth-reference/{id}/approve` | `CLINIC_CONTENT_APPROVE` |
| Growth | POST | `/growth-reference/{id}/send-back` | `CLINIC_CONTENT_APPROVE` |
| Growth | POST | `/growth-reference/{id}/activate` | `CLINIC_CONTENT_APPROVE` |
| Growth | POST | `/growth-reference/{id}/retire` | `CLINIC_CONTENT_APPROVE` |
| Handoff | POST | `/consultations/{id}/start-nurse-work` | `CLINIC_NURSE_INTAKE_CREATE` |
| Handoff | POST | `/consultations/{id}/send-to-doctor` | `CLINIC_NURSE_HANDOFF` |
| Handoff | POST | `/consultations/{id}/accept` | `CLINIC_NURSE_HANDOFF_ACCEPT` |
| Handoff | POST | `/consultations/{id}/return-to-nurse` | `CLINIC_NURSE_HANDOFF_RETURN` |
| Handoff | POST | `/consultations/{id}/resume-nurse-work` | `CLINIC_NURSE_INTAKE_UPDATE` |
| Handoff | POST | `/consultations/{id}/doctor-complete` | `CLINIC_NURSE_HANDOFF_COMPLETE` |
| Handoff | GET | `/consultations/handoff-queue` | `CLINIC_QUEUE_READ` |
| Handoff | GET | `/consultations/{id}/transitions` | `CLINIC_CONSULTATION_READ` |
| Lab | GET | `/lab/results` | `CLINIC_RESULT_READ` |
| Lab | GET | `/lab/patients/{patientId}/results` | `CLINIC_RESULT_READ` |
| Lab | POST | `/lab/results` | `CLINIC_RESULT_CREATE` |
| Lab | GET | `/lab/results/{id}/pdf` | `CLINIC_RESULT_READ` |
| Lab | GET | `/lab/results/{id}/summary-pdf` | `CLINIC_RESULT_READ` |
| Lab | POST | `/lab/results/{id}/match-patient` | `CLINIC_RESULT_MATCH` |
| Lab | POST | `/lab/results/{id}/interpret` | `CLINIC_RESULT_INTERPRET` |
| Lab | POST | `/lab/results/{id}/interpret-ai` | `CLINIC_RESULT_INTERPRET` |
| Lab | POST | `/lab/results/{id}/review` | `CLINIC_RESULT_REVIEW` |
| Lab | POST | `/lab/results/{id}/file` | `CLINIC_RESULT_FILE` |
| LabMarker | PUT | `/lab/results/{id}/markers` | `CLINIC_RESULT_UPDATE` |
| LabMarker | GET | `/lab/critical` | `CLINIC_RESULT_READ` |
| Letter | GET | `/letter-templates` | `CLINIC_DOCUMENT_READ` |
| Letter | GET | `/letter-templates/merge-fields` | `CLINIC_DOCUMENT_READ` |
| Letter | POST | `/letter-templates` | `CLINIC_DOCUMENT_CREATE` |
| Letter | PUT | `/letter-templates/{id}` | `CLINIC_DOCUMENT_CREATE` |
| Letter | DELETE | `/letter-templates/{id}` | `CLINIC_DOCUMENT_CREATE` |
| Letter | GET | `/letter-templates/{id}/render` | `CLINIC_DOCUMENT_READ` |
| Letter | POST | `/consultations/{id}/letter` | `CLINIC_DOCUMENT_CREATE` |
| Observation | POST | `/patients/{patientId}/observations` | `CLINIC_VITALS_WRITE` |
| Observation | GET | `/patients/{patientId}/observations` | `CLINIC_VITALS_READ` |
| Observation | GET | `/patients/{patientId}/observations/latest` | `CLINIC_VITALS_READ` |
| Observation | POST | `/patients/{patientId}/observations/{id}/void` | `CLINIC_VITALS_VOID` |
| PatientClinical | GET | `/patients/{patientId}/allergies` | `CLINIC_ALLERGY_READ` |
| PatientClinical | POST | `/patients/{patientId}/allergies` | `CLINIC_ALLERGY_WRITE` |
| PatientClinical | PATCH | `/patients/{patientId}/allergies/{id}` | `CLINIC_ALLERGY_WRITE` |
| PatientClinical | GET | `/patients/{patientId}/conditions` | `CLINIC_CONDITION_READ` |
| PatientClinical | POST | `/patients/{patientId}/conditions` | `CLINIC_CONDITION_WRITE` |
| PatientClinical | PATCH | `/patients/{patientId}/conditions/{id}` | `CLINIC_CONDITION_WRITE` |
| PatientClinical | GET | `/patients/{patientId}/medications` | `CLINIC_MEDICATION_READ` |
| PatientClinical | POST | `/patients/{patientId}/medications` | `CLINIC_MEDICATION_WRITE` |
| PatientClinical | PATCH | `/patients/{patientId}/medications/{id}` | `CLINIC_MEDICATION_WRITE` |
| PatientDirectory | GET | `/patients/directory` | `CLINIC_PATIENT_READ` |
| PatientDocument | GET | `/patients/{patientId}/documents` | `CLINIC_DOCUMENT_READ` |
| PatientDocument | POST | `/patients/{patientId}/documents` | `CLINIC_DOCUMENT_CREATE` |
| PatientDocument | GET | `/patients/{patientId}/documents/{docId}/file` | `CLINIC_DOCUMENT_DOWNLOAD` |
| PatientDocument | DELETE | `/patients/{patientId}/documents/{docId}` | `CLINIC_DOCUMENT_VOID` |
| PatientHistory | GET | `/patients/{patientId}/family-history` | `CLINIC_CLINICAL_HISTORY_READ` |
| PatientHistory | POST | `/patients/{patientId}/family-history` | `CLINIC_CLINICAL_HISTORY_WRITE` |
| PatientHistory | PATCH | `/patients/{patientId}/family-history/{id}` | `CLINIC_CLINICAL_HISTORY_WRITE` |
| PatientHistory | GET | `/patients/{patientId}/social-history` | `CLINIC_CLINICAL_HISTORY_READ` |
| PatientHistory | PUT | `/patients/{patientId}/social-history` | `CLINIC_CLINICAL_HISTORY_WRITE` |
| PatientHistory | GET | `/patients/{patientId}/medical-aid` | `CLINIC_PATIENT_READ` |
| PatientHistory | PUT | `/patients/{patientId}/medical-aid` | `CLINIC_PATIENT_UPDATE` |
| PatientHistory | DELETE | `/patients/{patientId}/medical-aid` | `CLINIC_PATIENT_UPDATE` |
| PatientNote | GET | `/patients/{patientId}/notes` | `CLINIC_NOTE_READ` |
| PatientNote | POST | `/patients/{patientId}/notes` | `CLINIC_NOTE_CREATE` |
| PatientNote | POST | `/patients/{patientId}/notes/{noteId}/resolve` | `CLINIC_NOTE_UPDATE` |
| QuestionLibrary | GET | `/question-groups` | `CLINIC_QUESTIONNAIRE_READ` |
| QuestionLibrary | GET | `/question-groups/{code}` | `CLINIC_QUESTIONNAIRE_READ` |
| QuestionLibrary | POST | `/question-groups/{code}/evaluate` | `CLINIC_QUESTIONNAIRE_READ` |
| QuestionLibrary | GET | `/consultations/{id}/form-data` | `CLINIC_QUESTIONNAIRE_READ` |
| QuestionLibrary | PUT | `/consultations/{id}/form-data/{groupCode}` | `CLINIC_QUESTIONNAIRE_ANSWER` |
| QuestionLibrary | GET | `/question-groups/admin` | `CLINIC_CONTENT_ADMIN` or `CLINIC_CONTENT_APPROVE` |
| QuestionLibrary | GET | `/question-groups/{groupId}/definition` | `CLINIC_CONTENT_ADMIN` or `CLINIC_CONTENT_APPROVE` |
| QuestionLibrary | POST | `/question-groups` | `CLINIC_CONTENT_ADMIN` |
| QuestionLibrary | PUT | `/question-groups/{groupId}/definition` | `CLINIC_CONTENT_ADMIN` |
| QuestionLibrary | POST | `/question-groups/{groupId}/new-version` | `CLINIC_CONTENT_ADMIN` |
| QuestionLibrary | POST | `/question-groups/{groupId}/submit-for-review` | `CLINIC_CONTENT_ADMIN` |
| QuestionLibrary | POST | `/question-groups/{groupId}/status` | `CLINIC_CONTENT_APPROVE` |
| Recall | GET | `/recalls` | `CLINIC_RECALL_READ` |
| Recall | POST | `/recalls/{consultationId}/actions` | `CLINIC_RECALL_MANAGE` |
| RestrictedRecord | GET | `/patients/{patientId}/restriction` | `CLINIC_PATIENT_READ` |
| RestrictedRecord | PUT | `/patients/{patientId}/restriction` | `CLINIC_RESTRICTED_RECORD_MANAGE` |
| RestrictedRecord | DELETE | `/patients/{patientId}/restriction` | `CLINIC_RESTRICTED_RECORD_MANAGE` |
| RestrictedRecord | POST | `/patients/{patientId}/break-glass` | `CLINIC_BREAK_GLASS_VIEW` |
| RestrictedRecord | POST | `/patients/{patientId}/break-glass/print` | `CLINIC_BREAK_GLASS_PRINT` |
| RestrictedRecord | GET | `/break-glass/sessions` | `CLINIC_BREAK_GLASS_REVIEW` |
| RestrictedRecord | POST | `/break-glass/sessions/{sessionId}/acknowledge` | `CLINIC_BREAK_GLASS_REVIEW` |
| Room | GET | `/rooms` | `CLINIC_APPOINTMENT_READ` |
| Room | POST | `/rooms` | `CLINIC_ROOM_MANAGE` |
| Room | PUT | `/rooms/{id}` | `CLINIC_ROOM_MANAGE` |
| Task | GET | `/tasks` | `CLINIC_TASK_READ` |
| Task | POST | `/tasks` | `CLINIC_TASK_CREATE` |
| Task | POST | `/tasks/{id}/complete` | `CLINIC_TASK_COMPLETE` |
| Task | POST | `/tasks/{id}/dismiss` | `CLINIC_TASK_COMPLETE` |
| TimeOff | GET | `/practitioners/{practitionerId}/time-off` | `CLINIC_APPOINTMENT_READ` |
| TimeOff | POST | `/practitioners/{practitionerId}/time-off` | `CLINIC_TIME_OFF_WRITE` |
| TimeOff | DELETE | `/time-off/{id}` | `CLINIC_TIME_OFF_WRITE` |
| Timeline | GET | `/patients/{patientId}/timeline` | `CLINIC_TIMELINE_READ` |
| Visit | GET | `/patients/{patientId}/visits` | `CLINIC_CONSULTATION_READ` |
| VisitMapping | GET | `/visit-types/{visitType}/groups` | `CLINIC_CONTENT_ADMIN` or `CLINIC_CONTENT_APPROVE` |
| VisitMapping | PUT | `/visit-types/{visitType}/groups` | `CLINIC_CONTENT_ADMIN` |
| VisitMapping | DELETE | `/visit-types/{visitType}/groups` | `CLINIC_CONTENT_ADMIN` |
| VisitStage | GET | `/visit-types/{visitType}/stages` | `CLINIC_QUESTIONNAIRE_READ` |
| VisitStage | PUT | `/visit-types/{visitType}/stages` | `CLINIC_CONTENT_ADMIN` |
| VisitStage | DELETE | `/visit-types/{visitType}/stages` | `CLINIC_CONTENT_ADMIN` |
| Waitlist | GET | `/waitlist` | `CLINIC_APPOINTMENT_READ` |
| Waitlist | POST | `/waitlist` | `CLINIC_APPOINTMENT_CREATE` |
| Waitlist | POST | `/waitlist/{id}/contacted` | `CLINIC_APPOINTMENT_UPDATE` |
| Waitlist | POST | `/waitlist/{id}/scheduled` | `CLINIC_APPOINTMENT_UPDATE` |
| Waitlist | DELETE | `/waitlist/{id}` | `CLINIC_APPOINTMENT_UPDATE` |
| WorkingHours | GET | `/practitioners/{practitionerId}/working-hours` | `CLINIC_APPOINTMENT_READ` |
| WorkingHours | PUT | `/practitioners/{practitionerId}/working-hours` | `CLINIC_WORKING_HOURS_WRITE` |

## 4. Findings from building the matrix

1. **Front desk can read every clinical record.** Before 0148, `CLINIC_READ` (held by the default receptionist role) opened allergies, notes, consultations, lab results, prescriptions and the patient briefing. They are now separate permissions (`CLINIC_ALLERGY_READ`, `CLINIC_NOTE_READ`, `CLINIC_CONSULTATION_READ`, `CLINIC_RESULT_READ`, `CLINIC_PRESCRIPTION_READ`, `CLINIC_CLINICAL_SUMMARY_READ`), but **V363 deliberately preserves existing access**, so receptionists still hold them until a practice removes them or switches the receptionist to the Reception template. Changing that default silently would break the briefing and the dashboard for the front desk; it needs the front-desk screens to degrade first.
2. **Any user with `CLINIC_WRITE` could register a practitioner.** Now `CLINIC_PRACTITIONER_MANAGE`, still granted to the same roles by V363. Recommended: move it to the Practice Manager / Clinic Administrator templates.
3. **A state-changing POST was behind a read permission.** `POST /appointments/{id}/video-room` required `CLINIC_READ`. It is now `CLINIC_TELEHEALTH_ROOM_CREATE`.
4. **Claim lifecycle actions are one permission.** `POST /billing/claims/{id}/{action}` (accept, reject, mark paid, partial payment) is `CLINIC_CLAIM_PROGRESS`. It splits when the claims-money decisions are built (write-off, reversal, allocation).
5. **The appointment action endpoint cannot tell cancel from check-in.** One path variable covers both, so `CLINIC_APPOINTMENT_CANCEL` and `CLINIC_APPOINTMENT_CHECK_IN` are reserved and `CLINIC_APPOINTMENT_UPDATE` guards the endpoint.
6. **`PATCH /patients/{id}` is not field-level.** One endpoint changes demographics, contacts, addresses and scheme details, so the field permissions (`CLINIC_PATIENT_DEMOGRAPHICS_WRITE` and friends) are reserved until the endpoint is split into `/demographics`, `/contacts`, `/relationships`, `/emergency-contacts`, `/scheme`.
7. **Signing with an overridden required step needs no extra permission.** Anyone who can sign can override with a reason. `CLINIC_CONSULTATION_OVERRIDE_REQUIRED_STEP` is reserved to separate the two.
8. **`PermissionConsistencyTest` already guards "checked but never seeded".** V363 seeds every new name; an offline check found every permission used by a Clinic endpoint is seeded.

## 5. Rolling it out

1. Apply 0148 and restart (V363).
2. **Every user signs out and in once** so the new permissions reach their token.
3. Nothing changes for anyone on day one.
4. A practice then builds roles from the templates and moves people over. Until a person's role stops carrying the coarse permissions, they keep the broad access.
