package za.co.handyflow.platform.training.domain.model;

import java.time.LocalDate;

/** When an employee may book themselves onto a session, and take themselves off it. Pure. Each returns null when the step may go ahead. */
public final class TrainingBookingRules {

    private TrainingBookingRules() {}

    public static String bookProblem(String employeeStatus, String sessionStatus, boolean openBooking, LocalDate start, LocalDate today,
                                     boolean alreadyBooked, Integer capacity, long liveBookings) {
        if (employeeStatus != null && !"ACTIVE".equals(employeeStatus)) return "Only active employees can book training.";
        if (!"SCHEDULED".equals(sessionStatus)) return "This session is no longer open for booking.";
        if (!openBooking) return "This session is not open for you to book yourself. Ask your manager or HR to put you on it.";
        if (start != null && today != null && start.isBefore(today)) return "This session has already started.";
        if (alreadyBooked) return "You are already booked on this session.";
        if (capacity != null && liveBookings >= capacity) return "This session is full.";
        return null;
    }

    /** A person may take themselves off only a place they still hold, and only until the day the session starts. */
    public static String cancelProblem(String enrollmentStatus, String sessionStatus, LocalDate start, LocalDate today) {
        if (!"ENROLLED".equals(enrollmentStatus)) return "Only a booking that is still waiting for the session can be cancelled.";
        if (!"SCHEDULED".equals(sessionStatus) || (start != null && today != null && !start.isAfter(today)))
            return "The session starts today or has started. Ask your manager or HR to change your booking.";
        return null;
    }

    public static String managerBookedMessage(String person, String course, LocalDate start) {
        return person + " booked themselves onto \"" + course + "\"" + (start == null ? "." : " starting " + start + ".");
    }

    public static String managerCancelledMessage(String person, String course, LocalDate start) {
        return person + " cancelled their booking on \"" + course + "\"" + (start == null ? "." : " (it starts " + start + ").");
    }
}
