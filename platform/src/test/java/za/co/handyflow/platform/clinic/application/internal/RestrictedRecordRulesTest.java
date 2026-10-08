package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.RestrictedRecordRules.Kind;
import za.co.handyflow.platform.clinic.application.internal.RestrictedRecordRules.Outcome;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RestrictedRecordRulesTest {

    private static final String ID = "9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f";
    private static final String B = "/api/v1/clinic";

    // ── which requests the restriction covers ─────────────────────────────────

    @Test void clinicalPartsOfAPatientAreGuarded() {
        for (String part : new String[] {"/consultations", "/allergies", "/conditions", "/medications", "/notes", "/observations",
                "/observations/latest", "/timeline", "/briefing", "/allergies/" + ID}) {
            var g = RestrictedRecordRules.classify(B + "/patients/" + ID + part);
            assertNotNull(g, part);
            assertEquals(Kind.PATIENT, g.kind());
            assertEquals(UUID.fromString(ID), g.id());
        }
    }

    @Test void identityFamilySchedulingConsentAndTheBreakGlassCallsAreNotGuarded() {
        for (String part : new String[] {"", "/family", "/restriction", "/break-glass", "/break-glass/print", "/appointments", "/consent", "/consent/history"}) {
            assertNull(RestrictedRecordRules.classify(B + "/patients/" + ID + part), part);
        }
    }

    @Test void consultationLabResultPrescriptionAndAppointmentConsultationResolveToTheirKinds() {
        assertEquals(Kind.CONSULTATION, RestrictedRecordRules.classify(B + "/consultations/" + ID + "/summary-pdf").kind());
        assertEquals(Kind.CONSULTATION, RestrictedRecordRules.classify(B + "/consultations/" + ID).kind());
        assertEquals(Kind.LAB_RESULT, RestrictedRecordRules.classify(B + "/lab/results/" + ID + "/pdf").kind());
        assertEquals(Kind.PRESCRIPTION, RestrictedRecordRules.classify(B + "/prescriptions/" + ID + "/fills").kind());
        assertEquals(Kind.APPOINTMENT, RestrictedRecordRules.classify(B + "/appointments/" + ID + "/consultation").kind());
    }

    @Test void listsPlainAppointmentsAndMoneyAreNotGuardedByThisRule() {
        assertNull(RestrictedRecordRules.classify(B + "/patients"));
        assertNull(RestrictedRecordRules.classify(B + "/patients/directory"));
        assertNull(RestrictedRecordRules.classify(B + "/consultations/drafts"));
        assertNull(RestrictedRecordRules.classify(B + "/consultations/handoff-queue"));
        assertNull(RestrictedRecordRules.classify(B + "/appointments/" + ID));
        assertNull(RestrictedRecordRules.classify(B + "/billing/patients/" + ID + "/statement-pdf"));
        assertNull(RestrictedRecordRules.classify(null));
    }

    @Test void documentsAreRecognised() {
        assertTrue(RestrictedRecordRules.isDocument(B + "/consultations/" + ID + "/summary-pdf"));
        assertTrue(RestrictedRecordRules.isDocument(B + "/consultations/" + ID + "/prescription-pdf"));
        assertTrue(RestrictedRecordRules.isDocument(B + "/lab/results/" + ID + "/pdf"));
        assertTrue(RestrictedRecordRules.isDocument(B + "/consultations/" + ID + "/medical-certificate"));
        assertTrue(RestrictedRecordRules.isDocument(B + "/consultations/" + ID + "/referral-letter"));
        assertFalse(RestrictedRecordRules.isDocument(B + "/patients/" + ID + "/notes"));
        assertFalse(RestrictedRecordRules.isDocument(null));
    }

    // ── the decision ──────────────────────────────────────────────────────────

    @Test void anUnrestrictedRecordIsAlwaysOpen() {
        assertEquals(Outcome.ALLOW, RestrictedRecordRules.decide(false, false, false, false, false));
        assertEquals(Outcome.ALLOW, RestrictedRecordRules.decide(false, false, false, true, false));
    }

    @Test void standingAccessOpensARestrictedRecord() {
        assertEquals(Outcome.ALLOW, RestrictedRecordRules.decide(true, true, false, false, false));
        assertEquals(Outcome.ALLOW, RestrictedRecordRules.decide(true, true, false, true, false));
    }

    @Test void withoutAccessOrBreakGlassARestrictedRecordIsRefused() {
        assertEquals(Outcome.DENY_RESTRICTED, RestrictedRecordRules.decide(true, false, false, false, false));
        assertEquals(Outcome.DENY_RESTRICTED, RestrictedRecordRules.decide(true, false, false, true, true));
    }

    @Test void breakGlassOpensViewing() {
        assertEquals(Outcome.ALLOW_VIEW_UNDER_BREAK_GLASS, RestrictedRecordRules.decide(true, false, true, false, false));
    }

    @Test void breakGlassDoesNotAllowDocumentsWithoutPrintOrExportPermission() {
        assertEquals(Outcome.DENY_DOCUMENT_NOT_PERMITTED, RestrictedRecordRules.decide(true, false, true, true, false));
        assertEquals(Outcome.ALLOW_DOCUMENT_UNDER_BREAK_GLASS, RestrictedRecordRules.decide(true, false, true, true, true));
    }

    // ── reasons, categories, session length ───────────────────────────────────

    @Test void reasonMustBeTypedAndReasonable() {
        assertEquals("Patient collapsed, need allergy history", RestrictedRecordRules.reason("  Patient collapsed, need allergy history "));
        assertThrows(IllegalArgumentException.class, () -> RestrictedRecordRules.reason(null));
        assertThrows(IllegalArgumentException.class, () -> RestrictedRecordRules.reason("   "));
        assertThrows(IllegalArgumentException.class, () -> RestrictedRecordRules.reason("urgent"));
        assertThrows(IllegalArgumentException.class, () -> RestrictedRecordRules.reason("x".repeat(501)));
    }

    @Test void categoryMustBeOneOfTheList() {
        assertEquals("HIV", RestrictedRecordRules.category(" hiv "));
        assertEquals("MENTAL_HEALTH", RestrictedRecordRules.category("mental_health"));
        assertThrows(IllegalArgumentException.class, () -> RestrictedRecordRules.category("secret"));
        assertThrows(IllegalArgumentException.class, () -> RestrictedRecordRules.category(null));
        assertEquals(6, RestrictedRecordRules.CATEGORIES.size());
    }

    @Test void sessionLengthDefaultsToAnHourAndIsBounded() {
        assertEquals(60, RestrictedRecordRules.sessionMinutes(null));
        assertEquals(30, RestrictedRecordRules.sessionMinutes(30));
        assertEquals(60, RestrictedRecordRules.sessionMinutes(1));
        assertEquals(60, RestrictedRecordRules.sessionMinutes(10_000));
    }
}
