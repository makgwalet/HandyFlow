package za.co.handyflow.platform.debtcollection.domain.model;

/** DRAFT → IN_REVIEW → APPROVED → TRANSFERRED. A reviewer can send IN_REVIEW/APPROVED back to DRAFT; CANCELLED ends it. */
public enum HandoverStatus {
    DRAFT, IN_REVIEW, APPROVED, TRANSFERRED, CANCELLED;

    public boolean isOpen() { return this == DRAFT || this == IN_REVIEW || this == APPROVED; }
}
