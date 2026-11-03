package za.co.handyflow.platform.projects.application.internal.document;

import za.co.handyflow.platform.projects.domain.enums.DocumentStatus;

import java.util.Set;

/**
 * Which documents may go out under which transmittal purpose. Pure — no Spring, no database.
 *
 *  - Nothing superseded, voided or rejected is ever sent (DocumentStatus.canBeTransmitted()).
 *  - FOR_INFORMATION, FOR_REVIEW and FOR_APPROVAL may carry documents still under review: the
 *    recipient is being asked to look at or approve them.
 *  - FOR_CONSTRUCTION and AS_BUILT are instructions to build or the record of what was built, so
 *    every document on them must be APPROVED. A draft drawing must never reach site as "for
 *    construction".
 */
public final class TransmittalRules {

    private TransmittalRules() {}

    public static final Set<String> PURPOSES = Set.of(
            "FOR_INFORMATION", "FOR_REVIEW", "FOR_APPROVAL", "FOR_CONSTRUCTION", "AS_BUILT");

    private static final Set<String> NEED_APPROVED = Set.of("FOR_CONSTRUCTION", "AS_BUILT");

    public static boolean requiresApproved(String purpose) {
        return purpose != null && NEED_APPROVED.contains(purpose);
    }

    /** Null when the document may be sent for this purpose; otherwise the reason it may not. */
    public static String problem(String purpose, DocumentStatus status) {
        if (status == null) return "has no status";
        if (!status.canBeTransmitted()) return "is " + status;
        if (requiresApproved(purpose) && status != DocumentStatus.APPROVED) {
            return "is " + status + " — only APPROVED documents can be issued " + purpose.replace('_', ' ').toLowerCase();
        }
        return null;
    }
}
