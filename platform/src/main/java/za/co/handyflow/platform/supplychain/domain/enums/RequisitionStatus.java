package za.co.handyflow.platform.supplychain.domain.enums;

/**
 * DRAFT -> PENDING_APPROVAL -> APPROVED | REJECTED
 * PENDING_APPROVAL -> DRAFT (withdrawn);  REJECTED -> DRAFT (re-opened for correction)
 * DRAFT | PENDING_APPROVAL | APPROVED -> CANCELLED
 * Whether an APPROVED requisition has been turned into POs is NOT a stored status — it is derived
 * from the conversions, so a cancelled PO releases its quantity automatically.
 */
public enum RequisitionStatus {
    DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, CANCELLED
}
