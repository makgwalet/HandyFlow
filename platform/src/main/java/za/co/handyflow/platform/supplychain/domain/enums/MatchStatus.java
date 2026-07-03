package za.co.handyflow.platform.supplychain.domain.enums;

/**
 * Three-way match result: PO ↔ Goods Receipt ↔ Supplier Invoice.
 *
 * WHY 3-way matching matters:
 * Before paying a supplier confirm all three legs:
 *   1. You ordered the goods   (Purchase Order exists)
 *   2. You received the goods  (Goods Receipt posted)
 *   3. The invoice matches     (amounts within tolerance)
 *
 * Without this, you can be invoiced for goods never delivered, or at
 * prices higher than the agreed PO rate.
 *
 * Tolerance: ±2% variance on total amount is considered MATCHED.
 * Variance > 2% triggers DISPUTE requiring human review.
 */
public enum MatchStatus {
    PENDING,        // not yet evaluated (no PO or GR linked)
    MATCHED,        // all amounts agree within tolerance
    PARTIAL_MATCH,  // some lines match, others don't (partial delivery)
    DISPUTE,        // significant variance — hold for review
    OVERRIDDEN      // manager forced approval despite mismatch — reason in match_notes
}