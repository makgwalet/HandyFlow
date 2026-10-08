package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.application.internal.ClinicPermission;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Starting points for Clinic roles, as sets of {@link ClinicPermission}. A template is not a role: roles belong to a
 * tenant, so a practice creates a role and picks a template as its starting set, then adjusts it. Nobody inherits "doctor
 * can do everything": signing, prescribing, sick notes and referrals are separate permissions, and a clinical
 * associate's signing rights stay off until the tenant's scope configuration turns them on.
 * Break-glass permissions are only in the Doctor template and are meant to be reviewed per tenant (CLINIC-DEC-008).
 */
public final class ClinicRoleTemplates {
    private ClinicRoleTemplates() {}

    public record Template(String key, String name, String description, Set<ClinicPermission> permissions) {}

    private static final class P { private P() {}
        static final ClinicPermission CLINIC_PATIENT_ACCESS = ClinicPermission.CLINIC_PATIENT_ACCESS;
        static final ClinicPermission CLINIC_PATIENT_READ = ClinicPermission.CLINIC_PATIENT_READ;
        static final ClinicPermission CLINIC_PATIENT_CREATE = ClinicPermission.CLINIC_PATIENT_CREATE;
        static final ClinicPermission CLINIC_PATIENT_UPDATE = ClinicPermission.CLINIC_PATIENT_UPDATE;
        static final ClinicPermission CLINIC_PATIENT_ARCHIVE = ClinicPermission.CLINIC_PATIENT_ARCHIVE;
        static final ClinicPermission CLINIC_PATIENT_MERGE = ClinicPermission.CLINIC_PATIENT_MERGE;
        static final ClinicPermission CLINIC_PATIENT_DUPLICATE_REVIEW = ClinicPermission.CLINIC_PATIENT_DUPLICATE_REVIEW;
        static final ClinicPermission CLINIC_PATIENT_DEMOGRAPHICS_READ = ClinicPermission.CLINIC_PATIENT_DEMOGRAPHICS_READ;
        static final ClinicPermission CLINIC_PATIENT_DEMOGRAPHICS_WRITE = ClinicPermission.CLINIC_PATIENT_DEMOGRAPHICS_WRITE;
        static final ClinicPermission CLINIC_PATIENT_CONTACT_READ = ClinicPermission.CLINIC_PATIENT_CONTACT_READ;
        static final ClinicPermission CLINIC_PATIENT_CONTACT_WRITE = ClinicPermission.CLINIC_PATIENT_CONTACT_WRITE;
        static final ClinicPermission CLINIC_PATIENT_ADDRESS_READ = ClinicPermission.CLINIC_PATIENT_ADDRESS_READ;
        static final ClinicPermission CLINIC_PATIENT_ADDRESS_WRITE = ClinicPermission.CLINIC_PATIENT_ADDRESS_WRITE;
        static final ClinicPermission CLINIC_PATIENT_RELATIONSHIP_READ = ClinicPermission.CLINIC_PATIENT_RELATIONSHIP_READ;
        static final ClinicPermission CLINIC_PATIENT_RELATIONSHIP_WRITE = ClinicPermission.CLINIC_PATIENT_RELATIONSHIP_WRITE;
        static final ClinicPermission CLINIC_PATIENT_EMERGENCY_CONTACT_READ = ClinicPermission.CLINIC_PATIENT_EMERGENCY_CONTACT_READ;
        static final ClinicPermission CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE = ClinicPermission.CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE;
        static final ClinicPermission CLINIC_CLINICAL_SUMMARY_READ = ClinicPermission.CLINIC_CLINICAL_SUMMARY_READ;
        static final ClinicPermission CLINIC_ALLERGY_READ = ClinicPermission.CLINIC_ALLERGY_READ;
        static final ClinicPermission CLINIC_ALLERGY_WRITE = ClinicPermission.CLINIC_ALLERGY_WRITE;
        static final ClinicPermission CLINIC_CONDITION_READ = ClinicPermission.CLINIC_CONDITION_READ;
        static final ClinicPermission CLINIC_CONDITION_WRITE = ClinicPermission.CLINIC_CONDITION_WRITE;
        static final ClinicPermission CLINIC_MEDICATION_READ = ClinicPermission.CLINIC_MEDICATION_READ;
        static final ClinicPermission CLINIC_MEDICATION_WRITE = ClinicPermission.CLINIC_MEDICATION_WRITE;
        static final ClinicPermission CLINIC_CLINICAL_HISTORY_READ = ClinicPermission.CLINIC_CLINICAL_HISTORY_READ;
        static final ClinicPermission CLINIC_CLINICAL_HISTORY_WRITE = ClinicPermission.CLINIC_CLINICAL_HISTORY_WRITE;
        static final ClinicPermission CLINIC_VITALS_READ = ClinicPermission.CLINIC_VITALS_READ;
        static final ClinicPermission CLINIC_VITALS_WRITE = ClinicPermission.CLINIC_VITALS_WRITE;
        static final ClinicPermission CLINIC_VITALS_VOID = ClinicPermission.CLINIC_VITALS_VOID;
        static final ClinicPermission CLINIC_CLINICAL_MATRIX_READ = ClinicPermission.CLINIC_CLINICAL_MATRIX_READ;
        static final ClinicPermission CLINIC_CLINICAL_MATRIX_WRITE = ClinicPermission.CLINIC_CLINICAL_MATRIX_WRITE;
        static final ClinicPermission CLINIC_NOTE_READ = ClinicPermission.CLINIC_NOTE_READ;
        static final ClinicPermission CLINIC_NOTE_CREATE = ClinicPermission.CLINIC_NOTE_CREATE;
        static final ClinicPermission CLINIC_NOTE_UPDATE = ClinicPermission.CLINIC_NOTE_UPDATE;
        static final ClinicPermission CLINIC_NOTE_LOCK = ClinicPermission.CLINIC_NOTE_LOCK;
        static final ClinicPermission CLINIC_ADMIN_NOTE_READ = ClinicPermission.CLINIC_ADMIN_NOTE_READ;
        static final ClinicPermission CLINIC_PRIVATE_NOTE_READ = ClinicPermission.CLINIC_PRIVATE_NOTE_READ;
        static final ClinicPermission CLINIC_CONSULTATION_READ = ClinicPermission.CLINIC_CONSULTATION_READ;
        static final ClinicPermission CLINIC_CONSULTATION_CREATE = ClinicPermission.CLINIC_CONSULTATION_CREATE;
        static final ClinicPermission CLINIC_CONSULTATION_UPDATE = ClinicPermission.CLINIC_CONSULTATION_UPDATE;
        static final ClinicPermission CLINIC_CONSULTATION_SIGN = ClinicPermission.CLINIC_CONSULTATION_SIGN;
        static final ClinicPermission CLINIC_CONSULTATION_LOCK = ClinicPermission.CLINIC_CONSULTATION_LOCK;
        static final ClinicPermission CLINIC_CONSULTATION_AMEND = ClinicPermission.CLINIC_CONSULTATION_AMEND;
        static final ClinicPermission CLINIC_CONSULTATION_OVERRIDE_REQUIRED_STEP = ClinicPermission.CLINIC_CONSULTATION_OVERRIDE_REQUIRED_STEP;
        static final ClinicPermission CLINIC_NURSE_INTAKE_CREATE = ClinicPermission.CLINIC_NURSE_INTAKE_CREATE;
        static final ClinicPermission CLINIC_NURSE_INTAKE_UPDATE = ClinicPermission.CLINIC_NURSE_INTAKE_UPDATE;
        static final ClinicPermission CLINIC_NURSE_HANDOFF = ClinicPermission.CLINIC_NURSE_HANDOFF;
        static final ClinicPermission CLINIC_NURSE_HANDOFF_ACCEPT = ClinicPermission.CLINIC_NURSE_HANDOFF_ACCEPT;
        static final ClinicPermission CLINIC_NURSE_HANDOFF_RETURN = ClinicPermission.CLINIC_NURSE_HANDOFF_RETURN;
        static final ClinicPermission CLINIC_NURSE_HANDOFF_COMPLETE = ClinicPermission.CLINIC_NURSE_HANDOFF_COMPLETE;
        static final ClinicPermission CLINIC_PRESCRIPTION_READ = ClinicPermission.CLINIC_PRESCRIPTION_READ;
        static final ClinicPermission CLINIC_PRESCRIPTION_CREATE = ClinicPermission.CLINIC_PRESCRIPTION_CREATE;
        static final ClinicPermission CLINIC_PRESCRIPTION_UPDATE = ClinicPermission.CLINIC_PRESCRIPTION_UPDATE;
        static final ClinicPermission CLINIC_PRESCRIPTION_SIGN = ClinicPermission.CLINIC_PRESCRIPTION_SIGN;
        static final ClinicPermission CLINIC_PRESCRIPTION_VOID = ClinicPermission.CLINIC_PRESCRIPTION_VOID;
        static final ClinicPermission CLINIC_PRESCRIPTION_DISPENSE = ClinicPermission.CLINIC_PRESCRIPTION_DISPENSE;
        static final ClinicPermission CLINIC_SICK_NOTE_READ = ClinicPermission.CLINIC_SICK_NOTE_READ;
        static final ClinicPermission CLINIC_SICK_NOTE_CREATE = ClinicPermission.CLINIC_SICK_NOTE_CREATE;
        static final ClinicPermission CLINIC_SICK_NOTE_UPDATE_DRAFT = ClinicPermission.CLINIC_SICK_NOTE_UPDATE_DRAFT;
        static final ClinicPermission CLINIC_SICK_NOTE_SIGN = ClinicPermission.CLINIC_SICK_NOTE_SIGN;
        static final ClinicPermission CLINIC_SICK_NOTE_VOID = ClinicPermission.CLINIC_SICK_NOTE_VOID;
        static final ClinicPermission CLINIC_SICK_NOTE_DOWNLOAD = ClinicPermission.CLINIC_SICK_NOTE_DOWNLOAD;
        static final ClinicPermission CLINIC_SICK_NOTE_PRINT = ClinicPermission.CLINIC_SICK_NOTE_PRINT;
        static final ClinicPermission CLINIC_REFERRAL_READ = ClinicPermission.CLINIC_REFERRAL_READ;
        static final ClinicPermission CLINIC_REFERRAL_CREATE = ClinicPermission.CLINIC_REFERRAL_CREATE;
        static final ClinicPermission CLINIC_REFERRAL_UPDATE = ClinicPermission.CLINIC_REFERRAL_UPDATE;
        static final ClinicPermission CLINIC_REFERRAL_SIGN = ClinicPermission.CLINIC_REFERRAL_SIGN;
        static final ClinicPermission CLINIC_REFERRAL_VOID = ClinicPermission.CLINIC_REFERRAL_VOID;
        static final ClinicPermission CLINIC_REFERRAL_SEND = ClinicPermission.CLINIC_REFERRAL_SEND;
        static final ClinicPermission CLINIC_GROWTH_READ = ClinicPermission.CLINIC_GROWTH_READ;
        static final ClinicPermission CLINIC_GROWTH_RECORD = ClinicPermission.CLINIC_GROWTH_RECORD;
        static final ClinicPermission CLINIC_GROWTH_UPDATE = ClinicPermission.CLINIC_GROWTH_UPDATE;
        static final ClinicPermission CLINIC_GROWTH_INTERPRET = ClinicPermission.CLINIC_GROWTH_INTERPRET;
        static final ClinicPermission CLINIC_GROWTH_EXPORT = ClinicPermission.CLINIC_GROWTH_EXPORT;
        static final ClinicPermission CLINIC_RESULT_READ = ClinicPermission.CLINIC_RESULT_READ;
        static final ClinicPermission CLINIC_RESULT_CREATE = ClinicPermission.CLINIC_RESULT_CREATE;
        static final ClinicPermission CLINIC_RESULT_UPDATE = ClinicPermission.CLINIC_RESULT_UPDATE;
        static final ClinicPermission CLINIC_RESULT_MATCH = ClinicPermission.CLINIC_RESULT_MATCH;
        static final ClinicPermission CLINIC_RESULT_INTERPRET = ClinicPermission.CLINIC_RESULT_INTERPRET;
        static final ClinicPermission CLINIC_RESULT_REVIEW = ClinicPermission.CLINIC_RESULT_REVIEW;
        static final ClinicPermission CLINIC_RESULT_FILE = ClinicPermission.CLINIC_RESULT_FILE;
        static final ClinicPermission CLINIC_RESULT_ACKNOWLEDGE = ClinicPermission.CLINIC_RESULT_ACKNOWLEDGE;
        static final ClinicPermission CLINIC_DOCUMENT_READ = ClinicPermission.CLINIC_DOCUMENT_READ;
        static final ClinicPermission CLINIC_DOCUMENT_CREATE = ClinicPermission.CLINIC_DOCUMENT_CREATE;
        static final ClinicPermission CLINIC_DOCUMENT_DOWNLOAD = ClinicPermission.CLINIC_DOCUMENT_DOWNLOAD;
        static final ClinicPermission CLINIC_DOCUMENT_PRINT = ClinicPermission.CLINIC_DOCUMENT_PRINT;
        static final ClinicPermission CLINIC_DOCUMENT_EMAIL = ClinicPermission.CLINIC_DOCUMENT_EMAIL;
        static final ClinicPermission CLINIC_DOCUMENT_VOID = ClinicPermission.CLINIC_DOCUMENT_VOID;
        static final ClinicPermission CLINIC_CONSENT_READ = ClinicPermission.CLINIC_CONSENT_READ;
        static final ClinicPermission CLINIC_CONSENT_HISTORY_READ = ClinicPermission.CLINIC_CONSENT_HISTORY_READ;
        static final ClinicPermission CLINIC_CONSENT_RECORD = ClinicPermission.CLINIC_CONSENT_RECORD;
        static final ClinicPermission CLINIC_CONSENT_UPDATE = ClinicPermission.CLINIC_CONSENT_UPDATE;
        static final ClinicPermission CLINIC_CONSENT_REVOKE = ClinicPermission.CLINIC_CONSENT_REVOKE;
        static final ClinicPermission CLINIC_RESTRICTED_RECORD_MANAGE = ClinicPermission.CLINIC_RESTRICTED_RECORD_MANAGE;
        static final ClinicPermission CLINIC_RESTRICTED_RECORD_REQUEST = ClinicPermission.CLINIC_RESTRICTED_RECORD_REQUEST;
        static final ClinicPermission CLINIC_RESTRICTED_RECORD_ACCESS = ClinicPermission.CLINIC_RESTRICTED_RECORD_ACCESS;
        static final ClinicPermission CLINIC_BREAK_GLASS_VIEW = ClinicPermission.CLINIC_BREAK_GLASS_VIEW;
        static final ClinicPermission CLINIC_BREAK_GLASS_PRINT = ClinicPermission.CLINIC_BREAK_GLASS_PRINT;
        static final ClinicPermission CLINIC_BREAK_GLASS_REVIEW = ClinicPermission.CLINIC_BREAK_GLASS_REVIEW;
        static final ClinicPermission CLINIC_BREAK_GLASS_EXPORT = ClinicPermission.CLINIC_BREAK_GLASS_EXPORT;
        static final ClinicPermission CLINIC_QUEUE_READ = ClinicPermission.CLINIC_QUEUE_READ;
        static final ClinicPermission CLINIC_APPOINTMENT_READ = ClinicPermission.CLINIC_APPOINTMENT_READ;
        static final ClinicPermission CLINIC_APPOINTMENT_CREATE = ClinicPermission.CLINIC_APPOINTMENT_CREATE;
        static final ClinicPermission CLINIC_APPOINTMENT_UPDATE = ClinicPermission.CLINIC_APPOINTMENT_UPDATE;
        static final ClinicPermission CLINIC_APPOINTMENT_CANCEL = ClinicPermission.CLINIC_APPOINTMENT_CANCEL;
        static final ClinicPermission CLINIC_APPOINTMENT_RESCHEDULE = ClinicPermission.CLINIC_APPOINTMENT_RESCHEDULE;
        static final ClinicPermission CLINIC_APPOINTMENT_CHECK_IN = ClinicPermission.CLINIC_APPOINTMENT_CHECK_IN;
        static final ClinicPermission CLINIC_QUEUE_MANAGE = ClinicPermission.CLINIC_QUEUE_MANAGE;
        static final ClinicPermission CLINIC_PRACTITIONER_READ = ClinicPermission.CLINIC_PRACTITIONER_READ;
        static final ClinicPermission CLINIC_PRACTITIONER_MANAGE = ClinicPermission.CLINIC_PRACTITIONER_MANAGE;
        static final ClinicPermission CLINIC_WORKING_HOURS_WRITE = ClinicPermission.CLINIC_WORKING_HOURS_WRITE;
        static final ClinicPermission CLINIC_TIME_OFF_WRITE = ClinicPermission.CLINIC_TIME_OFF_WRITE;
        static final ClinicPermission CLINIC_CLOSURE_MANAGE = ClinicPermission.CLINIC_CLOSURE_MANAGE;
        static final ClinicPermission CLINIC_ROOM_MANAGE = ClinicPermission.CLINIC_ROOM_MANAGE;
        static final ClinicPermission CLINIC_TELEHEALTH_ROOM_CREATE = ClinicPermission.CLINIC_TELEHEALTH_ROOM_CREATE;
        static final ClinicPermission CLINIC_RECALL_READ = ClinicPermission.CLINIC_RECALL_READ;
        static final ClinicPermission CLINIC_RECALL_MANAGE = ClinicPermission.CLINIC_RECALL_MANAGE;
        static final ClinicPermission CLINIC_DASHBOARD_READ = ClinicPermission.CLINIC_DASHBOARD_READ;
        static final ClinicPermission CLINIC_TIMELINE_READ = ClinicPermission.CLINIC_TIMELINE_READ;
        static final ClinicPermission CLINIC_CATALOGUE_READ = ClinicPermission.CLINIC_CATALOGUE_READ;
        static final ClinicPermission CLINIC_QUESTIONNAIRE_READ = ClinicPermission.CLINIC_QUESTIONNAIRE_READ;
        static final ClinicPermission CLINIC_QUESTIONNAIRE_ANSWER = ClinicPermission.CLINIC_QUESTIONNAIRE_ANSWER;
        static final ClinicPermission CLINIC_BILL_READ = ClinicPermission.CLINIC_BILL_READ;
        static final ClinicPermission CLINIC_BILL_CREATE = ClinicPermission.CLINIC_BILL_CREATE;
        static final ClinicPermission CLINIC_BILL_ADJUST = ClinicPermission.CLINIC_BILL_ADJUST;
        static final ClinicPermission CLINIC_PAYMENT_READ = ClinicPermission.CLINIC_PAYMENT_READ;
        static final ClinicPermission CLINIC_PAYMENT_CREATE = ClinicPermission.CLINIC_PAYMENT_CREATE;
        static final ClinicPermission CLINIC_PAYMENT_ALLOCATE = ClinicPermission.CLINIC_PAYMENT_ALLOCATE;
        static final ClinicPermission CLINIC_PAYMENT_REVERSE = ClinicPermission.CLINIC_PAYMENT_REVERSE;
        static final ClinicPermission CLINIC_WRITE_OFF = ClinicPermission.CLINIC_WRITE_OFF;
        static final ClinicPermission CLINIC_CLAIM_READ = ClinicPermission.CLINIC_CLAIM_READ;
        static final ClinicPermission CLINIC_CLAIM_CREATE = ClinicPermission.CLINIC_CLAIM_CREATE;
        static final ClinicPermission CLINIC_CLAIM_SUBMIT = ClinicPermission.CLINIC_CLAIM_SUBMIT;
        static final ClinicPermission CLINIC_CLAIM_PROGRESS = ClinicPermission.CLINIC_CLAIM_PROGRESS;
        static final ClinicPermission CLINIC_CLAIM_CORRECT = ClinicPermission.CLINIC_CLAIM_CORRECT;
        static final ClinicPermission CLINIC_CLAIM_REVERSE = ClinicPermission.CLINIC_CLAIM_REVERSE;
        static final ClinicPermission CLINIC_CLAIM_RESUBMIT = ClinicPermission.CLINIC_CLAIM_RESUBMIT;
        static final ClinicPermission CLINIC_CONTENT_ADMIN = ClinicPermission.CLINIC_CONTENT_ADMIN;
        static final ClinicPermission CLINIC_CONTENT_APPROVE = ClinicPermission.CLINIC_CONTENT_APPROVE;
        static final ClinicPermission CLINIC_ACCESS_LOG_READ = ClinicPermission.CLINIC_ACCESS_LOG_READ;
    }

    private static final Map<String, Template> ALL = build();

    private static Map<String, Template> build() {
        Map<String, Template> t = new LinkedHashMap<>();
        t.put("DOCTOR", new Template("DOCTOR", "Medical Practitioner / Doctor",
            "Full clinical authority within their scope; signs consultations, prescriptions, sick notes and referrals.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_CREATE,
            P.CLINIC_PATIENT_UPDATE,
            P.CLINIC_PATIENT_DUPLICATE_REVIEW,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_PATIENT_DEMOGRAPHICS_WRITE,
            P.CLINIC_PATIENT_CONTACT_READ,
            P.CLINIC_PATIENT_CONTACT_WRITE,
            P.CLINIC_PATIENT_ADDRESS_READ,
            P.CLINIC_PATIENT_ADDRESS_WRITE,
            P.CLINIC_PATIENT_RELATIONSHIP_READ,
            P.CLINIC_PATIENT_RELATIONSHIP_WRITE,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_READ,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE,
            P.CLINIC_CLINICAL_SUMMARY_READ,
            P.CLINIC_ALLERGY_READ,
            P.CLINIC_ALLERGY_WRITE,
            P.CLINIC_CONDITION_READ,
            P.CLINIC_CONDITION_WRITE,
            P.CLINIC_MEDICATION_READ,
            P.CLINIC_MEDICATION_WRITE,
            P.CLINIC_CLINICAL_HISTORY_READ,
            P.CLINIC_CLINICAL_HISTORY_WRITE,
            P.CLINIC_VITALS_READ,
            P.CLINIC_VITALS_WRITE,
            P.CLINIC_VITALS_VOID,
            P.CLINIC_CLINICAL_MATRIX_READ,
            P.CLINIC_CLINICAL_MATRIX_WRITE,
            P.CLINIC_NOTE_READ,
            P.CLINIC_NOTE_CREATE,
            P.CLINIC_NOTE_UPDATE,
            P.CLINIC_NOTE_LOCK,
            P.CLINIC_ADMIN_NOTE_READ,
            P.CLINIC_PRIVATE_NOTE_READ,
            P.CLINIC_CONSULTATION_READ,
            P.CLINIC_CONSULTATION_CREATE,
            P.CLINIC_CONSULTATION_UPDATE,
            P.CLINIC_CONSULTATION_SIGN,
            P.CLINIC_CONSULTATION_LOCK,
            P.CLINIC_CONSULTATION_AMEND,
            P.CLINIC_CONSULTATION_OVERRIDE_REQUIRED_STEP,
            P.CLINIC_NURSE_INTAKE_CREATE,
            P.CLINIC_NURSE_INTAKE_UPDATE,
            P.CLINIC_NURSE_HANDOFF,
            P.CLINIC_NURSE_HANDOFF_ACCEPT,
            P.CLINIC_NURSE_HANDOFF_RETURN,
            P.CLINIC_NURSE_HANDOFF_COMPLETE,
            P.CLINIC_PRESCRIPTION_READ,
            P.CLINIC_PRESCRIPTION_CREATE,
            P.CLINIC_PRESCRIPTION_UPDATE,
            P.CLINIC_PRESCRIPTION_SIGN,
            P.CLINIC_PRESCRIPTION_VOID,
            P.CLINIC_PRESCRIPTION_DISPENSE,
            P.CLINIC_SICK_NOTE_READ,
            P.CLINIC_SICK_NOTE_CREATE,
            P.CLINIC_SICK_NOTE_UPDATE_DRAFT,
            P.CLINIC_SICK_NOTE_SIGN,
            P.CLINIC_SICK_NOTE_VOID,
            P.CLINIC_SICK_NOTE_DOWNLOAD,
            P.CLINIC_SICK_NOTE_PRINT,
            P.CLINIC_REFERRAL_READ,
            P.CLINIC_REFERRAL_CREATE,
            P.CLINIC_REFERRAL_UPDATE,
            P.CLINIC_REFERRAL_SIGN,
            P.CLINIC_REFERRAL_VOID,
            P.CLINIC_REFERRAL_SEND,
            P.CLINIC_GROWTH_READ,
            P.CLINIC_GROWTH_RECORD,
            P.CLINIC_GROWTH_UPDATE,
            P.CLINIC_GROWTH_INTERPRET,
            P.CLINIC_GROWTH_EXPORT,
            P.CLINIC_RESULT_READ,
            P.CLINIC_RESULT_CREATE,
            P.CLINIC_RESULT_UPDATE,
            P.CLINIC_RESULT_MATCH,
            P.CLINIC_RESULT_INTERPRET,
            P.CLINIC_RESULT_REVIEW,
            P.CLINIC_RESULT_FILE,
            P.CLINIC_RESULT_ACKNOWLEDGE,
            P.CLINIC_DOCUMENT_READ,
            P.CLINIC_DOCUMENT_CREATE,
            P.CLINIC_DOCUMENT_DOWNLOAD,
            P.CLINIC_DOCUMENT_PRINT,
            P.CLINIC_DOCUMENT_EMAIL,
            P.CLINIC_CONSENT_READ,
            P.CLINIC_CONSENT_HISTORY_READ,
            P.CLINIC_CONSENT_RECORD,
            P.CLINIC_CONSENT_UPDATE,
            P.CLINIC_APPOINTMENT_READ,
            P.CLINIC_APPOINTMENT_CHECK_IN,
            P.CLINIC_PRACTITIONER_READ,
            P.CLINIC_RECALL_READ,
            P.CLINIC_RECALL_MANAGE,
            P.CLINIC_TIMELINE_READ,
            P.CLINIC_QUESTIONNAIRE_READ,
            P.CLINIC_QUESTIONNAIRE_ANSWER,
            P.CLINIC_TELEHEALTH_ROOM_CREATE,
            P.CLINIC_BILL_READ,
            P.CLINIC_PAYMENT_READ,
            P.CLINIC_CLAIM_READ,
            P.CLINIC_RESTRICTED_RECORD_REQUEST,
            P.CLINIC_BREAK_GLASS_VIEW)));
        t.put("CLINICAL_ASSOCIATE", new Template("CLINICAL_ASSOCIATE", "Clinical Associate",
            "Clinical practitioner; signing of prescriptions, sick notes and referrals depends on the tenant's scope configuration and is off by default.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_CREATE,
            P.CLINIC_PATIENT_UPDATE,
            P.CLINIC_PATIENT_DUPLICATE_REVIEW,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_PATIENT_DEMOGRAPHICS_WRITE,
            P.CLINIC_PATIENT_CONTACT_READ,
            P.CLINIC_PATIENT_CONTACT_WRITE,
            P.CLINIC_PATIENT_ADDRESS_READ,
            P.CLINIC_PATIENT_ADDRESS_WRITE,
            P.CLINIC_PATIENT_RELATIONSHIP_READ,
            P.CLINIC_PATIENT_RELATIONSHIP_WRITE,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_READ,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE,
            P.CLINIC_CLINICAL_SUMMARY_READ,
            P.CLINIC_ALLERGY_READ,
            P.CLINIC_ALLERGY_WRITE,
            P.CLINIC_CONDITION_READ,
            P.CLINIC_CONDITION_WRITE,
            P.CLINIC_MEDICATION_READ,
            P.CLINIC_MEDICATION_WRITE,
            P.CLINIC_CLINICAL_HISTORY_READ,
            P.CLINIC_CLINICAL_HISTORY_WRITE,
            P.CLINIC_VITALS_READ,
            P.CLINIC_VITALS_WRITE,
            P.CLINIC_VITALS_VOID,
            P.CLINIC_CLINICAL_MATRIX_READ,
            P.CLINIC_CLINICAL_MATRIX_WRITE,
            P.CLINIC_NOTE_READ,
            P.CLINIC_NOTE_CREATE,
            P.CLINIC_NOTE_UPDATE,
            P.CLINIC_ADMIN_NOTE_READ,
            P.CLINIC_CONSULTATION_READ,
            P.CLINIC_CONSULTATION_CREATE,
            P.CLINIC_CONSULTATION_UPDATE,
            P.CLINIC_CONSULTATION_SIGN,
            P.CLINIC_CONSULTATION_AMEND,
            P.CLINIC_CONSULTATION_OVERRIDE_REQUIRED_STEP,
            P.CLINIC_NURSE_INTAKE_CREATE,
            P.CLINIC_NURSE_INTAKE_UPDATE,
            P.CLINIC_NURSE_HANDOFF,
            P.CLINIC_NURSE_HANDOFF_ACCEPT,
            P.CLINIC_NURSE_HANDOFF_RETURN,
            P.CLINIC_NURSE_HANDOFF_COMPLETE,
            P.CLINIC_PRESCRIPTION_READ,
            P.CLINIC_PRESCRIPTION_CREATE,
            P.CLINIC_PRESCRIPTION_UPDATE,
            P.CLINIC_PRESCRIPTION_DISPENSE,
            P.CLINIC_SICK_NOTE_READ,
            P.CLINIC_SICK_NOTE_CREATE,
            P.CLINIC_SICK_NOTE_UPDATE_DRAFT,
            P.CLINIC_SICK_NOTE_DOWNLOAD,
            P.CLINIC_SICK_NOTE_PRINT,
            P.CLINIC_REFERRAL_READ,
            P.CLINIC_REFERRAL_CREATE,
            P.CLINIC_REFERRAL_UPDATE,
            P.CLINIC_GROWTH_READ,
            P.CLINIC_GROWTH_RECORD,
            P.CLINIC_GROWTH_UPDATE,
            P.CLINIC_GROWTH_INTERPRET,
            P.CLINIC_GROWTH_EXPORT,
            P.CLINIC_RESULT_READ,
            P.CLINIC_RESULT_CREATE,
            P.CLINIC_RESULT_UPDATE,
            P.CLINIC_RESULT_MATCH,
            P.CLINIC_RESULT_INTERPRET,
            P.CLINIC_RESULT_REVIEW,
            P.CLINIC_RESULT_FILE,
            P.CLINIC_RESULT_ACKNOWLEDGE,
            P.CLINIC_DOCUMENT_READ,
            P.CLINIC_DOCUMENT_CREATE,
            P.CLINIC_DOCUMENT_DOWNLOAD,
            P.CLINIC_DOCUMENT_PRINT,
            P.CLINIC_DOCUMENT_EMAIL,
            P.CLINIC_CONSENT_READ,
            P.CLINIC_CONSENT_HISTORY_READ,
            P.CLINIC_CONSENT_RECORD,
            P.CLINIC_CONSENT_UPDATE,
            P.CLINIC_APPOINTMENT_READ,
            P.CLINIC_APPOINTMENT_CHECK_IN,
            P.CLINIC_PRACTITIONER_READ,
            P.CLINIC_RECALL_READ,
            P.CLINIC_RECALL_MANAGE,
            P.CLINIC_TIMELINE_READ,
            P.CLINIC_QUESTIONNAIRE_READ,
            P.CLINIC_QUESTIONNAIRE_ANSWER,
            P.CLINIC_TELEHEALTH_ROOM_CREATE,
            P.CLINIC_BILL_READ,
            P.CLINIC_PAYMENT_READ,
            P.CLINIC_CLAIM_READ,
            P.CLINIC_RESTRICTED_RECORD_REQUEST,
            P.CLINIC_BREAK_GLASS_VIEW)));
        t.put("PROFESSIONAL_NURSE", new Template("PROFESSIONAL_NURSE", "Professional Nurse",
            "Nursing assessment, observations and the nurse side of the handoff; cannot sign.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_CREATE,
            P.CLINIC_PATIENT_UPDATE,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_PATIENT_DEMOGRAPHICS_WRITE,
            P.CLINIC_PATIENT_CONTACT_READ,
            P.CLINIC_PATIENT_CONTACT_WRITE,
            P.CLINIC_PATIENT_ADDRESS_READ,
            P.CLINIC_PATIENT_ADDRESS_WRITE,
            P.CLINIC_PATIENT_RELATIONSHIP_READ,
            P.CLINIC_PATIENT_RELATIONSHIP_WRITE,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_READ,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE,
            P.CLINIC_CLINICAL_SUMMARY_READ,
            P.CLINIC_ALLERGY_READ,
            P.CLINIC_ALLERGY_WRITE,
            P.CLINIC_CONDITION_READ,
            P.CLINIC_CONDITION_WRITE,
            P.CLINIC_MEDICATION_READ,
            P.CLINIC_MEDICATION_WRITE,
            P.CLINIC_CLINICAL_HISTORY_READ,
            P.CLINIC_CLINICAL_HISTORY_WRITE,
            P.CLINIC_VITALS_READ,
            P.CLINIC_VITALS_WRITE,
            P.CLINIC_VITALS_VOID,
            P.CLINIC_CLINICAL_MATRIX_READ,
            P.CLINIC_CLINICAL_MATRIX_WRITE,
            P.CLINIC_NOTE_READ,
            P.CLINIC_NOTE_CREATE,
            P.CLINIC_NOTE_UPDATE,
            P.CLINIC_CONSULTATION_READ,
            P.CLINIC_CONSULTATION_CREATE,
            P.CLINIC_CONSULTATION_UPDATE,
            P.CLINIC_NURSE_INTAKE_CREATE,
            P.CLINIC_NURSE_INTAKE_UPDATE,
            P.CLINIC_NURSE_HANDOFF,
            P.CLINIC_QUEUE_READ,
            P.CLINIC_QUEUE_MANAGE,
            P.CLINIC_GROWTH_READ,
            P.CLINIC_GROWTH_RECORD,
            P.CLINIC_GROWTH_UPDATE,
            P.CLINIC_GROWTH_EXPORT,
            P.CLINIC_PRESCRIPTION_READ,
            P.CLINIC_SICK_NOTE_READ,
            P.CLINIC_REFERRAL_READ,
            P.CLINIC_RESULT_READ,
            P.CLINIC_RESULT_CREATE,
            P.CLINIC_RESULT_MATCH,
            P.CLINIC_RESULT_UPDATE,
            P.CLINIC_DOCUMENT_READ,
            P.CLINIC_CONSENT_READ,
            P.CLINIC_CONSENT_HISTORY_READ,
            P.CLINIC_CONSENT_RECORD,
            P.CLINIC_APPOINTMENT_READ,
            P.CLINIC_PRACTITIONER_READ,
            P.CLINIC_TIMELINE_READ,
            P.CLINIC_QUESTIONNAIRE_READ,
            P.CLINIC_QUESTIONNAIRE_ANSWER,
            P.CLINIC_RECALL_READ)));
        t.put("ENROLLED_NURSE", new Template("ENROLLED_NURSE", "Enrolled / Staff Nurse",
            "More restricted clinical capture: vitals, growth measurements and intake; reads allergies, conditions and notes.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_PATIENT_CONTACT_READ,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_READ,
            P.CLINIC_PATIENT_RELATIONSHIP_READ,
            P.CLINIC_CLINICAL_SUMMARY_READ,
            P.CLINIC_ALLERGY_READ,
            P.CLINIC_CONDITION_READ,
            P.CLINIC_MEDICATION_READ,
            P.CLINIC_VITALS_READ,
            P.CLINIC_VITALS_WRITE,
            P.CLINIC_NOTE_READ,
            P.CLINIC_CONSULTATION_READ,
            P.CLINIC_NURSE_INTAKE_CREATE,
            P.CLINIC_NURSE_INTAKE_UPDATE,
            P.CLINIC_NURSE_HANDOFF,
            P.CLINIC_QUEUE_READ,
            P.CLINIC_GROWTH_READ,
            P.CLINIC_GROWTH_RECORD,
            P.CLINIC_RESULT_READ,
            P.CLINIC_APPOINTMENT_READ,
            P.CLINIC_PRACTITIONER_READ,
            P.CLINIC_QUESTIONNAIRE_READ,
            P.CLINIC_QUESTIONNAIRE_ANSWER,
            P.CLINIC_DOCUMENT_READ)));
        t.put("RECEPTION", new Template("RECEPTION", "Reception / Front Desk",
            "Registration, appointments, queue, demographics and recalls. No clinical content.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_CREATE,
            P.CLINIC_PATIENT_UPDATE,
            P.CLINIC_PATIENT_DUPLICATE_REVIEW,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_PATIENT_DEMOGRAPHICS_WRITE,
            P.CLINIC_PATIENT_CONTACT_READ,
            P.CLINIC_PATIENT_CONTACT_WRITE,
            P.CLINIC_PATIENT_ADDRESS_READ,
            P.CLINIC_PATIENT_ADDRESS_WRITE,
            P.CLINIC_PATIENT_RELATIONSHIP_READ,
            P.CLINIC_PATIENT_RELATIONSHIP_WRITE,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_READ,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE,
            P.CLINIC_APPOINTMENT_READ,
            P.CLINIC_APPOINTMENT_CREATE,
            P.CLINIC_APPOINTMENT_UPDATE,
            P.CLINIC_APPOINTMENT_CANCEL,
            P.CLINIC_APPOINTMENT_RESCHEDULE,
            P.CLINIC_APPOINTMENT_CHECK_IN,
            P.CLINIC_QUEUE_MANAGE,
            P.CLINIC_QUEUE_READ,
            P.CLINIC_PRACTITIONER_READ,
            P.CLINIC_RECALL_READ,
            P.CLINIC_RECALL_MANAGE,
            P.CLINIC_CONSENT_READ,
            P.CLINIC_CONSENT_RECORD,
            P.CLINIC_DOCUMENT_READ)));
        t.put("BILLING_OFFICER", new Template("BILLING_OFFICER", "Billing / Claims Officer",
            "Claims, bills and payments. Reads patient identity only.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_PATIENT_CONTACT_READ,
            P.CLINIC_PATIENT_RELATIONSHIP_READ,
            P.CLINIC_BILL_READ,
            P.CLINIC_BILL_CREATE,
            P.CLINIC_PAYMENT_READ,
            P.CLINIC_PAYMENT_CREATE,
            P.CLINIC_PAYMENT_ALLOCATE,
            P.CLINIC_CLAIM_READ,
            P.CLINIC_CLAIM_CREATE,
            P.CLINIC_CLAIM_SUBMIT,
            P.CLINIC_CLAIM_PROGRESS,
            P.CLINIC_CLAIM_CORRECT,
            P.CLINIC_CLAIM_RESUBMIT,
            P.CLINIC_DOCUMENT_READ,
            P.CLINIC_DOCUMENT_DOWNLOAD,
            P.CLINIC_DOCUMENT_PRINT,
            P.CLINIC_DOCUMENT_EMAIL,
            P.CLINIC_APPOINTMENT_READ,
            P.CLINIC_TIMELINE_READ)));
        t.put("PHARMACY", new Template("PHARMACY", "Pharmacy / Dispensary",
            "Reads prescriptions and allergies and records dispensing.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_ALLERGY_READ,
            P.CLINIC_MEDICATION_READ,
            P.CLINIC_PRESCRIPTION_READ,
            P.CLINIC_PRESCRIPTION_DISPENSE)));
        t.put("LAB", new Template("LAB", "Lab / Diagnostics",
            "Receives, matches and updates results. Does not review or interpret.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_RESULT_READ,
            P.CLINIC_RESULT_CREATE,
            P.CLINIC_RESULT_MATCH,
            P.CLINIC_RESULT_UPDATE)));
        t.put("RECORDS_CLERK", new Template("RECORDS_CLERK", "Practice Support / Records Clerk",
            "Documents and records handling without clinical access.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_PATIENT_CONTACT_READ,
            P.CLINIC_DOCUMENT_READ,
            P.CLINIC_DOCUMENT_CREATE,
            P.CLINIC_DOCUMENT_DOWNLOAD,
            P.CLINIC_DOCUMENT_PRINT)));
        t.put("AUDITOR", new Template("AUDITOR", "Tenant Auditor / Compliance",
            "Audit and access-log review; no clinical modification and no clinical content.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_ACCESS_LOG_READ,
            P.CLINIC_BREAK_GLASS_REVIEW,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ)));
        t.put("PRACTICE_MANAGER", new Template("PRACTICE_MANAGER", "Practice Manager",
            "Operations: front desk, scheduling, billing and documents. Clinical-note access is read-only and only where the tenant's governance allows it, so none is granted by default.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_CREATE,
            P.CLINIC_PATIENT_UPDATE,
            P.CLINIC_PATIENT_DUPLICATE_REVIEW,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_PATIENT_DEMOGRAPHICS_WRITE,
            P.CLINIC_PATIENT_CONTACT_READ,
            P.CLINIC_PATIENT_CONTACT_WRITE,
            P.CLINIC_PATIENT_ADDRESS_READ,
            P.CLINIC_PATIENT_ADDRESS_WRITE,
            P.CLINIC_PATIENT_RELATIONSHIP_READ,
            P.CLINIC_PATIENT_RELATIONSHIP_WRITE,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_READ,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE,
            P.CLINIC_QUEUE_READ,
            P.CLINIC_APPOINTMENT_READ,
            P.CLINIC_APPOINTMENT_CREATE,
            P.CLINIC_APPOINTMENT_UPDATE,
            P.CLINIC_APPOINTMENT_CANCEL,
            P.CLINIC_APPOINTMENT_RESCHEDULE,
            P.CLINIC_APPOINTMENT_CHECK_IN,
            P.CLINIC_QUEUE_MANAGE,
            P.CLINIC_PRACTITIONER_READ,
            P.CLINIC_PRACTITIONER_MANAGE,
            P.CLINIC_WORKING_HOURS_WRITE,
            P.CLINIC_TIME_OFF_WRITE,
            P.CLINIC_CLOSURE_MANAGE,
            P.CLINIC_ROOM_MANAGE,
            P.CLINIC_TELEHEALTH_ROOM_CREATE,
            P.CLINIC_RECALL_READ,
            P.CLINIC_RECALL_MANAGE,
            P.CLINIC_TIMELINE_READ,
            P.CLINIC_QUESTIONNAIRE_READ,
            P.CLINIC_QUESTIONNAIRE_ANSWER,
            P.CLINIC_BILL_READ,
            P.CLINIC_BILL_CREATE,
            P.CLINIC_BILL_ADJUST,
            P.CLINIC_PAYMENT_READ,
            P.CLINIC_PAYMENT_CREATE,
            P.CLINIC_PAYMENT_ALLOCATE,
            P.CLINIC_CLAIM_READ,
            P.CLINIC_CLAIM_CREATE,
            P.CLINIC_CLAIM_SUBMIT,
            P.CLINIC_CLAIM_PROGRESS,
            P.CLINIC_CLAIM_CORRECT,
            P.CLINIC_CLAIM_RESUBMIT,
            P.CLINIC_DOCUMENT_READ,
            P.CLINIC_DOCUMENT_CREATE,
            P.CLINIC_DOCUMENT_DOWNLOAD,
            P.CLINIC_DOCUMENT_PRINT,
            P.CLINIC_DOCUMENT_EMAIL,
            P.CLINIC_DOCUMENT_VOID,
            P.CLINIC_CONSENT_READ,
            P.CLINIC_CONSENT_HISTORY_READ,
            P.CLINIC_CONSENT_RECORD,
            P.CLINIC_CONSENT_UPDATE,
            P.CLINIC_CONSENT_REVOKE,
            P.CLINIC_ACCESS_LOG_READ,
            P.CLINIC_BREAK_GLASS_REVIEW)));
        t.put("CLINIC_ADMINISTRATOR", new Template("CLINIC_ADMINISTRATOR", "Clinic Administrator",
            "Practice configuration; everything the Practice Manager has, plus restricted-record flags, content authoring and archive/merge.",
            EnumSet.of(
            P.CLINIC_DASHBOARD_READ,
            P.CLINIC_CATALOGUE_READ,
            P.CLINIC_PATIENT_ACCESS,
            P.CLINIC_PATIENT_READ,
            P.CLINIC_PATIENT_CREATE,
            P.CLINIC_PATIENT_UPDATE,
            P.CLINIC_PATIENT_DUPLICATE_REVIEW,
            P.CLINIC_PATIENT_DEMOGRAPHICS_READ,
            P.CLINIC_PATIENT_DEMOGRAPHICS_WRITE,
            P.CLINIC_PATIENT_CONTACT_READ,
            P.CLINIC_PATIENT_CONTACT_WRITE,
            P.CLINIC_PATIENT_ADDRESS_READ,
            P.CLINIC_PATIENT_ADDRESS_WRITE,
            P.CLINIC_PATIENT_RELATIONSHIP_READ,
            P.CLINIC_PATIENT_RELATIONSHIP_WRITE,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_READ,
            P.CLINIC_PATIENT_EMERGENCY_CONTACT_WRITE,
            P.CLINIC_QUEUE_READ,
            P.CLINIC_APPOINTMENT_READ,
            P.CLINIC_APPOINTMENT_CREATE,
            P.CLINIC_APPOINTMENT_UPDATE,
            P.CLINIC_APPOINTMENT_CANCEL,
            P.CLINIC_APPOINTMENT_RESCHEDULE,
            P.CLINIC_APPOINTMENT_CHECK_IN,
            P.CLINIC_QUEUE_MANAGE,
            P.CLINIC_PRACTITIONER_READ,
            P.CLINIC_PRACTITIONER_MANAGE,
            P.CLINIC_WORKING_HOURS_WRITE,
            P.CLINIC_TIME_OFF_WRITE,
            P.CLINIC_CLOSURE_MANAGE,
            P.CLINIC_ROOM_MANAGE,
            P.CLINIC_TELEHEALTH_ROOM_CREATE,
            P.CLINIC_RECALL_READ,
            P.CLINIC_RECALL_MANAGE,
            P.CLINIC_TIMELINE_READ,
            P.CLINIC_QUESTIONNAIRE_READ,
            P.CLINIC_QUESTIONNAIRE_ANSWER,
            P.CLINIC_BILL_READ,
            P.CLINIC_BILL_CREATE,
            P.CLINIC_BILL_ADJUST,
            P.CLINIC_PAYMENT_READ,
            P.CLINIC_PAYMENT_CREATE,
            P.CLINIC_PAYMENT_ALLOCATE,
            P.CLINIC_CLAIM_READ,
            P.CLINIC_CLAIM_CREATE,
            P.CLINIC_CLAIM_SUBMIT,
            P.CLINIC_CLAIM_PROGRESS,
            P.CLINIC_CLAIM_CORRECT,
            P.CLINIC_CLAIM_RESUBMIT,
            P.CLINIC_DOCUMENT_READ,
            P.CLINIC_DOCUMENT_CREATE,
            P.CLINIC_DOCUMENT_DOWNLOAD,
            P.CLINIC_DOCUMENT_PRINT,
            P.CLINIC_DOCUMENT_EMAIL,
            P.CLINIC_DOCUMENT_VOID,
            P.CLINIC_CONSENT_READ,
            P.CLINIC_CONSENT_HISTORY_READ,
            P.CLINIC_CONSENT_RECORD,
            P.CLINIC_CONSENT_UPDATE,
            P.CLINIC_CONSENT_REVOKE,
            P.CLINIC_ACCESS_LOG_READ,
            P.CLINIC_BREAK_GLASS_REVIEW,
            P.CLINIC_PATIENT_ARCHIVE,
            P.CLINIC_PATIENT_MERGE,
            P.CLINIC_RESTRICTED_RECORD_MANAGE,
            P.CLINIC_CONTENT_ADMIN,
            P.CLINIC_WRITE_OFF,
            P.CLINIC_CLAIM_REVERSE)));
        return Collections.unmodifiableMap(t);
    }

    public static Map<String, Template> all() { return ALL; }
}
