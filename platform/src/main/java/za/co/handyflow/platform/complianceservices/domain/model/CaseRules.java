package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** How a case moves and what each move needs. Pure, so every rule can be tested. */
public final class CaseRules {

    private CaseRules() {}

    public static final int MIN_REASON = 15;
    public static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    public static final Set<String> LIVE = Set.of("OPEN", "IN_PROGRESS", "AWAITING_CLIENT");
    /** Assessment statuses that can start a case. Anything else is not a problem. */
    public static final Set<String> PROBLEM_STATUSES = Set.of("NON_COMPLIANT", "PARTIALLY_COMPLIANT", "EVIDENCE_MISSING", "EXPIRED", "UNDER_REVIEW");

    private static final Map<String, Set<String>> MOVES = Map.of(
            "OPEN", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("AWAITING_CLIENT", "RESOLVED", "CANCELLED"),
            "AWAITING_CLIENT", Set.of("IN_PROGRESS", "RESOLVED", "CANCELLED"),
            "RESOLVED", Set.of("CLOSED", "IN_PROGRESS"),
            "CLOSED", Set.of(),
            "CANCELLED", Set.of());

    public static boolean canMove(String from, String to) { return MOVES.getOrDefault(from, Set.of()).contains(to); }

    public static boolean isLive(String status) { return LIVE.contains(status); }

    public static String title(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < 3 || t.length() > 200) throw new IllegalArgumentException("The title must be 3 to 200 characters");
        return t;
    }

    public static String step(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < 3 || t.length() > 500) throw new IllegalArgumentException("A step must be 3 to 500 characters");
        return t;
    }

    public static String severity(String raw, String fallback) {
        String s = raw == null || raw.isBlank() ? fallback : raw.trim().toUpperCase();
        if (s == null || !SEVERITIES.contains(s)) throw new IllegalArgumentException("severity must be one of " + SEVERITIES.stream().sorted().toList());
        return s;
    }

    /** A case takes its first severity from the obligation's risk level. */
    public static String severityFromRisk(String risk) { return risk != null && SEVERITIES.contains(risk) ? risk : "MEDIUM"; }

    public static String reason(String raw, String what) {
        String r = raw == null ? "" : raw.trim();
        if (r.length() < MIN_REASON) throw new IllegalArgumentException("Say " + what + ", in at least " + MIN_REASON + " characters");
        return r.length() > 2000 ? r.substring(0, 2000) : r;
    }

    public static void dueDate(LocalDate due, LocalDate today) {
        if (due != null && due.isBefore(today)) throw new IllegalArgumentException("The due date cannot be in the past");
    }

    /** Why a case cannot be resolved yet. Empty means it can. */
    public static List<String> resolveBlockers(int openSteps, String note) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (openSteps > 0) out.add(openSteps + (openSteps == 1 ? " step is" : " steps are") + " still open. Finish or cancel " + (openSteps == 1 ? "it" : "them"));
        if (note == null || note.trim().length() < MIN_REASON) out.add("Say how it was resolved, in at least " + MIN_REASON + " characters");
        return out;
    }

    /** Closing is confirmed by someone other than the person who resolved it, unless they record why no one else can. */
    public static String closeException(java.util.UUID resolvedBy, java.util.UUID closer, String reason) {
        if (closer == null) throw new IllegalArgumentException("Who is closing this case?");
        if (!closer.equals(resolvedBy)) return null;
        String r = reason == null ? "" : reason.trim();
        if (r.length() < MIN_REASON) {
            throw new IllegalArgumentException("You resolved this case, so someone else should close it. If no one else can, give a reason of at least " + MIN_REASON + " characters");
        }
        return r.length() > 1000 ? r.substring(0, 1000) : r;
    }
}
