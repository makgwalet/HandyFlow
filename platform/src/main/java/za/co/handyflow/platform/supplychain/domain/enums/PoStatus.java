package za.co.handyflow.platform.supplychain.domain.enums;

/**
 * Purchase order lifecycle states.
 *
 * WHY enums instead of String constants?
 * If you type "PENDING_APROVAL" (typo) as a String, the compiler says nothing —
 * the bug ships, and at runtime the status never matches. With an enum the
 * compiler rejects the typo immediately.
 *
 * JPA stores the name() of each constant in the database, matching the
 * CHECK constraint values already in V86, so no migration is needed.
 */
public enum PoStatus {
    DRAFT,              // being built — lines can be added/removed
    PENDING_APPROVAL,   // submitted, waiting for approver
    APPROVED,           // approved, not yet sent to supplier
    SENT,               // transmitted to supplier
    ACKNOWLEDGED,       // supplier confirmed receipt
    PARTIALLY_RECEIVED, // some lines received via goods receipt
    FULLY_RECEIVED,     // all lines received
    INVOICED,           // supplier invoice matched and approved
    CANCELLED
}