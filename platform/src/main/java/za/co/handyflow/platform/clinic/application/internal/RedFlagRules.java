package za.co.handyflow.platform.clinic.application.internal;

import java.util.List;
import java.util.Set;

/**
 * Red flag review before signing (Q-5, CLINIC-DEC-021, patch 0181). Every red flag that is active on a consultation must have been
 * reviewed by a clinician before the visit is signed; a clinician may sign without reviewing only by giving a reason, which is audited.
 * Reviewed means seen, not resolved, and nothing here judges a flag. Pure: no Spring, no database.
 */
final class RedFlagRules {

    static final int MAX_REASON = 500;

    private RedFlagRules() {}

    record Active(String groupCode, String flagCode, String label) {
        String key() { return RedFlagRules.key(groupCode, flagCode); }
    }

    static String key(String groupCode, String flagCode) { return groupCode + ":" + flagCode; }

    /** Active flags that no clinician has reviewed yet, in the order given. */
    static List<Active> unreviewed(List<Active> active, Set<String> reviewedKeys) {
        return active.stream().filter(a -> !reviewedKeys.contains(a.key())).toList();
    }

    /** Throws when flags are unreviewed and no usable reason was given; returns the trimmed reason, or null when nothing is outstanding. */
    static String requireReviewedOrReason(List<Active> unreviewed, String overrideReason) {
        if (unreviewed.isEmpty()) return null;
        if (overrideReason == null || overrideReason.isBlank()) {
            String names = String.join(", ", unreviewed.stream().map(Active::label).toList());
            throw new IllegalStateException("Cannot sign yet: " + unreviewed.size() + " red flag"
                    + (unreviewed.size() == 1 ? " has" : "s have") + " not been reviewed (" + names
                    + "). Review " + (unreviewed.size() == 1 ? "it" : "them") + " or give a reason to sign without reviewing.");
        }
        String reason = overrideReason.trim();
        if (reason.length() > MAX_REASON) {
            throw new IllegalArgumentException("The reason is limited to " + MAX_REASON + " characters.");
        }
        return reason;
    }
}
