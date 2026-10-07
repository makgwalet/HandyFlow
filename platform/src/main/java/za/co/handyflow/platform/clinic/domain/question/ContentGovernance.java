package za.co.handyflow.platform.clinic.domain.question;

import java.util.List;
import java.util.UUID;

/**
 * Rules for moving clinical configuration through review (DEC-CLINIC-001). Pure, so it can be tested
 * without a database. Four-eyes: the person who activates content cannot be the person who reviewed it,
 * and synthetic demo content can never become ACTIVE.
 */
public final class ContentGovernance {

    private ContentGovernance() {}

    /**
     * @param reviewer           who reviewed the content (set when it left CLINICAL_REVIEW), may be null
     * @param actor              who is making this move
     * @param demo               synthetic content flag
     * @param expressionProblems problems found by validating every rule expression (empty = fine)
     * @param hasSource          whether clinicalSource is recorded
     */
    public static void checkTransition(ContentStatus from, ContentStatus to, UUID reviewer, UUID actor,
                                       boolean demo, List<String> expressionProblems, boolean hasSource) {
        if (!from.canMoveTo(to)) {
            throw new IllegalStateException("Content cannot move from " + from + " to " + to + ".");
        }
        if (actor == null) throw new IllegalArgumentException("The person making this change must be known.");
        if (to == ContentStatus.CLINICAL_REVIEW) {
            if (!expressionProblems.isEmpty()) {
                throw new IllegalArgumentException("Fix the rules before review: " + String.join("; ", expressionProblems));
            }
            if (!hasSource) throw new IllegalArgumentException("Record the clinical source before sending for review.");
        }
        if (to == ContentStatus.ACTIVE) {
            if (demo) throw new IllegalStateException("Synthetic demo content cannot be activated.");
            if (reviewer == null) throw new IllegalStateException("Content must be reviewed before it is activated.");
            if (reviewer.equals(actor)) {
                throw new IllegalStateException("A different person from the reviewer must approve activation.");
            }
            if (!expressionProblems.isEmpty()) {
                throw new IllegalArgumentException("Fix the rules before activation: " + String.join("; ", expressionProblems));
            }
        }
    }
}
