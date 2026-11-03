package za.co.handyflow.platform.projects.domain.enums;

/**
 * Change-order lifecycle.
 *
 *   DRAFT ──► SUBMITTED ──► APPROVED   (terminal — applied to the baseline)
 *     │           │  └────► REJECTED ──► DRAFT   (reopened as a new revision, then resubmitted)
 *     │           └───────► WITHDRAWN            (terminal)
 *     └───────────────────► WITHDRAWN
 *
 * Client sign-off and client disputes are tracked separately on an APPROVED change order;
 * they do not change this status.
 */
public enum ChangeOrderStatus {
    DRAFT, SUBMITTED, APPROVED, REJECTED, WITHDRAWN;

    public boolean canTransitionTo(ChangeOrderStatus target) {
        return switch (this) {
            case DRAFT     -> target == SUBMITTED || target == WITHDRAWN;
            case SUBMITTED -> target == APPROVED || target == REJECTED || target == WITHDRAWN;
            case REJECTED  -> target == DRAFT;
            case APPROVED, WITHDRAWN -> false;
        };
    }

    public boolean isTerminal() { return this == APPROVED || this == WITHDRAWN; }

    /** Can the content (cost lines, impacts, tasks) still be edited? */
    public boolean isEditable() { return this == DRAFT; }
}