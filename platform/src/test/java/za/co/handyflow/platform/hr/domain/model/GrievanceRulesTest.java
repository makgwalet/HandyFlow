package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GrievanceRulesTest {

    private static final ErPolicy POLICY = ErPolicy.defaults();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test
    void theResponseIsDueAfterTheCompanysWorkingDays() {
        assertThat(GrievanceRules.responseDue(LocalDate.of(2026, 10, 14), POLICY)).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(GrievanceRules.responseDue(LocalDate.of(2026, 10, 16), POLICY)).isEqualTo(LocalDate.of(2026, 10, 23));
    }

    @Test
    void aGrievanceIsOverdueOnlyWhileItWaitsForAnOutcome() {
        LocalDate lodged = LocalDate.of(2026, 10, 7);
        assertThat(GrievanceRules.overdue("REPORTED", lodged, TODAY, POLICY)).isTrue();
        assertThat(GrievanceRules.overdue("INVESTIGATING", lodged, TODAY, POLICY)).isTrue();
        assertThat(GrievanceRules.overdue("DECIDED", lodged, TODAY, POLICY)).isFalse();
        assertThat(GrievanceRules.overdue("CLOSED", lodged, TODAY, POLICY)).isFalse();
        assertThat(GrievanceRules.overdue("REPORTED", LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 21), POLICY)).isFalse();
        assertThat(GrievanceRules.overdue("REPORTED", LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 22), POLICY)).isTrue();
    }

    @Test
    void aGrievanceNeedsACategoryAndADescription() {
        UUID emp = UUID.randomUUID();
        assertThat(GrievanceRules.lodgeProblem("PAY_AND_BENEFITS", "My overtime was not paid", TODAY, TODAY, emp, null)).isNull();
        assertThat(GrievanceRules.lodgeProblem("THEFT", "x", TODAY, TODAY, emp, null)).contains("category");
        assertThat(GrievanceRules.lodgeProblem("PAY_AND_BENEFITS", " ", TODAY, TODAY, emp, null)).contains("required");
        assertThat(GrievanceRules.lodgeProblem("PAY_AND_BENEFITS", "x", LocalDate.of(2026, 10, 21), TODAY, emp, null)).contains("future");
        assertThat(GrievanceRules.lodgeProblem("PAY_AND_BENEFITS", "x", null, TODAY, emp, null)).contains("date");
    }

    @Test
    void aGrievanceCannotBeAboutTheirOwnself() {
        UUID emp = UUID.randomUUID();
        assertThat(GrievanceRules.lodgeProblem("TREATMENT_BY_MANAGER", "x", TODAY, TODAY, emp, emp)).contains("about the person lodging");
        assertThat(GrievanceRules.lodgeProblem("TREATMENT_BY_MANAGER", "x", TODAY, TODAY, emp, UUID.randomUUID())).isNull();
    }

    @Test
    void theDeciderIsNeitherSideOfTheGrievance() {
        UUID emp = UUID.randomUUID(), resp = UUID.randomUUID(), decider = UUID.randomUUID();
        assertThat(GrievanceRules.deciderProblem(decider, emp, resp)).isNull();
        assertThat(GrievanceRules.deciderProblem(emp, emp, resp)).contains("lodged the grievance");
        assertThat(GrievanceRules.deciderProblem(resp, emp, resp)).contains("is about");
        assertThat(GrievanceRules.deciderProblem(null, emp, resp)).contains("Choose");
        assertThat(GrievanceRules.deciderProblem(decider, emp, null)).isNull();
    }

    @Test
    void theNextLevelIsSomeoneNewToTheGrievance() {
        UUID emp = UUID.randomUUID(), resp = UUID.randomUUID(), first = UUID.randomUUID(), next = UUID.randomUUID();
        assertThat(GrievanceRules.escalationDeciderProblem(next, emp, resp, first)).isNull();
        assertThat(GrievanceRules.escalationDeciderProblem(first, emp, resp, first)).contains("someone other");
        assertThat(GrievanceRules.escalationDeciderProblem(emp, emp, resp, first)).contains("lodged the grievance");
    }

    @Test
    void anOutcomeNeedsAValidResultAndReasons() {
        assertThat(GrievanceRules.outcomeProblem("UPHELD", "Overtime will be paid in November")).isNull();
        assertThat(GrievanceRules.outcomeProblem("PARTLY_UPHELD", "Some of it")).isNull();
        assertThat(GrievanceRules.outcomeProblem("NOT_UPHELD", "No basis")).isNull();
        assertThat(GrievanceRules.outcomeProblem("MAYBE", "x")).contains("Choose");
        assertThat(GrievanceRules.outcomeProblem("UPHELD", " ")).contains("required");
    }
}
