package za.co.handyflow.platform.projects.domain.enums;

/**
 * RFI lifecycle.
 *
 *   DRAFT ──► SUBMITTED ──► RESPONDED ──► CLOSED
 *     │           ▲             │            │
 *     │           └─────────────┘ (clarification requested: back to the responder)
 *     │                                      └──► RESPONDED  (reopened, with a reason)
 *     └─► CANCELLED   (from DRAFT, SUBMITTED or RESPONDED; final)
 *
 * Closing an RFI never deletes anything: every response, clarification and reopening stays in
 * project_rfi_events.
 */
public enum RfiStatus {
    DRAFT, SUBMITTED, RESPONDED, CLOSED, CANCELLED;

    public boolean canTransitionTo(RfiStatus target) {
        return switch (this) {
            case DRAFT     -> target == SUBMITTED || target == CANCELLED;
            case SUBMITTED -> target == RESPONDED || target == CANCELLED;
            case RESPONDED -> target == SUBMITTED || target == CLOSED || target == CANCELLED;
            case CLOSED    -> target == RESPONDED;
            case CANCELLED -> false;
        };
    }

    public boolean isTerminal() { return this == CANCELLED; }

    /** Still waiting on someone's answer. */
    public boolean isAwaitingResponse() { return this == SUBMITTED; }
}