package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Set;

/**
 * Rules for moderation. A moderator reviews a decision that has been made; they do not make it. The moderator must be someone other than the
 * assessor, and the person who recorded the decision cannot enter the moderation. The decision is never edited: a query is answered in writing.
 */
public final class ModerationRules {

    private ModerationRules() {
    }

    public static final String CONFIRMED = "CONFIRMED";
    public static final String QUERIED = "QUERIED";
    public static final Set<String> OUTCOMES = Set.of(CONFIRMED, QUERIED);

    /** Everything the check needs, so the call site reads clearly. */
    public record Facts(String attemptStatus, boolean decided, LocalDate decidedOn, boolean requiresModeration, boolean alreadyModerated,
                        boolean moderatorActive, PractitionerRules.Standing standing, boolean moderatorIsAssessor, boolean moderatorUserInvolved,
                        boolean enteredByDecider) {
    }

    /** Why a moderation cannot be recorded, or null. */
    public static String recordProblem(Facts f, String outcome, String basis, LocalDate moderatedOn, LocalDate today) {
        if (!AttemptRules.RECORDED.equals(f.attemptStatus())) {
            return "A void attempt cannot be moderated";
        }
        if (!f.decided()) {
            return "There is no decision to moderate yet";
        }
        if (!f.requiresModeration()) {
            return "The instrument for this attempt is not set to be moderated";
        }
        if (f.alreadyModerated()) {
            return "This attempt has already been moderated";
        }
        if (outcome == null || !OUTCOMES.contains(outcome)) {
            return "Choose confirmed or queried";
        }
        if (!CohortRules.longEnough(basis, CohortRules.MIN_REASON)) {
            return "Write the basis for the moderation, in at least " + CohortRules.MIN_REASON + " characters";
        }
        if (moderatedOn == null) {
            return "Enter the day of the moderation";
        }
        if (moderatedOn.isAfter(today)) {
            return "The moderation day cannot be in the future";
        }
        if (f.decidedOn() != null && moderatedOn.isBefore(f.decidedOn())) {
            return "The moderation day cannot be before the decision was recorded";
        }
        if (f.moderatorIsAssessor()) {
            return "The assessor cannot moderate their own decision";
        }
        if (f.moderatorUserInvolved()) {
            return "The moderator must be someone who took no part in recording this attempt or its decision";
        }
        if (f.enteredByDecider()) {
            return "The person who recorded the decision cannot enter its moderation";
        }
        if (!f.moderatorActive()) {
            return "That moderator is no longer active";
        }
        if (f.standing() != PractitionerRules.Standing.VERIFIED_IN_FORCE) {
            return "That person has no moderator appointment for this programme that was checked and in force on the day of the moderation";
        }
        return null;
    }

    /** Why a query cannot be resolved, or null. */
    public static String resolveProblem(String outcome, boolean resolved, String note) {
        if (!QUERIED.equals(outcome)) {
            return "Only a queried moderation needs a resolution";
        }
        if (resolved) {
            return "This query has already been resolved";
        }
        if (!CohortRules.longEnough(note, CohortRules.MIN_REASON)) {
            return "Say how the query was answered, in at least " + CohortRules.MIN_REASON + " characters";
        }
        return null;
    }
}
