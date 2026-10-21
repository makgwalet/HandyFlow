package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.domain.model.HrErStep;
import za.co.handyflow.platform.hr.domain.model.IncapacityRules;
import za.co.handyflow.platform.hr.dto.ErDtos.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static za.co.handyflow.platform.hr.application.internal.ErHarness.*;

class IncapacityServiceTest {

    private final ErHarness h = new ErHarness();

    /** A case with its steps, wired so the service finds them. */
    private HrErCase withSteps(String category, UUID emp, List<HrErStep> out) {
        HrErCase c = h.newCase("INCAPACITY", category, emp);
        int seq = 1;
        for (IncapacityRules.Step s : IncapacityRules.stepsFor(category)) out.add(HrErStep.of(TENANT.getValue(), c.getId(), seq++, s));
        when(h.steps.forCase(c.getId())).thenReturn(out);
        return c;
    }

    private void meetingHeld(HrErCase c, UUID chair) {
        c.issueNotice(Instant.now(), today(), LocalTime.of(10, 0), "Room", chair, null);
        c.holdHearing(today(), true, null, "Discussed");
    }

    @Test
    void aStepIsTickedWithItsDateAndWhoDidIt() {
        UUID emp = h.employee();
        List<HrErStep> steps = new ArrayList<>();
        HrErCase c = withSteps("ILL_HEALTH", emp, steps);
        h.incapacity.step(TENANT, c.getId(), new StepRequest("MEDICAL_EVIDENCE", today().minusDays(2), "Specialist report", null), HR_USER, MANAGE);
        HrErStep done = steps.stream().filter(s -> s.getCode().equals("MEDICAL_EVIDENCE")).findFirst().orElseThrow();
        assertThat(done.isDone()).isTrue();
        assertThat(done.getDoneBy()).isEqualTo(HR_USER);
        assertThat(done.getNote()).isEqualTo("Specialist report");
    }

    @Test
    void aTickCanBeTakenAway() {
        UUID emp = h.employee();
        List<HrErStep> steps = new ArrayList<>();
        HrErCase c = withSteps("ILL_HEALTH", emp, steps);
        h.incapacity.step(TENANT, c.getId(), new StepRequest("MEDICAL_EVIDENCE", today(), null, null), HR_USER, MANAGE);
        h.incapacity.step(TENANT, c.getId(), new StepRequest("MEDICAL_EVIDENCE", null, null, true), HR_USER, MANAGE);
        assertThat(steps.stream().filter(HrErStep::isDone).count()).isEqualTo(0L);
    }

    @Test
    void aStepFromAnotherKindOfCaseIsRefused() {
        UUID emp = h.employee();
        List<HrErStep> steps = new ArrayList<>();
        HrErCase c = withSteps("ILL_HEALTH", emp, steps);
        assertThatThrownBy(() -> h.incapacity.step(TENANT, c.getId(), new StepRequest("STANDARD_SET", today(), null, null), HR_USER, MANAGE))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void dismissalForIllHealthIsBlockedUntilEveryStepIsRecorded() {
        UUID emp = h.employee(), chair = h.employee(), decider = h.employee();
        List<HrErStep> steps = new ArrayList<>();
        HrErCase c = withSteps("ILL_HEALTH", emp, steps);
        meetingHeld(c, chair);
        assertThatThrownBy(() -> h.incapacity.decide(TENANT, c.getId(), new IncapacityDecisionRequest(decider, "DISMISSAL", "Permanent", null), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Before a dismissal");
        assertThat(c.getStatus()).isEqualTo("HEARING_HELD");
        for (HrErStep s : steps) s.done(today().minusDays(1), HR_USER, null);
        h.incapacity.decide(TENANT, c.getId(), new IncapacityDecisionRequest(decider, "DISMISSAL", "Permanent incapacity and no suitable post", null), HR_USER, DECIDE);
        assertThat(c.getStatus()).isEqualTo("DECIDED");
        assertThat(c.getOutcome()).isEqualTo("DISMISSAL");
    }

    @Test
    void aGentlerOutcomeNeedsOnlyReasons() {
        UUID emp = h.employee(), chair = h.employee(), decider = h.employee();
        List<HrErStep> steps = new ArrayList<>();
        HrErCase c = withSteps("ILL_HEALTH", emp, steps);
        meetingHeld(c, chair);
        h.incapacity.decide(TENANT, c.getId(), new IncapacityDecisionRequest(decider, "ADJUSTED_DUTIES", "Lighter duties for three months", null), HR_USER, DECIDE);
        assertThat(c.getOutcome()).isEqualTo("ADJUSTED_DUTIES");
    }

    @Test
    void dismissalForPoorPerformanceNeedsAFinishedReviewPeriodOfEnoughDays() {
        UUID emp = h.employee(), chair = h.employee(), decider = h.employee();
        List<HrErStep> steps = new ArrayList<>();
        HrErCase c = withSteps("POOR_PERFORMANCE", emp, steps);
        for (HrErStep s : steps) s.done(today().minusDays(60), HR_USER, null);
        meetingHeld(c, chair);
        assertThatThrownBy(() -> h.incapacity.decide(TENANT, c.getId(), new IncapacityDecisionRequest(decider, "DISMISSAL", "x", null), HR_USER, DECIDE))
                .hasMessageContaining("review period");
        c.setReview(today().minusDays(50), today().minusDays(40));
        assertThatThrownBy(() -> h.incapacity.decide(TENANT, c.getId(), new IncapacityDecisionRequest(decider, "DISMISSAL", "x", null), HR_USER, DECIDE))
                .hasMessageContaining("shorter than the company minimum");
        c.setReview(today().minusDays(60), today().minusDays(10));
        h.incapacity.decide(TENANT, c.getId(), new IncapacityDecisionRequest(decider, "DISMISSAL", "Did not reach the standard", null), HR_USER, DECIDE);
        assertThat(c.getOutcome()).isEqualTo("DISMISSAL");
    }

    @Test
    void theDeciderCannotBeTheEmployee() {
        UUID emp = h.employee(), chair = h.employee();
        List<HrErStep> steps = new ArrayList<>();
        HrErCase c = withSteps("ILL_HEALTH", emp, steps);
        meetingHeld(c, chair);
        assertThatThrownBy(() -> h.incapacity.decide(TENANT, c.getId(), new IncapacityDecisionRequest(emp, "ADJUSTED_DUTIES", "x", null), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aReviewPeriodAppliesToPoorPerformanceOnly() {
        UUID emp = h.employee();
        List<HrErStep> health = new ArrayList<>();
        HrErCase ill = withSteps("ILL_HEALTH", emp, health);
        assertThatThrownBy(() -> h.incapacity.review(TENANT, ill.getId(), new ReviewRequest(today().minusDays(10), today()), HR_USER, MANAGE))
                .isInstanceOf(IllegalStateException.class);
        List<HrErStep> perf = new ArrayList<>();
        HrErCase poor = withSteps("POOR_PERFORMANCE", emp, perf);
        h.incapacity.review(TENANT, poor.getId(), new ReviewRequest(today().minusDays(10), today().plusDays(20)), HR_USER, MANAGE);
        assertThat(poor.getReviewTo()).isEqualTo(today().plusDays(20));
    }

    @Test
    void anAppealCannotTurnADecisionIntoADismissal() {
        UUID emp = h.employee(), chair = h.employee(), decider = h.employee(), other = h.employee();
        List<HrErStep> steps = new ArrayList<>();
        HrErCase c = withSteps("ILL_HEALTH", emp, steps);
        meetingHeld(c, chair);
        c.setParties(null, decider, null);
        c.decide("ADJUSTED_DUTIES", today().minusDays(3), "x", HR_USER, null, null);
        c.communicate(today().minusDays(2), false);
        c.lodgeAppeal(today().minusDays(1), "Not enough");
        assertThatThrownBy(() -> h.incapacity.decideAppeal(TENANT, c.getId(), new AppealDecisionRequest(other, null, "VARIED", "DISMISSAL", null, "x"), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot lead to a dismissal");
    }

    @Test
    void anAppealCanReplaceTheDecisionWithAnotherThatBelongsToTheCase() {
        UUID emp = h.employee(), chair = h.employee(), decider = h.employee(), other = h.employee();
        List<HrErStep> steps = new ArrayList<>();
        HrErCase c = withSteps("ILL_HEALTH", emp, steps);
        meetingHeld(c, chair);
        c.setParties(null, decider, null);
        c.decide("ADJUSTED_DUTIES", today().minusDays(3), "x", HR_USER, null, null);
        c.communicate(today().minusDays(2), false);
        c.lodgeAppeal(today().minusDays(1), "Prefer extended leave");
        h.incapacity.decideAppeal(TENANT, c.getId(), new AppealDecisionRequest(other, null, "VARIED", "EXTENDED_LEAVE", null, "Medical advice supports rest"), HR_USER, DECIDE);
        assertThat(c.getOutcome()).isEqualTo("EXTENDED_LEAVE");
        assertThat(c.getStatus()).isEqualTo("CLOSED");
    }
}
