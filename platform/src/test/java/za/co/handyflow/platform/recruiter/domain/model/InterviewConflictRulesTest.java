package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.recruiter.domain.model.InterviewConflictRules.Slot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewConflictRulesTest {

    private final UUID sipho = UUID.randomUUID(), pieter = UUID.randomUUID(), appA = UUID.randomUUID(), appB = UUID.randomUUID();
    private final Map<UUID, String> names = Map.of(sipho, "Sipho Dube", pieter, "Pieter Jacobs");
    // 12:00 UTC is 14:00 in South Africa
    private final Instant noon = Instant.parse("2026-11-02T12:00:00Z");

    private Slot slot(UUID app, Instant start, int minutes, UUID... people) {
        return new Slot(UUID.randomUUID(), app, start, minutes, Set.of(people));
    }

    @Test
    void noOneInCommonMeansNoClash() {
        var booked = List.of(slot(appA, noon, 60, sipho));
        assertThat(InterviewConflictRules.conflictProblem(slot(appB, noon, 60, pieter), booked, names)).isNull();
    }

    @Test
    void theSameInterviewerAtTheSameTimeClashesAndIsNamed() {
        var booked = List.of(slot(appA, noon, 60, sipho));
        assertThat(InterviewConflictRules.conflictProblem(slot(appB, noon, 60, sipho), booked, names))
                .isEqualTo("Sipho Dube is already booked for another interview from 14:00 to 15:00 on 2 Nov.");
    }

    @Test
    void overlappingByAMinuteClashes() {
        var booked = List.of(slot(appA, noon, 60, sipho));
        assertThat(InterviewConflictRules.conflictProblem(slot(appB, noon.plusSeconds(59 * 60), 30, sipho), booked, names)).contains("already booked");
        assertThat(InterviewConflictRules.conflictProblem(slot(appB, noon.minusSeconds(29 * 60), 30, sipho), booked, names)).contains("already booked");
    }

    @Test
    void backToBackIsFine() {
        var booked = List.of(slot(appA, noon, 60, sipho));
        assertThat(InterviewConflictRules.conflictProblem(slot(appB, noon.plusSeconds(3600), 60, sipho), booked, names)).isNull();
        assertThat(InterviewConflictRules.conflictProblem(slot(appB, noon.minusSeconds(3600), 60, sipho), booked, names)).isNull();
    }

    @Test
    void aPanelistClashesLikeAnInterviewer() {
        var booked = List.of(slot(appA, noon, 90, sipho, pieter));
        assertThat(InterviewConflictRules.conflictProblem(slot(appB, noon.plusSeconds(1800), 60, pieter), booked, names)).startsWith("Pieter Jacobs is already booked");
    }

    @Test
    void theCandidateCannotBeInTwoInterviewsAtOnce() {
        var booked = List.of(slot(appA, noon, 60, sipho));
        assertThat(InterviewConflictRules.conflictProblem(slot(appA, noon.plusSeconds(600), 60, pieter), booked, names))
                .startsWith("This candidate already has an interview from 14:00 to 15:00");
    }

    @Test
    void theInterviewBeingReplacedIsIgnored() {
        var old = slot(appA, noon, 60, sipho);
        var wanted = new Slot(old.interviewId(), appA, noon.plusSeconds(1800), 60, Set.of(sipho));
        assertThat(InterviewConflictRules.conflictProblem(wanted, List.of(old), names)).isNull();
    }

    @Test
    void noTimeMeansNothingToCheck() {
        var booked = List.of(slot(appA, noon, 60, sipho));
        assertThat(InterviewConflictRules.conflictProblem(new Slot(null, appB, null, 60, Set.of(sipho)), booked, names)).isNull();
    }

    @Test
    void anUnnamedPersonIsDescribedGenerically() {
        var booked = List.of(slot(appA, noon, 60, sipho));
        assertThat(InterviewConflictRules.conflictProblem(slot(appB, noon, 60, sipho), booked, Map.of())).startsWith("An interviewer is already booked");
    }

    @Test
    void lengthsAreLimited() {
        assertThat(InterviewConflictRules.durationProblem(null)).isNull();
        assertThat(InterviewConflictRules.durationProblem(15)).isNull();
        assertThat(InterviewConflictRules.durationProblem(480)).isNull();
        assertThat(InterviewConflictRules.durationProblem(14)).contains("between 15 minutes and 8 hours");
        assertThat(InterviewConflictRules.durationProblem(481)).contains("between 15 minutes and 8 hours");
        assertThat(InterviewConflictRules.minutesOrDefault(null)).isEqualTo(60);
        assertThat(InterviewConflictRules.minutesOrDefault(45)).isEqualTo(45);
    }
}
