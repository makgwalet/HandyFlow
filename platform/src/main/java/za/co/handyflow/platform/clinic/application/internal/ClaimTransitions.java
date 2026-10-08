package za.co.handyflow.platform.clinic.application.internal;

import java.util.Map;
import java.util.Set;

/** Which status changes a claim may go through (a claim can no longer jump from DRAFT straight to PAID). */
final class ClaimTransitions {

    private ClaimTransitions() {}

    private static final Map<String, Set<String>> ALLOWED_FROM = Map.of(
            "ACCEPT",  Set.of("SUBMITTED"),
            "REJECT",  Set.of("SUBMITTED", "ACCEPTED"),
            "PAID",    Set.of("ACCEPTED", "PARTIAL"),
            "PARTIAL", Set.of("ACCEPTED", "PARTIAL"));

    /** @throws IllegalStateException when the action is not allowed from the claim's current status */
    static void require(String action, String currentStatus) {
        Set<String> from = ALLOWED_FROM.get(action);
        if (from == null) throw new IllegalArgumentException("Unknown action: " + action);
        if (!from.contains(currentStatus)) {
            throw new IllegalStateException("A claim that is " + currentStatus + " cannot be marked "
                    + describe(action) + " (allowed from: " + String.join(", ", from.stream().sorted().toList()) + ")");
        }
    }

    private static String describe(String action) {
        return switch (action) {
            case "ACCEPT" -> "accepted";
            case "REJECT" -> "rejected";
            case "PAID" -> "paid";
            default -> "partially paid";
        };
    }
}
