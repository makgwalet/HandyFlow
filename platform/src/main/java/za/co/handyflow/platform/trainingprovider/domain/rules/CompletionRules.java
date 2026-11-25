package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Rules for completion records. Readiness is advice built from competency decisions and moderation. It never completes anyone: a person records
 * the outcome with a written basis, and completing with items still open needs a written reason. Attendance is not consulted. Completion is not
 * certification.
 */
public final class CompletionRules {

    private CompletionRules() {
    }

    public static final String COMPLETED = "COMPLETED";
    public static final String DID_NOT_COMPLETE = "DID_NOT_COMPLETE";
    public static final Set<String> OUTCOMES = Set.of(COMPLETED, DID_NOT_COMPLETE);
    public static final String RECORDED = "RECORDED";
    public static final String WITHDRAWN = "WITHDRAWN";

    public static final String COMPETENT = "COMPETENT";
    public static final String NO_ATTEMPT = "NO_ATTEMPT";
    public static final String WAITING_DECISION = "WAITING_DECISION";
    public static final String NOT_YET_COMPETENT = "NOT_YET_COMPETENT";
    public static final String WAITING_MODERATION = "WAITING_MODERATION";
    public static final String QUERY_OPEN = "QUERY_OPEN";

    private static final Set<String> COMPLETABLE = Set.of(EnrolmentRules.ENROLLED, EnrolmentRules.IN_PROGRESS);

    /** What is known about one live (not void) attempt. moderationOutcome is null when it has not been moderated. */
    public record AttemptFact(String decision, String moderationOutcome, boolean queryResolved) {
    }

    /**
     * Where a learner stands on one instrument, from their live attempts. Competent only when a competent decision stands and, if the instrument is
     * moderated, the moderation confirmed it or its query has been answered.
     */
    public static String instrumentState(boolean requiresModeration, List<AttemptFact> attempts) {
        String best = NO_ATTEMPT;
        for (AttemptFact a : attempts) {
            String state;
            if (a.decision() == null) {
                state = WAITING_DECISION;
            } else if (!AttemptRules.COMPETENT.equals(a.decision())) {
                state = NOT_YET_COMPETENT;
            } else if (!requiresModeration) {
                state = COMPETENT;
            } else if (a.moderationOutcome() == null) {
                state = WAITING_MODERATION;
            } else if (ModerationRules.QUERIED.equals(a.moderationOutcome()) && !a.queryResolved()) {
                state = QUERY_OPEN;
            } else {
                state = COMPETENT;
            }
            if (COMPETENT.equals(state)) {
                return COMPETENT;
            }
            best = better(best, state);
        }
        return best;
    }

    private static int rank(String s) {
        return switch (s) {
            case WAITING_MODERATION, QUERY_OPEN -> 3;
            case WAITING_DECISION -> 2;
            case NOT_YET_COMPETENT -> 1;
            default -> 0;
        };
    }

    private static String better(String a, String b) {
        return rank(b) > rank(a) ? b : a;
    }

    /** Why an outcome cannot be recorded, or null. */
    public static String recordProblem(String enrolmentStatus, LocalDate enrolmentStart, boolean hasLiveOutcome, String outcome, LocalDate outcomeOn, LocalDate today,
                                       String basis, int openGaps, String overrideReason) {
        if (outcome == null || !OUTCOMES.contains(outcome)) {
            return "Choose completed or did not complete";
        }
        if (hasLiveOutcome) {
            return "This enrolment already has a recorded outcome. Withdraw it first if it was wrong";
        }
        if (COMPLETED.equals(outcome) && !COMPLETABLE.contains(enrolmentStatus)) {
            return "Only an enrolment that is enrolled or in progress can be completed";
        }
        if (outcomeOn == null) {
            return "Enter the day of the outcome";
        }
        if (outcomeOn.isAfter(today)) {
            return "The day of the outcome cannot be in the future";
        }
        if (enrolmentStart != null && outcomeOn.isBefore(enrolmentStart)) {
            return "The day of the outcome cannot be before the enrolment started";
        }
        if (!CohortRules.longEnough(basis, CohortRules.MIN_REASON)) {
            return "Write the basis for the outcome, in at least " + CohortRules.MIN_REASON + " characters";
        }
        if (COMPLETED.equals(outcome) && openGaps > 0 && !CohortRules.longEnough(overrideReason, CohortRules.MIN_REASON)) {
            return openGaps + (openGaps == 1 ? " assessment is" : " assessments are") + " not yet competent and moderated. To complete anyway, say why in at least "
                    + CohortRules.MIN_REASON + " characters";
        }
        return null;
    }

    public static String withdrawProblem(String status, String reason) {
        if (WITHDRAWN.equals(status)) {
            return "This outcome has already been withdrawn";
        }
        return CohortRules.reasonProblem(reason);
    }
}
