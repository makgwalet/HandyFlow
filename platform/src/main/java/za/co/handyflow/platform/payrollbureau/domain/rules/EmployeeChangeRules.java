package za.co.handyflow.platform.payrollbureau.domain.rules;

import java.math.BigDecimal;
import java.util.UUID;

/** Pure rules for held salary and bank-detail changes (audit 2026-11-26, D4). */
public final class EmployeeChangeRules {

    private EmployeeChangeRules() {}

    public static boolean differs(BigDecimal a, BigDecimal b) {
        BigDecimal x = a == null ? BigDecimal.ZERO : a;
        BigDecimal y = b == null ? BigDecimal.ZERO : b;
        return x.compareTo(y) != 0;
    }

    public static boolean differs(String a, String b) {
        return !norm(a).equals(norm(b));
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }

    /** Why a decision cannot be made, or null when it can. */
    public static String decisionProblem(String status, UUID requestedBy, UUID decidedBy) {
        if (!"PENDING".equals(status)) return "This change has already been " + status.toLowerCase() + ".";
        if (decidedBy == null) return "Your user could not be identified.";
        if (decidedBy.equals(requestedBy)) return "A different user must approve or reject this change. Use cancel to withdraw your own request.";
        return null;
    }

    /** Why a request cannot be cancelled, or null when it can: only the requester, only while pending. */
    public static String cancelProblem(String status, UUID requestedBy, UUID userId) {
        if (!"PENDING".equals(status)) return "This change has already been " + status.toLowerCase() + ".";
        if (userId == null || !userId.equals(requestedBy)) return "Only the person who asked for the change can cancel it.";
        return null;
    }
}
