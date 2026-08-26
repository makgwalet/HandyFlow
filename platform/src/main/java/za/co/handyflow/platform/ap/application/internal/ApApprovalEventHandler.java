package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.approvals.ApprovalCompletedEvent;

/**
 * FIX: backlog 1.1 — AP's migration onto the shared approval engine.
 * Reacts to ApprovalCompletedEvent for module="ap"/entityType="BILL" —
 * same @ApplicationModuleListener pattern already established for
 * hr.EmployeeCreatedEvent → contracting.ContractingHrEventHandler this
 * session.
 * <p>
 * WHY this listener exists ALONGSIDE ApService.approveBill() already
 * calling completeApprovalAndPostJournal() directly: a bill's final
 * approval can complete through TWO different routes — AP's own (kept
 * for URL backward-compatibility) POST /bills/{id}/approve, which calls
 * it synchronously so the HTTP caller gets back an already-APPROVED
 * bill with a real journal reference in the same response; OR the new
 * generic POST /api/v1/approvals/steps/{id}/approve, which has no way
 * to know anything AP-specific needs to happen. This listener is what
 * covers that second path. completeApprovalAndPostJournal() is
 * idempotent (guarded on bill status), so when a bill completes via
 * AP's own endpoint, this listener's call becomes a harmless no-op —
 * both paths are always safe to run together.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ApApprovalEventHandler {

    private final ApService apService;

    @ApplicationModuleListener
    void onApprovalCompleted(ApprovalCompletedEvent event) {
        if (!"ap".equals(event.module()) || !"BILL".equals(event.entityType())) {
            return; // not ours
        }

        try {
            if ("APPROVED".equals(event.outcome())) {
                apService.completeApprovalAndPostJournal(event.tenantId(), event.entityId());
                log.info("[AP] Bill={} tenant={} approval completed via engine listener", event.entityId(), event.tenantId());
            }  else if ("REJECTED".equals(event.outcome())) {
                // FIX: backlog 1.1b — real rejection handling, now that
                // ApBill has a genuine REJECTED status. Delegates to
                // ApService's own idempotent public method rather than
                // duplicating the reject-and-save logic here.
                apService.rejectBillFromEngine(event.tenantId(), event.entityId());
            }
        } catch (Exception e) {
            log.error("[AP] Failed to process approval completion for bill={} tenant={}: {}",
                    event.entityId(), event.tenantId(), e.getMessage(), e);
        }
    }
}