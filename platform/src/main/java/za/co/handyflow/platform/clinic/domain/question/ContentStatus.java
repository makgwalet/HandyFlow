package za.co.handyflow.platform.clinic.domain.question;

/**
 * Lifecycle of clinical configuration (DEC-CLINIC-001). Only ACTIVE content is served to clinicians,
 * and only inside its effective dates.
 */
public enum ContentStatus {
    DRAFT, CLINICAL_REVIEW, CHANGES_REQUESTED, APPROVED, ACTIVE, DEPRECATED, RETIRED;

    /** Allowed next states. APPROVED -> ACTIVE is the only way content reaches clinicians. */
    public boolean canMoveTo(ContentStatus next) {
        return switch (this) {
            case DRAFT             -> next == CLINICAL_REVIEW || next == RETIRED;
            case CLINICAL_REVIEW   -> next == APPROVED || next == CHANGES_REQUESTED;
            case CHANGES_REQUESTED -> next == CLINICAL_REVIEW || next == RETIRED;
            case APPROVED          -> next == ACTIVE || next == CHANGES_REQUESTED;
            case ACTIVE            -> next == DEPRECATED;
            case DEPRECATED        -> next == RETIRED;
            case RETIRED           -> false;
        };
    }
}
