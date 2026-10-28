package za.co.handyflow.platform.training.domain.model;

import java.time.LocalDate;

/** What an employee is told about their own certificates and bookings. Pure. */
public final class TrainingSelfRules {

    private TrainingSelfRules() {}

    public static final String VALID = "VALID", EXPIRING = "EXPIRING", EXPIRED = "EXPIRED", REVOKED = "REVOKED";
    /** A certificate is shown as expiring from this many days before its expiry date. */
    public static final int EXPIRING_WITHIN_DAYS = 30;

    /** How a certificate looks on the employee's screen. A lapsed one shows as expired even before the morning sweep has marked it. */
    public static String certificateState(String status, LocalDate expiry, LocalDate today) {
        if ("REVOKED".equals(status)) return REVOKED;
        if ("EXPIRED".equals(status) || CertificateExpiryRules.lapsed(expiry, today)) return EXPIRED;
        if (expiry != null && CertificateExpiryRules.daysLeft(expiry, today) <= EXPIRING_WITHIN_DAYS) return EXPIRING;
        return VALID;
    }

    /** The word for a booking: a place on a session that has not yet been held reads "Booked". */
    public static String bookingLabel(String enrollmentStatus) {
        return switch (enrollmentStatus == null ? "" : enrollmentStatus) {
            case "ENROLLED" -> "Booked";
            case "ATTENDED" -> "Attended";
            case "COMPLETED" -> "Completed";
            case "NO_SHOW" -> "Did not attend";
            case "CANCELLED" -> "Cancelled";
            default -> String.valueOf(enrollmentStatus);
        };
    }
}
