package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.approvals.ApprovalCompletedEvent;

/** When the Approvals engine decides a fuel purchase order, place it (approved) or send it back to draft (rejected). Price changes and purchase requisitions are decided the same way. */
@Slf4j
@Component
@RequiredArgsConstructor
class FuelApprovalEventHandler {

    private final FuelPurchasingService purchasing;
    private final FuelPricingService pricing;
    private final FuelRequisitionService requisitions;

    @ApplicationModuleListener
    void onApprovalCompleted(ApprovalCompletedEvent event) {
        if (!"fuel".equals(event.module())) return;
        boolean price = "PRICE".equals(event.entityType());
        boolean requisition = "REQUISITION".equals(event.entityType());
        if (!price && !requisition && !"PURCHASE_ORDER".equals(event.entityType())) return;
        try {
            if (price) pricing.applyApprovalOutcome(event.tenantId(), event.entityId(), event.outcome());
            else if (requisition) requisitions.applyApprovalOutcome(event.tenantId(), event.entityId(), event.outcome());
            else purchasing.applyApprovalOutcome(event.tenantId(), event.entityId(), event.outcome());
        } catch (Exception e) {
            log.error("[Fuel] Failed to apply approval outcome={} purchaseOrder={} tenant={}: {}",
                    event.outcome(), event.entityId(), event.tenantId(), e.getMessage(), e);
        }
    }
}
