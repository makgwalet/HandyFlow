package za.co.handyflow.platform.clinic.application.internal;

import java.util.Set;

/** The rules for what may be written as a note or an alert. Pure, so they are tested without a database. */
final class PatientNoteRules {
    static final int MAX_BODY = 1000;
    private static final Set<String> KINDS = Set.of("NOTE", "ALERT");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "CRITICAL");

    private PatientNoteRules() {}

    /** The kind in canonical form. */
    static String kind(String raw) {
        String k = raw == null ? "" : raw.trim().toUpperCase();
        if (!KINDS.contains(k)) throw new IllegalArgumentException("Kind must be NOTE or ALERT.");
        return k;
    }

    /** Alerts carry a severity (default WARNING); notes carry none. */
    static String severity(String kind, String raw) {
        if (!"ALERT".equals(kind)) return null;
        if (raw == null || raw.isBlank()) return "WARNING";
        String s = raw.trim().toUpperCase();
        if (!SEVERITIES.contains(s)) throw new IllegalArgumentException("Severity must be INFO, WARNING or CRITICAL.");
        return s;
    }

    static String body(String raw) {
        String b = raw == null ? "" : raw.trim();
        if (b.isEmpty()) throw new IllegalArgumentException("Write something in the note.");
        if (b.length() > MAX_BODY) throw new IllegalArgumentException("A note can be at most " + MAX_BODY + " characters.");
        return b;
    }
}
