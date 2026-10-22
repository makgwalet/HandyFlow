package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PerfRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final PerfRules.Who SUBJECT = PerfRules.Who.of(true, false, false);
    private static final PerfRules.Who BOSS = PerfRules.Who.of(false, true, false);
    private static final PerfRules.Who HR = PerfRules.Who.of(false, false, true);

    @Test
    void aSubjectIsNeverTreatedAsTheirOwnReviewerOrHr() {
        PerfRules.Who w = PerfRules.Who.of(true, true, true);
        assertThat(w.subject()).isTrue();
        assertThat(w.reviewer()).isFalse();
        assertThat(w.hr()).isFalse();
    }

    @Test
    void permissions() {
        assertThat(PerfRules.canRead(Set.of("HR_PERF_READ"))).isTrue();
        assertThat(PerfRules.canRead(Set.of("HR_PERF_MANAGE"))).isTrue();
        assertThat(PerfRules.canRead(Set.of("HR_READ"))).isFalse();
        assertThat(PerfRules.canManage(Set.of("HR_PERF_READ"))).isFalse();
    }

    @Test
    void aCycleNeedsAPeriodAndSensibleDueDates() {
        LocalDate s = LocalDate.of(2026, 3, 1), e = LocalDate.of(2026, 8, 31);
        assertThat(PerfRules.cycleProblem("Mid-year 2026", s, e, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 30))).isNull();
        assertThat(PerfRules.cycleProblem(" ", s, e, s, e)).contains("required");
        assertThat(PerfRules.cycleProblem("Mid-year", e, s, s, e)).contains("cannot end before");
        assertThat(PerfRules.cycleProblem("Mid-year", s, e, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 15))).contains("manager review cannot be due before");
        assertThat(PerfRules.cycleProblem("Mid-year", s, e, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 9, 15))).contains("before the period starts");
        assertThat(PerfRules.cycleProblem("Mid-year", s, s.plusDays(500), s, e)).contains("too long");
        assertThat(PerfRules.cycleProblem("Mid-year", s, e, null, null)).contains("due");
    }

    @Test
    void ratingsAreOneToFiveWithLabels() {
        assertThat(PerfRules.ratingLabel(3)).isEqualTo("Meets expectations");
        assertThat(PerfRules.ratingLabel(0)).isNull();
        assertThat(PerfRules.ratingLabel(null)).isNull();
        assertThat(PerfRules.selfProblem(6, "x")).contains("1 to 5");
        assertThat(PerfRules.selfProblem(4, null)).isNull();
    }

    @Test
    void aManagerMustGiveReasonsAndExtremeRatingsNeedMore() {
        assertThat(PerfRules.managerProblem(3, "Solid year.")).isNull();
        assertThat(PerfRules.managerProblem(3, "")).contains("required");
        assertThat(PerfRules.managerProblem(2, "Late on tasks")).contains("20 characters");
        assertThat(PerfRules.managerProblem(5, "Great")).contains("20 characters");
        assertThat(PerfRules.managerProblem(5, "Led the migration with no downtime.")).isNull();
        assertThat(PerfRules.managerProblem(null, "ok")).contains("1 to 5");
    }

    @Test
    void changingTheFinalRatingNeedsADifferentRatingAndAReason() {
        assertThat(PerfRules.calibrationProblem(4, "Aligned with the department distribution.", 3)).isNull();
        assertThat(PerfRules.calibrationProblem(3, "Aligned with the department.", 3)).contains("already");
        assertThat(PerfRules.calibrationProblem(4, "short", 3)).contains("too short");
    }

    @Test
    void anEmployeeDoesTheSelfReviewFirstAndTheManagerAfterOrAfterTheDueDate() {
        LocalDate selfDue = LocalDate.of(2026, 10, 30);
        assertThat(PerfRules.reviewActions(SUBJECT, "AWAITING_SELF", "OPEN", false, true, selfDue, TODAY)).containsExactly("SELF");
        assertThat(PerfRules.reviewActions(BOSS, "AWAITING_SELF", "OPEN", false, true, selfDue, TODAY)).isEmpty();
        assertThat(PerfRules.reviewActions(BOSS, "AWAITING_SELF", "OPEN", false, true, selfDue, selfDue.plusDays(1))).containsExactly("MANAGER");
        assertThat(PerfRules.reviewActions(BOSS, "AWAITING_MANAGER", "OPEN", false, true, selfDue, TODAY)).containsExactly("MANAGER");
        assertThat(PerfRules.reviewActions(SUBJECT, "AWAITING_MANAGER", "OPEN", false, true, selfDue, TODAY)).isEmpty();
    }

    @Test
    void nothingIsDoneInAClosedCycleExceptAcknowledgingAndHrWrapUp() {
        LocalDate d = LocalDate.of(2026, 10, 30);
        assertThat(PerfRules.reviewActions(SUBJECT, "AWAITING_SELF", "CLOSED", false, true, d, TODAY)).isEmpty();
        assertThat(PerfRules.reviewActions(SUBJECT, "SHARED", "CLOSED", false, true, d, TODAY)).containsExactly("ACKNOWLEDGE");
        assertThat(PerfRules.reviewActions(BOSS, "AWAITING_MANAGER", "CLOSED", false, true, d, TODAY)).isEmpty();
    }

    @Test
    void hrCalibratesAndSharesOnlyAfterTheManagerHasSubmitted() {
        LocalDate d = LocalDate.of(2026, 10, 30);
        assertThat(PerfRules.reviewActions(HR, "SUBMITTED", "OPEN", false, true, d, TODAY)).contains("CALIBRATE", "SHARE", "EXCLUDE").doesNotContain("REVIEWER");
        assertThat(PerfRules.reviewActions(HR, "AWAITING_MANAGER", "OPEN", false, true, d, TODAY)).doesNotContain("SHARE", "CALIBRATE").contains("EXCLUDE", "REVIEWER");
        assertThat(PerfRules.reviewActions(HR, "SHARED", "OPEN", false, true, d, TODAY)).isEmpty();
        assertThat(PerfRules.reviewActions(HR, "ACKNOWLEDGED", "OPEN", false, true, d, TODAY)).isEmpty();
    }

    @Test
    void feedbackCanBeRequestedOnlyWhenTheCycleUsesItAndThereIsAReviewer() {
        LocalDate d = LocalDate.of(2026, 10, 30);
        assertThat(PerfRules.reviewActions(BOSS, "AWAITING_MANAGER", "OPEN", true, true, d, TODAY)).contains("REQUEST_FEEDBACK");
        assertThat(PerfRules.reviewActions(BOSS, "AWAITING_MANAGER", "OPEN", false, true, d, TODAY)).doesNotContain("REQUEST_FEEDBACK");
        assertThat(PerfRules.reviewActions(HR, "AWAITING_MANAGER", "OPEN", true, false, d, TODAY)).doesNotContain("REQUEST_FEEDBACK");
        assertThat(PerfRules.reviewActions(SUBJECT, "AWAITING_SELF", "OPEN", true, true, d, TODAY)).doesNotContain("REQUEST_FEEDBACK");
    }

    @Test
    void anEmployeeSeesTheManagerSideAndFeedbackOnlyOnceReleased() {
        PerfRules.Sight before = PerfRules.sight(SUBJECT, "SUBMITTED");
        assertThat(before.self()).isTrue();
        assertThat(before.manager()).isFalse();
        assertThat(before.feedback()).isFalse();
        PerfRules.Sight after = PerfRules.sight(SUBJECT, "SHARED");
        assertThat(after.manager()).isTrue();
        assertThat(after.feedback()).isTrue();
        assertThat(after.feedbackNames()).isFalse();
        assertThat(PerfRules.sight(PerfRules.Who.of(false, false, false), "SHARED").self()).isFalse();
    }

    @Test
    void onlyHrSeesWhoGaveFeedback() {
        assertThat(PerfRules.sight(HR, "SUBMITTED").feedbackNames()).isTrue();
        assertThat(PerfRules.sight(BOSS, "SUBMITTED").feedbackNames()).isFalse();
        assertThat(PerfRules.sight(BOSS, "SUBMITTED").feedback()).isTrue();
    }

    @Test
    void reviewerChangesNeedAReasonAndNobodyReviewsThemselves() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertThat(PerfRules.reviewerProblem(a, b, "Manager left")).isNull();
        assertThat(PerfRules.reviewerProblem(a, a, "Manager left")).contains("themselves");
        assertThat(PerfRules.reviewerProblem(null, b, "x")).contains("Choose");
        assertThat(PerfRules.reviewerProblem(a, b, " ")).contains("required");
    }

    @Test
    void aCycleCannotCloseWithReviewsStillBeingWorked() {
        assertThat(PerfRules.closeCycleProblem(0)).isNull();
        assertThat(PerfRules.closeCycleProblem(1)).contains("1 review is");
        assertThat(PerfRules.closeCycleProblem(4)).contains("4 reviews are");
    }

    @Test
    void feedbackRequestsAreChecked() {
        UUID subject = UUID.randomUUID(), boss = UUID.randomUUID(), p = UUID.randomUUID();
        assertThat(PerfRules.requestProblem(p, subject, boss, false, 0, "PEER")).isNull();
        assertThat(PerfRules.requestProblem(subject, subject, boss, false, 0, "PEER")).contains("themselves");
        assertThat(PerfRules.requestProblem(boss, subject, boss, false, 0, "PEER")).contains("reviewer");
        assertThat(PerfRules.requestProblem(p, subject, boss, true, 1, "PEER")).contains("already");
        assertThat(PerfRules.requestProblem(p, subject, boss, false, 15, "PEER")).contains("At most");
        assertThat(PerfRules.requestProblem(p, subject, boss, false, 0, "FRIEND")).contains("how this person works");
        assertThat(PerfRules.requestProblem(null, subject, boss, false, 0, "PEER")).contains("Choose");
    }

    @Test
    void feedbackNeedsARatingAndSomethingSpecific() {
        assertThat(PerfRules.feedbackProblem(4, "Always helps new starters settle in.", null)).isNull();
        assertThat(PerfRules.feedbackProblem(4, null, "Could share status earlier in the week.")).isNull();
        assertThat(PerfRules.feedbackProblem(4, "nice", "ok")).contains("specific");
        assertThat(PerfRules.feedbackProblem(null, "Always helps new starters settle in.", null)).contains("1 to 5");
        assertThat(PerfRules.feedbackProblem(3, "x".repeat(2001), null)).contains("2000");
    }

    @Test
    void theSummaryIsHiddenUntilThreePeopleHaveReplied() {
        PerfRules.Summary two = PerfRules.summarise(List.of(new PerfRules.Reply(4, "Kind", null), new PerfRules.Reply(5, "Reliable", null)));
        assertThat(two.shown()).isFalse();
        assertThat(two.replies()).isEqualTo(2);
        assertThat(two.average()).isNull();
        assertThat(two.strengths()).isEmpty();
    }

    @Test
    void theSummaryAveragesAndOrdersCommentsWithoutNamesOrOrder() {
        PerfRules.Summary s = PerfRules.summarise(List.of(
                new PerfRules.Reply(4, "reliable under pressure", "Document more"),
                new PerfRules.Reply(5, "Always helps others", null),
                new PerfRules.Reply(4, "Calm", "Say no more often")));
        assertThat(s.shown()).isTrue();
        assertThat(s.replies()).isEqualTo(3);
        assertThat(s.average()).isEqualTo(4.3);
        assertThat(s.strengths()).containsExactly("Always helps others", "Calm", "reliable under pressure");
        assertThat(s.improvements()).containsExactly("Document more", "Say no more often");
    }

    @Test
    void goalsNeedATitleAndADueDate() {
        assertThat(PerfRules.goalProblem("Cut invoice turnaround", null, "Days to invoice", TODAY.plusMonths(3), TODAY, true)).isNull();
        assertThat(PerfRules.goalProblem("ab", null, null, TODAY.plusDays(5), TODAY, true)).contains("too short");
        assertThat(PerfRules.goalProblem("Cut invoice turnaround", null, null, null, TODAY, true)).contains("due");
        assertThat(PerfRules.goalProblem("Cut invoice turnaround", null, null, TODAY.minusDays(1), TODAY, true)).contains("past");
        assertThat(PerfRules.goalProblem("Cut invoice turnaround", null, null, TODAY.minusDays(1), TODAY, false)).isNull();
        assertThat(PerfRules.goalProblem("Cut invoice turnaround", null, null, TODAY.plusYears(6), TODAY, true)).contains("five years");
    }

    @Test
    void progressIsAPercentage() {
        assertThat(PerfRules.progressProblem(0, null)).isNull();
        assertThat(PerfRules.progressProblem(100, "Done")).isNull();
        assertThat(PerfRules.progressProblem(101, null)).contains("0 to 100");
        assertThat(PerfRules.progressProblem(-1, null)).contains("0 to 100");
        assertThat(PerfRules.progressProblem(null, null)).contains("0 to 100");
    }

    @Test
    void closingAGoalThatWasNotFullyAchievedNeedsAnExplanation() {
        assertThat(PerfRules.goalCloseProblem("ACHIEVED", null)).isNull();
        assertThat(PerfRules.goalCloseProblem("PARTLY_ACHIEVED", null)).contains("required");
        assertThat(PerfRules.goalCloseProblem("NOT_ACHIEVED", "Budget was cut in July.")).isNull();
        assertThat(PerfRules.goalCloseProblem("DONE", "x")).contains("Choose");
    }

    @Test
    void anEmployeeProposesAndTheirManagerApproves() {
        assertThat(PerfRules.initialGoalStatus(true)).isEqualTo("PROPOSED");
        assertThat(PerfRules.initialGoalStatus(false)).isEqualTo("ACTIVE");
        assertThat(PerfRules.goalActions(SUBJECT, "PROPOSED")).containsExactly("EDIT", "CANCEL");
        assertThat(PerfRules.goalActions(BOSS, "PROPOSED")).containsExactly("EDIT", "APPROVE", "CANCEL");
        assertThat(PerfRules.goalActions(SUBJECT, "ACTIVE")).containsExactly("PROGRESS");
        assertThat(PerfRules.goalActions(BOSS, "ACTIVE")).containsExactly("EDIT", "PROGRESS", "CLOSE", "CANCEL");
        assertThat(PerfRules.goalActions(HR, "ACTIVE")).contains("CLOSE");
        assertThat(PerfRules.goalActions(SUBJECT, "ACHIEVED")).isEmpty();
        assertThat(PerfRules.goalActions(BOSS, "CANCELLED")).isEmpty();
    }

    @Test
    void aPlanStatesTheShortfallTheStandardAndTheSupport() {
        LocalDate s = LocalDate.of(2026, 11, 2);
        String t = "Reduce invoice errors";
        String ok = "Ten or more characters of detail.";
        assertThat(PerfRules.planProblem(t, ok, ok, ok, s, s.plusDays(60))).isNull();
        assertThat(PerfRules.planProblem(t, "short", ok, ok, s, s.plusDays(60))).contains("shortfall");
        assertThat(PerfRules.planProblem(t, ok, "short", ok, s, s.plusDays(60))).contains("standard");
        assertThat(PerfRules.planProblem(t, ok, ok, "short", s, s.plusDays(60))).contains("support");
        assertThat(PerfRules.planProblem(t, ok, ok, ok, s, s.minusDays(1))).contains("cannot end before");
        assertThat(PerfRules.planProblem(t, ok, ok, ok, s, s.plusDays(400))).contains("too long");
        assertThat(PerfRules.planProblem(t, ok, ok, ok, null, null)).contains("start and end");
    }

    @Test
    void startingAPlanRecordsWhenTheEmployeeWasTold() {
        assertThat(PerfRules.startProblem(TODAY, TODAY)).isNull();
        assertThat(PerfRules.startProblem(null, TODAY)).contains("told");
        assertThat(PerfRules.startProblem(TODAY.plusDays(1), TODAY)).contains("future");
    }

    @Test
    void aCheckInIsDatedWithinThePlanAndSaysHowItIsGoing() {
        LocalDate s = LocalDate.of(2026, 10, 1);
        assertThat(PerfRules.checkinProblem(TODAY, s, TODAY, "ON_TRACK", "Reviewed the week's invoices together.")).isNull();
        assertThat(PerfRules.checkinProblem(s.minusDays(1), s, TODAY, "ON_TRACK", "Reviewed the week's invoices.")).contains("before the plan starts");
        assertThat(PerfRules.checkinProblem(TODAY.plusDays(1), s, TODAY, "ON_TRACK", "Reviewed the week's invoices.")).contains("before it happens");
        assertThat(PerfRules.checkinProblem(TODAY, s, TODAY, "FINE", "Reviewed the week's invoices.")).contains("on track");
        assertThat(PerfRules.checkinProblem(TODAY, s, TODAY, "AT_RISK", "short")).contains("too short");
    }

    @Test
    void timeForAPlanCanBeLengthenedNeverShortened() {
        LocalDate end = LocalDate.of(2026, 12, 1);
        assertThat(PerfRules.extendProblem(end.plusDays(14), end, "Employee was on sick leave for two weeks.")).isNull();
        assertThat(PerfRules.extendProblem(end, end, "Employee was on sick leave.")).contains("later than");
        assertThat(PerfRules.extendProblem(end.minusDays(1), end, "Employee was on sick leave.")).contains("later than");
        assertThat(PerfRules.extendProblem(end.plusDays(14), end, "")).contains("required");
    }

    @Test
    void aPlanIsNotMetBeforeTheTimePromisedHasRunAndNeedsARecord() {
        LocalDate end = LocalDate.of(2026, 12, 1);
        String note = "Error rate stayed above the standard.";
        assertThat(PerfRules.closePlanProblem("NOT_MET", note, end, end, 3)).isNull();
        assertThat(PerfRules.closePlanProblem("NOT_MET", note, end.minusDays(1), end, 3)).contains("all the time");
        assertThat(PerfRules.closePlanProblem("NOT_MET", note, end, end, 0)).contains("check-in");
        assertThat(PerfRules.closePlanProblem("MET", "Met the standard for six weeks.", end.minusDays(20), end, 0)).isNull();
        assertThat(PerfRules.closePlanProblem("DONE", note, end, end, 3)).contains("Choose");
        assertThat(PerfRules.closePlanProblem("MET", "ok", end, end, 3)).contains("too short");
    }

    @Test
    void aShortPlanIsFlaggedAgainstTheCompanyMinimum() {
        LocalDate s = LocalDate.of(2026, 11, 1);
        assertThat(PerfRules.shorterThanMinimum(s, s.plusDays(13), 28)).isTrue();
        assertThat(PerfRules.shorterThanMinimum(s, s.plusDays(27), 28)).isFalse();
        assertThat(PerfRules.shorterThanMinimum(null, s, 28)).isFalse();
    }

    @Test
    void aPlanCanOnlyBelongToAnOpenPoorPerformanceCaseAboutTheSameEmployee() {
        UUID e = UUID.randomUUID();
        assertThat(PerfRules.caseLinkProblem("INCAPACITY", "POOR_PERFORMANCE", "INVESTIGATING", e, e)).isNull();
        assertThat(PerfRules.caseLinkProblem("INCAPACITY", "ILL_HEALTH", "INVESTIGATING", e, e)).contains("poor performance");
        assertThat(PerfRules.caseLinkProblem("DISCIPLINARY", "LATENESS", "INVESTIGATING", e, e)).contains("poor performance");
        assertThat(PerfRules.caseLinkProblem("INCAPACITY", "POOR_PERFORMANCE", "CLOSED", e, e)).contains("closed");
        assertThat(PerfRules.caseLinkProblem("INCAPACITY", "POOR_PERFORMANCE", "WITHDRAWN", e, e)).contains("closed");
        assertThat(PerfRules.caseLinkProblem("INCAPACITY", "POOR_PERFORMANCE", "INVESTIGATING", UUID.randomUUID(), e)).contains("different employee");
    }

    @Test
    void whoMayDoWhatWithAPlan() {
        PerfRules.Who owner = PerfRules.Who.of(false, true, false);
        assertThat(PerfRules.planActions(HR, "DRAFT", false)).containsExactly("EDIT", "START", "LINK", "CANCEL");
        assertThat(PerfRules.planActions(HR, "ACTIVE", false)).containsExactly("CHECKIN", "EXTEND", "CLOSE", "LINK", "CANCEL");
        assertThat(PerfRules.planActions(owner, "ACTIVE", false)).containsExactly("CHECKIN");
        assertThat(PerfRules.planActions(owner, "DRAFT", false)).isEmpty();
        assertThat(PerfRules.planActions(SUBJECT, "ACTIVE", false)).containsExactly("ACKNOWLEDGE");
        assertThat(PerfRules.planActions(SUBJECT, "ACTIVE", true)).isEmpty();
        assertThat(PerfRules.planActions(SUBJECT, "DRAFT", false)).isEmpty();
        assertThat(PerfRules.planActions(HR, "NOT_MET", false)).isEmpty();
        assertThat(PerfRules.planActions(SUBJECT, "NOT_MET", false)).containsExactly("ACKNOWLEDGE");
    }

    @Test
    void anEmployeeSeesAPlanOnlyOnceTheyHaveBeenTold() {
        assertThat(PerfRules.canSeePlan(SUBJECT, false, false, false)).isFalse();
        assertThat(PerfRules.canSeePlan(SUBJECT, false, false, true)).isTrue();
        assertThat(PerfRules.canSeePlan(HR, true, false, false)).isTrue();
        assertThat(PerfRules.canSeePlan(BOSS, false, true, false)).isTrue();
        assertThat(PerfRules.canSeePlan(PerfRules.Who.of(false, false, false), false, false, true)).isFalse();
    }
}
