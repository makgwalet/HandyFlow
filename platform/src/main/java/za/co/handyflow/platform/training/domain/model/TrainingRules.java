package za.co.handyflow.platform.training.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The rules for putting people on training sessions and recording what happened. Pure: the services load the facts and ask these
 * questions; each returns null when the step may go ahead, or the reason it may not.
 */
public final class TrainingRules {

    private TrainingRules() {}

    /** Null when this employee may be put on a session in this state. */
    public static String enrollProblem(String employeeStatus, String sessionStatus) {
        if (employeeStatus != null && !"ACTIVE".equals(employeeStatus))
            return "This employee is " + employeeStatus.toLowerCase().replace('_', ' ') + " and cannot be put on a training session.";
        if (!"SCHEDULED".equals(sessionStatus) && !"IN_PROGRESS".equals(sessionStatus))
            return "This session is " + String.valueOf(sessionStatus).toLowerCase().replace('_', ' ') + " and no longer accepts enrollments.";
        return null;
    }

    /**
     * Null when attendance (attended or no-show) may be recorded. A session that has not been started in the system still counts once
     * its start date has come, because trainers do not always press Start.
     */
    public static String attendanceProblem(String enrollmentStatus, String sessionStatus, LocalDate sessionStart, LocalDate today) {
        if (!"ENROLLED".equals(enrollmentStatus)) return "Attendance can only be recorded for someone who is still enrolled (this one is "
                + String.valueOf(enrollmentStatus).toLowerCase().replace('_', ' ') + ").";
        return heldProblem(sessionStatus, sessionStart, today);
    }

    /** Null when a result (pass or fail) may be recorded. */
    public static String resultProblem(String enrollmentStatus, String sessionStatus, LocalDate sessionStart, LocalDate today, BigDecimal score) {
        if (!"ENROLLED".equals(enrollmentStatus) && !"ATTENDED".equals(enrollmentStatus))
            return "A result can only be recorded for someone who is enrolled or has attended (this one is "
                    + String.valueOf(enrollmentStatus).toLowerCase().replace('_', ' ') + ").";
        String held = heldProblem(sessionStatus, sessionStart, today);
        if (held != null) return held;
        if (score != null && (score.signum() < 0 || score.compareTo(new BigDecimal("100")) > 0)) return "The score must be between 0 and 100.";
        return null;
    }

    private static String heldProblem(String sessionStatus, LocalDate start, LocalDate today) {
        if ("CANCELLED".equals(sessionStatus)) return "This session was cancelled.";
        if ("SCHEDULED".equals(sessionStatus) && start != null && today != null && start.isAfter(today))
            return "This session has not started yet (it starts on " + start + ").";
        return null;
    }

    /** Null when a session may be marked complete: everyone must have been dealt with first. */
    public static String sessionCompleteProblem(long stillEnrolled) {
        return stillEnrolled <= 0 ? null : "Record attendance for the " + stillEnrolled + (stillEnrolled == 1 ? " person" : " people")
                + " still shown as enrolled (attended, no-show or cancelled) before completing the session.";
    }

    /** Null when the capacity may be set to this: not below the people already on the session. */
    public static String capacityProblem(Integer capacity, long live) {
        if (capacity == null) return null;
        if (capacity < 1) return "Capacity must be at least 1.";
        if (capacity < live) return "There are already " + live + " people on this session, so the capacity cannot be less than that.";
        return null;
    }

    public static String enrolledTitle() { return "You have been put on a training session"; }

    public static String enrolledMessage(String course, LocalDate start, String venue) {
        return "You are booked on " + what(course) + " starting " + start + (venue == null || venue.isBlank() ? "." : " at " + venue.trim() + ".");
    }

    public static String movedMessage(String course, LocalDate start, LocalDate end) {
        return what(course) + " you are booked on has moved to " + start + (end != null && !end.equals(start) ? " to " + end : "") + ".";
    }

    public static String cancelledMessage(String course, LocalDate start) {
        return what(course) + " starting " + start + " has been cancelled. Your booking is cancelled too.";
    }

    private static String what(String course) { return course == null || course.isBlank() ? "a training session" : "the course \"" + course.trim() + "\""; }
}
