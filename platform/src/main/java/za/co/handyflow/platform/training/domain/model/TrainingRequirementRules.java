package za.co.handyflow.platform.training.domain.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Who must hold which certificate, and whether they do. Pure: no database and no clock.
 * <p>
 * A requirement names a course and, optionally, a job title and a department. Someone is required to hold the certificate when every
 * part the requirement names matches theirs, ignoring case, spaces at the ends and doubled spaces. A requirement naming neither applies to
 * everyone.
 */
public final class TrainingRequirementRules {

    private TrainingRequirementRules() {}

    public static final String OK = "OK", EXPIRING = "EXPIRING", EXPIRED = "EXPIRED", MISSING = "MISSING", BOOKED = "BOOKED";
    public static final int MAX_TEXT = 150;

    /** A certificate as the rules need it: its stored status and expiry date (null when it never expires). */
    public record Held(String status, LocalDate expiry) {}

    /** Lower case, trimmed, doubled spaces collapsed; null when nothing is left. */
    public static String norm(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    /** Cleaned for storing: trimmed and spaces collapsed, but with the capitals as typed. Null when blank. */
    public static String clean(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    public static boolean applies(String requiredTitle, String requiredDepartment, String jobTitle, String department) {
        String rt = norm(requiredTitle), rd = norm(requiredDepartment);
        if (rt != null && !rt.equals(norm(jobTitle))) return false;
        return rd == null || rd.equals(norm(department));
    }

    /** Null when the requirement can be saved. */
    public static String problem(boolean courseFound, boolean courseActive, boolean certificationOffered, String jobTitle, String department) {
        if (!courseFound) return "That course was not found.";
        if (!courseActive) return "That course is archived. Choose a course that is in use.";
        if (!certificationOffered) return "That course does not issue a certificate, so there is nothing to check people against. Switch on \"certificate offered\" for the course first.";
        if (jobTitle != null && jobTitle.trim().length() > MAX_TEXT) return "The job title is too long.";
        if (department != null && department.trim().length() > MAX_TEXT) return "The department is too long.";
        return null;
    }

    /**
     * Where a person stands on one required course. A certificate that is valid today counts, however soon it runs out (it is then
     * "expiring"); a withdrawn one never does. Without one, a booking on a session not yet over shows as "booked".
     */
    public static String status(List<Held> certificates, boolean bookedOnComingSession, LocalDate today) {
        List<Held> live = certificates.stream().filter(c -> !"REVOKED".equals(c.status())).toList();
        Held best = live.stream().filter(c -> !TrainingSelfRules.EXPIRED.equals(TrainingSelfRules.certificateState(c.status(), c.expiry(), today)))
                .max(Comparator.comparing((Held c) -> c.expiry() == null ? LocalDate.MAX : c.expiry())).orElse(null);
        if (best != null) {
            return TrainingSelfRules.EXPIRING.equals(TrainingSelfRules.certificateState(best.status(), best.expiry(), today)) ? EXPIRING : OK;
        }
        if (bookedOnComingSession) return BOOKED;
        return live.isEmpty() ? MISSING : EXPIRED;
    }

    /** The expiry date to show next to a status: the latest of the certificates that count, or of all when none does. */
    public static LocalDate shownExpiry(List<Held> certificates) {
        return certificates.stream().filter(c -> !"REVOKED".equals(c.status()) && c.expiry() != null).map(Held::expiry).max(Comparator.naturalOrder()).orElse(null);
    }

    /** A person who needs to act: nothing valid and nothing booked. */
    public static boolean needsAction(String status) { return MISSING.equals(status) || EXPIRED.equals(status); }

    public static String label(String status) {
        return switch (status == null ? "" : status) {
            case OK -> "Valid";
            case EXPIRING -> "Expiring soon";
            case EXPIRED -> "Expired";
            case MISSING -> "Not held";
            case BOOKED -> "Booked";
            default -> String.valueOf(status);
        };
    }

    /** Who a requirement is for, in words. */
    public static String appliesTo(String jobTitle, String department) {
        String t = clean(jobTitle), d = clean(department);
        if (t == null && d == null) return "Everyone";
        if (t != null && d != null) return t + " in " + d;
        return t != null ? t : "Everyone in " + d;
    }
}
