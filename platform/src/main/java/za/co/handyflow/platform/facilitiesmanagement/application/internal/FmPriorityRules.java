package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import java.util.List;

/**
 * Priority resolution for new requests. Transparent, code-level rules for now (the SLA chunk will tie
 * priority to response/resolution targets, and per-tenant configuration comes later):
 * <ul>
 *   <li>valid values: LOW, NORMAL, HIGH, URGENT, EMERGENCY (default NORMAL);</li>
 *   <li>an EMERGENCY category is never below EMERGENCY;</li>
 *   <li>a safety-impacting request is never below HIGH.</li>
 * </ul>
 * A requester or approver can raise a priority above the floor but not lower it below.
 */
public final class FmPriorityRules {

    private static final List<String> ORDER = List.of("LOW", "NORMAL", "HIGH", "URGENT", "EMERGENCY");

    private FmPriorityRules() {}

    public static String resolve(String requested, String category, boolean safetyImpact) {
        String priority = requested == null || requested.isBlank() ? "NORMAL" : requested.trim().toUpperCase();
        if (!ORDER.contains(priority)) {
            throw new IllegalArgumentException("priority must be one of " + ORDER);
        }
        String floor = "EMERGENCY".equals(category) ? "EMERGENCY" : (safetyImpact ? "HIGH" : "LOW");
        return ORDER.indexOf(priority) >= ORDER.indexOf(floor) ? priority : floor;
    }
}