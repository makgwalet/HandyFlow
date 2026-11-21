package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;

/** The 24-hour SAPS reporting clock for lost or stolen firearms. Pure. */
final class LostReportRules {

    static final Duration SAPS_WINDOW = Duration.ofHours(24);
    static final int CASE_MAX = 60;

    private LostReportRules() {}

    static Instant dueAt(Instant reportedAt) { return reportedAt.plus(SAPS_WINDOW); }

    /** Overdue when there is still no case number and the due time has passed. */
    static boolean overdue(Instant now, Instant dueAt, String caseNumber) {
        return dueAt != null && (caseNumber == null || caseNumber.isBlank()) && now.isAfter(dueAt);
    }

    /** Trimmed case number, null when blank. Throws on one that is too long to be real. */
    static String normaliseCase(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String c = raw.trim();
        if (c.length() > CASE_MAX) throw new IllegalArgumentException("SAPS case number is too long");
        return c;
    }
}
