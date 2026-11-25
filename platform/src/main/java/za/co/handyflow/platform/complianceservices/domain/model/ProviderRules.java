package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Rules for outside professionals, with no database in them. An accreditation someone enters is only a claim. It counts as usable when a
 * different person has checked it, said how, and it has not expired. Nothing here calls anyone accredited because a number was typed in.
 */
public final class ProviderRules {

    private ProviderRules() {}

    public static final String CLAIMED = "CLAIMED";
    public static final String VERIFIED = "VERIFIED";
    public static final String REJECTED = "REJECTED";
    public static final String RETIRED = "RETIRED";
    public static final int MIN_NOTE = 10;
    /** An accreditation this close to expiring is raised in the work queue. A proposed starting value. */
    public static final int EXPIRY_WARNING_DAYS = 30;
    public static final Set<String> KINDS = Set.of("INDIVIDUAL", "FIRM");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static String required(String raw, String what, int max) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(what + " is required");
        if (t.length() > max) throw new IllegalArgumentException(what + " is too long (at most " + max + " characters)");
        return t;
    }

    public static String optional(String raw, String what, int max) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) return null;
        if (t.length() > max) throw new IllegalArgumentException(what + " is too long (at most " + max + " characters)");
        return t;
    }

    public static String kind(String raw) {
        String k = raw == null || raw.isBlank() ? "INDIVIDUAL" : raw.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(k)) throw new IllegalArgumentException("A provider is an individual or a firm");
        return k;
    }

    public static String email(String raw) {
        String t = optional(raw, "The email address", 255);
        if (t != null && !EMAIL.matcher(t).matches()) throw new IllegalArgumentException("That email address does not look right");
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    public static String note(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < MIN_NOTE) throw new IllegalArgumentException("Say why or how, in at least " + MIN_NOTE + " characters");
        if (t.length() > 4000) throw new IllegalArgumentException("The note is too long");
        return t;
    }

    /** The service codes a provider offers: tidied, no blanks, no repeats. */
    public static List<String> serviceCodes(Collection<String> raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) for (String c : raw) {
            String t = c == null ? "" : c.trim();
            if (t.isEmpty()) continue;
            if (t.length() > 60) throw new IllegalArgumentException("A service code is too long");
            out.add(t);
        }
        if (out.size() > 50) throw new IllegalArgumentException("A provider can offer at most 50 services");
        return new ArrayList<>(out);
    }

    public static void checkDates(LocalDate issued, LocalDate expires) {
        if (issued != null && expires != null && expires.isBefore(issued)) throw new IllegalArgumentException("The accreditation cannot expire before it was issued");
    }

    /** Only a person other than the one who entered it may check an accreditation. */
    public static void checkChecker(UUID recordedBy, UUID checker) {
        if (checker == null) throw new IllegalArgumentException("Someone must record this");
        if (checker.equals(recordedBy)) throw new IllegalStateException("Someone other than the person who entered it must check an accreditation");
    }

    /** Usable means checked by someone else and not expired. A claim, a rejection or a retired one is never usable. */
    public static boolean usable(String status, LocalDate expiresOn, LocalDate today) {
        return VERIFIED.equals(status) && (expiresOn == null || !expiresOn.isBefore(today));
    }

    /** NO_EXPIRY, VALID, EXPIRING (within the warning period) or EXPIRED. */
    public static String expiry(LocalDate expiresOn, LocalDate today) {
        if (expiresOn == null) return "NO_EXPIRY";
        if (expiresOn.isBefore(today)) return "EXPIRED";
        return ChronoUnit.DAYS.between(today, expiresOn) <= EXPIRY_WARNING_DAYS ? "EXPIRING" : "VALID";
    }

    /** What the work queue shows for one accreditation of an active provider, or null when nothing needs doing. */
    public static WorkQueueRules.Item queueItem(String providerName, String body, String status, LocalDate expiresOn, LocalDate recordedOn, LocalDate today) {
        String title = providerName + ": " + body;
        if (CLAIMED.equals(status)) {
            long days = ChronoUnit.DAYS.between(recordedOn, today);
            return new WorkQueueRules.Item("PROVIDER_ACCREDITATION", days > 7 ? WorkQueueRules.MEDIUM : WorkQueueRules.LOW, null, null, title,
                    "Accreditation entered " + days + (days == 1 ? " day" : " days") + " ago and not yet checked", null, "providers", false);
        }
        if (!VERIFIED.equals(status) || expiresOn == null) return null;
        String e = expiry(expiresOn, today);
        if ("EXPIRED".equals(e)) {
            long days = ChronoUnit.DAYS.between(expiresOn, today);
            return new WorkQueueRules.Item("PROVIDER_ACCREDITATION", WorkQueueRules.HIGH, null, null, title, "Accreditation expired " + days + (days == 1 ? " day" : " days") + " ago", expiresOn, "providers", false);
        }
        if ("EXPIRING".equals(e)) {
            long days = ChronoUnit.DAYS.between(today, expiresOn);
            return new WorkQueueRules.Item("PROVIDER_ACCREDITATION", WorkQueueRules.MEDIUM, null, null, title, days == 0 ? "Accreditation expires today" : "Accreditation expires in " + days + (days == 1 ? " day" : " days"), expiresOn, "providers", false);
        }
        return null;
    }
}
