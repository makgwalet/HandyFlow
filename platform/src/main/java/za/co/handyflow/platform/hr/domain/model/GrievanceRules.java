package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Grievances. There is no private-sector statutory deadline: the time to respond is the company's own setting in {@link ErPolicy}
 * (a public-service rule of 90 days to lodge and 30 days to respond exists, but does not bind a private employer).
 */
public final class GrievanceRules {

    private GrievanceRules() {}

    public static final String UPHELD = "UPHELD", PARTLY_UPHELD = "PARTLY_UPHELD", NOT_UPHELD = "NOT_UPHELD";

    public static LocalDate responseDue(LocalDate lodgedOn, ErPolicy policy) {
        return ErRules.addWorkingDays(lodgedOn, policy.grievanceResponseDays());
    }

    /** A grievance still waiting for its outcome after the response time the company set. */
    public static boolean overdue(String status, LocalDate lodgedOn, LocalDate today, ErPolicy policy) {
        if (!ErRules.REPORTED.equals(status) && !ErRules.INVESTIGATING.equals(status)) return false;
        return today.isAfter(responseDue(lodgedOn, policy));
    }

    public static String lodgeProblem(String category, String description, LocalDate lodgedOn, LocalDate today, UUID employee, UUID respondent) {
        String c = ErRules.categoryProblem(ErRules.GRIEVANCE, category);
        if (c != null) return c;
        if (lodgedOn == null) return "Give the date the grievance was lodged.";
        if (lodgedOn.isAfter(today)) return "The grievance cannot be dated in the future.";
        if (respondent != null && respondent.equals(employee)) return "A grievance cannot be about the person lodging it.";
        return ErRules.textProblem("The grievance", description, 4000);
    }

    /** Whoever decides must not be the person complaining or the person complained about. */
    public static String deciderProblem(UUID decider, UUID employee, UUID respondent) {
        if (decider == null) return "Choose who will decide the grievance.";
        if (decider.equals(employee)) return "The person who lodged the grievance cannot decide it.";
        if (Objects.equals(decider, respondent)) return "The person the grievance is about cannot decide it.";
        return null;
    }

    public static String outcomeProblem(String outcome, String note) {
        if (!UPHELD.equals(outcome) && !PARTLY_UPHELD.equals(outcome) && !NOT_UPHELD.equals(outcome))
            return "Choose the outcome: upheld, partly upheld or not upheld.";
        return ErRules.textProblem("The reasons and what will be done", note, 4000);
    }

    /** The grievant can take an unresolved grievance to the next level, within the company's appeal period. */
    public static String escalationDeciderProblem(UUID decider, UUID employee, UUID respondent, UUID firstDecider) {
        String p = deciderProblem(decider, employee, respondent);
        if (p != null) return p;
        if (decider.equals(firstDecider)) return "The next level must be someone other than the person who decided first.";
        return null;
    }
}
