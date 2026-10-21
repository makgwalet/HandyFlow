package za.co.handyflow.platform.catalogue.domain.model;

/**
 * Where a price change is. PENDING waits for an approver; SCHEDULED is decided but not yet due;
 * APPLIED is in force (or was); REJECTED and CANCELLED never took effect.
 */
public enum PriceChangeStatus {
    PENDING, SCHEDULED, APPLIED, REJECTED, CANCELLED;

    /** Still open: can be cancelled. */
    public boolean open() { return this == PENDING || this == SCHEDULED; }
}
