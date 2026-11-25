package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Rules for regulatory reporting. Nothing about any authority is built in: the provider sets each obligation up, with a written note on where the
 * rule came from. A submission is a staff record, not an authority's acceptance. Whether a period is overdue is worked out, never stored, and nothing
 * here ever says a provider is compliant.
 */
public final class ReportRules {

    private ReportRules() {
    }

    public static final Set<String> CADENCES = Set.of("MONTHLY", "QUARTERLY", "HALF_YEARLY", "ANNUAL", "ONE_OFF");
    public static final String OPEN = "OPEN";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String WAIVED = "WAIVED";
    public static final String VOID = "VOID";
    public static final int DEFAULT_SOON_DAYS = 14;

    public static String obligationProblem(String name, String cadence, Integer dueDays, String sourceNote) {
        if (!CohortRules.longEnough(name, 2)) {
            return "Give the obligation a name";
        }
        if (cadence == null || !CADENCES.contains(cadence)) {
            return "Choose how often it is reported";
        }
        if (dueDays == null || dueDays < 0 || dueDays > 366) {
            return "Days after the period ends must be between 0 and 366";
        }
        if (!CohortRules.longEnough(sourceNote, 10)) {
            return "Say where this rule came from, in at least 10 characters (for example the notice or letter you took it from)";
        }
        return null;
    }

    public static LocalDate computedDue(LocalDate periodEnd, int dueDays) {
        return periodEnd.plusDays(dueDays);
    }

    /** Why a period cannot be created, or null. The due day is the computed one unless an override reason is given. */
    public static String periodProblem(boolean obligationActive, boolean windowTaken, LocalDate start, LocalDate end, LocalDate computedDue, LocalDate due, String overrideReason) {
        if (!obligationActive) {
            return "That obligation has been retired";
        }
        if (start == null || end == null) {
            return "Enter the start and end of the reporting period";
        }
        if (end.isBefore(start)) {
            return "The period cannot end before it starts";
        }
        if (windowTaken) {
            return "That reporting period already exists for this obligation";
        }
        if (due != null && !due.equals(computedDue) && !CohortRules.longEnough(overrideReason, 10)) {
            return "The due day differs from the obligation's rule. Say why, in at least 10 characters";
        }
        return null;
    }

    public static String submitProblem(String status, LocalDate on, LocalDate periodStart, LocalDate today) {
        if (!OPEN.equals(status)) {
            return "Only an open period can be marked as submitted";
        }
        if (on == null) {
            return "Enter the day it was submitted";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (on.isBefore(periodStart)) {
            return "It cannot be submitted before the period starts";
        }
        return null;
    }

    public static String acknowledgeProblem(String status, boolean alreadyAcknowledged, LocalDate on, LocalDate submittedOn, LocalDate today) {
        if (!SUBMITTED.equals(status)) {
            return "Only a submitted period can have an acknowledgement";
        }
        if (alreadyAcknowledged) {
            return "An acknowledgement is already recorded";
        }
        if (on == null) {
            return "Enter the day the authority acknowledged it";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (submittedOn != null && on.isBefore(submittedOn)) {
            return "It cannot be acknowledged before it was submitted";
        }
        return null;
    }

    public static String waiveProblem(String status, String reason) {
        return OPEN.equals(status) ? CohortRules.reasonProblem(reason) : "Only an open period can be waived";
    }

    public static String voidProblem(String status, String reason) {
        return VOID.equals(status) ? "This period is already void" : CohortRules.reasonProblem(reason);
    }

    /** OVERDUE, DUE_SOON, OPEN_LATER, or the plain status for periods that are not open. */
    public static String dueState(String status, LocalDate dueOn, LocalDate today, int soonDays) {
        if (!OPEN.equals(status)) {
            return status;
        }
        if (dueOn.isBefore(today)) {
            return "OVERDUE";
        }
        return ChronoUnit.DAYS.between(today, dueOn) <= soonDays ? "DUE_SOON" : "OPEN_LATER";
    }
}
