package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterClientRulesTest {
    @Test
    void aClientNeedsAReasonableName() {
        assertThat(RecruiterClientRules.clientProblem("TechCorp Solutions", "IT", "Cape Town", null)).isNull();
        assertThat(RecruiterClientRules.clientProblem(" ", null, null, null)).contains("name");
        assertThat(RecruiterClientRules.clientProblem("A", null, null, null)).contains("name");
        assertThat(RecruiterClientRules.clientProblem("x".repeat(151), null, null, null)).contains("too long");
        assertThat(RecruiterClientRules.clientProblem("Ok", "x".repeat(81), null, null)).contains("industry");
        assertThat(RecruiterClientRules.clientProblem("Ok", null, "x".repeat(121), null)).contains("location");
        assertThat(RecruiterClientRules.clientProblem("Ok", null, null, "x".repeat(2001))).contains("notes");
    }

    @Test
    void statusIsActiveOrInactive() {
        assertThat(RecruiterClientRules.statusProblem(null)).isNull();
        assertThat(RecruiterClientRules.statusProblem("ACTIVE")).isNull();
        assertThat(RecruiterClientRules.statusProblem("INACTIVE")).isNull();
        assertThat(RecruiterClientRules.statusProblem("PAUSED")).isNotNull();
    }

    @Test
    void aContactCanBeReached() {
        assertThat(RecruiterClientRules.contactProblem("Sarah Mitchell", "sarah@techcorp.co.za", null, "HR Director")).isNull();
        assertThat(RecruiterClientRules.contactProblem("Sarah", null, "+27 82 123 4567", null)).isNull();
        assertThat(RecruiterClientRules.contactProblem("", "a@b.co", null, null)).contains("name");
        assertThat(RecruiterClientRules.contactProblem("Sarah", null, null, null)).contains("email address or a phone");
        assertThat(RecruiterClientRules.contactProblem("Sarah", "not an email", null, null)).contains("email");
        assertThat(RecruiterClientRules.contactProblem("Sarah", null, "abc", null)).contains("phone");
        assertThat(RecruiterClientRules.contactProblem("Sarah", "a@b.co", null, "x".repeat(81))).contains("title");
    }

    @Test
    void aFeeIsRandAndCents() {
        assertThat(RecruiterClientRules.feeProblem(null)).isNull();
        assertThat(RecruiterClientRules.feeProblem(new BigDecimal("0"))).isNull();
        assertThat(RecruiterClientRules.feeProblem(new BigDecimal("45000.50"))).isNull();
        assertThat(RecruiterClientRules.feeProblem(new BigDecimal("45000.500"))).isNull();
        assertThat(RecruiterClientRules.feeProblem(new BigDecimal("-1"))).contains("negative");
        assertThat(RecruiterClientRules.feeProblem(new BigDecimal("10.005"))).contains("two decimal");
        assertThat(RecruiterClientRules.feeProblem(new BigDecimal("10000001"))).contains("too large");
    }

    @Test
    void blanksBecomeNothing() {
        assertThat(RecruiterClientRules.clean("  ")).isNull();
        assertThat(RecruiterClientRules.clean(null)).isNull();
        assertThat(RecruiterClientRules.clean(" Cape Town ")).isEqualTo("Cape Town");
    }
}
