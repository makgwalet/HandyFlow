package za.co.handyflow.platform.legalcompliance.domain.model;

/**
 * COMPLIANT/DUE_SOON/OVERDUE are derived from reviewDate by
 * RegulatoryObligation.refreshStatus() (called by the scheduler, same
 * "recompute on schedule" shape as every other proactive-expiry check
 * in this codebase) — never set directly by a user for those three.
 * NON_COMPLIANT is the one status a user sets explicitly: "we checked,
 * and we are not meeting this obligation right now" is a real finding
 * distinct from "the review is merely overdue."
 */
public enum ObligationStatus {
    COMPLIANT,
    DUE_SOON,
    OVERDUE,
    NON_COMPLIANT
}
