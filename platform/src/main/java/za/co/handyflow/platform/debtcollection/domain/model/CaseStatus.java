package za.co.handyflow.platform.debtcollection.domain.model;

/**
 * Lifecycle of an internal debt collection case. This is a proposed default
 * workflow shape, not a confirmed hard requirement — flagged the same way
 * LitigationStatus's lifecycle was flagged for Module 1.
 * <p>
 * OPEN → DEMAND_SENT → (PAYMENT_PLAN_ACTIVE | DISPUTED) → ... → CLOSED, with
 * HANDED_TO_LEGAL reachable from any non-terminal state as an escalation
 * path. CLOSED is only ever set via DebtCollectionCase.close(), never via
 * advanceStatus() — mirroring LitigationMatter's own two-method split
 * (advanceStatus for workflow states, close() for the terminal action, with
 * a ClosureReason recorded).
 */
public enum CaseStatus {
    /** Case just opened — soft invoicing/accounting reminders have failed, staff is now managing this debtor directly. */
    OPEN,
    /** A formal demand (letter/AOD) has been sent. */
    DEMAND_SENT,
    /** Debtor has agreed to a structured PaymentPlan and is currently keeping to it. */
    PAYMENT_PLAN_ACTIVE,
    /** Debtor disputes the debt (amount, validity, or both) — collection activity paused pending resolution. */
    DISPUTED,
    /** Escalated to legal action / external attorneys — this module no longer drives the case day-to-day. */
    HANDED_TO_LEGAL,
    /** Debt fully recovered. */
    SETTLED,
    /** Debt formally written off as uncollectable — see DebtCollectionCase.writeOff(). */
    WRITTEN_OFF,
    /** Case closed — terminal, set only by close(). */
    CLOSED
}
