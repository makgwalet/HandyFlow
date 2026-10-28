package za.co.handyflow.platform.training.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which Training requests are written to the audit trail and what they are called. Pure. A request is identified by its HTTP method and the route
 * it matched (for example POST /enrollments/{id}/complete), so a new endpoint missing from the table is not audited, and a test fails if one that
 * changes data is missing.
 */
public final class TrainingAuditRules {
    private TrainingAuditRules() {}

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    public static final int MAX_DETAIL = 500, MAX_RANGE_DAYS = 366, DEFAULT_DAYS = 90;

    /** "METHOD route" to the action name. Routes are relative to /api/v1/training. */
    private static final Map<String, String> ACTIONS = new LinkedHashMap<>();
    static {
        ACTIONS.put("POST /courses", "COURSE_CREATED");
        ACTIONS.put("PUT /courses/{id}", "COURSE_UPDATED");
        ACTIONS.put("PUT /courses/{id}/pass-mark", "COURSE_PASS_MARK_SET");
        ACTIONS.put("POST /courses/{id}/archive", "COURSE_ARCHIVED");
        ACTIONS.put("POST /courses/{id}/reactivate", "COURSE_REACTIVATED");
        ACTIONS.put("DELETE /courses/{id}", "COURSE_DELETED");
        ACTIONS.put("POST /sessions", "SESSION_CREATED");
        ACTIONS.put("PUT /sessions/{id}", "SESSION_UPDATED");
        ACTIONS.put("POST /sessions/{id}/reschedule", "SESSION_RESCHEDULED");
        ACTIONS.put("POST /sessions/{id}/start", "SESSION_STARTED");
        ACTIONS.put("POST /sessions/{id}/complete", "SESSION_COMPLETED");
        ACTIONS.put("POST /sessions/{id}/cancel", "SESSION_CANCELLED");
        ACTIONS.put("POST /sessions/{sessionId}/costs", "SESSION_COST_ADDED");
        ACTIONS.put("DELETE /sessions/{sessionId}/costs/{costId}", "SESSION_COST_REMOVED");
        ACTIONS.put("POST /sessions/{id}/evidence", "EVIDENCE_ATTACHED");
        ACTIONS.put("GET /sessions/{id}/attendance-register/pdf", "REGISTER_DOWNLOADED");
        ACTIONS.put("POST /sessions/{sessionId}/enrollments", "EMPLOYEE_ENROLLED");
        ACTIONS.put("POST /enrollments/{id}/attended", "MARKED_ATTENDED");
        ACTIONS.put("POST /enrollments/{id}/no-show", "MARKED_NO_SHOW");
        ACTIONS.put("POST /enrollments/{id}/complete", "RESULT_RECORDED");
        ACTIONS.put("POST /enrollments/{id}/cancel", "ENROLMENT_CANCELLED");
        ACTIONS.put("POST /enrollments/{enrollmentId}/certificate", "CERTIFICATE_ISSUED");
        ACTIONS.put("POST /certificates/{id}/revoke", "CERTIFICATE_REVOKED");
        ACTIONS.put("GET /certificates/{id}/pdf", "CERTIFICATE_DOWNLOADED");
        ACTIONS.put("POST /requirements", "REQUIREMENT_ADDED");
        ACTIONS.put("DELETE /requirements/{id}", "REQUIREMENT_REMOVED");
        ACTIONS.put("POST /mine/bookings", "SELF_BOOKED");
        ACTIONS.put("DELETE /mine/bookings/{id}", "SELF_BOOKING_CANCELLED");
        ACTIONS.put("GET /audit/export.csv", "AUDIT_EXPORTED");
    }

    /** The action name for a request, or null when it is not audited. */
    public static String actionFor(String httpMethod, String route) {
        if (httpMethod == null || route == null) return null;
        return ACTIONS.get(httpMethod.toUpperCase() + " " + route);
    }

    public static Set<String> actions() { return Set.copyOf(ACTIONS.values()); }

    /** Kind of record a route is about, for the log. */
    public static String entityType(String route) {
        if (route.startsWith("/courses")) return "COURSE";
        if (route.startsWith("/sessions")) return "SESSION";
        if (route.startsWith("/enrollments") || route.startsWith("/mine")) return "ENROLMENT";
        if (route.startsWith("/certificates")) return "CERTIFICATE";
        if (route.startsWith("/requirements")) return "REQUIREMENT";
        return "SETTINGS";
    }

    /** Text safe to store and show: control characters removed, spaces collapsed, cut to the limit. */
    public static String detail(String text) {
        if (text == null) return null;
        String t = text.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return null;
        return t.length() > MAX_DETAIL ? t.substring(0, MAX_DETAIL) : t;
    }

    /** Why this filter cannot be used, or null. An empty action means all of them. */
    public static String filterProblem(String action) {
        if (action == null || action.isBlank()) return null;
        return actions().contains(action.trim().toUpperCase()) ? null : "That is not an action in the audit trail.";
    }

    public static boolean isSuccess(int status) { return status >= 200 && status < 300; }

    /** Why this range cannot be used, or null. Either end may be missing. */
    public static String rangeProblem(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = toOrDefault(to, today), start = fromOrDefault(from, to, today);
        if (end.isAfter(today)) return "The end date cannot be in the future.";
        if (start.isAfter(end)) return "The start date is after the end date.";
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) return "Choose a range of " + MAX_RANGE_DAYS + " days or less.";
        return null;
    }

    public static LocalDate toOrDefault(LocalDate to, LocalDate today) { return to == null ? today : to; }

    public static LocalDate fromOrDefault(LocalDate from, LocalDate to, LocalDate today) {
        return from != null ? from : toOrDefault(to, today).minusDays(DEFAULT_DAYS);
    }

    public static Instant start(LocalDate day) { return day.atStartOfDay(ZONE).toInstant(); }

    public static Instant endExclusive(LocalDate day) { return day.plusDays(1).atStartOfDay(ZONE).toInstant(); }

    /** One CSV line per row; a value that starts like a spreadsheet formula is made harmless. */
    public static String csv(java.util.List<String> header, java.util.List<java.util.List<Object>> rows) {
        StringBuilder b = new StringBuilder();
        line(b, header.stream().map(h -> (Object) h).toList());
        for (java.util.List<Object> r : rows) line(b, r);
        return b.toString();
    }

    private static void line(StringBuilder b, java.util.List<Object> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) b.append(',');
            String v = cells.get(i) == null ? "" : cells.get(i).toString();
            if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !v.matches("^[+-]?[0-9][0-9.,]*$")) v = "'" + v;
            if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) v = "\"" + v.replace("\"", "\"\"") + "\"";
            b.append(v);
        }
        b.append("\r\n");
    }
}
