package za.co.handyflow.platform.debtcollection.domain.model;

/** The recorded outcome that clears a work item. A work item cannot be completed without one. */
public enum WorkItemDisposition {
    CONTACT_MADE,
    NO_ANSWER_RETRY_SCHEDULED,
    PTP_CAPTURED,
    PLAN_RECORDED,
    PAYMENT_CONFIRMED,
    ESCALATED_TO_LEGAL,
    DISPUTE_LOGGED,
    WRITE_OFF_RECOMMENDED,
    NO_ACTION_REQUIRED,
    OTHER;

    /** Dispositions that say "nothing happened" must be justified in free text. */
    public boolean requiresNotes() {
        return this == NO_ACTION_REQUIRED || this == OTHER;
    }
}
