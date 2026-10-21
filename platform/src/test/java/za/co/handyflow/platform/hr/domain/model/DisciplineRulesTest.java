package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.DisciplineRules.Warning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DisciplineRulesTest {

    private static final ErPolicy POLICY = ErPolicy.defaults();
    private static final Instant NOTICE = ZonedDateTime.of(2026, 9, 14, 9, 0, 0, 0, DisciplineRules.ZONE).toInstant();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private static String notice(LocalDate date, LocalTime time, String venue, List<String> charges) {
        return DisciplineRules.noticeProblem(NOTICE, date, time, venue, charges, true, POLICY);
    }

    @Test
    void aNoticeNeedsChargesAndAVenue() {
        LocalDate d = LocalDate.of(2026, 9, 18);
        assertThat(notice(d, LocalTime.of(10, 0), "Boardroom", List.of("Late on 3 days"))).isNull();
        assertThat(notice(d, LocalTime.of(10, 0), "Boardroom", List.of())).contains("at least one charge");
        assertThat(notice(d, LocalTime.of(10, 0), "Boardroom", List.of(" "))).contains("cannot be empty");
        assertThat(notice(d, LocalTime.of(10, 0), " ", List.of("x"))).contains("venue");
        assertThat(notice(null, LocalTime.of(10, 0), "Boardroom", List.of("x"))).contains("date and time");
    }

    @Test
    void aNoticeMustGiveTheTimeThatCompanyPolicySets() {
        assertThat(notice(LocalDate.of(2026, 9, 16), LocalTime.of(9, 0), "Room", List.of("x"))).isNull();
        assertThat(notice(LocalDate.of(2026, 9, 16), LocalTime.of(8, 59), "Room", List.of("x"))).contains("47 hours");
        assertThat(notice(LocalDate.of(2026, 9, 14), LocalTime.of(8, 0), "Room", List.of("x"))).contains("after the notice");
    }

    @Test
    void anIncapacityInvitationNeedsNoCharges() {
        assertThat(DisciplineRules.noticeProblem(NOTICE, LocalDate.of(2026, 9, 18), LocalTime.of(10, 0), "Room", List.of(), false, POLICY)).isNull();
    }

    @Test
    void theChairIsNeitherTheEmployeeNorTheInitiator() {
        UUID emp = UUID.randomUUID(), init = UUID.randomUUID(), chair = UUID.randomUUID();
        assertThat(DisciplineRules.chairProblem(chair, emp, init)).isNull();
        assertThat(DisciplineRules.chairProblem(emp, emp, init)).contains("cannot chair their own");
        assertThat(DisciplineRules.chairProblem(init, emp, init)).contains("bringing the charges");
        assertThat(DisciplineRules.chairProblem(null, emp, init)).contains("Choose");
        assertThat(DisciplineRules.chairProblem(chair, emp, null)).isNull();
    }

    @Test
    void aFinalWarningOrDismissalCannotBeGivenWithoutAHearing() {
        assertThat(DisciplineRules.informalProblem("VERBAL_WARNING", "He said the bus was late", "Pattern of lateness")).isNull();
        assertThat(DisciplineRules.informalProblem("WRITTEN_WARNING", "She agreed", "Second offence")).isNull();
        assertThat(DisciplineRules.informalProblem("COUNSELLING", "ok", "Coached")).isNull();
        assertThat(DisciplineRules.informalProblem("FINAL_WRITTEN_WARNING", "ok", "r")).contains("hearing");
        assertThat(DisciplineRules.informalProblem("DISMISSAL", "ok", "r")).contains("hearing");
    }

    @Test
    void anInformalSanctionNeedsTheEmployeesSideAndReasons() {
        assertThat(DisciplineRules.informalProblem("VERBAL_WARNING", " ", "r")).contains("what the employee said");
        assertThat(DisciplineRules.informalProblem("VERBAL_WARNING", "ok", " ")).contains("reasons");
    }

    @Test
    void aHearingCannotBeRecordedBeforeItHappenedOrBeforeTheNotice() {
        LocalDate notice = LocalDate.of(2026, 9, 14);
        assertThat(DisciplineRules.hearingProblem(LocalDate.of(2026, 9, 18), notice, TODAY, true, "Heard both sides")).isNull();
        assertThat(DisciplineRules.hearingProblem(LocalDate.of(2026, 10, 21), notice, TODAY, true, "x")).contains("before it has happened");
        assertThat(DisciplineRules.hearingProblem(LocalDate.of(2026, 9, 13), notice, TODAY, true, "x")).contains("before the notice");
        assertThat(DisciplineRules.hearingProblem(LocalDate.of(2026, 9, 18), notice, TODAY, null, "x")).contains("attended");
        assertThat(DisciplineRules.hearingProblem(LocalDate.of(2026, 9, 18), notice, TODAY, false, " ")).contains("record of the hearing");
    }

    @Test
    void aFindingIsNeededOnEveryCharge() {
        assertThat(DisciplineRules.decisionProblem(Arrays.asList("GUILTY", null), "VERBAL_WARNING", 3, "Reasons")).contains("every charge");
        assertThat(DisciplineRules.decisionProblem(List.of(), "VERBAL_WARNING", 3, "Reasons")).contains("no charges");
    }

    @Test
    void noSanctionFollowsAnAcquittal() {
        assertThat(DisciplineRules.decisionProblem(List.of("NOT_GUILTY", "NOT_GUILTY"), "NOT_GUILTY", null, "No evidence")).isNull();
        assertThat(DisciplineRules.decisionProblem(List.of("NOT_GUILTY"), "VERBAL_WARNING", 3, "x")).contains("not guilty");
        assertThat(DisciplineRules.decisionProblem(List.of("NOT_GUILTY"), "NOT_GUILTY", 3, "x")).contains("cannot be given");
    }

    @Test
    void aGuiltyFindingNeedsASanctionAndReasons() {
        assertThat(DisciplineRules.decisionProblem(List.of("GUILTY", "NOT_GUILTY"), "FINAL_WRITTEN_WARNING", 12, "Repeat offence")).isNull();
        assertThat(DisciplineRules.decisionProblem(List.of("GUILTY"), null, null, "x")).contains("Choose the sanction");
        assertThat(DisciplineRules.decisionProblem(List.of("GUILTY"), "NOT_GUILTY", null, "x")).contains("Choose the sanction");
        assertThat(DisciplineRules.decisionProblem(List.of("GUILTY"), "SUSPENSION", null, "x")).contains("Choose the sanction");
        assertThat(DisciplineRules.decisionProblem(List.of("GUILTY"), "VERBAL_WARNING", 3, " ")).contains("reasons");
    }

    @Test
    void aWarningNeedsAValidityPeriodAndNothingElseDoes() {
        assertThat(DisciplineRules.decisionProblem(List.of("GUILTY"), "WRITTEN_WARNING", null, "x")).contains("months");
        assertThat(DisciplineRules.decisionProblem(List.of("GUILTY"), "WRITTEN_WARNING", 0, "x")).contains("months");
        assertThat(DisciplineRules.decisionProblem(List.of("GUILTY"), "WRITTEN_WARNING", 61, "x")).contains("months");
        assertThat(DisciplineRules.decisionProblem(List.of("GUILTY"), "DISMISSAL", 6, "x")).contains("Only a warning");
        assertThat(DisciplineRules.decisionProblem(List.of("GUILTY"), "DISMISSAL", null, "Serious dishonesty")).isNull();
    }

    @Test
    void aWarningIsInForceFromIssueToItsLastDay() {
        Warning w = new Warning("WRITTEN_WARNING", LocalDate.of(2026, 3, 1), DisciplineRules.validUntil(LocalDate.of(2026, 3, 1), 6));
        assertThat(w.validUntil()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(DisciplineRules.active(List.of(w), LocalDate.of(2026, 3, 1))).hasSize(1);
        assertThat(DisciplineRules.active(List.of(w), LocalDate.of(2026, 9, 1))).hasSize(1);
        assertThat(DisciplineRules.active(List.of(w), LocalDate.of(2026, 9, 2))).isEmpty();
        assertThat(DisciplineRules.active(List.of(w), LocalDate.of(2026, 2, 28))).isEmpty();
    }

    @Test
    void aWarningWithNoRecordedValidityIsNotCountedButIsReported() {
        Warning old = new Warning("FINAL_WRITTEN_WARNING", LocalDate.of(2025, 1, 10), null);
        assertThat(DisciplineRules.active(List.of(old), TODAY)).isEmpty();
        assertThat(DisciplineRules.unknownValidity(List.of(old))).hasSize(1);
        Warning dismissal = new Warning("DISMISSAL", LocalDate.of(2025, 1, 10), null);
        assertThat(DisciplineRules.active(List.of(dismissal), TODAY)).isEmpty();
        assertThat(DisciplineRules.unknownValidity(List.of(dismissal))).isEmpty();
    }

    @Test
    void guidanceFollowsTheHighestWarningInForceAndNeverDecides() {
        LocalDate on = LocalDate.of(2026, 9, 1);
        assertThat(DisciplineRules.guidance(List.of())).startsWith("No warning is in force");
        assertThat(DisciplineRules.guidance(List.of(new Warning("VERBAL_WARNING", on, on.plusMonths(3))))).contains("usually a written warning");
        assertThat(DisciplineRules.guidance(List.of(new Warning("VERBAL_WARNING", on, on.plusMonths(3)), new Warning("WRITTEN_WARNING", on, on.plusMonths(6)))))
                .contains("usually a final written warning");
        assertThat(DisciplineRules.guidance(List.of(new Warning("FINAL_WRITTEN_WARNING", on, on.plusMonths(12))))).contains("can justify dismissal");
        assertThat(DisciplineRules.guidance(List.of())).contains("the chair decides");
    }

    @Test
    void dismissalNeedsTheCcmaRightExplainedAndGivesThe30DayDate() {
        LocalDate decided = LocalDate.of(2026, 10, 12);
        assertThat(DisciplineRules.communicationProblem("DISMISSAL", LocalDate.of(2026, 10, 13), decided, TODAY, false)).contains("CCMA");
        assertThat(DisciplineRules.communicationProblem("DISMISSAL", LocalDate.of(2026, 10, 13), decided, TODAY, true)).isNull();
        assertThat(DisciplineRules.communicationProblem("VERBAL_WARNING", LocalDate.of(2026, 10, 13), decided, TODAY, false)).isNull();
        assertThat(DisciplineRules.communicationProblem("VERBAL_WARNING", LocalDate.of(2026, 10, 11), decided, TODAY, false)).contains("before the decision");
        assertThat(DisciplineRules.communicationProblem("VERBAL_WARNING", LocalDate.of(2026, 10, 21), decided, TODAY, false)).contains("future");
        assertThat(DisciplineRules.communicationProblem("VERBAL_WARNING", null, decided, TODAY, false)).contains("date");
        assertThat(DisciplineRules.ccmaDeadline(LocalDate.of(2026, 10, 13))).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    void anAppealMustBeLodgedInTimeUnlessHrAcceptsItLate() {
        LocalDate told = LocalDate.of(2026, 10, 1);
        assertThat(DisciplineRules.appealProblem(told, LocalDate.of(2026, 10, 8), TODAY, "Too harsh", false, POLICY)).isNull();
        assertThat(DisciplineRules.appealProblem(told, LocalDate.of(2026, 10, 9), TODAY, "Too harsh", false, POLICY)).contains("ended on 2026-10-08");
        assertThat(DisciplineRules.appealProblem(told, LocalDate.of(2026, 10, 9), TODAY, "Too harsh", true, POLICY)).isNull();
        assertThat(DisciplineRules.appealProblem(told, LocalDate.of(2026, 9, 30), TODAY, "x", true, POLICY)).contains("before the outcome");
        assertThat(DisciplineRules.appealProblem(told, LocalDate.of(2026, 10, 21), TODAY, "x", true, POLICY)).contains("future");
        assertThat(DisciplineRules.appealProblem(null, LocalDate.of(2026, 10, 8), TODAY, "x", true, POLICY)).contains("not been communicated");
        assertThat(DisciplineRules.appealProblem(told, LocalDate.of(2026, 10, 3), TODAY, " ", true, POLICY)).contains("grounds");
    }

    @Test
    void theAppealIsDecidedBySomeoneNewToTheCase() {
        UUID emp = UUID.randomUUID(), chair = UUID.randomUUID(), init = UUID.randomUUID(), other = UUID.randomUUID();
        assertThat(DisciplineRules.appealDeciderProblem(other, emp, chair, init)).isNull();
        assertThat(DisciplineRules.appealDeciderProblem(chair, emp, chair, init)).contains("other than the chair");
        assertThat(DisciplineRules.appealDeciderProblem(emp, emp, chair, init)).contains("own appeal");
        assertThat(DisciplineRules.appealDeciderProblem(init, emp, chair, init)).contains("brought the charges");
        assertThat(DisciplineRules.appealDeciderProblem(null, emp, chair, init)).contains("Choose");
    }

    @Test
    void anAppealCanLowerOrOverturnButNeverRaiseASanction() {
        assertThat(DisciplineRules.appealOutcomeProblem("CONFIRMED", "FINAL_WRITTEN_WARNING", null, null, "Fair")).isNull();
        assertThat(DisciplineRules.appealOutcomeProblem("OVERTURNED", "DISMISSAL", null, null, "Procedure was unfair")).isNull();
        assertThat(DisciplineRules.appealOutcomeProblem("VARIED", "DISMISSAL", "FINAL_WRITTEN_WARNING", 12, "Too harsh")).isNull();
        assertThat(DisciplineRules.appealOutcomeProblem("VARIED", "WRITTEN_WARNING", "FINAL_WRITTEN_WARNING", 12, "x")).contains("lower");
        assertThat(DisciplineRules.appealOutcomeProblem("VARIED", "WRITTEN_WARNING", "WRITTEN_WARNING", 6, "x")).contains("lower");
        assertThat(DisciplineRules.appealOutcomeProblem("VARIED", "FINAL_WRITTEN_WARNING", "WRITTEN_WARNING", null, "x")).contains("months");
        assertThat(DisciplineRules.appealOutcomeProblem("VARIED", "DISMISSAL", null, null, "x")).contains("lower sanction");
        assertThat(DisciplineRules.appealOutcomeProblem("MAYBE", "DISMISSAL", null, null, "x")).contains("Choose");
        assertThat(DisciplineRules.appealOutcomeProblem("CONFIRMED", "DISMISSAL", null, null, " ")).contains("reasons");
    }

    @Test
    void aPrecautionarySuspensionNeedsAReasonAndASensibleDateRange() {
        assertThat(DisciplineRules.suspensionProblem(LocalDate.of(2026, 10, 19), null, "Risk of interfering with witnesses", TODAY)).isNull();
        assertThat(DisciplineRules.suspensionProblem(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 18), "r", TODAY)).contains("cannot end before");
        assertThat(DisciplineRules.suspensionProblem(LocalDate.of(2026, 10, 19), null, " ", TODAY)).contains("reason");
        assertThat(DisciplineRules.suspensionProblem(LocalDate.of(2026, 11, 20), null, "r", TODAY)).contains("more than a week");
        assertThat(DisciplineRules.suspensionProblem(null, null, "r", TODAY)).contains("starts");
    }

    @Test
    void severityOrdersTheSanctions() {
        assertThat(DisciplineRules.severity("NOT_GUILTY")).isEqualTo(0);
        assertThat(DisciplineRules.severity("VERBAL_WARNING")).isEqualTo(2);
        assertThat(DisciplineRules.severity("DISMISSAL")).isEqualTo(5);
        assertThat(DisciplineRules.severity("NONSENSE")).isEqualTo(-1);
        assertThat(DisciplineRules.severity(null)).isEqualTo(-1);
    }
}
