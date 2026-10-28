package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterRequisitionRulesTest {
    @Test
    void headcountLimits() {
        assertThat(RecruiterRequisitionRules.problem(3, "REQ-7", 1)).isNull();
        assertThat(RecruiterRequisitionRules.problem(null, null, 0)).contains("at least one");
        assertThat(RecruiterRequisitionRules.problem(0, null, 0)).contains("at least one");
        assertThat(RecruiterRequisitionRules.problem(501, null, 0)).contains("500");
        assertThat(RecruiterRequisitionRules.problem(1, null, 2)).contains("already hired");
        assertThat(RecruiterRequisitionRules.problem(2, "x".repeat(41), 0)).contains("too long");
    }

    @Test
    void hiringStopsWhenFull() {
        assertThat(RecruiterRequisitionRules.hireProblem(2, 1)).isNull();
        assertThat(RecruiterRequisitionRules.hireProblem(1, 1)).contains("already filled");
        assertThat(RecruiterRequisitionRules.hireProblem(3, 3)).contains("All 3");
    }

    @Test
    void aFullJobIsMarkedFilledOnlyWhileLive() {
        assertThat(RecruiterRequisitionRules.fillsJob(2, 2, "OPEN")).isTrue();
        assertThat(RecruiterRequisitionRules.fillsJob(2, 2, "PAUSED")).isTrue();
        assertThat(RecruiterRequisitionRules.fillsJob(2, 1, "OPEN")).isFalse();
        assertThat(RecruiterRequisitionRules.fillsJob(1, 1, "CLOSED")).isFalse();
        assertThat(RecruiterRequisitionRules.fillsJob(1, 1, "DRAFT")).isFalse();
    }

    @Test
    void versionsOnlyWhenTheWordingChanges() {
        BigDecimal a = new BigDecimal("100.00");
        assertThat(RecruiterRequisitionRules.wordingChanged("T", "D", "R", "B", a, a, "T", "D", "R", "B", new BigDecimal("100"), a)).isFalse();
        assertThat(RecruiterRequisitionRules.wordingChanged("T", "D", null, null, null, null, "T", "D", null, null, null, null)).isFalse();
        assertThat(RecruiterRequisitionRules.wordingChanged("T", "D", "R", "B", a, a, "T2", "D", "R", "B", a, a)).isTrue();
        assertThat(RecruiterRequisitionRules.wordingChanged("T", "D", "R", "B", a, a, "T", "D", "R", "B", null, a)).isTrue();
        assertThat(RecruiterRequisitionRules.wordingChanged("T", "D", "R", "B", a, a, "T", "D", "R", "B", a, new BigDecimal("120"))).isTrue();
    }
}
