package za.co.handyflow.platform.crm.application.internal;

import java.time.LocalDate;

/** Small rules for the audit log: trimming what is stored, and the date range and page size a search may ask for. Pure, no database. */
final class AuditRules {
    static final int NAME_MAX = 200, DETAIL_MAX = 500, MAX_PAGE_SIZE = 100, DEFAULT_PAGE_SIZE = 50;

    private AuditRules() {}

    /** Trimmed, blank becomes null, and cut to fit with an ellipsis. */
    static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.strip();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }

    static void checkRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("The start date cannot be after the end date");
    }

    static int pageSize(Integer requested) {
        if (requested == null || requested < 1) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    static int pageNumber(Integer requested) { return requested == null || requested < 0 ? 0 : requested; }
}
