package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.dto.ErDtos.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static za.co.handyflow.platform.hr.application.internal.ErHarness.*;

class GrievanceServiceTest {

    private final ErHarness h = new ErHarness();

    private HrErCase investigating(UUID emp, UUID respondent) {
        HrErCase c = h.newCase("GRIEVANCE", "TREATMENT_BY_MANAGER", emp);
        c.setParties(null, null, respondent);
        c.moveTo("INVESTIGATING");
        return c;
    }

    @Test
    void aGrievanceIsDecidedWithAnOutcomeAndReasons() {
        UUID emp = h.employee(), resp = h.employee(), decider = h.employee();
        HrErCase c = investigating(emp, resp);
        h.grievance.decide(TENANT, c.getId(), new GrievanceDecisionRequest(decider, "PARTLY_UPHELD", "Manager will be coached", null), HR_USER, DECIDE);
        assertThat(c.getStatus()).isEqualTo("DECIDED");
        assertThat(c.getOutcome()).isEqualTo("PARTLY_UPHELD");
        assertThat(c.getChairId()).isEqualTo(decider);
    }

    @Test
    void theDeciderIsNeitherTheEmployeeNorThePersonComplainedAbout() {
        UUID emp = h.employee(), resp = h.employee();
        HrErCase c = investigating(emp, resp);
        assertThatThrownBy(() -> h.grievance.decide(TENANT, c.getId(), new GrievanceDecisionRequest(resp, "UPHELD", "x", null), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is about");
        assertThatThrownBy(() -> h.grievance.decide(TENANT, c.getId(), new GrievanceDecisionRequest(emp, "UPHELD", "x", null), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lodged");
        assertThat(c.getStatus()).isEqualTo("INVESTIGATING");
    }

    @Test
    void aGrievanceMustBeInvestigatedBeforeItIsDecided() {
        UUID emp = h.employee(), decider = h.employee();
        HrErCase c = h.newCase("GRIEVANCE", "WORKLOAD", emp);
        assertThatThrownBy(() -> h.grievance.decide(TENANT, c.getId(), new GrievanceDecisionRequest(decider, "UPHELD", "x", null), HR_USER, DECIDE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anOutcomeNeedsReasons() {
        UUID emp = h.employee(), decider = h.employee();
        HrErCase c = investigating(emp, null);
        assertThatThrownBy(() -> h.grievance.decide(TENANT, c.getId(), new GrievanceDecisionRequest(decider, "UPHELD", " ", null), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private HrErCase escalated(UUID emp, UUID resp, UUID first) {
        HrErCase c = investigating(emp, resp);
        c.setParties(null, first, resp);
        c.decide("NOT_UPHELD", today().minusDays(4), "No basis", HR_USER, null, null);
        c.communicate(today().minusDays(3), false);
        c.lodgeAppeal(today().minusDays(1), "I disagree");
        return c;
    }

    @Test
    void theNextLevelIsSomeoneOtherThanTheFirstDecider() {
        UUID emp = h.employee(), resp = h.employee(), first = h.employee();
        HrErCase c = escalated(emp, resp, first);
        assertThatThrownBy(() -> h.grievance.decideEscalation(TENANT, c.getId(), new GrievanceDecisionRequest(first, "UPHELD", "x", null), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("someone other");
    }

    @Test
    void aChangedOutcomeAtTheNextLevelReplacesTheFirst() {
        UUID emp = h.employee(), resp = h.employee(), first = h.employee(), next = h.employee();
        HrErCase c = escalated(emp, resp, first);
        h.grievance.decideEscalation(TENANT, c.getId(), new GrievanceDecisionRequest(next, "UPHELD", "New facts came out", null), HR_USER, DECIDE);
        assertThat(c.getOutcome()).isEqualTo("UPHELD");
        assertThat(c.getAppealOutcome()).isEqualTo("VARIED");
        assertThat(c.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void theSameOutcomeAtTheNextLevelConfirmsTheFirst() {
        UUID emp = h.employee(), resp = h.employee(), first = h.employee(), next = h.employee();
        HrErCase c = escalated(emp, resp, first);
        h.grievance.decideEscalation(TENANT, c.getId(), new GrievanceDecisionRequest(next, "NOT_UPHELD", "Agree with the first decision", null), HR_USER, DECIDE);
        assertThat(c.getOutcome()).isEqualTo("NOT_UPHELD");
        assertThat(c.getAppealOutcome()).isEqualTo("CONFIRMED");
    }
}
