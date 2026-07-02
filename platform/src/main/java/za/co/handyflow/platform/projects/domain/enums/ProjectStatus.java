package za.co.handyflow.platform.projects.domain.enums;

/**
 * Type-safe project lifecycle statuses.
 *
 * WHY AN ENUM INSTEAD OF STRING?
 * ───────────────────────────────
 * With plain strings, a typo like "CANCELD" compiles fine, passes every layer,
 * and silently does nothing at runtime.  With an enum, the compiler catches it
 * the moment you type it.  Editors also auto-complete enum values — no more
 * copy-pasting string literals across 15 files.
 *
 * canTransitionTo() encodes the state-machine in ONE place.  Previously the
 * "requireStatus" guard in Project.java was the only enforcer, and any new
 * action had to remember to call it.  Now the transition rules live here and
 * are trivially unit-testable without spinning up Spring.
 */
public enum ProjectStatus {
    PLANNING,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    CANCELLED;

    /**
     * Returns true when a project in {@code this} status may move to {@code target}.
     *
     * <pre>
     *  PLANNING  ──► ACTIVE  ──► ON_HOLD ──► ACTIVE
     *                       └──► COMPLETED
     *  Any non-terminal ──► CANCELLED
     * </pre>
     */
    public boolean canTransitionTo(ProjectStatus target) {
        return switch (this) {
            case PLANNING  -> target == ACTIVE   || target == CANCELLED;
            case ACTIVE    -> target == ON_HOLD  || target == COMPLETED || target == CANCELLED;
            case ON_HOLD   -> target == ACTIVE   || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;   // terminal states — no exit
        };
    }

    /** Convenience: is the project still editable (not in a terminal state)? */
    public boolean isEditable() {
        return this != COMPLETED && this != CANCELLED;
    }
}
