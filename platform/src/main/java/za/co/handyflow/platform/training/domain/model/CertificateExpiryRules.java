package za.co.handyflow.platform.training.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * When a training certificate is worth a message. Pure: no database and no clock. A valid certificate is mentioned 30, 14, 7 and 1
 * days before it lapses, not every day; the day after it lapses it is marked expired and mentioned once more.
 */
public final class CertificateExpiryRules {

    private CertificateExpiryRules() {}

    public static final List<Integer> WARN_DAYS_BEFORE = List.of(30, 14, 7, 1);

    public static long daysLeft(LocalDate expiry, LocalDate today) {
        return ChronoUnit.DAYS.between(today, expiry);
    }

    public static boolean lapsed(LocalDate expiry, LocalDate today) {
        return expiry != null && expiry.isBefore(today);
    }

    /** A valid certificate with this expiry date is due a warning today. */
    public static boolean warnToday(LocalDate expiry, LocalDate today) {
        return expiry != null && !lapsed(expiry, today) && WARN_DAYS_BEFORE.contains((int) daysLeft(expiry, today));
    }

    public static String holderTitle(String course, long daysLeft) {
        return "Your " + course + " certificate " + (daysLeft <= 1 ? "expires tomorrow" : "expires in " + daysLeft + " days");
    }

    public static String holderWarning(String course, String number, LocalDate expiry) {
        return "Your certificate for \"" + course + "\" (" + number + ") expires on " + expiry + ". Ask your manager or HR about refresher training.";
    }

    public static String holderExpired(String course, String number, LocalDate expiry) {
        return "Your certificate for \"" + course + "\" (" + number + ") expired on " + expiry + ". Ask your manager or HR about refresher training.";
    }
}
