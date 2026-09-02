package za.co.handyflow.platform.debtcollection.domain.model;

/** Why a case was closed — recorded by DebtCollectionCase.close(). */
public enum ClosureReason {
    PAID_IN_FULL,
    SETTLED_PARTIAL,
    WRITTEN_OFF,
    HANDED_TO_LEGAL,
    DISPUTE_UPHELD,
    OTHER
}
