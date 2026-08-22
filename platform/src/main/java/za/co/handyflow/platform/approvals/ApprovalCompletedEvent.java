package za.co.handyflow.platform.approvals;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Published once an ApprovalRequest reaches a terminal outcome
 * (APPROVED or REJECTED — RETURNED_FOR_CORRECTION does NOT publish this;
 * that outcome expects the submitting module to react to the request's
 * own status via ApprovalFacade, typically by prompting a resubmission,
 * not by treating it as "done"). Same pattern as identity.TenantCreatedEvent
 * → billing and hr.EmployeeCreatedEvent → contracting: a plain DomainEvent
 * at the module root, consumed elsewhere via @ApplicationModuleListener.
 * <p>
 * Carries only identifiers, not the request's full state — a listener
 * that needs more calls back into ApprovalFacade.getRequestForEntity()
 * rather than this event growing into a second, event-shaped copy of
 * ApprovalRequest.
 */
public record ApprovalCompletedEvent(
        TenantId tenantId,
        String module,
        String entityType,
        UUID entityId,
        UUID approvalRequestId,
        String outcome,   // "APPROVED" | "REJECTED"
        Instant occurredOn
) implements DomainEvent {

    public static ApprovalCompletedEvent of(TenantId tenantId, String module, String entityType,
                                            UUID entityId, UUID approvalRequestId, String outcome) {
        return new ApprovalCompletedEvent(tenantId, module, entityType, entityId,
                approvalRequestId, outcome, Instant.now());
    }
}