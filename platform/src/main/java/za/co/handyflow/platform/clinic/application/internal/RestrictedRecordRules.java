package za.co.handyflow.platform.clinic.application.internal;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who may open a restricted patient record, and which requests the restriction covers (CLINIC-DEC-008, 009).
 * Pure: no Spring, no database. The interceptor and the service only apply what this decides.
 */
public final class RestrictedRecordRules {
    private RestrictedRecordRules() {}

    public static final List<String> CATEGORIES = List.of("MENTAL_HEALTH", "HIV", "SEXUAL_REPRODUCTIVE", "STAFF", "VIP", "OTHER");
    public static final int MIN_REASON = 10;
    public static final int MAX_REASON = 500;
    public static final int DEFAULT_SESSION_MINUTES = 60;

    public static final String OPENED = "BREAK_GLASS_OPENED", VIEWED = "BREAK_GLASS_VIEWED",
            PRINTED = "BREAK_GLASS_DOCUMENT_PRINTED", EXPORTED = "BREAK_GLASS_DOCUMENT_EXPORTED";

    public enum Kind { PATIENT, CONSULTATION, LAB_RESULT, APPOINTMENT, PRESCRIPTION }
    /** A request that touches a patient's record through this resource. */
    public record Guard(Kind kind, UUID id) {}

    private static final String UUID_RE = "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})";
    private static final Pattern PATIENT      = Pattern.compile("/patients/" + UUID_RE + "(/.*)?$");
    private static final Pattern CONSULTATION = Pattern.compile("/consultations/" + UUID_RE + "(/.*)?$");
    private static final Pattern LAB_RESULT   = Pattern.compile("/lab/results/" + UUID_RE + "(/.*)?$");
    private static final Pattern APPT_CONSULT = Pattern.compile("/appointments/" + UUID_RE + "/consultation$");
    private static final Pattern PRESCRIPTION = Pattern.compile("/prescriptions/" + UUID_RE + "(/.*)?$");
    private static final Pattern DOCUMENT     = Pattern.compile("(-pdf|/pdf|/medical-certificate|/referral-letter)$");

    /** Parts of a patient path the restriction does not cover: identity, family, scheduling, consent and the break-glass calls themselves. */
    private static final Set<String> OPEN_PATIENT_PARTS = Set.of("", "/family", "/restriction", "/break-glass", "/break-glass/print",
            "/appointments", "/consent", "/consent/history");

    /** The clinical record a request path reaches, or null when the restriction does not apply to it. */
    public static Guard classify(String path) {
        if (path == null || path.contains("/billing/")) return null;   // money, not clinical content
        Matcher p = PATIENT.matcher(path);
        if (p.find()) {
            String rest = p.group(2) == null ? "" : p.group(2);
            return OPEN_PATIENT_PARTS.contains(rest) ? null : new Guard(Kind.PATIENT, UUID.fromString(p.group(1)));
        }
        Matcher l = LAB_RESULT.matcher(path);
        if (l.find()) return new Guard(Kind.LAB_RESULT, UUID.fromString(l.group(1)));
        Matcher a = APPT_CONSULT.matcher(path);
        if (a.find()) return new Guard(Kind.APPOINTMENT, UUID.fromString(a.group(1)));
        Matcher c = CONSULTATION.matcher(path);
        if (c.find()) return new Guard(Kind.CONSULTATION, UUID.fromString(c.group(1)));
        Matcher rx = PRESCRIPTION.matcher(path);
        if (rx.find()) return new Guard(Kind.PRESCRIPTION, UUID.fromString(rx.group(1)));
        return null;
    }

    /** True when the request produces a document (PDF, certificate, letter): printing or exporting, not just viewing. */
    public static boolean isDocument(String path) { return path != null && DOCUMENT.matcher(path).find(); }

    public enum Outcome { ALLOW, ALLOW_VIEW_UNDER_BREAK_GLASS, ALLOW_DOCUMENT_UNDER_BREAK_GLASS, DENY_RESTRICTED, DENY_DOCUMENT_NOT_PERMITTED }

    /**
     * @param restricted       the patient has an active restriction
     * @param standingAccess   the user holds CLINIC_RESTRICTED_RECORD_ACCESS
     * @param breakGlassActive the user has an unexpired break-glass session for this patient
     * @param document         the request produces a document
     * @param mayPrintOrExport the user holds CLINIC_BREAK_GLASS_PRINT or CLINIC_BREAK_GLASS_EXPORT
     */
    public static Outcome decide(boolean restricted, boolean standingAccess, boolean breakGlassActive, boolean document, boolean mayPrintOrExport) {
        if (!restricted || standingAccess) return Outcome.ALLOW;
        if (!breakGlassActive) return Outcome.DENY_RESTRICTED;
        if (document) return mayPrintOrExport ? Outcome.ALLOW_DOCUMENT_UNDER_BREAK_GLASS : Outcome.DENY_DOCUMENT_NOT_PERMITTED;
        return Outcome.ALLOW_VIEW_UNDER_BREAK_GLASS;
    }

    public static String category(String raw) {
        String c = raw == null ? "" : raw.trim().toUpperCase();
        if (!CATEGORIES.contains(c)) throw new IllegalArgumentException("Choose a restriction category: " + String.join(", ", CATEGORIES) + ".");
        return c;
    }

    /** The trimmed reason; throws with a plain message when it is missing, too short or too long. */
    public static String reason(String raw) {
        String r = raw == null ? "" : raw.trim();
        if (r.length() < MIN_REASON) throw new IllegalArgumentException("Give a reason of at least " + MIN_REASON + " characters.");
        if (r.length() > MAX_REASON) throw new IllegalArgumentException("The reason is limited to " + MAX_REASON + " characters.");
        return r;
    }

    /** Session length in minutes, falling back to the default for anything outside 5 to 480. */
    public static int sessionMinutes(Integer configured) {
        return configured == null || configured < 5 || configured > 480 ? DEFAULT_SESSION_MINUTES : configured;
    }
}
