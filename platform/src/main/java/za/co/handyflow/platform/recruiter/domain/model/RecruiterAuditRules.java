package za.co.handyflow.platform.recruiter.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which requests are written to the audit trail and what they are called. Pure. A request is identified by its HTTP method and the route it matched
 * (for example POST /applications/{id}/stage), so a new endpoint that is not in the table is not audited, and a test fails if one that changes data
 * is missing.
 */
public final class RecruiterAuditRules {
    private RecruiterAuditRules() {}

    public static final int MAX_DETAIL = 500;

    /** "METHOD route" to the action name. Routes are relative to /api/v1/recruiter. */
    private static final Map<String, String> ACTIONS = new LinkedHashMap<>();
    static {
        ACTIONS.put("POST /jobs", "JOB_CREATED");
        ACTIONS.put("PUT /jobs/{id}", "JOB_UPDATED");
        ACTIONS.put("POST /jobs/{id}/action/{action}", "JOB_STATUS_CHANGED");
        ACTIONS.put("DELETE /jobs/{id}", "JOB_DELETED");
        ACTIONS.put("PUT /jobs/{id}/requisition", "REQUISITION_CHANGED");
        ACTIONS.put("PUT /jobs/{id}/screening", "SCREENING_CHANGED");
        ACTIONS.put("POST /jobs/{jobId}/interview-rounds", "ROUND_CHANGED");
        ACTIONS.put("PUT /jobs/{jobId}/interview-rounds/{roundId}", "ROUND_CHANGED");
        ACTIONS.put("DELETE /jobs/{jobId}/interview-rounds/{roundId}", "ROUND_CHANGED");
        ACTIONS.put("POST /jobs/{id}/approval/submit", "JOB_APPROVAL_SENT");
        ACTIONS.put("POST /jobs/{id}/approval/decision", "JOB_APPROVAL_DECIDED");
        ACTIONS.put("POST /applications/{id}/stage", "STAGE_MOVED");
        ACTIONS.put("POST /applications/{id}/score", "APPLICATION_SCORED");
        ACTIONS.put("POST /applications/{id}/interviews", "INTERVIEW_SCHEDULED");
        ACTIONS.put("POST /applications/{appId}/interviews/{intId}/reschedule", "INTERVIEW_RESCHEDULED");
        ACTIONS.put("POST /applications/{appId}/interviews/{intId}/outcome", "INTERVIEW_OUTCOME");
        ACTIONS.put("POST /applications/{id}/convert-to-employee", "CONVERTED_TO_EMPLOYEE");
        ACTIONS.put("POST /applications/{id}/offer-letter/send", "OFFER_SENT");
        ACTIONS.put("GET /applications/{id}/offer-letter", "OFFER_LETTER_DOWNLOADED");
        ACTIONS.put("POST /applications/{id}/offer/response", "OFFER_RESPONSE");
        ACTIONS.put("POST /applications/{id}/offer/revise", "OFFER_REVISED");
        ACTIONS.put("POST /applications/{id}/offer-approval/submit", "OFFER_APPROVAL_SENT");
        ACTIONS.put("POST /applications/{id}/offer-approval/decision", "OFFER_APPROVAL_DECIDED");
        ACTIONS.put("PUT /applications/{id}/referral", "REFERRAL_CHANGED");
        ACTIONS.put("POST /applications/{id}/verification", "CHECK_ADDED");
        ACTIONS.put("POST /applications/{id}/verification/{checkId}/decision", "CHECK_DECIDED");
        ACTIONS.put("DELETE /applications/{id}/verification/{checkId}", "CHECK_REMOVED");
        ACTIONS.put("PUT /settings", "SETTINGS_CHANGED");
        ACTIONS.put("POST /retention/remove", "PERSONAL_DATA_REMOVED");
        ACTIONS.put("POST /pools", "POOL_CREATED");
        ACTIONS.put("DELETE /pools/{poolId}", "POOL_DELETED");
        ACTIONS.put("POST /pools/{poolId}/members", "POOL_MEMBER_ADDED");
        ACTIONS.put("DELETE /pools/{poolId}/members/{applicantId}", "POOL_MEMBER_REMOVED");
        ACTIONS.put("PUT /candidates/{applicantId}/tags", "TAGS_CHANGED");
        ACTIONS.put("POST /candidates/{applicantId}/apply", "CANDIDATE_PUT_FORWARD");
        ACTIONS.put("GET /reports/{id}/export.csv", "REPORT_EXPORTED");
        ACTIONS.put("GET /audit/export.csv", "AUDIT_EXPORTED");
    }

    /** The action name for a request, or null when it is not audited. */
    public static String actionFor(String httpMethod, String route) {
        if (httpMethod == null || route == null) return null;
        return ACTIONS.get(httpMethod.toUpperCase() + " " + route);
    }

    public static Set<String> actions() { return Set.copyOf(ACTIONS.values()); }

    public static Map<String, String> table() { return Map.copyOf(ACTIONS); }

    /** Kind of record a route is about, for the log. */
    public static String entityType(String route) {
        if (route.startsWith("/applications")) return "APPLICATION";
        if (route.startsWith("/jobs")) return "JOB";
        if (route.startsWith("/pools")) return "POOL";
        if (route.startsWith("/candidates")) return "CANDIDATE";
        if (route.startsWith("/retention")) return "CANDIDATE";
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
}
