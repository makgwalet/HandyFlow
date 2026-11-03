package za.co.handyflow.platform.projects.domain.enums;

/** Lifecycle actions a user can request on a project. */
public enum ProjectAction {
    PROPOSE,    // PLANNING  -> PROPOSED
    APPROVE,    // PROPOSED  -> APPROVED  (locks baseline)
    RETURN,     // PROPOSED  -> PLANNING  (reason required)
    ACTIVATE,   // PLANNING/APPROVED/ON_HOLD -> ACTIVE
    HOLD,       // ACTIVE    -> ON_HOLD
    COMPLETE,   // ACTIVE    -> COMPLETED
    REOPEN,     // COMPLETED -> ACTIVE    (reason required)
    CLOSE,      // COMPLETED -> CLOSED    (lessons learned + closure checks)
    CANCEL      // any non-terminal -> CANCELLED (reason required)
}