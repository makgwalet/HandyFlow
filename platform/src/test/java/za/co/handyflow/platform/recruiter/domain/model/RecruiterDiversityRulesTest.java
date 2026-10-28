package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterDiversityRulesTest {
    @Test
    void everyAnswerIsOptionalButMustBeAChoice() {
        assertThat(RecruiterDiversityRules.problem(null, null, null)).isNull();
        assertThat(RecruiterDiversityRules.problem("female", "african", "no")).isNull();
        assertThat(RecruiterDiversityRules.problem(" ", "", null)).isNull();
        assertThat(RecruiterDiversityRules.problem("PREFER_NOT_TO_SAY", "PREFER_NOT_TO_SAY", "PREFER_NOT_TO_SAY")).isNull();
        assertThat(RecruiterDiversityRules.problem("robot", null, null)).contains("gender");
        assertThat(RecruiterDiversityRules.problem(null, "martian", null)).contains("race");
        assertThat(RecruiterDiversityRules.problem(null, null, "maybe")).contains("disability");
    }

    @Test
    void nothingIsKeptWhenNothingWasAnswered() {
        assertThat(RecruiterDiversityRules.isEmpty(null, " ", "")).isTrue();
        assertThat(RecruiterDiversityRules.isEmpty(null, "WHITE", null)).isFalse();
        assertThat(RecruiterDiversityRules.normalise(" african ")).isEqualTo("AFRICAN");
        assertThat(RecruiterDiversityRules.normalise("  ")).isNull();
    }

    @Test
    void smallGroupsAreNeverShownAsNumbers() {
        assertThat(RecruiterDiversityRules.cell(4)).isEqualTo("<5");
        assertThat(RecruiterDiversityRules.cell(5)).isEqualTo(5L);
        assertThat(RecruiterDiversityRules.row(3, 2, 1, 1)).containsExactly("<5", "<5", "<5", "<5");
        assertThat(RecruiterDiversityRules.row(12, 4, 2, 1)).containsExactly(12L, "<5", "<5", "<5");
        assertThat(RecruiterDiversityRules.row(12, 9, 6, 5)).containsExactly(12L, 9L, 6L, 5L);
    }
}
