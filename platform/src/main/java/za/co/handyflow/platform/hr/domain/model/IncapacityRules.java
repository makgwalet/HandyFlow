package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Incapacity: poor performance and ill health. The 2025 Code of Good Practice: Dismissal (as described in law-firm summaries) expects, for
 * poor performance, that the standard was set, performance was assessed against it, guidance, training or counselling was given, and
 * a reasonable time to improve was allowed; for ill health, that the nature, extent and likely duration of the incapacity were
 * investigated and alternatives to dismissal such as adjusted duties were considered. Dismissal for incapacity is blocked until the steps
 * are recorded. The meeting with the employee is the case's own meeting stage, not a step.
 */
public final class IncapacityRules {

    private IncapacityRules() {}

    public record Step(String code, String label) {}

    public static final String NO_ACTION = "NO_ACTION", SUPPORT_PLAN = "SUPPORT_PLAN", ALTERNATIVE_POST = "ALTERNATIVE_POST",
            RETURN_TO_WORK = "RETURN_TO_WORK", ADJUSTED_DUTIES = "ADJUSTED_DUTIES", EXTENDED_LEAVE = "EXTENDED_LEAVE", DISMISSAL = "DISMISSAL";

    public static List<Step> stepsFor(String category) {
        if ("POOR_PERFORMANCE".equals(category)) return List.of(
                new Step("STANDARD_SET", "The required standard was set and explained to the employee"),
                new Step("SHORTFALL_ASSESSED", "Performance was assessed against that standard"),
                new Step("SUPPORT_GIVEN", "Evaluation, guidance, training or counselling was given"),
                new Step("TIME_TO_IMPROVE", "A reasonable time to improve was allowed"));
        if ("ILL_HEALTH".equals(category)) return List.of(
                new Step("NATURE_INVESTIGATED", "The nature, extent and likely duration of the incapacity were investigated"),
                new Step("MEDICAL_EVIDENCE", "Medical evidence or a professional prognosis is on file"),
                new Step("ALTERNATIVES_CONSIDERED", "Adjusted duties, accommodation or another post were considered"));
        return List.of();
    }

    public static List<String> outcomes(String category) {
        if ("POOR_PERFORMANCE".equals(category)) return List.of(NO_ACTION, SUPPORT_PLAN, ALTERNATIVE_POST, DISMISSAL);
        if ("ILL_HEALTH".equals(category)) return List.of(RETURN_TO_WORK, ADJUSTED_DUTIES, EXTENDED_LEAVE, ALTERNATIVE_POST, DISMISSAL);
        return List.of();
    }

    /** Labels of the steps not yet done. */
    public static List<String> missing(String category, Set<String> doneCodes) {
        List<String> out = new ArrayList<>();
        for (Step s : stepsFor(category)) if (!doneCodes.contains(s.code())) out.add(s.label());
        return out;
    }

    public static String stepProblem(String category, String code, LocalDate doneOn, LocalDate today) {
        boolean known = stepsFor(category).stream().anyMatch(s -> s.code().equals(code));
        if (!known) return "That step does not belong to this kind of case.";
        if (doneOn == null) return "Give the date the step was done.";
        if (doneOn.isAfter(today)) return "A step cannot be recorded before it was done.";
        return null;
    }

    public static String reviewProblem(LocalDate from, LocalDate to) {
        if (from == null || to == null) return "Give the start and end of the review period.";
        if (to.isBefore(from)) return "The review period cannot end before it starts.";
        return null;
    }

    public static boolean reviewShort(LocalDate from, LocalDate to, ErPolicy policy) {
        return from != null && to != null && java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 < policy.minReviewDays();
    }

    /**
     * Null when the decision may be made. Dismissal needs every step recorded and, for poor performance, a review period that has ended and
     * is not shorter than the company's minimum. Other outcomes only need reasons.
     */
    public static String outcomeProblem(String category, String outcome, Set<String> doneCodes, LocalDate reviewFrom, LocalDate reviewTo,
                                        LocalDate today, ErPolicy policy, String note) {
        if (!outcomes(category).contains(outcome)) return "That outcome does not belong to this kind of case.";
        String n = ErRules.textProblem("The reasons for the decision", note, 4000);
        if (n != null) return n;
        if (!DISMISSAL.equals(outcome)) return null;
        List<String> missing = missing(category, doneCodes);
        if (!missing.isEmpty()) return "Before a dismissal for incapacity, record: " + String.join("; ", missing) + ".";
        if ("POOR_PERFORMANCE".equals(category)) {
            String r = reviewProblem(reviewFrom, reviewTo);
            if (r != null) return "Before a dismissal for poor performance, set the review period. " + r;
            if (reviewTo.isAfter(today)) return "The review period has not ended yet (it ends on " + reviewTo + ").";
            if (reviewShort(reviewFrom, reviewTo, policy))
                return "The review period is shorter than the company minimum of " + policy.minReviewDays() + " days.";
        }
        return null;
    }
}
