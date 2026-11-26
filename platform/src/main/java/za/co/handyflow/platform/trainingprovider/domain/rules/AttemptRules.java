package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Rules for assessment attempts and the competency decision. The mark and the decision are separate. No pass mark exists here: the decision
 * is a person's, recorded with a written basis. Attendance is not looked at.
 */
public final class AttemptRules {

    private AttemptRules() {
    }

    public static final String RECORDED = "RECORDED";
    public static final String VOID = "VOID";
    public static final String COMPETENT = "COMPETENT";
    public static final String NOT_YET_COMPETENT = "NOT_YET_COMPETENT";
    public static final Set<String> DECISIONS = Set.of(COMPETENT, NOT_YET_COMPETENT);

    /** Where the learner stands on an instrument, from their earlier attempts. */
    public enum Prior { NONE, OPEN_ATTEMPT, ALREADY_COMPETENT }

    /**
     * Why a mark is wrong for the instrument, or null. A marked instrument needs a mark between 0 and its maximum. An unmarked instrument takes none.
     */
    public static String markProblem(BigDecimal maxMark, BigDecimal mark) {
        if (maxMark == null) {
            return mark == null ? null : "This instrument is not marked, so leave the mark empty";
        }
        if (mark == null) {
            return "This instrument is marked out of " + maxMark.stripTrailingZeros().toPlainString() + ". Enter the mark";
        }
        if (mark.signum() < 0) {
            return "The mark cannot be below 0";
        }
        if (mark.compareTo(maxMark) > 0) {
            return "The mark cannot be more than the maximum of " + maxMark.stripTrailingZeros().toPlainString();
        }
        return null;
    }

    /** Why an attempt cannot be recorded, or null. The assessor's standing is passed in as already checked for the day of the attempt. */
    public static String attemptProblem(String instrumentStatus, UUIDPair versions, String memberStatus, String cohortStatus, LocalDate cohortStart, LocalDate assessedOn,
                                        LocalDate today, boolean assessorActive, PractitionerRules.Standing standing, Prior prior) {
        if (!AssessmentRules.APPROVED.equals(instrumentStatus)) {
            return "Only an approved instrument can be used for an attempt";
        }
        if (!versions.same()) {
            return "This instrument belongs to a different version of the programme than the cohort is running";
        }
        if (!"ACTIVE".equals(memberStatus)) {
            return "This learner has been removed from the cohort";
        }
        if (!CohortRules.COMPLETED.equals(cohortStatus) && !CohortRules.RUNNING.equals(cohortStatus)) {
            return "Attempts can be recorded once the cohort is running";
        }
        if (assessedOn == null) {
            return "Enter the day of the assessment";
        }
        if (assessedOn.isAfter(today)) {
            return "The assessment day cannot be in the future";
        }
        if (cohortStart != null && assessedOn.isBefore(cohortStart)) {
            return "The assessment day cannot be before the cohort started";
        }
        if (!assessorActive) {
            return "That assessor is no longer active";
        }
        if (standing != PractitionerRules.Standing.VERIFIED_IN_FORCE) {
            return "That person has no assessor appointment for this programme that was checked and in force on the day of the assessment";
        }
        if (prior == Prior.OPEN_ATTEMPT) {
            return "This learner already has an attempt on this instrument waiting for a decision. Record the decision or void it first";
        }
        if (prior == Prior.ALREADY_COMPETENT) {
            return "This learner has already been found competent on this instrument";
        }
        return null;
    }

    /** Small carrier so the version comparison reads clearly at the call site. */
    public record UUIDPair(java.util.UUID a, java.util.UUID b) {
        public boolean same() {
            return a != null && a.equals(b);
        }
    }

    public static String correctionProblem(String status, boolean decided, BigDecimal maxMark, BigDecimal newMark, BigDecimal oldMark, String reason) {
        if (!RECORDED.equals(status)) {
            return "A void attempt cannot be changed";
        }
        if (decided) {
            return "A decision has been recorded on this attempt, so its mark cannot be changed. Void the attempt and record it again if it was wrong";
        }
        String markProblem = markProblem(maxMark, newMark);
        if (markProblem != null) {
            return markProblem;
        }
        if (oldMark != null && newMark != null && oldMark.compareTo(newMark) == 0) {
            return "That is the mark already recorded";
        }
        return CohortRules.reasonProblem(reason);
    }

    public static String decisionProblem(String status, boolean decided, String decision, String basis, boolean assessorIsLinkedToAnotherUser) {
        if (!RECORDED.equals(status)) {
            return "A void attempt cannot be decided";
        }
        if (decided) {
            return "A decision has already been recorded on this attempt";
        }
        if (decision == null || !DECISIONS.contains(decision)) {
            return "Choose competent or not yet competent";
        }
        if (!CohortRules.longEnough(basis, CohortRules.MIN_REASON)) {
            return "Write the basis for the decision, in at least " + CohortRules.MIN_REASON + " characters";
        }
        if (assessorIsLinkedToAnotherUser) {
            return "Only the assessor who ran this assessment can record the decision";
        }
        return null;
    }

    /**
     * Whether the assessor may have this decision recorded today, or why not. The appointment must be checked and in force on the day of the
     * decision, not only on the day of the assessment. An assessor with no login cannot record their own decision, so whoever records it for them
     * says how it reached them. A login is only a link to a person: it is never what makes someone an authorised assessor.
     */
    public static String decisionAuthorityProblem(PractitionerRules.Standing standingOnDecisionDay, boolean assessorHasLogin, String recordedForAssessor) {
        if (standingOnDecisionDay != PractitionerRules.Standing.VERIFIED_IN_FORCE) {
            return "The assessor's appointment is not checked and in force today (" + standingOnDecisionDay.note() + "), so the decision cannot be recorded yet";
        }
        if (!assessorHasLogin && !CohortRules.longEnough(recordedForAssessor, CohortRules.MIN_REASON)) {
            return "This assessor has no login, so the decision is recorded for them. Say how their decision reached you (who gave it, and how), in at least "
                    + CohortRules.MIN_REASON + " characters";
        }
        return null;
    }

    public static String voidProblem(String status, String reason) {
        if (VOID.equals(status)) {
            return "This attempt is already void";
        }
        return CohortRules.reasonProblem(reason);
    }
}
