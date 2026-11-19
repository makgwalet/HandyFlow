package za.co.handyflow.platform.legalcompliance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.ApplicabilityRules;
import za.co.handyflow.platform.legalcompliance.application.internal.ApplicabilityRules.Conditions;
import za.co.handyflow.platform.legalcompliance.application.internal.ApplicabilityRules.Profile;
import za.co.handyflow.platform.legalcompliance.domain.model.ApplicabilitySuggestion;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pure rules — no Spring, no DB. The suggestion is a hint; these tests pin down that it can never over-claim. */
class ApplicabilityRulesTest {

    private static Profile profile(Integer employees, Set<String> industries, Set<String> activities) {
        return new Profile(industries, activities, employees);
    }

    @Test
    @DisplayName("no recorded conditions -> NEEDS_REVIEW, never assumed to apply to everyone")
    void noConditionsNeedsReview() {
        var r = ApplicabilityRules.suggest(profile(50, Set.of("RETAIL"), Set.of()), new Conditions(null, Set.of(), Set.of()));
        assertEquals(ApplicabilitySuggestion.NEEDS_REVIEW, r.suggestion());
        assertFalse(r.reasons().isEmpty());
    }

    @Test
    @DisplayName("all conditions pass -> LIKELY_APPLICABLE")
    void allPass() {
        var r = ApplicabilityRules.suggest(profile(120, Set.of("Manufacturing"), Set.of("PROCESSES_PERSONAL_INFO")),
                new Conditions(50, Set.of("MANUFACTURING", "RETAIL"), Set.of("processes_personal_info")));
        assertEquals(ApplicabilitySuggestion.LIKELY_APPLICABLE, r.suggestion());
    }

    @Test
    @DisplayName("matching is case-insensitive and trims whitespace")
    void caseInsensitive() {
        var r = ApplicabilityRules.suggest(profile(null, Set.of(" retail "), Set.of()), new Conditions(null, Set.of("RETAIL"), Set.of()));
        assertEquals(ApplicabilitySuggestion.LIKELY_APPLICABLE, r.suggestion());
    }

    @Test
    @DisplayName("below the employee threshold -> LIKELY_NOT_APPLICABLE")
    void belowThreshold() {
        var r = ApplicabilityRules.suggest(profile(10, Set.of(), Set.of()), new Conditions(50, Set.of(), Set.of()));
        assertEquals(ApplicabilitySuggestion.LIKELY_NOT_APPLICABLE, r.suggestion());
    }

    @Test
    @DisplayName("exactly at the threshold counts as meeting it")
    void atThreshold() {
        var r = ApplicabilityRules.suggest(profile(50, Set.of(), Set.of()), new Conditions(50, Set.of(), Set.of()));
        assertEquals(ApplicabilitySuggestion.LIKELY_APPLICABLE, r.suggestion());
    }

    @Test
    @DisplayName("missing profile data -> NEEDS_REVIEW, not a guess either way")
    void missingProfileData() {
        var r = ApplicabilityRules.suggest(profile(null, Set.of(), Set.of()), new Conditions(50, Set.of(), Set.of()));
        assertEquals(ApplicabilitySuggestion.NEEDS_REVIEW, r.suggestion());
        assertTrue(r.reasons().get(0).contains("not recorded"));
    }

    @Test
    @DisplayName("a definite failure beats an unknown: conditions are ANDed")
    void failBeatsUnknown() {
        var r = ApplicabilityRules.suggest(profile(5, Set.of(), Set.of()), new Conditions(50, Set.of("MINING"), Set.of()));
        assertEquals(ApplicabilitySuggestion.LIKELY_NOT_APPLICABLE, r.suggestion());
    }

    @Test
    @DisplayName("industry with no overlap -> LIKELY_NOT_APPLICABLE")
    void industryMismatch() {
        var r = ApplicabilityRules.suggest(profile(100, Set.of("RETAIL"), Set.of()), new Conditions(null, Set.of("MINING"), Set.of()));
        assertEquals(ApplicabilitySuggestion.LIKELY_NOT_APPLICABLE, r.suggestion());
    }

    @Test
    @DisplayName("activity with no overlap -> LIKELY_NOT_APPLICABLE")
    void activityMismatch() {
        var r = ApplicabilityRules.suggest(profile(100, Set.of(), Set.of("EXPORTS")), new Conditions(null, Set.of(), Set.of("HANDLES_CASH")));
        assertEquals(ApplicabilitySuggestion.LIKELY_NOT_APPLICABLE, r.suggestion());
    }

    @Test
    @DisplayName("null sets are treated as empty rather than throwing")
    void nullSafe() {
        var r = ApplicabilityRules.suggest(new Profile(null, null, null), new Conditions(null, null, null));
        assertEquals(ApplicabilitySuggestion.NEEDS_REVIEW, r.suggestion());
    }
}
