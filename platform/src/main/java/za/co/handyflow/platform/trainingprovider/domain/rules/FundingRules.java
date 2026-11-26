package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.Set;

/**
 * Rules for funders and the links from enrolments to the funders paying for them. A link records who is paying, from when, and a reference. No amount,
 * rate, percentage or eligibility rule is held, and a link never means funding was approved or paid.
 */
public final class FundingRules {

    private FundingRules() {
    }

    public static final String ACTIVE = "ACTIVE";
    public static final String RETIRED = "RETIRED";
    public static final String ENDED = "ENDED";
    public static final Set<String> TYPES = Set.of("SELF", "EMPLOYER", "SETA", "GRANT", "GOVERNMENT", "OTHER");

    public static String funderProblem(String type, String name, String email, boolean nameTaken) {
        if (type == null || !TYPES.contains(type)) {
            return "Choose the kind of funder";
        }
        if (!CohortRules.longEnough(name, 2)) {
            return "Give the name of the funder";
        }
        if (email != null && !email.isBlank() && (!email.contains("@") || email.trim().startsWith("@") || email.trim().endsWith("@"))) {
            return "That email address does not look right";
        }
        if (nameTaken) {
            return "A live funder with that name already exists";
        }
        return null;
    }

    public static String editProblem(String status) {
        return ACTIVE.equals(status) ? null : "A retired funder cannot be changed";
    }

    public static String retireProblem(String status, String reason, int activeLinks) {
        if (RETIRED.equals(status)) {
            return "This funder has already been retired";
        }
        if (activeLinks > 0) {
            return activeLinks + (activeLinks == 1 ? " enrolment is" : " enrolments are") + " still linked to this funder. End those links first";
        }
        return CohortRules.reasonProblem(reason);
    }

    public static String linkProblem(String enrolmentStatus, String funderStatus, LocalDate from, LocalDate enrolmentStart, LocalDate today, boolean alreadyLinked) {
        if (EnrolmentRules.CANCELLED.equals(enrolmentStatus)) {
            return "This enrolment was cancelled";
        }
        if (!ACTIVE.equals(funderStatus)) {
            return "That funder has been retired";
        }
        if (from == null) {
            return "Enter the day the funding starts";
        }
        if (from.isAfter(today.plusYears(1))) {
            return "The day is more than a year ahead";
        }
        if (enrolmentStart != null && from.isBefore(enrolmentStart.minusYears(1))) {
            return "The day is more than a year before the enrolment started";
        }
        if (alreadyLinked) {
            return "This enrolment is already linked to that funder. End the link first to change it";
        }
        return null;
    }

    public static String endProblem(String status, LocalDate on, LocalDate from, LocalDate today, String reason) {
        if (!ACTIVE.equals(status)) {
            return "This link has already ended";
        }
        if (on == null) {
            return "Enter the day the funding ended";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (from != null && on.isBefore(from)) {
            return "It cannot end before it started";
        }
        return CohortRules.reasonProblem(reason);
    }

    /** A plain line. It says who is recorded as paying and never that funding was approved, paid or is owed. */
    public static String summary(java.util.List<String> funderNames) {
        if (funderNames.isEmpty()) {
            return "No funder is recorded for this enrolment.";
        }
        return "Recorded as funded by " + String.join(", ", funderNames) + ". This records who is paying. It holds no amounts and does not mean any funding was approved or paid.";
    }
}
