package za.co.handyflow.platform.crm.domain.model;

/**
 * CustomerStatus — the business-level lifecycle state of a customer.
 *
 * WHY separate from soft-delete?
 * deletedAt controls whether the record APPEARS in queries (soft-delete).
 * CustomerStatus controls the BUSINESS meaning of an active record.
 *
 * A customer can be NOT deleted (deletedAt = null) but INACTIVE
 * (no bookings in 90+ days).  That's still a valid, visible record —
 * just one that staff should chase up, not one that doesn't exist.
 *
 * BLOCKED means "do not accept new bookings" — e.g. bad debt, fraud.
 * This is separate from delete because we want to KEEP the history
 * but prevent new transactions.
 */
public enum CustomerStatus {
    /** Normal state — accepts bookings and invoices. */
    ACTIVE,
    /** No activity in 90+ days (can be set automatically by a scheduler). */
    INACTIVE,
    /** Manually blocked — do not transact. Reason stored in notes/activity. */
    BLOCKED
}
