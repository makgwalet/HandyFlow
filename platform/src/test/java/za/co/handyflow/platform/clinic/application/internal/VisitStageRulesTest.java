package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.dto.VisitStageDtos.Stage;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VisitStageRulesTest {

    private static List<Stage> all(boolean s, boolean e, boolean d, boolean p) {
        return List.of(new Stage("SYMPTOMS", s), new Stage("EXAMINATION", e), new Stage("DIAGNOSIS", d), new Stage("PLAN", p));
    }

    @Test void noConfigurationMeansSymptomsAndDiagnosis() {
        assertEquals(Set.of("SYMPTOMS", "DIAGNOSIS"), VisitStageRules.required(null));
        assertEquals(Set.of("SYMPTOMS", "DIAGNOSIS"), VisitStageRules.required(List.of()));
    }

    @Test void requiredIsTakenFromTheFlags() {
        assertEquals(Set.of("DIAGNOSIS", "PLAN"), VisitStageRules.required(all(false, false, true, true)));
        assertEquals(Set.of("SYMPTOMS", "EXAMINATION", "DIAGNOSIS", "PLAN"), VisitStageRules.required(all(true, true, true, true)));
    }

    @Test void checkReturnsAllFourInClinicalOrder() {
        var shuffled = List.of(new Stage("plan", true), new Stage("symptoms", true), new Stage("Diagnosis", false), new Stage("EXAMINATION", false));
        assertEquals(List.of("SYMPTOMS", "EXAMINATION", "DIAGNOSIS", "PLAN"),
                VisitStageRules.check(shuffled).stream().map(Stage::stage).toList());
    }

    @Test void checkRejectsUnknownMissingAndDuplicateStages() {
        assertThrows(IllegalArgumentException.class, () -> VisitStageRules.check(null));
        assertThrows(IllegalArgumentException.class, () -> VisitStageRules.check(List.of(new Stage("SYMPTOMS", true))));
        assertThrows(IllegalArgumentException.class, () -> VisitStageRules.check(
                List.of(new Stage("SYMPTOMS", true), new Stage("SYMPTOMS", true), new Stage("DIAGNOSIS", true), new Stage("PLAN", true))));
        assertThrows(IllegalArgumentException.class, () -> VisitStageRules.check(
                List.of(new Stage("SYMPTOMS", true), new Stage("EXAMINATION", true), new Stage("DIAGNOSIS", true), new Stage("BILLING", true))));
    }

    @Test void visitTypeIsNormalisedAndDefaultsToConsultation() {
        assertEquals("RESULTS_REVIEW", VisitStageRules.visitType(" results_review "));
        assertEquals("CONSULTATION", VisitStageRules.visitType(null));
        assertEquals("CONSULTATION", VisitStageRules.visitType("  "));
        assertThrows(IllegalArgumentException.class, () -> VisitStageRules.visitType("bad type!"));
    }
}
