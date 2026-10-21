package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;

/**
 * What can be done with an overtime claim. A claim is worked out from the attendance record, then approved or rejected by someone
 * other than the employee, and only an approved claim reaches payroll. Pay already agreed is never changed behind the approver's
 * back: once a claim is approved, the day's entries are locked until it is rejected.
 */
public final class OvertimeRules {

    private OvertimeRules() {}

    public static final String PENDING = "PENDING", APPROVED = "APPROVED", REJECTED = "REJECTED", WITHDRAWN = "WITHDRAWN";

    public enum Action { CREATE, UPDATE, KEEP }

    /**
     * What to do on a new request for a day that already has a claim (or none).
     * A pending, rejected or withdrawn claim follows the attendance record again; an approved one is left alone.
     */
    public static Action onRequest(String existingStatus, int existingMinutes, int newMinutes) {
        if (existingStatus == null) return Action.CREATE;
        if (APPROVED.equals(existingStatus)) return Action.KEEP;
        if (PENDING.equals(existingStatus)) return existingMinutes == newMinutes ? Action.KEEP : Action.UPDATE;
        // Rejected or withdrawn: only claimed again when the hours changed, so a rejection is not undone by asking twice.
        return existingMinutes == newMinutes ? Action.KEEP : Action.UPDATE;
    }

    /** Why a claim cannot be made for this day, or null. */
    public static String claimProblem(String kind, int minutes, LocalDate date, LocalDate today, boolean aboveThreshold, boolean dayOpen) {
        if (kind == null || minutes <= 0) return "There is no overtime on this day.";
        if (AttendanceRules.OT_HOLIDAY.equals(kind)) return "Work on a public holiday is not paid from here. HR decides what is due under s 18.";
        if (!date.isBefore(today)) return "The day is not over yet.";
        if (dayOpen) return "The day has a clock-in with no clock-out. Correct it first.";
        if (aboveThreshold) return "This employee earns above the BCEA threshold, so overtime is paid as their contract says, not at the Act's rates.";
        return null;
    }

    /** Why this decision cannot be made, or null. {@code approving} false means rejecting (or revoking). */
    public static String decisionProblem(String status, boolean approving, String note, boolean linkedToPayRun, boolean decidingOwnClaim) {
        if (decidingOwnClaim) return "You cannot decide your own overtime.";
        if (linkedToPayRun) return "This overtime is already in a pay run, so it can no longer be changed.";
        if (approving && !PENDING.equals(status)) return "Only a claim that is waiting can be approved.";
        if (!approving && !PENDING.equals(status) && !APPROVED.equals(status)) return "This claim is already " + status.toLowerCase() + ".";
        if (!approving && (note == null || note.isBlank())) return "Give the reason for rejecting this claim.";
        if (note != null && note.trim().length() > 500) return "Keep the note under 500 characters.";
        return null;
    }

    /** Why the employee cannot withdraw this claim, or null. */
    public static String withdrawProblem(String status) {
        return PENDING.equals(status) || REJECTED.equals(status) ? null : "Only a claim that is waiting or rejected can be withdrawn.";
    }

    /** The reason a day's entries cannot be changed because of its claim, or null. */
    public static String lockReason(String status, boolean linkedToPayRun) {
        if (linkedToPayRun) return "The overtime for this day is already in a pay run, so its time cannot be changed.";
        if (APPROVED.equals(status)) return "Overtime for this day has been approved. Reject it first if the time has to change.";
        return null;
    }
}
