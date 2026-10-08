package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SignRulesTest {

    @Test void completeRecordNeedsNothing() {
        assertEquals(List.of(), SignRules.missing("Cough", "Acute URTI", List.of()));
    }

    @Test void anIcdCodeAloneCountsAsDiagnosis() {
        assertEquals(List.of(), SignRules.missing("Cough", null, List.of("J06.9")));
        assertEquals(List.of("DIAGNOSIS"), SignRules.missing("Cough", " ", List.of(" ")));
    }

    @Test void reportsMissingStepsInClinicalOrder() {
        assertEquals(List.of("SYMPTOMS", "DIAGNOSIS"), SignRules.missing(null, "", null));
        assertEquals(List.of("SYMPTOMS"), SignRules.missing("  ", "Asthma", List.of()));
    }

    @Test void nothingMissingNeedsNoReason() {
        assertNull(SignRules.requireCompleteOrReason(List.of(), null));
    }

    @Test void missingStepWithoutReasonIsRefused() {
        var e = assertThrows(IllegalStateException.class,
                () -> SignRules.requireCompleteOrReason(List.of("SYMPTOMS", "DIAGNOSIS"), "   "));
        assertTrue(e.getMessage().contains("Symptoms and Diagnosis"));
        assertThrows(IllegalStateException.class, () -> SignRules.requireCompleteOrReason(List.of("DIAGNOSIS"), null));
    }

    @Test void reasonIsTrimmedAndLengthLimited() {
        assertEquals("Results review", SignRules.requireCompleteOrReason(List.of("DIAGNOSIS"), "  Results review "));
        assertThrows(IllegalArgumentException.class,
                () -> SignRules.requireCompleteOrReason(List.of("DIAGNOSIS"), "x".repeat(501)));
    }

    // ── Visit-type stages (CLINIC-DEC-012) ────────────────────────────────────

    private static final java.util.Set<String> ALL = java.util.Set.of("SYMPTOMS", "EXAMINATION", "DIAGNOSIS", "PLAN");

    @Test void stagesThatAreNotRequiredAreNotReported() {
        assertEquals(List.of(), SignRules.missing(java.util.Set.of("DIAGNOSIS", "PLAN"), null, false, null, "Anaemia", List.of(), true));
    }

    @Test void allFourRequiredAreReportedInClinicalOrder() {
        assertEquals(List.of("SYMPTOMS", "EXAMINATION", "DIAGNOSIS", "PLAN"),
                SignRules.missing(ALL, "", false, " ", null, List.of(), false));
    }

    @Test void vitalsAloneSatisfyExamination() {
        assertEquals(List.of(), SignRules.missing(ALL, "Booking", true, null, "Normal pregnancy", List.of(), true));
        assertEquals(List.of(), SignRules.missing(ALL, "Booking", false, "Fundal height 20 cm", null, List.of("Z34.9"), true));
    }

    @Test void planNeedsATreatmentPlanOrFollowUp() {
        assertEquals(List.of("PLAN"), SignRules.missing(ALL, "Booking", true, null, "Normal pregnancy", List.of(), false));
    }

    @Test void labelsCoverAllStages() {
        assertEquals("Examination", SignRules.label("EXAMINATION"));
        assertEquals("Plan", SignRules.label("PLAN"));
    }
}
