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
}
