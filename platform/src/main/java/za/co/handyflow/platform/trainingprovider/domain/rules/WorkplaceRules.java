package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Rules for workplace learning. Hours are totalled and split into confirmed and unconfirmed. No required number of hours is held anywhere, and logged
 * hours are not a competency decision. A mentor's confirmation is recorded by staff and names the mentor.
 */
public final class WorkplaceRules {

    private WorkplaceRules() {
    }

    public static final String PLANNED = "PLANNED";
    public static final String ACTIVE = "ACTIVE";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String RECORDED = "RECORDED";
    public static final String VOID = "VOID";
    private static final Map<String, Set<String>> NEXT = Map.of(PLANNED, Set.of(ACTIVE, CANCELLED), ACTIVE, Set.of(COMPLETED, CANCELLED), COMPLETED, Set.of(), CANCELLED, Set.of());

    public static Set<String> nextStatuses(String from) {
        return NEXT.getOrDefault(from, Set.of());
    }

    public static boolean enrolmentOpen(String enrolmentStatus) {
        return EnrolmentRules.ENROLLED.equals(enrolmentStatus) || EnrolmentRules.IN_PROGRESS.equals(enrolmentStatus);
    }

    public static String placementProblem(String enrolmentStatus, String host, String mentor, LocalDate start, LocalDate end, LocalDate enrolmentStart) {
        if (!enrolmentOpen(enrolmentStatus)) {
            return "A placement can only be added while the enrolment is enrolled or in progress";
        }
        if (!CohortRules.longEnough(host, 2)) {
            return "Give the name of the host workplace";
        }
        if (!CohortRules.longEnough(mentor, 2)) {
            return "Give the name of the workplace mentor";
        }
        if (start == null) {
            return "Enter the planned start day";
        }
        if (end != null && end.isBefore(start)) {
            return "The end day cannot be before the start day";
        }
        if (enrolmentStart != null && start.isBefore(enrolmentStart)) {
            return "The placement cannot start before the enrolment started";
        }
        return null;
    }

    public static String editProblem(String status) {
        return PLANNED.equals(status) || ACTIVE.equals(status) ? null : "A " + status.toLowerCase() + " placement cannot be changed";
    }

    public static String startProblem(String status, String enrolmentStatus, LocalDate on, LocalDate plannedStart, LocalDate today) {
        if (!PLANNED.equals(status)) {
            return "Only a planned placement can be started";
        }
        if (!enrolmentOpen(enrolmentStatus)) {
            return "The enrolment is not enrolled or in progress";
        }
        if (on == null) {
            return "Enter the day the placement started";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (plannedStart != null && on.isBefore(plannedStart.minusYears(1))) {
            return "That day is more than a year before the planned start";
        }
        return null;
    }

    public static String completeProblem(String status, LocalDate on, LocalDate startedOn, LocalDate today) {
        if (!ACTIVE.equals(status)) {
            return "Only an active placement can be completed";
        }
        if (on == null) {
            return "Enter the day the placement ended";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (startedOn != null && on.isBefore(startedOn)) {
            return "It cannot end before it started";
        }
        return null;
    }

    public static String cancelProblem(String status, String reason) {
        return nextStatuses(status).contains(CANCELLED) ? CohortRules.reasonProblem(reason) : "A " + status.toLowerCase() + " placement cannot be cancelled";
    }

    /** Why a logbook entry cannot be added, or null. */
    public static String logProblem(String placementStatus, LocalDate day, LocalDate startedOn, LocalDate endDate, LocalDate today, BigDecimal hours, String activity, boolean dayTaken) {
        if (!ACTIVE.equals(placementStatus)) {
            return "Entries can only be added while the placement is active";
        }
        if (day == null) {
            return "Enter the day";
        }
        if (day.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (startedOn != null && day.isBefore(startedOn)) {
            return "The day cannot be before the placement started";
        }
        if (endDate != null && day.isAfter(endDate)) {
            return "The day is after the placement's planned end";
        }
        if (hours == null || hours.signum() <= 0 || hours.compareTo(BigDecimal.valueOf(24)) > 0) {
            return "Hours must be more than 0 and no more than 24";
        }
        if (!CohortRules.longEnough(activity, 10)) {
            return "Say what was done, in at least 10 characters";
        }
        if (dayTaken) {
            return "There is already an entry for that day. Void it with a reason first";
        }
        return null;
    }

    public static String confirmProblem(String logStatus, boolean alreadyConfirmed, LocalDate on, LocalDate logDay, LocalDate today, String name) {
        if (!RECORDED.equals(logStatus)) {
            return "A void entry cannot be confirmed";
        }
        if (alreadyConfirmed) {
            return "This entry is already confirmed";
        }
        if (on == null) {
            return "Enter the day the mentor confirmed it";
        }
        if (on.isAfter(today)) {
            return "The day cannot be in the future";
        }
        if (on.isBefore(logDay)) {
            return "It cannot be confirmed before the day it records";
        }
        if (!CohortRules.longEnough(name, 2)) {
            return "Give the name of the mentor who confirmed it";
        }
        return null;
    }

    public static String voidLogProblem(String status, String reason) {
        return VOID.equals(status) ? "This entry is already void" : CohortRules.reasonProblem(reason);
    }

    /** A plain summary. Never a pass or a threshold. */
    public static String summary(BigDecimal confirmed, BigDecimal unconfirmed) {
        BigDecimal total = confirmed.add(unconfirmed);
        return total.stripTrailingZeros().toPlainString() + " hours logged, " + confirmed.stripTrailingZeros().toPlainString() + " confirmed by the mentor and "
                + unconfirmed.stripTrailingZeros().toPlainString() + " not yet confirmed. This is a record of time spent. It is not a competency decision.";
    }
}
