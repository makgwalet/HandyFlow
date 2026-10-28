package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterPoolRulesTest {
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @Test
    void poolNames() {
        assertThat(RecruiterPoolRules.poolProblem("Operators", null)).isNull();
        assertThat(RecruiterPoolRules.poolProblem(" a ", null)).contains("name");
        assertThat(RecruiterPoolRules.poolProblem("x".repeat(81), null)).contains("too long");
        assertThat(RecruiterPoolRules.poolProblem("Operators", "d".repeat(301))).contains("description");
        assertThat(RecruiterPoolRules.noteProblem("n".repeat(301))).contains("too long");
    }

    @Test
    void tagsAreNormalisedAndLimited() {
        assertThat(RecruiterPoolRules.normaliseTag("  Code   14 ")).isEqualTo("code 14");
        assertThat(RecruiterPoolRules.normaliseTag("   ")).isNull();
        assertThat(RecruiterPoolRules.cleanTags(List.of("Welder", "welder ", "C#", " "))).containsExactly("welder", "c#");
        assertThat(RecruiterPoolRules.tagsProblem(List.of("welder", "c++", "code-14"))).isNull();
        assertThat(RecruiterPoolRules.tagsProblem(List.of("<script>"))).contains("letters");
        assertThat(RecruiterPoolRules.tagsProblem(List.of("x".repeat(31)))).contains("30");
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add("tag" + i);
        assertThat(RecruiterPoolRules.tagsProblem(many)).contains("20");
        assertThat(RecruiterPoolRules.tagsProblem(null)).isNull();
    }

    @Test
    void reuseNeedsConsentAndTime() {
        Instant consent = Instant.parse("2026-03-01T08:00:00Z");
        Instant recent = Instant.parse("2026-06-01T08:00:00Z");
        assertThat(RecruiterPoolRules.reuseProblem(false, consent, recent, 12, NOW)).isNull();
        assertThat(RecruiterPoolRules.reuseProblem(true, consent, recent, 12, NOW)).contains("removed");
        assertThat(RecruiterPoolRules.reuseProblem(false, null, recent, 12, NOW)).contains("no record");
        assertThat(RecruiterPoolRules.reuseProblem(false, consent, Instant.parse("2025-06-01T08:00:00Z"), 12, NOW)).contains("retention");
        assertThat(RecruiterPoolRules.reuseProblem(false, consent, null, 12, NOW)).isNull();
    }
}
