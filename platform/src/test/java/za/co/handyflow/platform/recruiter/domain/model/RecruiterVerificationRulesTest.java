package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.recruiter.domain.model.RecruiterVerificationRules.Check;

class RecruiterVerificationRulesTest {
    @Test
    void addingAcheck() {
        assertThat(RecruiterVerificationRules.addProblem("IDENTITY", "ID document", "OFFER")).isNull();
        assertThat(RecruiterVerificationRules.addProblem("PASSPORT", "ID document", "OFFER")).contains("kind");
        assertThat(RecruiterVerificationRules.addProblem(null, "ID document", "OFFER")).contains("kind");
        assertThat(RecruiterVerificationRules.addProblem("REFERENCE", "ab", "OFFER")).contains("what is being checked");
        assertThat(RecruiterVerificationRules.addProblem("REFERENCE", "x".repeat(201), "OFFER")).contains("too long");
        for (String closed : new String[]{"HIRED", "REJECTED", "WITHDRAWN"})
            assertThat(RecruiterVerificationRules.addProblem("REFERENCE", "Previous employer", closed)).contains("closed");
    }

    @Test
    void decisions() {
        assertThat(RecruiterVerificationRules.decisionProblem("PENDING", "verified", null)).isNull();
        assertThat(RecruiterVerificationRules.decisionProblem("PENDING", "FAILED", " ")).contains("reason");
        assertThat(RecruiterVerificationRules.decisionProblem("PENDING", "FAILED", "Certificate not genuine")).isNull();
        assertThat(RecruiterVerificationRules.decisionProblem("FAILED", "VERIFIED", null)).isNull();
        assertThat(RecruiterVerificationRules.decisionProblem("PENDING", "WAIVED", null)).contains("reason");
        assertThat(RecruiterVerificationRules.decisionProblem("VERIFIED", "FAILED", "x")).contains("already been verified");
        assertThat(RecruiterVerificationRules.decisionProblem("WAIVED", "VERIFIED", null)).contains("already been waived");
        assertThat(RecruiterVerificationRules.decisionProblem("PENDING", "MAYBE", "x")).contains("Choose");
        assertThat(RecruiterVerificationRules.decisionProblem("PENDING", "FAILED", "x".repeat(501))).contains("too long");
        assertThat(RecruiterVerificationRules.needsApprover("waived")).isTrue();
        assertThat(RecruiterVerificationRules.needsApprover("VERIFIED")).isFalse();
        assertThat(RecruiterVerificationRules.needsApprover(null)).isFalse();
    }

    @Test
    void hiringWaitsForRequiredChecks() {
        assertThat(RecruiterVerificationRules.hireProblem(List.of())).isNull();
        assertThat(RecruiterVerificationRules.hireProblem(List.of(new Check(true, "VERIFIED"), new Check(true, "WAIVED")))).isNull();
        assertThat(RecruiterVerificationRules.hireProblem(List.of(new Check(true, "PENDING")))).isEqualTo("1 required check is still outstanding.");
        assertThat(RecruiterVerificationRules.hireProblem(List.of(new Check(true, "PENDING"), new Check(true, "PENDING")))).isEqualTo("2 required checks are still outstanding.");
        assertThat(RecruiterVerificationRules.hireProblem(List.of(new Check(false, "PENDING"), new Check(false, "FAILED")))).isNull();
        assertThat(RecruiterVerificationRules.hireProblem(List.of(new Check(true, "FAILED"), new Check(true, "PENDING")))).contains("failed");
    }

    @Test
    void onlyUndecidedChecksCanBeRemoved() {
        assertThat(RecruiterVerificationRules.removeProblem("PENDING")).isNull();
        assertThat(RecruiterVerificationRules.removeProblem("VERIFIED")).contains("not been decided");
    }
}
