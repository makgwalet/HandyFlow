package za.co.handyflow.platform.legalcompliance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.CoverageRules;
import za.co.handyflow.platform.legalcompliance.application.internal.RiskScoringRules;
import za.co.handyflow.platform.legalcompliance.domain.model.RiskPosition;
import za.co.handyflow.platform.legalcompliance.domain.model.RiskRating;

import static org.junit.jupiter.api.Assertions.*;

class RiskScoringRulesTest {

    @Test
    @DisplayName("score is likelihood x impact")
    void score() {
        assertEquals(1, RiskScoringRules.score(1, 1));
        assertEquals(12, RiskScoringRules.score(3, 4));
        assertEquals(25, RiskScoringRules.score(5, 5));
    }

    @Test
    @DisplayName("values outside 1..5 are rejected")
    void scale() {
        assertThrows(IllegalArgumentException.class, () -> RiskScoringRules.score(0, 3));
        assertThrows(IllegalArgumentException.class, () -> RiskScoringRules.score(3, 6));
    }

    @Test
    @DisplayName("rating band edges: 4 LOW, 5 MEDIUM, 9 MEDIUM, 10 HIGH, 16 HIGH, 17 CRITICAL")
    void bands() {
        assertEquals(RiskRating.LOW, RiskScoringRules.rating(4));
        assertEquals(RiskRating.MEDIUM, RiskScoringRules.rating(5));
        assertEquals(RiskRating.MEDIUM, RiskScoringRules.rating(9));
        assertEquals(RiskRating.HIGH, RiskScoringRules.rating(10));
        assertEquals(RiskRating.HIGH, RiskScoringRules.rating(16));
        assertEquals(RiskRating.CRITICAL, RiskScoringRules.rating(17));
        assertThrows(IllegalArgumentException.class, () -> RiskScoringRules.rating(26));
    }

    @Test
    @DisplayName("position against appetite 9 and tolerance 16: at the threshold is still inside it")
    void position() {
        assertEquals(RiskPosition.WITHIN_APPETITE, RiskScoringRules.position(9, 9, 16));
        assertEquals(RiskPosition.ABOVE_APPETITE, RiskScoringRules.position(10, 9, 16));
        assertEquals(RiskPosition.ABOVE_APPETITE, RiskScoringRules.position(16, 9, 16));
        assertEquals(RiskPosition.OUTSIDE_TOLERANCE, RiskScoringRules.position(17, 9, 16));
    }

    @Test
    @DisplayName("thresholds: appetite must be below tolerance, tolerance at most 25")
    void thresholds() {
        assertDoesNotThrow(() -> RiskScoringRules.validateThresholds(9, 16));
        assertThrows(IllegalArgumentException.class, () -> RiskScoringRules.validateThresholds(16, 16));
        assertThrows(IllegalArgumentException.class, () -> RiskScoringRules.validateThresholds(0, 10));
        assertThrows(IllegalArgumentException.class, () -> RiskScoringRules.validateThresholds(9, 26));
    }

    @Test
    @DisplayName("an obligation with active controls but no effective one is COVERED_UNTESTED, never effective by default")
    void coverage() {
        assertEquals("UNCOVERED", CoverageRules.obligationState(0, 0));
        assertEquals("COVERED_UNTESTED", CoverageRules.obligationState(2, 0));
        assertEquals("COVERED_EFFECTIVE", CoverageRules.obligationState(2, 1));
    }
}
