package za.co.handyflow.platform.projects.domain.enums;

/**
 * Project lifecycle states.
 *
 * <pre>
 *  PLANNING ──► PROPOSED ──► APPROVED ──► ACTIVE ◄──► ON_HOLD
 *     │  └────────────────────(fast track, unless gate enforced)──► ACTIVE
 *     ▲            │
 *     └── (return) ┘                        ACTIVE ──► COMPLETED ──► CLOSED
 *                                           COMPLETED ──► ACTIVE   (reopen)
 *  Any non-terminal state ──► CANCELLED
 * </pre>
 *
 * PLANNING doubles as the "draft" state so existing rows need no data migration.
 * The PLANNING -> ACTIVE fast track is a structural possibility here; whether it is
 * allowed for a deployment is decided by ProjectLifecycleService
 * (app.projects.require-approval-gate).
 */
public enum ProjectStatus {
    PLANNING,
    PROPOSED,
    APPROVED,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    CLOSED,
    CANCELLED;

    public boolean canTransitionTo(ProjectStatus target) {
        return switch (this) {
            case PLANNING  -> target == PROPOSED || target == ACTIVE || target == CANCELLED;
            case PROPOSED  -> target == APPROVED || target == PLANNING || target == CANCELLED;
            case APPROVED  -> target == ACTIVE   || target == CANCELLED;
            case ACTIVE    -> target == ON_HOLD  || target == COMPLETED || target == CANCELLED;
            case ON_HOLD   -> target == ACTIVE   || target == CANCELLED;
            case COMPLETED -> target == CLOSED   || target == ACTIVE;
            case CLOSED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    /** Can day-to-day content (tasks, risks, time, project details) still be changed? */
    public boolean isEditable() {
        return this != COMPLETED && this != CLOSED && this != CANCELLED;
    }

    /** Has the schedule baseline been locked (approved or beyond)? */
    public boolean isBaselineLocked() {
        return this != PLANNING && this != PROPOSED;
    }
}