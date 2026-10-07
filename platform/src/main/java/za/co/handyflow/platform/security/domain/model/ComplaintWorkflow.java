// security/domain/model/ComplaintWorkflow.java
package za.co.handyflow.platform.security.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The rules of a guard complaint, with no framework dependencies so they can be tested directly.
 *
 * Flow: RECEIVED -> UNDER_INVESTIGATION -> FINDING_MADE -> ACTION_TAKEN -> CLOSED.
 * A complaint that was not substantiated may be closed straight after the finding.
 * RECEIVED or UNDER_INVESTIGATION may be withdrawn. CLOSED and WITHDRAWN are final.
 * The system records what people decide; it never decides an employment outcome.
 */
public final class ComplaintWorkflow {

    private ComplaintWorkflow() {}

    public enum Status { RECEIVED, UNDER_INVESTIGATION, FINDING_MADE, ACTION_TAKEN, CLOSED, WITHDRAWN }

    public enum Step { START, FINDING, ACTION, CLOSE, WITHDRAW }

    public enum Finding { SUBSTANTIATED, UNSUBSTANTIATED, INCONCLUSIVE }

    public enum Action {
        NO_ACTION, COUNSELLING, VERBAL_WARNING, WRITTEN_WARNING, FINAL_WARNING, RETRAINING, SUSPENSION, DISCIPLINARY_HEARING
    }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    public enum Category {
        ABSENTEEISM, LATENESS, MISCONDUCT, SLEEPING_ON_DUTY, NEGLIGENCE, POOR_CUSTOMER_SERVICE, FAILURE_TO_PATROL,
        FAILURE_TO_FOLLOW_POST_ORDERS, DISHONESTY, THEFT, EXCESSIVE_FORCE, HARASSMENT, INTOXICATION,
        FIREARM_VIOLATION, ACCESS_CONTROL_VIOLATION, OTHER
    }

    public enum ComplainantType { CLIENT, SUPERVISOR, COLLEAGUE, MEMBER_OF_PUBLIC, INTERNAL, OTHER }

    /** Complaints in these categories are flagged for urgent review whatever their severity. */
    private static final Set<Category> URGENT_CATEGORIES =
            EnumSet.of(Category.EXCESSIVE_FORCE, Category.FIREARM_VIOLATION, Category.THEFT, Category.HARASSMENT);

    public static boolean isOpen(Status s) { return s != Status.CLOSED && s != Status.WITHDRAWN; }

    /** Details may be edited until a finding is made. */
    public static boolean isEditable(Status s) { return s == Status.RECEIVED || s == Status.UNDER_INVESTIGATION; }

    public static boolean isUrgent(Category c, Severity s) { return s == Severity.CRITICAL || URGENT_CATEGORIES.contains(c); }

    /** The steps that can be taken now. `finding` matters only once a finding exists. */
    public static Set<Step> allowedSteps(Status status, Finding finding) {
        return switch (status) {
            case RECEIVED -> EnumSet.of(Step.START, Step.WITHDRAW);
            case UNDER_INVESTIGATION -> EnumSet.of(Step.FINDING, Step.WITHDRAW);
            case FINDING_MADE -> finding == Finding.UNSUBSTANTIATED ? EnumSet.of(Step.ACTION, Step.CLOSE) : EnumSet.of(Step.ACTION);
            case ACTION_TAKEN -> EnumSet.of(Step.CLOSE);
            case CLOSED, WITHDRAWN -> EnumSet.noneOf(Step.class);
        };
    }

    /** Null when the action is acceptable for the finding, otherwise what is wrong. */
    public static String validateAction(Finding finding, Action action, String note) {
        if (finding == Finding.UNSUBSTANTIATED && action != Action.NO_ACTION)
            return "A complaint that was not substantiated cannot lead to disciplinary action";
        if (finding == Finding.SUBSTANTIATED && action == Action.NO_ACTION && (note == null || note.isBlank()))
            return "Say why no action is taken on a substantiated complaint";
        return null;
    }
}
