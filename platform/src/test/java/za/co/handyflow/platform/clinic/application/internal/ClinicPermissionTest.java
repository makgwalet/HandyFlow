package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The catalogue and the role templates hold together. Pure: no Spring, no database. */
class ClinicPermissionTest {

    @Test void everyCodeStartsWithClinicAndIsUnique() {
        Set<String> seen = new HashSet<>();
        for (ClinicPermission p : ClinicPermission.values()) {
            assertTrue(p.code().startsWith("CLINIC_"), p.code());
            assertTrue(p.code().matches("[A-Z][A-Z0-9_]*"), p.code());
            assertTrue(seen.add(p.code()), "duplicate " + p.code());
            assertFalse(p.description().isBlank(), p.code() + " has no description");
        }
    }

    @Test void legacyIsAlwaysAKnownCoarsePermissionOrItself() {
        for (ClinicPermission p : ClinicPermission.values()) {
            p.legacy().ifPresent(l -> assertTrue(ClinicPermission.LEGACY.contains(l) || l.equals(p.code()),
                    p.code() + " has legacy " + l + " which is not a coarse permission"));
        }
    }

    @Test void breakGlassAndMoneyControlsAreNeverGrantedAutomatically() {
        for (String code : new String[] {
                "CLINIC_BREAK_GLASS_VIEW", "CLINIC_BREAK_GLASS_PRINT", "CLINIC_BREAK_GLASS_EXPORT",
                "CLINIC_RESTRICTED_RECORD_MANAGE", "CLINIC_RESTRICTED_RECORD_REQUEST", "CLINIC_RESTRICTED_RECORD_ACCESS",
                "CLINIC_WRITE_OFF", "CLINIC_PAYMENT_ALLOCATE", "CLINIC_PAYMENT_REVERSE", "CLINIC_BILL_ADJUST",
                "CLINIC_CLAIM_CORRECT", "CLINIC_CLAIM_REVERSE", "CLINIC_CLAIM_RESUBMIT"}) {
            assertTrue(ClinicPermission.byCode(code).orElseThrow().legacy().isEmpty(), code + " must not follow a coarse permission");
        }
    }

    @Test void signingIsNeverEquivalentToWriting() {
        for (String sign : new String[] {"CLINIC_CONSULTATION_SIGN", "CLINIC_PRESCRIPTION_SIGN", "CLINIC_SICK_NOTE_SIGN", "CLINIC_REFERRAL_SIGN"}) {
            String legacy = ClinicPermission.byCode(sign).orElseThrow().legacy().orElseThrow();
            assertNotEquals("CLINIC_CLINICAL_WRITE", legacy, sign);
            assertNotEquals("CLINIC_WRITE", legacy, sign);
        }
    }

    @Test void sendingAReferralIsSeparateFromSigningIt() {
        assertNotEquals(ClinicPermission.CLINIC_REFERRAL_SEND, ClinicPermission.CLINIC_REFERRAL_SIGN);
        assertEquals(ClinicPermission.Kind.SEND, ClinicPermission.CLINIC_REFERRAL_SEND.kind());
    }

    @Test void deliveryOfADocumentIsSeparateFromSigningIt() {
        assertEquals(ClinicPermission.Kind.SEND, ClinicPermission.CLINIC_DOCUMENT_EMAIL.kind());
        assertTrue(ClinicPermission.CLINIC_CONSULTATION_SIGN.legacy().isPresent());
    }

    @Test void isKnownAcceptsCatalogueAndLegacyOnly() {
        assertTrue(ClinicPermission.isKnown("CLINIC_PATIENT_READ"));
        assertTrue(ClinicPermission.isKnown("CLINIC_READ"));
        assertFalse(ClinicPermission.isKnown("CLINIC_MADE_UP"));
        assertTrue(ClinicPermission.byCode("CLINIC_MADE_UP").isEmpty());
    }

    // ── role templates ────────────────────────────────────────────────────────

    @Test void thereIsATemplateForEveryRoleInTheProposal() {
        assertEquals(Set.of("CLINIC_ADMINISTRATOR", "PRACTICE_MANAGER", "DOCTOR", "CLINICAL_ASSOCIATE", "PROFESSIONAL_NURSE",
                "ENROLLED_NURSE", "RECEPTION", "BILLING_OFFICER", "PHARMACY", "LAB", "RECORDS_CLERK", "AUDITOR"),
                ClinicRoleTemplates.all().keySet());
    }

    private static Set<ClinicPermission> of(String role) { return ClinicRoleTemplates.all().get(role).permissions(); }

    @Test void onlyTheDoctorSignsByDefault() {
        assertTrue(of("DOCTOR").contains(ClinicPermission.CLINIC_CONSULTATION_SIGN));
        assertTrue(of("DOCTOR").contains(ClinicPermission.CLINIC_PRESCRIPTION_SIGN));
        assertTrue(of("DOCTOR").contains(ClinicPermission.CLINIC_SICK_NOTE_SIGN));
        assertTrue(of("DOCTOR").contains(ClinicPermission.CLINIC_REFERRAL_SIGN));
        for (String role : new String[] {"PROFESSIONAL_NURSE", "ENROLLED_NURSE", "RECEPTION", "BILLING_OFFICER", "PHARMACY", "LAB",
                "RECORDS_CLERK", "AUDITOR", "PRACTICE_MANAGER", "CLINIC_ADMINISTRATOR"}) {
            for (ClinicPermission p : of(role)) {
                assertNotEquals(ClinicPermission.Kind.SIGN, p.kind(), role + " must not get " + p);
            }
        }
    }

    @Test void clinicalAssociateSignsConsultationsButNotPrescriptionsSickNotesOrReferralsByDefault() {
        Set<ClinicPermission> p = of("CLINICAL_ASSOCIATE");
        assertTrue(p.contains(ClinicPermission.CLINIC_CONSULTATION_SIGN));
        assertFalse(p.contains(ClinicPermission.CLINIC_PRESCRIPTION_SIGN));
        assertFalse(p.contains(ClinicPermission.CLINIC_SICK_NOTE_SIGN));
        assertFalse(p.contains(ClinicPermission.CLINIC_REFERRAL_SIGN));
    }

    @Test void nursesDoNotAcceptOrCompleteHandoffs() {
        for (String role : new String[] {"PROFESSIONAL_NURSE", "ENROLLED_NURSE"}) {
            Set<ClinicPermission> p = of(role);
            assertTrue(p.contains(ClinicPermission.CLINIC_NURSE_HANDOFF), role);
            assertFalse(p.contains(ClinicPermission.CLINIC_NURSE_HANDOFF_ACCEPT), role);
            assertFalse(p.contains(ClinicPermission.CLINIC_NURSE_HANDOFF_COMPLETE), role);
            assertFalse(p.contains(ClinicPermission.CLINIC_CONSULTATION_SIGN), role);
        }
    }

    @Test void nonClinicalRolesHaveNoClinicalContent() {
        Set<ClinicPermission.Group> clinical = Set.of(ClinicPermission.Group.CLINICAL_SUMMARY, ClinicPermission.Group.NOTES,
                ClinicPermission.Group.CONSULTATION, ClinicPermission.Group.NURSE_WORKFLOW, ClinicPermission.Group.PRESCRIPTION,
                ClinicPermission.Group.SICK_NOTE, ClinicPermission.Group.REFERRAL, ClinicPermission.Group.GROWTH, ClinicPermission.Group.RESULTS);
        for (String role : new String[] {"RECEPTION", "BILLING_OFFICER", "RECORDS_CLERK", "AUDITOR", "PRACTICE_MANAGER", "CLINIC_ADMINISTRATOR"}) {
            for (ClinicPermission p : of(role)) {
                assertFalse(clinical.contains(p.group()), role + " must not get clinical permission " + p);
            }
        }
    }

    @Test void receptionCannotReadAllergiesNotesOrConsultations() {
        Set<ClinicPermission> p = of("RECEPTION");
        assertFalse(p.contains(ClinicPermission.CLINIC_ALLERGY_READ));
        assertFalse(p.contains(ClinicPermission.CLINIC_NOTE_READ));
        assertFalse(p.contains(ClinicPermission.CLINIC_CONSULTATION_READ));
        assertFalse(p.contains(ClinicPermission.CLINIC_CLINICAL_SUMMARY_READ));
        assertTrue(p.contains(ClinicPermission.CLINIC_PATIENT_CREATE));
        assertTrue(p.contains(ClinicPermission.CLINIC_APPOINTMENT_CREATE));
    }

    @Test void breakGlassIsOnlyInTheClinicalPractitionerTemplates() {
        for (var t : ClinicRoleTemplates.all().values()) {
            boolean has = t.permissions().stream().anyMatch(p -> p.group() == ClinicPermission.Group.RESTRICTED
                    && p != ClinicPermission.CLINIC_RESTRICTED_RECORD_MANAGE);
            assertEquals(t.key().equals("DOCTOR") || t.key().equals("CLINICAL_ASSOCIATE"), has, t.key());
        }
        assertFalse(of("DOCTOR").contains(ClinicPermission.CLINIC_BREAK_GLASS_PRINT));
        assertFalse(of("DOCTOR").contains(ClinicPermission.CLINIC_BREAK_GLASS_EXPORT));
    }

    @Test void moneyControlsAreNotInAnyTemplateExceptWhatTheRoleNeeds() {
        for (var t : ClinicRoleTemplates.all().values()) {
            assertFalse(t.permissions().contains(ClinicPermission.CLINIC_WRITE_OFF), t.key());
            assertFalse(t.permissions().contains(ClinicPermission.CLINIC_PAYMENT_REVERSE), t.key());
            assertFalse(t.permissions().contains(ClinicPermission.CLINIC_CLAIM_REVERSE), t.key());
        }
        assertTrue(of("BILLING_OFFICER").contains(ClinicPermission.CLINIC_PAYMENT_ALLOCATE));
        assertFalse(of("DOCTOR").stream().anyMatch(p -> p.name().startsWith("CLINIC_PAYMENT_") && p.kind() != ClinicPermission.Kind.READ));
    }

    @Test void everyTemplateHasANameADescriptionAndPermissions() {
        for (var t : ClinicRoleTemplates.all().values()) {
            assertFalse(t.name().isBlank(), t.key());
            assertFalse(t.description().isBlank(), t.key());
            assertFalse(t.permissions().isEmpty(), t.key());
        }
    }

    @Test void everyEnforcedPermissionAppearsInAtLeastOneTemplateExceptTheContentOnes() {
        Set<ClinicPermission> inAny = new HashSet<>();
        ClinicRoleTemplates.all().values().forEach(t -> inAny.addAll(t.permissions()));
        Arrays.stream(ClinicPermission.values()).filter(ClinicPermission::enforced)
                .filter(p -> p != ClinicPermission.CLINIC_CONTENT_APPROVE && p != ClinicPermission.CLINIC_TELEHEALTH_ROOM_CREATE
                        && p != ClinicPermission.CLINIC_PRACTITIONER_MANAGE && p != ClinicPermission.CLINIC_WORKING_HOURS_WRITE
                        && p != ClinicPermission.CLINIC_TIME_OFF_WRITE && p != ClinicPermission.CLINIC_ROOM_MANAGE
                        && p != ClinicPermission.CLINIC_CLOSURE_MANAGE && p != ClinicPermission.CLINIC_CLAIM_PROGRESS)
                .forEach(p -> assertTrue(inAny.contains(p), p + " is enforced on an endpoint but no role template can do it"));
    }
}
