package za.co.handyflow.platform.approvals;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * FIX: backlog 1.1 — Creative's migration surfaced a need
 * ApprovalCompletedEvent alone can't cover: Creative's SEQUENTIAL mode
 * only emails the next approver once the previous one has actually
 * acted (see CreativeService.notifyNextApprover()) — that needs to know
 * when ONE STEP completes, not just when the whole request reaches a
 * terminal state. ApprovalCompletedEvent only fires once, at the end;
 * this fires after every single step action, approved or rejected,
 * whether or not it happened to also complete the request.
 * <p>
 * Published for every step action across every module using the engine
 * (not Creative-specific) — AP doesn't currently listen for it (its
 * only step is materially "the last action = the outcome", already
 * covered by ApprovalCompletedEvent), but nothing stops a future module
 * needing per-step reactions the same way Creative does.
 */
public record ApprovalStepActedEvent(
        TenantId tenantId,
        String module,
        String entityType,
        UUID entityId,
        UUID approvalRequestId,
        UUID approvalStepId,
        int stepOrder,
        String outcome,   // "APPROVED" | "REJECTED"
        Instant occurredOn
) implements DomainEvent {

    public static ApprovalStepActedEvent of(TenantId tenantId, String module, String entityType,
                                            UUID entityId, UUID approvalRequestId, UUID approvalStepId,
                                            int stepOrder, String outcome) {
        return new ApprovalStepActedEvent(tenantId, module, entityType, entityId,
                approvalRequestId, approvalStepId, stepOrder, outcome, Instant.now());
    }
}