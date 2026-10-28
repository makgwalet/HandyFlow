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

    @Test
    void raceIsOptionalButMustBeOneHrOffers() {
        assertThat(HireDetailsRules.problem(null, null, null, null, null)).isNull();
        assertThat(HireDetailsRules.problem(null, " ", null, null, null)).isNull();
        assertThat(HireDetailsRules.problem(null, "AFRICAN", null, null, null)).isNull();
        assertThat(HireDetailsRules.problem(null, "indian", null, null, null)).isNull();
        assertThat(HireDetailsRules.problem(null, "ALIEN", null, null, null)).contains("Race must be");
    }

    @Test
    void aRelationNeedsAContactNameAndStaysShort() {
        assertThat(HireDetailsRules.problem(null, null, "Sipho Dlamini", "0821234567", "Brother")).isNull();
        assertThat(HireDetailsRules.problem(null, null, null, null, "Brother")).contains("name as well");
        assertThat(HireDetailsRules.problem(null, null, "Sipho Dlamini", "0821234567", "x".repeat(51))).contains("too long");
        assertThat(HireDetailsRules.problem(null, null, "Sipho Dlamini", "0821234567", "x".repeat(50))).isNull();
    }

    @Test
    void theHireListsTheSameRacesAsHr() {
        assertThat(HireDetailsRules.RACES).isEqualTo(za.co.handyflow.platform.hr.domain.model.EmployeeRules.RACES);
    }
}
