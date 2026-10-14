package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.approvals.ApprovalCompletedEvent;

/**
 * Finance patch F9: when a credit note's approval finishes in the approval engine (for example from the Approvals inbox),
 * issue or reject it. Both service methods do nothing if the credit note was already decided.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class InvoicingApprovalEventHandler {

    private final CreditNoteService creditNoteService;

    @ApplicationModuleListener
    void onApprovalCompleted(ApprovalCompletedEvent event) {
        if (!"invoicing".equals(event.module()) || !"CREDIT_NOTE".equals(event.entityType())) return;
        try {
            if ("APPROVED".equals(event.outcome())) creditNoteService.completeApproval(event.tenantId(), event.entityId(), null);
            else if ("REJECTED".equals(event.outcome())) creditNoteService.rejectFromEngine(event.tenantId(), event.entityId(), null, null);
        } catch (Exception e) {
            log.error("[Invoicing] Failed to apply approval outcome={} creditNote={} tenant={}: {}",
                    event.outcome(), event.entityId(), event.tenantId(), e.getMessage(), e);
        }
    }
}
