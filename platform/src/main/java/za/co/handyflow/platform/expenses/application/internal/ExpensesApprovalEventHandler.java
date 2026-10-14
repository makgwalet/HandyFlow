package za.co.handyflow.platform.expenses.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.approvals.ApprovalCompletedEvent;

/**
 * Finance patch F7: when an expense claim's approval finishes in the approval engine (for example from the Approvals
 * inbox rather than the Expenses screen), approve and post it, or reject it. Both service methods do nothing if the
 * Expenses screen already did it, so the two paths can run together.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ExpensesApprovalEventHandler {

    private final ExpensesService expensesService;

    @ApplicationModuleListener
    void onApprovalCompleted(ApprovalCompletedEvent event) {
        if (!"expenses".equals(event.module()) || !"CLAIM".equals(event.entityType())) return;
        try {
            if ("APPROVED".equals(event.outcome())) {
                expensesService.completeApproval(event.tenantId(), event.entityId(), null);
            } else if ("REJECTED".equals(event.outcome())) {
                expensesService.rejectFromEngine(event.tenantId(), event.entityId(), null, null);
            }
        } catch (Exception e) {
            log.error("[Expenses] Failed to apply approval outcome={} claim={} tenant={}: {}",
                    event.outcome(), event.entityId(), event.tenantId(), e.getMessage(), e);
        }
    }
}
