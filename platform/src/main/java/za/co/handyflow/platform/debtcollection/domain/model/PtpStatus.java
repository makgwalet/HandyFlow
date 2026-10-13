package za.co.handyflow.platform.debtcollection.domain.model;

/** proposed → active → kept | partially kept | broken | cancelled. Only ACTIVE promises are tracked/evaluated. */
public enum PtpStatus {
    PROPOSED, ACTIVE, KEPT, PARTIALLY_KEPT, BROKEN, CANCELLED;

    public boolean isOpen() { return this == PROPOSED || this == ACTIVE; }
}
