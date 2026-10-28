package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterSearchRulesTest {
    @Test
    void emptyTextIsNoSearch() {
        assertThat(RecruiterSearchRules.problem(null)).isNull();
        assertThat(RecruiterSearchRules.problem("  ")).isNull();
        assertThat(RecruiterSearchRules.active(" ")).isFalse();
        assertThat(RecruiterSearchRules.active("th")).isTrue();
    }

    @Test
    void lengthLimits() {
        assertThat(RecruiterSearchRules.problem("a")).contains("at least 2");
        assertThat(RecruiterSearchRules.problem("ab")).isNull();
        assertThat(RecruiterSearchRules.problem("x".repeat(101))).contains("too long");
    }

    @Test
    void wildcardsTypedByTheUserMeanThemselves() {
        assertThat(RecruiterSearchRules.pattern("  Thandi ")).isEqualTo("%thandi%");
        assertThat(RecruiterSearchRules.pattern("100%_x")).isEqualTo("%100\\%\\_x%");
        assertThat(RecruiterSearchRules.pattern("a\\b")).isEqualTo("%a\\\\b%");
    }
}
