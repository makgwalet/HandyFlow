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
    public static final String AWAITING_CLIENT = "AWAITING_CLIENT";
    public static final String CLIENT_APPROVED = "CLIENT_APPROVED";
    public static final String FINALISED = "FINALISED";
    public static final String REVERSED = "REVERSED";

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            DRAFT, Set.of(CALCULATED),
            CALCULATED, Set.of(APPROVED, DRAFT),   // approve, or reopen to change items
            APPROVED, Set.of(FINALISED, AWAITING_CLIENT, DRAFT),    // finalise, send to the client, or reopen
            AWAITING_CLIENT, Set.of(CLIENT_APPROVED, DRAFT),        // the client approves, or declines (or the bureau reopens)
            CLIENT_APPROVED, Set.of(FINALISED, DRAFT),
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

    /** A client set to need its own approval cannot have a run finalised straight from the bureau's approval. */
    public static String finalisationProblem(boolean clientApprovalRequired, String status) {
        if (clientApprovalRequired && APPROVED.equals(status)) {
            return "This client approves its own pay runs. Send the run to the client and wait for their approval before finalising.";
        }
        return null;
    }

    public static String declineProblem(String reason) {
        if (reason == null || reason.isBlank()) return "Say why you are declining this pay run.";
        if (reason.trim().length() > MAX_REASON) return "The reason is at most " + MAX_REASON + " characters.";
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
        return CALCULATED.equals(status) || APPROVED.equals(status) || AWAITING_CLIENT.equals(status)
                || CLIENT_APPROVED.equals(status) || FINALISED.equals(status);
    }
}
