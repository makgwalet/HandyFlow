package za.co.handyflow.platform.supplychain.domain.enums;

/**
 * Supplier invoice (AP) lifecycle.
 *
 * Normal flow:  RECEIVED → APPROVED → PAID
 * Dispute flow: RECEIVED → UNDER_REVIEW → DISPUTED → (resolve) → APPROVED → PAID
 */
public enum InvoiceStatus {
    RECEIVED,       // logged in the system, not yet reviewed
    UNDER_REVIEW,   // being checked against PO and GR
    APPROVED,       // cleared for payment
    DISPUTED,       // discrepancy found — hold payment until resolved
    PAID,           // payment made and reference recorded
    CANCELLED
}