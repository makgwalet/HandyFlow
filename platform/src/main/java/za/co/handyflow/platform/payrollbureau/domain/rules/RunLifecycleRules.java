package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The life of a pay run (phase 2): DRAFT, CALCULATED, APPROVED, FINALISED, and REVERSED after finalising.
 * Calculating and approving are done by different people.
 */
public final class RunLifecycleRules {

    public static final String DRAFT = "DRAFT";
    public static final String CALCULATED = "CALCULATED";
    public static final String APPROVED = "APPROVED";
    public static final String FINALISED = "FINALISED";
    public static final String REVERSED = "REVERSED";

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            DRAFT, Set.of(CALCULATED),
            CALCULATED, Set.of(APPROVED, DRAFT),   // approve, or reopen to change items
            APPROVED, Set.of(FINALISED, DRAFT),    // finalise, or reopen
            FINALISED, Set.of(REVERSED),
            REVERSED, Set.of());

    public static final int MAX_REASON = 500;

    private RunLifecycleRules() {}

    /** Why a run cannot go from one status to another, or null. */
    public static String transitionProblem(String from, String to) {
        if (!ALLOWED.containsKey(from)) return "This pay run has an unknown status (" + from + ").";
        if (!ALLOWED.get(from).contains(to)) {
            return "A " + from + " pay run cannot become " + to + ".";
        }
        return null;
    }

    /** The person who calculated a run cannot approve it. */
    public static String approvalProblem(UUID calculatedBy, UUID approver) {
        if (approver == null) return "Approving needs a signed-in user.";
        if (calculatedBy != null && calculatedBy.equals(approver)) {
            return "You calculated this pay run, so someone else has to approve it.";
        }
        return null;
    }

    public static String reversalProblem(String reason) {
        if (reason == null || reason.isBlank()) return "Say why this pay run is being reversed.";
        if (reason.trim().length() > MAX_REASON) return "The reason is at most " + MAX_REASON + " characters.";
        return null;
    }

    /** Payslips are produced for review from the moment a run is calculated, but only a finalised run is sent, billed or paid. */
    public static boolean payslipsCanBeSent(String status) {
        return FINALISED.equals(status);
    }

    /** A run whose payslips still count towards what a month has already been charged UIF on. */
    public static boolean countsTowardsMonth(String status) {
        return CALCULATED.equals(status) || APPROVED.equals(status) || FINALISED.equals(status);
    }
}
