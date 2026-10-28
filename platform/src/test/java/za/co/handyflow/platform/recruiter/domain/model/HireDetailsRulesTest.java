package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HireDetailsRulesTest {

    @Test
    void nothingGivenIsFine() {
        assertThat(HireDetailsRules.problem(null, null, null)).isNull();
        assertThat(HireDetailsRules.problem(" ", " ", " ")).isNull();
    }

    @Test
    void genderMustBeOneHrOffers() {
        assertThat(HireDetailsRules.problem("FEMALE", null, null)).isNull();
        assertThat(HireDetailsRules.problem("PREFER_NOT_TO_SAY", null, null)).isNull();
        assertThat(HireDetailsRules.problem("female", null, null)).contains("Gender must be");
        assertThat(HireDetailsRules.problem("X", null, null)).contains("Gender must be");
    }

    @Test
    void anEmergencyContactNeedsBothNameAndPhone() {
        assertThat(HireDetailsRules.problem(null, "Sipho Dlamini", "0821234567")).isNull();
        assertThat(HireDetailsRules.problem(null, "Sipho Dlamini", null)).contains("together");
        assertThat(HireDetailsRules.problem(null, null, "0821234567")).contains("together");
        assertThat(HireDetailsRules.problem(null, "x".repeat(121), "0821234567")).contains("too long");
    }
}
